package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.PdfDocumentEntity
import com.example.data.prefs.AppLanguage
import com.example.i18n.EnglishStrings
import com.example.i18n.RussianStrings
import com.example.i18n.resolveAppStrings
import com.example.pdf.MarkdownBlock
import com.example.pdf.PdfGeneratorEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app_name matches PDF MV`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("PDF MV", context.getString(R.string.app_name))
    }

    @Test
    fun `bilingual strings resolve automatically and manually`() {
        assertEquals(RussianStrings.appSubtitle, resolveAppStrings(AppLanguage.SYSTEM, Locale.forLanguageTag("ru-RU")).appSubtitle)
        assertEquals(EnglishStrings.appSubtitle, resolveAppStrings(AppLanguage.SYSTEM, Locale.US).appSubtitle)
        assertEquals(RussianStrings.settingsTitle, resolveAppStrings(AppLanguage.RUSSIAN, Locale.US).settingsTitle)
        assertEquals(EnglishStrings.settingsTitle, resolveAppStrings(AppLanguage.ENGLISH, Locale.forLanguageTag("ru-RU")).settingsTitle)
    }

    @Test
    fun `markdown parser parses headings lists quotes and dividers`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = PdfGeneratorEngine(context)
        val blocks = engine.parseMarkdown(
            """
            # Title
            ## Sub
            - Bullet one
            1. Numbered one
            > Important quote
            ---
            Plain paragraph
            """.trimIndent()
        )
        assertTrue(blocks[0] is MarkdownBlock.Heading && (blocks[0] as MarkdownBlock.Heading).level == 1)
        assertTrue(blocks[1] is MarkdownBlock.Heading && (blocks[1] as MarkdownBlock.Heading).level == 2)
        assertTrue(blocks[2] is MarkdownBlock.BulletItem)
        assertTrue(blocks[3] is MarkdownBlock.NumberedItem)
        assertTrue(blocks[4] is MarkdownBlock.Quote)
        assertTrue(blocks[5] is MarkdownBlock.Divider)
        assertTrue(blocks[6] is MarkdownBlock.Paragraph)
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
}
