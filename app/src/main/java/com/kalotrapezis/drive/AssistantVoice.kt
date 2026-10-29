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

    /** Speech to text with Voxtral, in Greek, spelling [terms] as given (My words). */
    fun transcribe(key: String, pcm: ShortArray, terms: List<String>, bill: Bill? = null): String {
        val b = "tetra" + System.nanoTime()
        val body = ByteArrayOutputStream().apply {
            fun field(name: String, value: String) = write("--$b\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray())
            field("model", Mistral.TRANSCRIBE); field("language", "el")
            terms.forEach { field("context_bias", it) }
            write("--$b\r\nContent-Disposition: form-data; name=\"file\"; filename=\"speech.wav\"\r\nContent-Type: audio/wav\r\n\r\n".toByteArray())
            write(Vad.wav(pcm)); write("\r\n--$b--\r\n".toByteArray())
        }.toByteArray()
        return JSONObject(send(key, "/audio/transcriptions", "multipart/form-data; boundary=$b", body).decodeToString()).optString("text").trim()
            .also { bill?.minutes(Mistral.TRANSCRIBE, pcm.size.toDouble() / Vad.RATE) }
    }

    /** A failed live connection in the user's words, as [send] says a failed request. */
    fun problem(code: Int?): DriveProblem = when (code) {
        null -> DriveProblem("Offline", R.string.assistant_offline)
        401 -> DriveProblem("Wrong key.", R.string.assistant_bad_key)
        429 -> DriveProblem("Too many requests.", R.string.assistant_busy)
        else -> DriveProblem("Mistral $code", R.string.assistant_server, code)
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
            throw problem(code)
        } catch (e: IOException) {
            throw DriveProblem("Offline: ${e.message}", R.string.assistant_offline)
        } finally { c.disconnect() }
    }
}

/** A conversation with Tetra's tools on this phone's files: the key read at each request, so a new one counts at once. */
internal fun assistantChat(c: Context, forgetMs: Long = Long.MAX_VALUE): AssistantChat {
    val app = c.applicationContext
    val tetra = driveRoot()
    val api = Mistral({ path, body -> AssistantNet.post(AssistantNet.key(app), path, body) })
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

/** "My words" (Settings › AI Assist): what Voxtral should spell right, sent with every recording as its context_bias. */
internal object AssistantWords {
    const val DEFAULT = "Καλοτραπέζης, Θεολόγος, Tetra, ΥΠΕΧΩΔΕ"
    private fun prefs(c: Context) = c.getSharedPreferences("assistant", Context.MODE_PRIVATE)
    fun words(c: Context): String = prefs(c).getString("words", DEFAULT) ?: DEFAULT
    fun setWords(c: Context, words: String) = prefs(c).edit().putString("words", words).apply()
    /** Off unless switched on: the favourites' names then go to Mistral too. */
    fun contacts(c: Context) = prefs(c).getBoolean("contactWords", false)
    fun setContacts(c: Context, on: Boolean) = prefs(c).edit().putBoolean("contactWords", on).apply()

    fun terms(c: Context): List<String> = Mistral.terms(words(c), if (!contacts(c)) emptyList() else
        runCatching { ContactsFiles.all(contactsRoot()).map { it.info }.filter { it.favourite }.map { it.name }.take(50) }.getOrDefault(emptyList()))
}

/**
 * Live (the waveform button): the microphone streamed to Voxtral realtime over a WebSocket that carries the key itself,
 * so no server of Tetra's own; Voxtral says when a turn ends ([turn]), and the screen hands it to the same chat and tools
 * as walkie-talkie. While [muted] (thinking, reading the answer aloud) silence is sent instead, so the phone does not
 * hear itself and the stream stays open. Mistral's voice-agent SDK needs a LiveKit server, so it is not used.
 */
internal class AssistantLive(
    private val key: String,
    private val terms: List<String>,
    private val heard: (String) -> Unit,
    private val turn: (String) -> Unit,
    private val level: (Float) -> Unit,
    private val failed: (Throwable) -> Unit,
) {
    @Volatile var muted = false
        set(on) { field = on; if (!on) synchronized(text) { text.clear() } }
    @Volatile private var open = true
    private var socket: okhttp3.WebSocket? = null
    private val text = StringBuilder()

    fun start() {
        val request = okhttp3.Request.Builder().url("wss://api.mistral.ai/v1/audio/transcriptions/realtime?model=${Mistral.REALTIME}")
            .header("Authorization", "Bearer $key").build()
        socket = okhttp3.OkHttpClient().newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) = runCatching { event(webSocket, JSONObject(text)) }
                .onFailure { WriterLog.w("AI Assist live: ${it.message}") }.let { }
            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                WriterLog.w("AI Assist live: closed (${response?.code}) ${t.message}")
                if (open) { open = false; failed(AssistantNet.problem(response?.code)) }
            }
        })
    }

    private fun event(ws: okhttp3.WebSocket, e: JSONObject) {
        when (e.optString("type")) {
            "session.created" -> {
                ws.send(JSONObject().put("type", "session.update").put("session", JSONObject()
                    .put("audio_format", JSONObject().put("encoding", "pcm_s16le").put("sample_rate", Vad.RATE))
                    .put("language", "el").put("context_bias", org.json.JSONArray(terms)).put("target_streaming_delay_ms", 480)
                    .put("turn_detection", JSONObject())).toString())
                Thread(::record, "ai-assist-live").start()
            }
            "transcription.text.delta" -> if (!muted) heard(synchronized(text) { text.append(e.optString("text")).toString() })
            "turn.end" -> if (!muted) synchronized(text) { text.toString().trim().also { text.clear() } }.takeIf { it.isNotEmpty() }?.let(turn)
            "error" -> { WriterLog.w("AI Assist live: $e"); if (open) { end(); failed(IllegalStateException(e.optJSONObject("error")?.optString("message") ?: "Mistral")) } }
        }
    }

    @SuppressLint("MissingPermission")
    private fun record() {
        val size = maxOf(AudioRecord.getMinBufferSize(Vad.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), Vad.FRAME * 2 * 8)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, Vad.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
        val chunk = ShortArray(Vad.FRAME * 4) // 120 ms
        val bytes = java.nio.ByteBuffer.allocate(chunk.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        try {
            rec.startRecording()
            while (open) {
                var n = 0
                while (n < chunk.size) { val r = rec.read(chunk, n, chunk.size - n); if (r <= 0) return; n += r }
                level((Vad.rms(chunk) / 3000).coerceIn(0.0, 1.0).toFloat())
                if (muted) chunk.fill(0)
                bytes.clear(); chunk.forEach { bytes.putShort(it) }
                socket?.send(JSONObject().put("type", "input_audio.append").put("audio", Base64.encodeToString(bytes.array(), Base64.NO_WRAP)).toString())
            }
        } catch (e: Exception) {
            WriterLog.e("AI Assist live: the microphone stopped", e)
        } finally { runCatching { rec.stop() }; rec.release() }
    }

    fun end() {
        if (!open) return
        open = false
        socket?.send("""{"type":"input_audio.end"}""")
        socket?.close(1000, null)
    }
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
                cb.results(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(AssistantNet.transcribe(key, pcm, AssistantWords.terms(this@AssistantRecognition)))) })
            }.onFailure { WriterLog.w("Assistant: recognition for another app failed", it); runCatching { cb.error(SpeechRecognizer.ERROR_NETWORK) } }
        }.start()
    }

    override fun onStopListening(cb: Callback) { stop = true }
    override fun onCancel(cb: Callback) { stop = true }
}
