package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.PdfDocumentEntity
import com.example.data.prefs.PdfPageSize
import com.example.data.repository.PdfLibraryRepository
import com.example.pdf.MarkdownBlock
import com.example.pdf.PdfGeneratorEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MarkdownToPdfUiState(
    val title: String = "",
    val markdownBody: String = "",
    val parsedBlocks: List<MarkdownBlock> = emptyList(),
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val includePageNumbers: Boolean = true,
    val isPreviewMode: Boolean = false,
    val isGenerating: Boolean = false,
    val errorMessage: String? = null
)

class MarkdownToPdfViewModel(
    private val generatorEngine: PdfGeneratorEngine,
    private val libraryRepository: PdfLibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarkdownToPdfUiState())
    val uiState: StateFlow<MarkdownToPdfUiState> = _uiState.asStateFlow()

    fun initSampleIfEmpty(sampleTitle: String, sampleBody: String) {
        if (_uiState.value.title.isEmpty() && _uiState.value.markdownBody.isEmpty()) {
            _uiState.update {
                it.copy(
                    title = sampleTitle,
                    markdownBody = sampleBody,
                    parsedBlocks = generatorEngine.parseMarkdown(sampleBody)
                )
            }
        }
    }

    fun updateTitle(title: String) {
        _uiState.update { it.copy(title = title) }
    }

    fun updateBody(body: String) {
        _uiState.update {
            it.copy(
                markdownBody = body,
                parsedBlocks = generatorEngine.parseMarkdown(body)
            )
        }
    }

    fun insertSnippet(snippet: String) {
        val current = _uiState.value.markdownBody
        val next = if (current.isEmpty() || current.endsWith("\n")) {
            current + snippet
        } else {
            "$current\n$snippet"
        }
        updateBody(next)
    }

    fun setPageSize(size: PdfPageSize) {
        _uiState.update { it.copy(pageSize = size) }
    }

    fun togglePageNumbers() {
        _uiState.update { it.copy(includePageNumbers = !it.includePageNumbers) }
    }

    fun setPreviewMode(preview: Boolean) {
        _uiState.update { it.copy(isPreviewMode = preview) }
    }

    fun exportPdf(
        externalSaveUri: Uri? = null,
        onCompleted: (PdfDocumentEntity) -> Unit
    ) {
        val snap = _uiState.value
        if (snap.isGenerating) return
        if (snap.title.isBlank() && snap.markdownBody.isBlank()) {
            _uiState.update { it.copy(errorMessage = "EMPTY") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isGenerating = true) }
            runCatching {
                val baseName = snap.title.trim().ifBlank { "Notes_${System.currentTimeMillis() % 10000}" }
                val outFile = libraryRepository.createManagedOutputFile(baseName)
                generatorEngine.createPdfFromMarkdown(
                    title = snap.title,
                    markdown = snap.markdownBody,
                    pageSize = snap.pageSize,
                    includePageNumbers = snap.includePageNumbers,
                    outputFile = outFile
                )
                if (externalSaveUri != null) {
                    libraryRepository.copyPdfToUri(Uri.fromFile(outFile), externalSaveUri)
                }
                libraryRepository.registerInternalPdf(
                    file = outFile,
                    displayName = baseName,
                    sourceType = PdfDocumentEntity.SOURCE_MARKDOWN
                )
            }.onSuccess { entity ->
                _uiState.update { it.copy(isGenerating = false) }
                onCompleted(entity)
            }.onFailure { err ->
                _uiState.update { it.copy(isGenerating = false, errorMessage = err.message) }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
