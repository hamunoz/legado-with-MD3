package io.legado.app.help.book

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import io.legado.app.constant.AppLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Helper for automatic language detection using ML Kit Language Identification.
 * Detects the language of text (e.g., chapter content) to pre-select the source
 * language in the translation UI.
 */
object LanguageDetectionHelper {

    /** Minimum confidence threshold to accept a detected language. */
    const val CONFIDENCE_THRESHOLD = 0.7f

    /** Maximum number of characters to sample for detection (for performance). */
    private const val MAX_DETECT_CHARS = 500

    /** Language code returned by ML Kit when detection is inconclusive. */
    private const val UNDETERMINED = "und"

    /**
     * Detects the language of the given text.
     *
     * @param text The text to detect language for.
     * @return Language code (e.g. "en", "es") or "en" as fallback.
     */
    suspend fun detectLanguage(text: String): String {
        return detectLanguageWithConfidence(text).first
    }

    /**
     * Detects the language of the given text and returns it with a confidence score.
     *
     * Only returns the detected language if confidence exceeds [CONFIDENCE_THRESHOLD];
     * otherwise falls back to "en".
     *
     * @param text The text to detect language for.
     * @return Pair of (languageCode, confidence). Language is "en" if detection fails
     *         or confidence is below [CONFIDENCE_THRESHOLD].
     */
    suspend fun detectLanguageWithConfidence(text: String): Pair<String, Float> {
        if (text.isBlank()) return Pair("en", 0f)
        val sample = text.take(MAX_DETECT_CHARS)
        return runCatching {
            detectWithMlKit(sample)
        }.getOrElse { e ->
            AppLog.put("Language detection failed", e)
            Pair("en", 0f)
        }
    }

    private suspend fun detectWithMlKit(text: String): Pair<String, Float> =
        suspendCancellableCoroutine { cont ->
            val options = LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.01f) // get raw result; we threshold manually
                .build()
            val identifier = LanguageIdentification.getClient(options)

            identifier.identifyPossibleLanguages(text)
                .addOnSuccessListener { languages ->
                    identifier.close()
                    val best = languages.firstOrNull()
                    if (best == null || best.languageTag == UNDETERMINED || best.confidence < CONFIDENCE_THRESHOLD) {
                        cont.resume(Pair("en", best?.confidence ?: 0f))
                    } else {
                        cont.resume(Pair(normalizeLanguageCode(best.languageTag), best.confidence))
                    }
                }
                .addOnFailureListener { e ->
                    identifier.close()
                    AppLog.put("ML Kit language identification failed", e)
                    cont.resume(Pair("en", 0f))
                }

            cont.invokeOnCancellation { identifier.close() }
        }

    /**
     * Normalises ML Kit language tags to simple two-letter codes used by the
     * translation UI (e.g. "zh-Hans" → "zh", "pt-BR" → "pt").
     */
    fun normalizeLanguageCode(tag: String): String {
        return tag.split("-").firstOrNull()?.lowercase() ?: "en"
    }
}
