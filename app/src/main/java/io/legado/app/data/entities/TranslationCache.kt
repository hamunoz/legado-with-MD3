package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "translation_cache",
    indices = [
        Index(value = ["bookTitle", "chapterId", "sourceLanguage", "targetLanguage"], unique = true),
        Index(value = ["timestamp"])
    ]
)
data class TranslationCache(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val bookTitle: String,
    val chapterId: String,
    val sourceLanguage: String,
    val targetLanguage: String,
    val originalTextHash: String,
    val translatedText: String,
    val translationMode: String,
    val timestamp: Long,
    val isExpired: Boolean = false
)
