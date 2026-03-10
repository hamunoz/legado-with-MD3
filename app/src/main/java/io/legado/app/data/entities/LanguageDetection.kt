package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached language detection result for a book chapter, so we avoid re-running
 * ML Kit detection on every chapter visit.
 *
 * Records are considered fresh for [CACHE_DAYS_DEFAULT] days; stale entries are
 * pruned by [io.legado.app.data.dao.LanguageDetectionDao.deleteOldDetections].
 */
@Entity(
    tableName = "language_cache",
    indices = [Index(value = ["bookUrl", "chapterId"], unique = true)]
)
data class LanguageDetection(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** URL/identifier of the book this chapter belongs to. */
    val bookUrl: String,
    /** Unique identifier of the chapter (typically its URL). */
    val chapterId: String,
    /** Detected BCP-47 language code, e.g. "en", "es", "zh". */
    val detectedLanguage: String,
    /** Detection confidence in the range [0.0, 1.0]. */
    val confidence: Float,
    /** Epoch-millis when this record was created/updated. */
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        const val CACHE_DAYS_DEFAULT = 7
    }
}
