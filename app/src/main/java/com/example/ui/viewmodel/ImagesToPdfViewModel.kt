package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.PdfDocumentEntity
import com.example.data.prefs.ImageEnhancementFilter
import com.example.data.prefs.PdfExportQuality
import com.example.data.prefs.PdfMarginSize
import com.example.data.prefs.PdfPageSize
import com.example.data.repository.PdfLibraryRepository
import com.example.pdf.ImagePageSpec
import com.example.pdf.PdfGeneratorEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class ImagesToPdfUiState(
    val pages: List<ImagePageSpec> = emptyList(),
    val filename: String = defaultScanFilename(),
    val pageSize: PdfPageSize = PdfPageSize.A4,
    val marginSize: PdfMarginSize = PdfMarginSize.SMALL,
    val quality: PdfExportQuality = PdfExportQuality.BALANCED,
    val isGenerating: Boolean = false,
    val progressCurrent: Int = 0,
    val progressTotal: Int = 0,
    val lastGeneratedUri: Uri? = null,
    val message: String? = null
)

private fun defaultScanFilename(): String {
    val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
    return "Scan_$stamp"
}

class ImagesToPdfViewModel(
    private val generatorEngine: PdfGeneratorEngine,
    private val libraryRepository: PdfLibraryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImagesToPdfUiState())
    val uiState: StateFlow<ImagesToPdfUiState> = _uiState.asStateFlow()

    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val newItems = uris.map { uri ->
            ImagePageSpec(id = UUID.randomUUID().toString(), uri = uri)
        }
        _uiState.update { it.copy(pages = it.pages + newItems) }
    }

    fun rotatePage(id: String) {
        _uiState.update { state ->
            state.copy(
                pages = state.pages.map { page ->
                    if (page.id == id) {
                        page.copy(rotationDegrees = (page.rotationDegrees + 90) % 360)
                    } else page
                }
            )
        }
    }

    fun cycleFilter(id: String) {
        val values = ImageEnhancementFilter.entries
        _uiState.update { state ->
            state.copy(
                pages = state.pages.map { page ->
                    if (page.id == id) {
                        val next = values[(page.filter.ordinal + 1) % values.size]
                        page.copy(filter = next)
                    } else page
                }
            )
        }
    }

    fun removePage(id: String) {
        _uiState.update { state ->
            state.copy(pages = state.pages.filterNot { it.id == id })
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            if (fromIndex !in state.pages.indices || toIndex !in state.pages.indices || fromIndex == toIndex) {
                return@update state
            }
            val mutable = state.pages.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            state.copy(pages = mutable)
        }
    }

    fun updateFilename(name: String) {
        _uiState.update { it.copy(filename = name) }
    }

    fun setPageSize(size: PdfPageSize) {
        _uiState.update { it.copy(pageSize = size) }
    }

    fun setMarginSize(margin: PdfMarginSize) {
        _uiState.update { it.copy(marginSize = margin) }
    }

    fun setQuality(quality: PdfExportQuality) {
        _uiState.update { it.copy(quality = quality) }
    }

    fun generateAndRegisterPdf(
        externalSaveUri: Uri? = null,
        onCompleted: (PdfDocumentEntity) -> Unit
    ) {
        val snapshot = _uiState.value
        if (snapshot.pages.isEmpty() || snapshot.isGenerating) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isGenerating = true, progressCurrent = 0, progressTotal = snapshot.pages.size)
            }
            runCatching {
                val outFile = libraryRepository.createManagedOutputFile(snapshot.filename)
                generatorEngine.createPdfFromImages(
                    pages = snapshot.pages,
                    pageSize = snapshot.pageSize,
                    marginSize = snapshot.marginSize,
                    quality = snapshot.quality,
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
                    displayName = snapshot.filename,
                    sourceType = PdfDocumentEntity.SOURCE_IMAGES
                )
            }.onSuccess { entity ->
                _uiState.update { it.copy(isGenerating = false) }
                onCompleted(entity)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isGenerating = false, message = err.message ?: "Failed to generate PDF")
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
