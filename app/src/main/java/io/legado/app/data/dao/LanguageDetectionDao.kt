package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.LanguageDetection

@Dao
interface LanguageDetectionDao {

    /** Insert or replace a detection result for a chapter. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetection(detection: LanguageDetection)

    /**
     * Returns a cached detection for the given book/chapter, or null if not cached.
     */
    @Query("SELECT * FROM language_cache WHERE bookUrl = :bookUrl AND chapterId = :chapterId LIMIT 1")
    suspend fun getDetection(bookUrl: String, chapterId: String): LanguageDetection?

    /**
     * Deletes detection records older than [daysOld] days.
     * Call periodically to keep the cache tidy.
     */
    @Query("DELETE FROM language_cache WHERE timestamp < :cutoff")
    suspend fun deleteOldDetections(cutoff: Long)

    /** Removes all cached detections for a specific book (e.g. on book deletion). */
    @Query("DELETE FROM language_cache WHERE bookUrl = :bookUrl")
    suspend fun deleteDetectionsByBook(bookUrl: String)

    /** Removes all cached language detections. */
    @Query("DELETE FROM language_cache")
    suspend fun clearAll()
}
