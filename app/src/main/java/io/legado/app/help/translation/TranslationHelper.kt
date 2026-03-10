package io.legado.app.help.translation

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import io.legado.app.utils.MD5Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Helper class for ML Kit on-device translation.
 *
 * Supports offline (downloaded model) mode.
 * Translated results are cached using an MD5-based key to avoid re-translating.
 */
class TranslationHelper(private val cacheDir: File) {

    private var translator: Translator? = null
    private var currentFrom: String = ""
    private var currentTo: String = ""

    /**
     * Initialise (or re-initialise) the ML Kit translator for the given language pair.
     */
    fun init(from: String, to: String) {
        if (from == currentFrom && to == currentTo && translator != null) return
        translator?.close()
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(from)
            .setTargetLanguage(to)
            .build()
        translator = Translation.getClient(options)
        currentFrom = from
        currentTo = to
    }

    /**
     * Download the ML Kit model for the current language pair.
     * Calls [onComplete] once the download is complete.
     *
     * @throws Exception if the download fails.
     */
    suspend fun downloadModel(onComplete: (() -> Unit)? = null) = withContext(Dispatchers.IO) {
        val t = translator ?: throw IllegalStateException("Translator not initialised")
        Tasks.await(t.downloadModelIfNeeded())
        onComplete?.invoke()
    }

    /**
     * Translate [text] by paragraph using the offline ML Kit model, reporting progress.
     *
     * @param onProgress called after each paragraph with (current, total) counts.
     * @return the fully translated text (paragraphs joined by newline).
     */
    suspend fun translateByParagraph(
        text: String,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): String = withContext(Dispatchers.IO) {
        val cacheKey = cacheKey(text)
        getCached(cacheKey)?.let { return@withContext it }

        val t = translator ?: throw IllegalStateException("Translator not initialised")
        val paragraphs = text.lines()
        val total = paragraphs.size
        val result = paragraphs.mapIndexed { index, paragraph ->
            onProgress?.invoke(index + 1, total)
            if (paragraph.isBlank()) paragraph else Tasks.await(t.translate(paragraph))
        }.joinToString("\n")
        putCache(cacheKey, result)
        result
    }

    fun close() {
        translator?.close()
        translator = null
    }

    // ── Cache helpers ──────────────────────────────────────────────────────────

    private fun cacheKey(text: String): String {
        return "${currentFrom}_${currentTo}_${MD5Utils.md5Encode(text)}"
    }

    private fun getCached(key: String): String? {
        val file = File(cacheDir, "$key.txt")
        return if (file.exists()) file.readText() else null
    }

    private fun putCache(key: String, value: String) {
        runCatching {
            cacheDir.mkdirs()
            File(cacheDir, "$key.txt").writeText(value)
        }
    }

    // ── Language utilities ─────────────────────────────────────────────────────

    companion object {
        /**
         * All languages supported by ML Kit Translate, with human-readable display names.
         */
        val supportedLanguages: List<Pair<String, String>> = listOf(
            TranslateLanguage.AFRIKAANS to "Afrikaans",
            TranslateLanguage.ALBANIAN to "Albanian",
            TranslateLanguage.ARABIC to "Arabic",
            TranslateLanguage.BELARUSIAN to "Belarusian",
            TranslateLanguage.BENGALI to "Bengali",
            TranslateLanguage.BULGARIAN to "Bulgarian",
            TranslateLanguage.CATALAN to "Catalan",
            TranslateLanguage.CHINESE to "Chinese (Simplified)",
            TranslateLanguage.CROATIAN to "Croatian",
            TranslateLanguage.CZECH to "Czech",
            TranslateLanguage.DANISH to "Danish",
            TranslateLanguage.DUTCH to "Dutch",
            TranslateLanguage.ENGLISH to "English",
            TranslateLanguage.ESPERANTO to "Esperanto",
            TranslateLanguage.ESTONIAN to "Estonian",
            TranslateLanguage.FINNISH to "Finnish",
            TranslateLanguage.FRENCH to "French",
            TranslateLanguage.GALICIAN to "Galician",
            TranslateLanguage.GEORGIAN to "Georgian",
            TranslateLanguage.GERMAN to "German",
            TranslateLanguage.GREEK to "Greek",
            TranslateLanguage.GUJARATI to "Gujarati",
            TranslateLanguage.HAITIAN_CREOLE to "Haitian Creole",
            TranslateLanguage.HEBREW to "Hebrew",
            TranslateLanguage.HINDI to "Hindi",
            TranslateLanguage.HUNGARIAN to "Hungarian",
            TranslateLanguage.ICELANDIC to "Icelandic",
            TranslateLanguage.INDONESIAN to "Indonesian",
            TranslateLanguage.IRISH to "Irish",
            TranslateLanguage.ITALIAN to "Italian",
            TranslateLanguage.JAPANESE to "Japanese",
            TranslateLanguage.KANNADA to "Kannada",
            TranslateLanguage.KOREAN to "Korean",
            TranslateLanguage.LATVIAN to "Latvian",
            TranslateLanguage.LITHUANIAN to "Lithuanian",
            TranslateLanguage.MACEDONIAN to "Macedonian",
            TranslateLanguage.MALAY to "Malay",
            TranslateLanguage.MALTESE to "Maltese",
            TranslateLanguage.MARATHI to "Marathi",
            TranslateLanguage.NORWEGIAN to "Norwegian",
            TranslateLanguage.PERSIAN to "Persian",
            TranslateLanguage.POLISH to "Polish",
            TranslateLanguage.PORTUGUESE to "Portuguese",
            TranslateLanguage.ROMANIAN to "Romanian",
            TranslateLanguage.RUSSIAN to "Russian",
            TranslateLanguage.SLOVAK to "Slovak",
            TranslateLanguage.SLOVENIAN to "Slovenian",
            TranslateLanguage.SPANISH to "Spanish",
            TranslateLanguage.SWAHILI to "Swahili",
            TranslateLanguage.SWEDISH to "Swedish",
            TranslateLanguage.TAGALOG to "Tagalog",
            TranslateLanguage.TAMIL to "Tamil",
            TranslateLanguage.TELUGU to "Telugu",
            TranslateLanguage.THAI to "Thai",
            TranslateLanguage.TURKISH to "Turkish",
            TranslateLanguage.UKRAINIAN to "Ukrainian",
            TranslateLanguage.URDU to "Urdu",
            TranslateLanguage.VIETNAMESE to "Vietnamese",
            TranslateLanguage.WELSH to "Welsh"
        )

        /** Returns the display name for the given language code, or the code itself if unknown. */
        fun displayName(code: String): String =
            supportedLanguages.firstOrNull { it.first == code }?.second ?: code
    }
}
