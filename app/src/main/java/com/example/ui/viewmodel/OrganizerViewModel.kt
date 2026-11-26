package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.PdfDocumentEntity
import com.example.data.repository.PdfLibraryRepository
import com.example.pdf.OrganizerPageSpec
import com.example.pdf.PdfGeneratorEngine
import com.example.pdf.PdfRenderEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class OrganizerUiState(
    val pages: List<OrganizerPageSpec> = emptyList(),
    val filename: String = defaultMergedName(),
    val isLoadingPdf: Boolean = false,
    val isExporting: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val errorMessage: String? = null
) {
    val selectedCount: Int get() = pages.count { it.isSelected }
}

private fun defaultMergedName(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    return "Organized_$stamp"
}

class OrganizerViewModel(
    val renderEngine: PdfRenderEngine,
    private val generatorEngine: PdfGeneratorEngine,
    private val libraryRepository: PdfLibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrganizerUiState())
    val uiState: StateFlow<OrganizerUiState> = _uiState.asStateFlow()

    private var preloadedDocId: String? = null

    fun preloadDocumentIfNeeded(docId: String?) {
        if (docId.isNullOrBlank() || docId == preloadedDocId) return
        preloadedDocId = docId
        viewModelScope.launch {
            val doc = libraryRepository.getDocument(docId) ?: return@launch
            appendPdfUri(Uri.parse(doc.uriString), doc.displayName)
        }
    }

    fun addPdfUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            uris.forEach { uri ->
                val name = libraryRepository.queryDisplayName(uri) ?: "Document.pdf"
                appendPdfUri(uri, name)
            }
        }
    }

    private suspend fun appendPdfUri(uri: Uri, name: String) {
        _uiState.update { it.copy(isLoadingPdf = true) }
        runCatching {
            val count = renderEngine.getPageCount(uri.toString())
            require(count > 0) { "Empty or unreadable PDF" }
            val newPages = (0 until count).map { idx ->
                OrganizerPageSpec(
                    id = UUID.randomUUID().toString(),
                    sourceUri = uri,
                    sourceName = name.removeSuffix(".pdf"),
                    sourcePageIndex = idx,
                    rotationDegrees = 0,
                    isSelected = false
                )
            }
            _uiState.update { state ->
                state.copy(pages = state.pages + newPages, isLoadingPdf = false)
            }
        }.onFailure { err ->
            _uiState.update {
                it.copy(isLoadingPdf = false, errorMessage = err.message ?: "Failed to load PDF")
            }
        }
    }

    fun toggleSelect(id: String) {
        _uiState.update { state ->
            state.copy(
                pages = state.pages.map { if (it.id == id) it.copy(isSelected = !it.isSelected) else it }
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(pages = state.pages.map { it.copy(isSelected = true) })
        }
    }

    fun clearSelection() {
        _uiState.update { state ->
            state.copy(pages = state.pages.map { it.copy(isSelected = false) })
        }
    }

    fun rotatePage(id: String) {
        _uiState.update { state ->
            state.copy(
                pages = state.pages.map {
                    if (it.id == id) it.copy(rotationDegrees = (it.rotationDegrees + 90) % 360) else it
                }
            )
        }
    }

    fun deletePage(id: String) {
        _uiState.update { state ->
            state.copy(pages = state.pages.filterNot { it.id == id })
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (fromIndex !in state.pages.indices || toIndex !in state.pages.indices || fromIndex == toIndex) {
                return@update state
            }
            val list = state.pages.toMutableList()
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            state.copy(pages = list)
        }
    }

    fun updateFilename(name: String) {
        _uiState.update { it.copy(filename = name) }
    }

    fun exportPdf(
        selectedOnly: Boolean,
        externalSaveUri: Uri? = null,
        onCompleted: (PdfDocumentEntity) -> Unit
    ) {
        val state = _uiState.value
        val targetPages = if (selectedOnly) state.pages.filter { it.isSelected } else state.pages
        if (targetPages.isEmpty() || state.isExporting) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isExporting = true, progressCurrent = 0, progressTotal = targetPages.size)
            }
            runCatching {
                val outFile = libraryRepository.createManagedOutputFile(state.filename)
                generatorEngine.exportOrganizedPdf(
                    pages = targetPages,
                    outputFile = outFile,
                    onProgress = { cur, total ->
                        _uiState.update { it.copy(progressCurrent = cur, progressTotal = total) }
                    }
                )
                if (externalSaveUri != null) {
                    libraryRepository.copyPdfToUri(Uri.fromFile(outFile), externalSaveUri)
                }
                libraryRepository.registerInternalPdf(
                    file = outFile,
                    displayName = state.filename,
                    sourceType = PdfDocumentEntity.SOURCE_MERGED
                )
            }.onSuccess { entity ->
                _uiState.update { it.copy(isExporting = false) }
                onCompleted(entity)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isExporting = false, errorMessage = err.message ?: "Export failed")
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
