package com.kalotrapezis.drive

import android.content.Context
import android.util.Log
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/*
 * Review › Spelling (asked 2026-09-30): the phone's own spell checker (Gboard's, as the system has it) asked about the
 * document's words, Greek ones in Greek and the rest in English. Nothing leaves the phone. A word asked once is
 * remembered for the session.
 */
internal class WriterSpelling(context: Context) {
    private val tsm = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
    private val sessions = HashMap<String, Session?>()
    /** Word → its suggestions when it looks misspelled, else null (known to be right). */
    private val known = HashMap<String, List<String>?>()
    private val lock = Mutex()

    /** One language's session: one request at a time, its answer handed back to the waiting caller. */
    private inner class Session(locale: Locale) : SpellCheckerSession.SpellCheckerSessionListener {
        var waiting: ((Array<SentenceSuggestionsInfo>?) -> Unit)? = null
        // Android 12+: the attributes asked for, the answers on the main thread (the older call never answered on Android 16).
        val session: SpellCheckerSession? = runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 31) tsm?.newSpellCheckerSession(SpellCheckerSession.SpellCheckerSessionParams.Builder().setLocale(locale)
                .setSupportedAttributes(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO or SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY or SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS).build(),
                ContextCompatMain, this)
            else @Suppress("DEPRECATION") tsm?.newSpellCheckerSession(null, locale, this, false)
        }.onFailure { Log.w("Writer", "spelling session", it) }.getOrNull()
        override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) = Unit
        override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
            @Suppress("UNCHECKED_CAST") waiting?.invoke(results as Array<SentenceSuggestionsInfo>?)
        }
    }

    private fun session(lang: String) = sessions.getOrPut(lang) { Session(Locale.forLanguageTag(if (lang == "el") "el-GR" else "en-US")).takeIf { it.session != null } }

    /** Greek letters: the Greek checker; any other: the English one. */
    private fun langOf(word: String) = if (word.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.GREEK }) "el" else "en"

    /** Which of [words] look misspelled, with their suggestions (words already asked answered from memory). */
    suspend fun check(words: Collection<String>): Map<String, List<String>> = lock.withLock {
        for ((lang, ask) in words.filter { it !in known }.distinct().groupBy(::langOf)) {
            val s = session(lang) ?: run { ask.forEach { known[it] = null }; continue } // no checker for it: nothing marked
            for (batch in ask.chunked(40)) {
                if (silent) break // not answering: the rest waits for the next time
                val answer = withTimeoutOrNull(4000) {
                    suspendCancellableCoroutine<Array<SentenceSuggestionsInfo>?> { k ->
                        s.waiting = { r -> s.waiting = null; if (k.isActive) k.resume(r) }
                        runCatching { s.session!!.getSentenceSuggestions(batch.map { TextInfo(it) }.toTypedArray(), 5) }
                            .onFailure { Log.w("Writer", "spelling", it); s.waiting = null; k.resume(null) }
                    }
                } ?: run { silent = true; continue } // no answer (yet): asked again next time
                batch.forEachIndexed { i, w ->
                    val info = answer.getOrNull(i)
                    known[w] = info?.let { sent -> (0 until sent.suggestionsCount).map { sent.getSuggestionsInfoAt(it) } }?.firstOrNull { si ->
                        si.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO != 0
                    }?.let { si -> (0 until si.suggestionsCount.coerceAtLeast(0)).map { si.getSuggestionAt(it) } }
                }
            }
        }
        words.mapNotNull { w -> known[w]?.let { w to it } }.toMap()
    }

    /**
     * The phone's checker did not answer (found 2026-09-30 on the tablet: the system never connected to Gboard's —
     * spell checking off in Gboard or in the system's settings). Said once on the screen; asked again next time.
     */
    var silent = false
    /** Whether the system has a spell checker switched on at all. */
    val available: Boolean get() = runCatching { tsm?.isSpellCheckerEnabled == true && tsm.currentSpellCheckerInfo != null }.getOrDefault(false)

    fun close() = sessions.values.forEach { runCatching { it?.session?.close() } }

    companion object {
        private val ContextCompatMain = java.util.concurrent.Executor { r -> android.os.Handler(android.os.Looper.getMainLooper()).post(r) }
        /** The words of a text and where they are: letters, with an apostrophe inside (don't, l'école); no digits. */
        private val WORD = Regex("""\p{L}+(?:['’]\p{L}+)*""")
        fun words(text: String): List<IntRange> = WORD.findAll(text).map { it.range }.filter { it.last > it.first }.toList() // one-letter words skipped
    }
}
