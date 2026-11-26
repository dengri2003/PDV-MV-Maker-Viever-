package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.repository.PdfLibraryRepository
import com.example.pdf.PdfGeneratorEngine
import com.example.pdf.PdfRenderEngine

class PdfMvApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val renderEngine: PdfRenderEngine by lazy { PdfRenderEngine(this) }
    val generatorEngine: PdfGeneratorEngine by lazy { PdfGeneratorEngine(this) }
    val libraryRepository: PdfLibraryRepository by lazy {
        PdfLibraryRepository(
            context = this,
            dao = database.pdfDocumentDao(),
            renderEngine = renderEngine
        )
    }
}
