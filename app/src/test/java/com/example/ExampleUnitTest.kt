package com.example

import com.example.data.db.PdfDocumentEntity
import com.example.data.prefs.AppLanguage
import com.example.data.repository.PdfLibraryRepository
import com.example.i18n.EnglishStrings
import com.example.i18n.RussianStrings
import com.example.i18n.resolveAppStrings
import com.example.util.formatFileSize
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ExampleUnitTest {

    @Test
    fun `bilingual strings resolve automatically and manually`() {
        val ruLocale = Locale.forLanguageTag("ru-RU")
        assertEquals(RussianStrings.appSubtitle, resolveAppStrings(AppLanguage.SYSTEM, ruLocale).appSubtitle)
        assertEquals(EnglishStrings.appSubtitle, resolveAppStrings(AppLanguage.SYSTEM, Locale.US).appSubtitle)
        assertEquals(RussianStrings.settingsTitle, resolveAppStrings(AppLanguage.RUSSIAN, Locale.US).settingsTitle)
        assertEquals(EnglishStrings.settingsTitle, resolveAppStrings(AppLanguage.ENGLISH, ruLocale).settingsTitle)
    }

    @Test
    fun `bookmarkSet parses csv cleanly`() {
        val entity = PdfDocumentEntity(
            id = "1",
            uriString = "file:///tmp/a.pdf",
            displayName = "a.pdf",
            pageCount = 10,
            fileSizeBytes = 1024L,
            bookmarksCsv = "0, 4, 9"
        )
        assertEquals(setOf(0, 4, 9), entity.bookmarkSet())
    }

    @Test
    fun `sanitizePdfName normalizes invalid characters and extension`() {
        assertEquals("Report_Q4.pdf", PdfLibraryRepository.sanitizePdfName("  Report/Q4  "))
        assertEquals("Notes.PDF", PdfLibraryRepository.sanitizePdfName("Notes.PDF"))
    }

    @Test
    fun `formatFileSize formats bytes kilobytes and megabytes`() {
        assertEquals("512 B", formatFileSize(512L))
        assertEquals("2.0 KB", formatFileSize(2048L))
        assertEquals("1.5 MB", formatFileSize(1572864L))
    }
}
