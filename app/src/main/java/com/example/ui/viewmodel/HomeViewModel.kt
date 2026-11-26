package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.PdfMvApplication
import com.example.data.db.PdfDocumentEntity
import com.example.data.repository.PdfLibraryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val allDocuments: List<PdfDocumentEntity> = emptyList(),
    val filteredDocuments: List<PdfDocumentEntity> = emptyList(),
    val searchQuery: String = "",
    val isImporting: Boolean = false,
    val errorEvent: String? = null
)

class HomeViewModel(
    private val libraryRepository: PdfLibraryRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val isImporting = MutableStateFlow(false)
    private val errorEvent = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        libraryRepository.documentsFlow,
        searchQuery,
        isImporting,
        errorEvent
    ) { docs, query, importing, err ->
        val trimmed = query.trim()
        val filtered = if (trimmed.isEmpty()) {
            docs
        } else {
            docs.filter { it.displayName.contains(trimmed, ignoreCase = true) }
        }
        HomeUiState(
            allDocuments = docs,
            filteredDocuments = filtered,
            searchQuery = query,
            isImporting = importing,
            errorEvent = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onSearchQueryChange(newQuery: String) {
        searchQuery.value = newQuery
    }

    fun importPdf(uri: Uri, onOpened: (String) -> Unit) {
        viewModelScope.launch {
            isImporting.value = true
            val result = libraryRepository.importExternalUri(uri)
            isImporting.value = false
            result.onSuccess { doc ->
                onOpened(doc.id)
            }.onFailure {
                errorEvent.value = it.message ?: "Unable to open PDF"
            }
        }
    }

    fun renameDocument(id: String, newName: String) {
        viewModelScope.launch {
            libraryRepository.renameDocument(id, newName)
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            libraryRepository.deleteDocument(id)
        }
    }

    fun prepareShareUri(doc: PdfDocumentEntity, onReady: (Uri) -> Unit) {
        viewModelScope.launch {
            val shareUri = libraryRepository.getShareableFileProviderUri(doc)
            if (shareUri != null) {
                onReady(shareUri)
            } else {
                errorEvent.value = "Unable to prepare file for sharing"
            }
        }
    }

    fun clearError() {
        errorEvent.value = null
    }
}

class AppViewModelFactory(private val app: PdfMvApplication) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) ->
                HomeViewModel(app.libraryRepository) as T
            modelClass.isAssignableFrom(ImagesToPdfViewModel::class.java) ->
                ImagesToPdfViewModel(app.generatorEngine, app.libraryRepository) as T
            modelClass.isAssignableFrom(MarkdownToPdfViewModel::class.java) ->
                MarkdownToPdfViewModel(app.generatorEngine, app.libraryRepository) as T
            modelClass.isAssignableFrom(OrganizerViewModel::class.java) ->
                OrganizerViewModel(app.renderEngine, app.generatorEngine, app.libraryRepository) as T
            modelClass.isAssignableFrom(SettingsViewModel::class.java) ->
                SettingsViewModel(app.preferencesRepository, app.renderEngine) as T
            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
