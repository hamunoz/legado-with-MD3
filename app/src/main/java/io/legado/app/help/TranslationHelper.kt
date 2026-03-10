package io.legado.app.help

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.TranslationCache
import io.legado.app.data.repository.TranslationCacheRepository
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import splitties.init.appCtx
import java.util.concurrent.TimeUnit

/**
 * ML Kit Translation helper with database caching and automatic offline→online fallback.
 *
 * Translation flow:
 *  1. Check DB cache → return immediately if found and not expired
 *  2. Try offline ML Kit translation (with configurable timeout)
 *  3. If offline fails → try online Google Translate API (if auto-fallback enabled)
 *  4. Persist result to DB with the mode used
 *  5. Return translated text (or original if all methods fail)
 */
object TranslationHelper {

    private const val TAG = "TranslationHelper"

    /** Offline translation mode identifier stored in DB. */
    const val MODE_OFFLINE = "offline"

    /** Online translation mode identifier stored in DB. */
    const val MODE_ONLINE = "online"

    /** Auto mode: try offline first, then fall back to online. */
    const val MODE_AUTO = "auto"

    /** Timeout in milliseconds for offline ML Kit translation before falling back. */
    private const val OFFLINE_TIMEOUT_MS = 30_000L

    // ---- Preference accessors ------------------------------------------------

    val enableAutoFallback: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.enableAutoFallback, true)

    val preferredMode: String
        get() = appCtx.getPrefString(PreferKey.preferredTranslationMode, MODE_AUTO) ?: MODE_AUTO

    val cacheDays: Int
        get() = appCtx.getPrefInt(PreferKey.translationCacheDays, 30)

    val enableCache: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.enableTranslationCache, true)

    // ---- Translator lifecycle ------------------------------------------------

    private var currentTranslator: Translator? = null

    /**
     * Returns the list of language codes supported by ML Kit.
     */
    fun getSupportedLanguages(): List<String> = TranslateLanguage.getAllLanguages()

    /**
     * Translates [text] for the given book/chapter context.
     *
     * @param text          The text to translate.
     * @param fromLang      BCP-47 source language code (e.g. "en").
     * @param toLang        BCP-47 target language code (e.g. "es").
     * @param bookTitle     Book title used as cache key.
     * @param chapterId     Chapter identifier used as cache key.
     * @return Translated text, or the original [text] if translation fails.
     */
    suspend fun translate(
        text: String,
        fromLang: String,
        toLang: String,
        bookTitle: String,
        chapterId: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        val textHash = MD5Utils.md5Encode(text) // MD5 used solely as cache key, not for security

        // 1. Check DB cache
        if (enableCache) {
            val cached = TranslationCacheRepository.instance
                .getTranslation(bookTitle, chapterId, fromLang, toLang)
            if (cached != null && cached.originalTextHash == textHash) {
                Log.d(TAG, "Cache hit: $bookTitle/$chapterId [$fromLang→$toLang]")
                return@withContext TranslationResult(
                    translatedText = cached.translatedText,
                    mode = cached.translationMode,
                    fromCache = true
                )
            }
        }

        val mode = preferredMode

        // 2. Try offline (or preferred mode)
        if (mode == MODE_OFFLINE || mode == MODE_AUTO) {
            try {
                val translated = translateOffline(text, fromLang, toLang)
                if (translated != null) {
                    Log.d(TAG, "Offline translation succeeded for $bookTitle/$chapterId")
                    persistTranslation(
                        bookTitle, chapterId, fromLang, toLang,
                        textHash, translated, MODE_OFFLINE
                    )
                    return@withContext TranslationResult(
                        translatedText = translated,
                        mode = MODE_OFFLINE,
                        fromCache = false
                    )
                }
            } catch (e: Exception) {
                AppLog.put("Offline translation failed: ${e.message}", e)
                Log.w(TAG, "Offline translation failed, will try online fallback", e)
            }
        }

        // 3. Try online (fallback or preferred)
        if (mode == MODE_ONLINE || (mode == MODE_AUTO && enableAutoFallback)) {
            try {
                val translated = translateOnline(text, fromLang, toLang)
                if (translated != null) {
                    Log.d(TAG, "Online translation succeeded for $bookTitle/$chapterId")
                    persistTranslation(
                        bookTitle, chapterId, fromLang, toLang,
                        textHash, translated, MODE_ONLINE
                    )
                    return@withContext TranslationResult(
                        translatedText = translated,
                        mode = MODE_ONLINE,
                        fromCache = false
                    )
                }
            } catch (e: Exception) {
                AppLog.put("Online translation failed: ${e.message}", e)
                Log.e(TAG, "Online translation also failed", e)
            }
        }

        // All methods failed → return original
        TranslationResult(
            translatedText = text,
            mode = null,
            fromCache = false,
            failed = true
        )
    }

    // ---- Offline translation -------------------------------------------------

    private suspend fun translateOffline(
        text: String,
        fromLang: String,
        toLang: String
    ): String? = withContext(Dispatchers.IO) {
        val translator = getOrCreateTranslator(fromLang, toLang)
        withTimeout(OFFLINE_TIMEOUT_MS) {
            Tasks.await(translator.translate(text))
        }
    }

    private fun getOrCreateTranslator(fromLang: String, toLang: String): Translator {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(fromLang)
            .setTargetLanguage(toLang)
            .build()
        currentTranslator?.close()
        val translator = Translation.getClient(options)
        currentTranslator = translator
        return translator
    }

    /**
     * Downloads the offline ML Kit model for [fromLang]→[toLang] translation.
     * Must be called before using offline translation.
     *
     * @param onProgress Called with download progress (0–1f).
     * @param onComplete Called when download finishes, with success flag and optional error message.
     */
    fun downloadModel(
        fromLang: String,
        toLang: String,
        onProgress: ((Float) -> Unit)? = null,
        onComplete: (success: Boolean, error: String?) -> Unit
    ) {
        val translator = getOrCreateTranslator(fromLang, toLang)
        translator.downloadModelIfNeeded()
            .addOnSuccessListener {
                Log.d(TAG, "Model downloaded: $fromLang→$toLang")
                onComplete(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Model download failed: $fromLang→$toLang", e)
                onComplete(false, e.message)
            }
    }

    // ---- Online translation --------------------------------------------------

    private suspend fun translateOnline(
        text: String,
        fromLang: String,
        toLang: String
    ): String? = withContext(Dispatchers.IO) {
        // Translate paragraph by paragraph to stay within API limits
        val paragraphs = text.split("\n")
        val translated = StringBuilder()
        for (paragraph in paragraphs) {
            if (paragraph.isBlank()) {
                translated.append("\n")
                continue
            }
            val result = translateParagraphOnline(paragraph, fromLang, toLang)
            translated.append(result).append("\n")
        }
        translated.toString().trimEnd('\n')
    }

    private suspend fun translateParagraphOnline(
        paragraph: String,
        fromLang: String,
        toLang: String
    ): String = withContext(Dispatchers.IO) {
        val encodedText = java.net.URLEncoder.encode(paragraph, Charsets.UTF_8.name())
        val url = "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx&sl=$fromLang&tl=$toLang&dt=t&q=$encodedText"
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36"
            )
        }
        try {
            val response = connection.inputStream.bufferedReader().readText()
            parseGoogleTranslateResponse(response)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Parses the raw JSON array returned by the Google Translate API.
     * Response format: [[["translatedText","originalText",null,null,1],...],...]
     * Extracts all translated sentence segments and joins them.
     */
    private fun parseGoogleTranslateResponse(response: String): String {
        return try {
            // Each sentence segment is encoded as [["translated","original",...], ...]
            // Match all leading quoted strings in each innermost array
            val segmentPattern = Regex("""\[\s*"((?:[^"\\]|\\.)*)"""")
            val matches = segmentPattern.findAll(response)
            val sb = StringBuilder()
            matches.forEach { match ->
                sb.append(
                    match.groupValues[1]
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                        .replace("\\\\", "\\")
                )
            }
            if (sb.isEmpty()) response else sb.toString()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse translation response", e)
            response
        }
    }

    // ---- Cache persistence ---------------------------------------------------

    private suspend fun persistTranslation(
        bookTitle: String,
        chapterId: String,
        fromLang: String,
        toLang: String,
        textHash: String,
        translatedText: String,
        mode: String
    ) {
        if (!enableCache) return
        runCatching {
            TranslationCacheRepository.instance.saveTranslation(
                TranslationCache(
                    bookTitle = bookTitle,
                    chapterId = chapterId,
                    sourceLanguage = fromLang,
                    targetLanguage = toLang,
                    originalTextHash = textHash,
                    translatedText = translatedText,
                    translationMode = mode,
                    timestamp = System.currentTimeMillis(),
                    isExpired = false
                )
            )
        }.onFailure { e ->
            AppLog.put("Failed to persist translation cache: ${e.message}", e)
        }
    }

    // ---- Cache management ---------------------------------------------------

    /** Removes translations older than [daysOld] days from the database. */
    suspend fun cleanExpiredCache(daysOld: Int = cacheDays) {
        TranslationCacheRepository.instance.deleteOldTranslations(daysOld)
    }

    /** Clears all cached translations for a specific book. */
    suspend fun clearBookCache(bookTitle: String) {
        TranslationCacheRepository.instance.deleteTranslationsByBook(bookTitle)
    }

    /** Clears all cached translations. */
    suspend fun clearAllCache() {
        TranslationCacheRepository.instance.deleteAllTranslations()
    }

    /** Returns the number of non-expired cached translations. */
    suspend fun getActiveCacheCount(): Int =
        TranslationCacheRepository.instance.getActiveTranslationCount()

    /** Returns total number of cached translations (including expired). */
    suspend fun getTotalCacheCount(): Int =
        TranslationCacheRepository.instance.getTotalTranslationCount()

    /** Releases the current ML Kit translator client. */
    fun release() {
        currentTranslator?.close()
        currentTranslator = null
    }

    // ---- Data class ---------------------------------------------------------

    data class TranslationResult(
        val translatedText: String,
        /** "offline", "online", or null if translation failed */
        val mode: String?,
        val fromCache: Boolean,
        val failed: Boolean = false
    )
}
