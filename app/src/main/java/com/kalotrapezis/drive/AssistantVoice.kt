package com.kalotrapezis.drive

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** The Mistral API key (Settings › Assistant), encrypted by a key that never leaves the phone's keystore. */
internal object AssistantKey {
    private const val ALIAS = "tetra_assistant_key"
    private fun prefs(c: Context) = c.getSharedPreferences("assistant", Context.MODE_PRIVATE)

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return ks.getKey(ALIAS, null) as? SecretKey ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    fun get(c: Context): String? = prefs(c).getString("key", null)?.let { saved -> runCatching {
        val b = Base64.decode(saved, Base64.NO_WRAP)
        Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, b, 0, 12)) }.doFinal(b, 12, b.size - 12).decodeToString()
    }.onFailure { WriterLog.e("Assistant: the key could not be read", it) }.getOrNull() }

    fun set(c: Context, key: String) {
        if (key.isBlank()) { prefs(c).edit().remove("key").apply(); return }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secret()) }
        prefs(c).edit().putString("key", Base64.encodeToString(cipher.iv + cipher.doFinal(key.trim().toByteArray()), Base64.NO_WRAP)).apply()
    }
}

/** Mistral over HTTPS; every failure a [DriveProblem] the screen says in the user's language. */
internal object AssistantNet {
    fun key(c: Context): String = AssistantKey.get(c) ?: throw DriveProblem("No Mistral key.", R.string.assistant_no_key)

    fun post(key: String, path: String, body: String): String =
        send(key, path, "application/json", body.toByteArray()).decodeToString()

    fun download(key: String, fileId: String): ByteArray = send(key, "/files/$fileId/content", null, null)

    /** Speech to text with Voxtral, in Greek. */
    fun transcribe(key: String, pcm: ShortArray): String {
        val b = "tetra" + System.nanoTime()
        val body = ByteArrayOutputStream().apply {
            fun field(name: String, value: String) = write("--$b\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            field("model", Mistral.TRANSCRIBE); field("language", "el")
            write("--$b\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
            write(Vad.wav(pcm)); write("\r\n--$b--\r\n".toByteArray())
        }.toByteArray()
        return JSONObject(send(key, "/audio/transcriptions", "multipart/form-data; boundary=$b", body).decodeToString()).optString("text").trim()
    }

    private fun send(key: String, path: String, type: String?, body: ByteArray?): ByteArray {
        val c = URL(Mistral.API + path).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000; c.readTimeout = 120_000 // a picture takes a while
            c.setRequestProperty("Authorization", "Bearer $key")
            if (body != null) { c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", type); c.outputStream.use { it.write(body) } }
            val code = try { c.responseCode } catch (e: IOException) { throw DriveProblem("Offline: ${e.message}", R.string.assistant_offline) }
            if (code in 200..299) return c.inputStream.use { it.readBytes() }
            val said = runCatching { c.errorStream?.use { it.readBytes().decodeToString() } }.getOrNull().orEmpty().take(300)
            WriterLog.w("Assistant: Mistral $path answered $code $said")
            throw when (code) {
                401 -> DriveProblem("Wrong key.", R.string.assistant_bad_key)
                429 -> DriveProblem("Too many requests.", R.string.assistant_busy)
                else -> DriveProblem("Mistral $code", R.string.assistant_server, code)
            }
        } catch (e: IOException) {
            throw DriveProblem("Offline: ${e.message}", R.string.assistant_offline)
        } finally { c.disconnect() }
    }
}

/** A conversation with Tetra's tools on this phone's files: the key read at each request, so a new one counts at once. */
internal fun assistantChat(c: Context, forgetMs: Long = Long.MAX_VALUE): AssistantChat {
    val app = c.applicationContext
    val tetra = driveRoot()
    val api = Mistral { path, body -> AssistantNet.post(AssistantNet.key(app), path, body) }
    val tools = AssistantTools(tetra, TimerControl.device(),
        changed = { what -> when (what) {
            "calendar" -> { ScopedSync.soon(app, "calendar", 0, orWhole = true); CalendarAlarms.planSoon(app) }
            "timer" -> { ScopedSync.soon(app, "timer", 0, orWhole = true); TimerService.check(app) }
            "notes" -> NotesSync.soon(app)
        } },
        search = { q ->
            val (text, sources, _) = Mistral.output(api.conversation(q, "web_search", "Search the web and answer briefly with the facts found."))
            text + if (sources.isEmpty()) "" else "\nSources: " + sources.take(3).joinToString("; ")
        },
        image = { prompt ->
            val id = Mistral.output(api.conversation(prompt, "image_generation", "Draw the picture asked for.")).third.firstOrNull() ?: error("No picture came back.")
            File(File(tetra, FOLDER).apply { mkdirs() }, "image-${System.currentTimeMillis()}.png").apply { writeBytes(AssistantNet.download(AssistantNet.key(app), id)) }
        })
    return AssistantChat(api, tools, forgetMs)
}

/** Pictures the assistant made: Tetra/Assistant, a folder of Files like any other. */
private const val FOLDER = "Assistant"

internal fun hasMic(c: Context) = c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * Records until the speaker pauses, [stop] says so, or nobody spoke for [waitMs]; null when nothing was said.
 * [speaking] follows whether a phrase has begun, for the screen.
 */
@SuppressLint("MissingPermission")
internal fun listen(waitMs: Long, stop: () -> Boolean, speaking: (Boolean) -> Unit = {}): ShortArray? {
    val size = maxOf(AudioRecord.getMinBufferSize(Vad.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), Vad.FRAME * 2 * 8)
    val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, Vad.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
    val vad = Vad()
    val started = System.currentTimeMillis()
    try {
        rec.startRecording()
        while (true) {
            val frame = ShortArray(Vad.FRAME)
            var n = 0
            while (n < frame.size) { val r = rec.read(frame, n, frame.size - n); if (r <= 0) return null; n += r }
            val done = vad.push(frame)
            speaking(vad.speaking)
            if (done || stop()) break
            if (!vad.speaking && System.currentTimeMillis() - started > waitMs) return null
        }
    } finally { runCatching { rec.stop() }; rec.release() }
    return vad.phrase().takeIf { it.isNotEmpty() }
}

/*
 * Tetra as the phone's digital assistant (Settings › Apps › Default apps › Digital assistant app): holding the power
 * button, a swipe up from a bottom corner, or holding Home shows a session, which opens the assistant's sheet over
 * whatever is on the screen, listening at once. Android asks an assistant for a speech recognizer too: Voxtral's.
 */
class AssistantService : VoiceInteractionService()

class AssistantSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = object : VoiceInteractionSession(this) {
        override fun onShow(args: Bundle?, showFlags: Int) {
            super.onShow(args, showFlags)
            runCatching { startAssistantActivity(Intent(context, AssistantSheetActivity::class.java)) }.onFailure { WriterLog.e("Assistant: the sheet did not open", it) }
            hide() // the sheet is its own window; the session's would sit over it
        }
    }
}

/** Speech to text for other apps that ask Android's recognizer, while Tetra is the assistant: one phrase, by Voxtral. */
class AssistantRecognition : RecognitionService() {
    @Volatile private var stop = false

    override fun onStartListening(intent: Intent?, cb: Callback) {
        val key = AssistantKey.get(this) ?: return cb.error(SpeechRecognizer.ERROR_CLIENT)
        if (!hasMic(this)) return cb.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        stop = false
        Thread {
            runCatching {
                cb.readyForSpeech(Bundle())
                val pcm = listen(8_000, { stop }) ?: return@Thread cb.error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                cb.endOfSpeech()
                cb.results(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(AssistantNet.transcribe(key, pcm))) })
            }.onFailure { WriterLog.w("Assistant: recognition for another app failed", it); runCatching { cb.error(SpeechRecognizer.ERROR_NETWORK) } }
        }.start()
    }

    override fun onStopListening(cb: Callback) { stop = true }
    override fun onCancel(cb: Callback) { stop = true }
}
