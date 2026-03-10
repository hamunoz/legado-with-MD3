package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.BaseBottomSheetDialogFragment
import io.legado.app.databinding.DialogTranslateBinding
import io.legado.app.help.translation.TranslationHelper
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * Bottom sheet dialog for configuring and triggering ML Kit chapter translation.
 *
 * The host Activity must be [ReadBookActivity] which owns a [ReadBookViewModel].
 */
class TranslateDialog : BaseBottomSheetDialogFragment(R.layout.dialog_translate) {

    private val binding by viewBinding(DialogTranslateBinding::bind)

    private val languages get() = TranslationHelper.supportedLanguages
    private val languageNames get() = languages.map { it.second }
    private val languageCodes get() = languages.map { it.first }

    private val viewModel: ReadBookViewModel?
        get() = (activity as? ReadBookActivity)?.viewModel

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (activity as? ReadBookActivity)?.let { it.bottomDialog-- }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        (activity as? ReadBookActivity)?.let { it.bottomDialog++ }
        setupLanguageDropdowns()
        setupButtons()
    }

    private fun setupLanguageDropdowns() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            languageNames
        )

        binding.acvFromLanguage.setAdapter(adapter)
        binding.acvToLanguage.setAdapter(adapter)

        // Restore saved selections
        val fromCode = viewModel?.translationFromLanguage ?: "en"
        val toCode = viewModel?.translationToLanguage ?: "es"
        val fromName = TranslationHelper.displayName(fromCode)
        val toName = TranslationHelper.displayName(toCode)
        binding.acvFromLanguage.setText(fromName, false)
        binding.acvToLanguage.setText(toName, false)
    }

    private fun setupButtons() {
        binding.btnDownloadModel.setOnClickListener {
            val fromCode = selectedFromCode() ?: return@setOnClickListener
            val toCode = selectedToCode() ?: return@setOnClickListener
            saveSelections(fromCode, toCode)
            showProgress(true)
            viewModel?.downloadTranslationModel(
                from = fromCode,
                to = toCode,
                onComplete = {
                    activity?.runOnUiThread {
                        showProgress(false)
                        context?.toastOnUi(getString(R.string.translate_model_downloaded))
                    }
                },
                onError = { e ->
                    activity?.runOnUiThread {
                        showProgress(false)
                        context?.toastOnUi(
                            getString(R.string.translate_model_download_failed, e.localizedMessage)
                        )
                    }
                }
            )
        }

        binding.btnTranslate.setOnClickListener {
            val fromCode = selectedFromCode() ?: return@setOnClickListener
            val toCode = selectedToCode() ?: return@setOnClickListener
            saveSelections(fromCode, toCode)
            showProgress(true)
            binding.tvProgress.text = getString(R.string.translate_in_progress)
            viewModel?.translateCurrentChapter(
                from = fromCode,
                to = toCode,
                onProgress = { current, total ->
                    activity?.runOnUiThread {
                        binding.progressBar.max = total
                        binding.progressBar.progress = current
                        binding.tvProgress.text = "$current / $total"
                    }
                },
                onSuccess = { translated ->
                    activity?.runOnUiThread {
                        showProgress(false)
                        viewModel?.let { vm ->
                            ReadBook.book?.let { book ->
                                vm.saveContent(book, translated)
                            }
                        }
                        dismiss()
                    }
                },
                onError = { e ->
                    activity?.runOnUiThread {
                        showProgress(false)
                        context?.toastOnUi(
                            getString(R.string.translate_failed, e.localizedMessage)
                        )
                    }
                }
            )
        }
    }

    private fun selectedFromCode(): String? {
        val name = binding.acvFromLanguage.text.toString()
        val index = languageNames.indexOf(name)
        return if (index >= 0) languageCodes[index] else {
            context?.toastOnUi(getString(R.string.translate_select_language))
            null
        }
    }

    private fun selectedToCode(): String? {
        val name = binding.acvToLanguage.text.toString()
        val index = languageNames.indexOf(name)
        return if (index >= 0) languageCodes[index] else {
            context?.toastOnUi(getString(R.string.translate_select_language))
            null
        }
    }

    private fun saveSelections(fromCode: String, toCode: String) {
        viewModel?.translationFromLanguage = fromCode
        viewModel?.translationToLanguage = toCode
    }

    private fun showProgress(visible: Boolean) {
        binding.llProgress.isVisible = visible
        binding.btnTranslate.isEnabled = !visible
        binding.btnDownloadModel.isEnabled = !visible
    }
}
