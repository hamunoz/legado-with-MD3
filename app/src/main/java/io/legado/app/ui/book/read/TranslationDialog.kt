package io.legado.app.ui.book.read

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.LanguageDetection
import io.legado.app.databinding.DialogTranslationBinding
import io.legado.app.help.book.LanguageDetectionHelper
import io.legado.app.model.ReadBook
import io.legado.app.utils.gone
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dialog for chapter translation.
 *
 * Automatically detects the source language of the current chapter using ML Kit
 * Language Identification, pre-selects it in the "From Language" dropdown, and
 * caches the result per chapter so the detection runs only once per chapter.
 */
class TranslationDialog : BaseBottomSheetDialogFragment(R.layout.dialog_translation) {

    private val binding by viewBinding(DialogTranslationBinding::bind)

    /** Ordered list of languages shown in both dropdowns. */
    private val languages: List<Pair<String, String>> by lazy {
        listOf(
            "en" to getString(R.string.lang_english),
            "es" to getString(R.string.lang_spanish),
            "fr" to getString(R.string.lang_french),
            "de" to getString(R.string.lang_german),
            "it" to getString(R.string.lang_italian),
            "pt" to getString(R.string.lang_portuguese),
            "ru" to getString(R.string.lang_russian),
            "ja" to getString(R.string.lang_japanese),
            "zh" to getString(R.string.lang_chinese),
            "ko" to getString(R.string.lang_korean),
            "ar" to getString(R.string.lang_arabic),
            "hi" to getString(R.string.lang_hindi),
            "tr" to getString(R.string.lang_turkish),
            "nl" to getString(R.string.lang_dutch),
            "pl" to getString(R.string.lang_polish),
            "sv" to getString(R.string.lang_swedish),
            "uk" to getString(R.string.lang_ukrainian),
            "vi" to getString(R.string.lang_vietnamese),
            "th" to getString(R.string.lang_thai),
            "id" to getString(R.string.lang_indonesian)
        )
    }

    private val languageNames: List<String> get() = languages.map { it.second }
    private val languageCodes: List<String> get() = languages.map { it.first }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setupDropdowns()
        binding.btnReDetect.setOnClickListener { triggerDetection(forceRefresh = true) }
        binding.btnTranslate.setOnClickListener { onTranslateClicked() }
        triggerDetection(forceRefresh = false)
    }

    // -------------------------------------------------------------------------
    // Dropdown setup
    // -------------------------------------------------------------------------

    private fun setupDropdowns() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            languageNames
        )
        binding.actvFromLanguage.setAdapter(adapter)
        binding.actvToLanguage.setAdapter(adapter)

        // Pre-select a sensible default for "To" (Spanish when not already selected)
        selectLanguage(binding.actvToLanguage, "es")
    }

    private fun selectLanguage(view: com.google.android.material.textfield.MaterialAutoCompleteTextView, code: String) {
        val index = languageCodes.indexOf(code)
        if (index >= 0) view.setText(languageNames[index], false)
    }

    // -------------------------------------------------------------------------
    // Language detection
    // -------------------------------------------------------------------------

    private fun triggerDetection(forceRefresh: Boolean) {
        val book = ReadBook.book ?: return
        val chapterUrl = ReadBook.curTextChapter?.chapter?.url ?: return
        val text = ReadBook.curTextChapter?.getContent() ?: return

        showDetectionProgress(true)

        lifecycleScope.launch {
            val detection = withContext(IO) {
                if (!forceRefresh) {
                    appDb.languageDetectionDao.getDetection(book.bookUrl, chapterUrl)
                        ?.takeIf { isRecent(it.timestamp) }
                } else null
            }

            if (detection != null) {
                applyDetection(detection.detectedLanguage, detection.confidence)
            } else {
                val (langCode, confidence) = withContext(IO) {
                    LanguageDetectionHelper.detectLanguageWithConfidence(text)
                }
                withContext(IO) {
                    runCatching {
                        appDb.languageDetectionDao.insertDetection(
                            LanguageDetection(
                                bookUrl = book.bookUrl,
                                chapterId = chapterUrl,
                                detectedLanguage = langCode,
                                confidence = confidence
                            )
                        )
                    }.onFailure { AppLog.put("Failed to cache language detection", it) }
                }
                applyDetection(langCode, confidence)
            }

            showDetectionProgress(false)
        }
    }

    private fun applyDetection(langCode: String, confidence: Float) {
        selectLanguage(binding.actvFromLanguage, langCode)
        updateDetectionInfo(langCode, confidence)
    }

    private fun updateDetectionInfo(langCode: String, confidence: Float) {
        val langName = languages.firstOrNull { it.first == langCode }?.second ?: langCode
        val percentText = getString(R.string.detected_language, langName, (confidence * 100).toInt())
        binding.tvDetectedLanguage.text = percentText
        binding.llDetectionInfo.visible()
    }

    private fun showDetectionProgress(show: Boolean) {
        if (show) {
            binding.progressDetection.visible()
        } else {
            binding.progressDetection.gone()
        }
    }

    // -------------------------------------------------------------------------
    // Translation
    // -------------------------------------------------------------------------

    private fun onTranslateClicked() {
        val fromName = binding.actvFromLanguage.text.toString()
        val toName = binding.actvToLanguage.text.toString()
        val fromCode = languages.firstOrNull { it.second == fromName }?.first
        val toCode = languages.firstOrNull { it.second == toName }?.first

        if (fromCode == null || toCode == null) {
            toastOnUi(R.string.select_both_languages)
            return
        }
        if (fromCode == toCode) {
            toastOnUi(R.string.same_language_error)
            return
        }

        // Pass selected language codes back to the reading activity
        (activity as? ReadBookActivity)?.startTranslation(fromCode, toCode)
        dismiss()
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun isRecent(timestamp: Long): Boolean {
        val cacheDays = LanguageDetection.CACHE_DAYS_DEFAULT
        return System.currentTimeMillis() - timestamp < cacheDays * 24 * 60 * 60 * 1000L
    }
}
