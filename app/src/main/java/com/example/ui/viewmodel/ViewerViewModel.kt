package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.PdfMvApplication
import com.example.data.db.PdfDocumentEntity
import com.example.data.prefs.EyeCareFilter
import com.example.data.prefs.ReadingMode
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.repository.PdfLibraryRepository
import com.example.pdf.PdfRenderEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ViewerUiState(
    val document: PdfDocumentEntity? = null,
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val initialPageToRestore: Int = 0,
    val readingMode: ReadingMode = ReadingMode.VERTICAL_CONTINUOUS,
    val eyeCareFilter: EyeCareFilter = EyeCareFilter.ORIGINAL,
    val bookmarks: Set<Int> = emptySet(),
    val isChromeVisible: Boolean = true,
    val isLoading: Boolean = true,
    val loadError: Boolean = false
)

class ViewerViewModel(
    private val docId: String,
    val renderEngine: PdfRenderEngine,
    private val libraryRepository: PdfLibraryRepository,
    private val preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ViewerUiState())
    val uiState: StateFlow<ViewerUiState> = _uiState.asStateFlow()

    init {
        loadDocument()
        observeDocumentChanges()
    }

    private fun observeDocumentChanges() {
        viewModelScope.launch {
            libraryRepository.observeDocument(docId).collect { entity ->
                if (entity != null) {
                    _uiState.update { state ->
                        state.copy(
                            document = entity,
                            bookmarks = entity.bookmarkSet()
                        )
                    }
                }
            }
        }
    }

    private fun loadDocument() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, loadError = false) }
            val prefs = preferencesRepository.preferencesFlow.first()
            val doc = libraryRepository.getDocument(docId)
            if (doc == null) {
                _uiState.update { it.copy(isLoading = false, loadError = true) }
                return@launch
            }
            val count = runCatching { renderEngine.getPageCount(doc.uriString) }.getOrDefault(0)
            if (count <= 0) {
                _uiState.update { it.copy(document = doc, isLoading = false, loadError = true) }
                return@launch
            }
            val safePage = doc.lastReadPage.coerceIn(0, count - 1)
            _uiState.update {
                it.copy(
                    document = doc,
                    pageCount = count,
                    currentPage = safePage,
                    initialPageToRestore = safePage,
                    readingMode = prefs.defaultReadingMode,
                    eyeCareFilter = prefs.defaultEyeCareFilter,
                    bookmarks = doc.bookmarkSet(),
                    isLoading = false,
                    loadError = false
                )
            }
            libraryRepository.updateReadingProgress(doc.id, safePage)
        }
    }

    fun onPageChanged(pageIndex: Int) {
        val maxPage = (_uiState.value.pageCount - 1).coerceAtLeast(0)
        val clamped = pageIndex.coerceIn(0, maxPage)
        if (_uiState.value.currentPage == clamped) return
        _uiState.update { it.copy(currentPage = clamped) }
        viewModelScope.launch {
            libraryRepository.updateReadingProgress(docId, clamped)
        }
    }

    fun toggleChromeVisibility() {
        _uiState.update { it.copy(isChromeVisible = !it.isChromeVisible) }
    }

    fun setReadingMode(mode: ReadingMode) {
        _uiState.update { it.copy(readingMode = mode) }
    }

    fun setEyeCareFilter(filter: EyeCareFilter) {
        _uiState.update { it.copy(eyeCareFilter = filter) }
    }

    fun toggleCurrentPageBookmark() {
        val page = _uiState.value.currentPage
        viewModelScope.launch {
            libraryRepository.toggleBookmark(docId, page)
        }
    }

    fun renameDocument(newName: String) {
        viewModelScope.launch {
            libraryRepository.renameDocument(docId, newName)
        }
    }

    fun prepareShareUri(onReady: (Uri) -> Unit) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            libraryRepository.getShareableFileProviderUri(doc)?.let(onReady)
        }
    }

    fun copyToDestinationUri(targetUri: Uri, onResult: (Boolean) -> Unit) {
        val doc = _uiState.value.document ?: return
        viewModelScope.launch {
            val ok = libraryRepository.copyPdfToUri(Uri.parse(doc.uriString), targetUri)
            onResult(ok)
        }
    }
}

class ViewerViewModelFactory(
    private val app: PdfMvApplication,
    private val docId: String
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ViewerViewModel(
            docId = docId,
            renderEngine = app.renderEngine,
            libraryRepository = app.libraryRepository,
            preferencesRepository = app.preferencesRepository
        ) as T
    }
}
