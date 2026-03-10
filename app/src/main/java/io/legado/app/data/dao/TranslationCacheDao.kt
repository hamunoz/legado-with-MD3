package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.TranslationCache

@Dao
interface TranslationCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertTranslation(translation: TranslationCache)

    @Query(
        """SELECT * FROM translation_cache 
        WHERE bookTitle = :bookTitle 
        AND chapterId = :chapterId 
        AND sourceLanguage = :fromLang 
        AND targetLanguage = :toLang 
        AND isExpired = 0
        LIMIT 1"""
    )
    fun getTranslation(
        bookTitle: String,
        chapterId: String,
        fromLang: String,
        toLang: String
    ): TranslationCache?

    @Query(
        """UPDATE translation_cache SET isExpired = 1 
        WHERE timestamp < :cutoffTimestamp"""
    )
    fun markOldTranslationsExpired(cutoffTimestamp: Long)

    @Query(
        """DELETE FROM translation_cache 
        WHERE timestamp < :cutoffTimestamp"""
    )
    fun deleteOldTranslations(cutoffTimestamp: Long)

    @Query("DELETE FROM translation_cache WHERE bookTitle = :bookTitle")
    fun deleteTranslationsByBook(bookTitle: String)

    @Query("DELETE FROM translation_cache")
    fun deleteAllTranslations()

    @Query("SELECT * FROM translation_cache ORDER BY timestamp DESC")
    fun getAllTranslations(): List<TranslationCache>

    @Query("SELECT COUNT(*) FROM translation_cache WHERE isExpired = 0")
    fun getActiveTranslationCount(): Int

    @Query("SELECT COUNT(*) FROM translation_cache")
    fun getTotalTranslationCount(): Int
}
