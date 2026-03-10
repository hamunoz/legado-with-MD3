package io.legado.app.data.repository

import io.legado.app.data.appDb
import io.legado.app.data.dao.TranslationCacheDao
import io.legado.app.data.entities.TranslationCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class TranslationCacheRepository(
    private val translationCacheDao: TranslationCacheDao
) {

    suspend fun getTranslation(
        bookTitle: String,
        chapterId: String,
        fromLang: String,
        toLang: String
    ): TranslationCache? = withContext(Dispatchers.IO) {
        translationCacheDao.getTranslation(bookTitle, chapterId, fromLang, toLang)
    }

    suspend fun saveTranslation(translation: TranslationCache) = withContext(Dispatchers.IO) {
        translationCacheDao.insertTranslation(translation)
    }

    suspend fun deleteOldTranslations(daysOld: Int) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysOld.toLong())
        translationCacheDao.deleteOldTranslations(cutoff)
    }

    suspend fun markExpiredTranslations(daysOld: Int) = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(daysOld.toLong())
        translationCacheDao.markOldTranslationsExpired(cutoff)
    }

    suspend fun deleteTranslationsByBook(bookTitle: String) = withContext(Dispatchers.IO) {
        translationCacheDao.deleteTranslationsByBook(bookTitle)
    }

    suspend fun deleteAllTranslations() = withContext(Dispatchers.IO) {
        translationCacheDao.deleteAllTranslations()
    }

    suspend fun getAllTranslations(): List<TranslationCache> = withContext(Dispatchers.IO) {
        translationCacheDao.getAllTranslations()
    }

    suspend fun getActiveTranslationCount(): Int = withContext(Dispatchers.IO) {
        translationCacheDao.getActiveTranslationCount()
    }

    suspend fun getTotalTranslationCount(): Int = withContext(Dispatchers.IO) {
        translationCacheDao.getTotalTranslationCount()
    }

    companion object {
        val instance: TranslationCacheRepository by lazy {
            TranslationCacheRepository(appDb.translationCacheDao)
        }
    }
}
