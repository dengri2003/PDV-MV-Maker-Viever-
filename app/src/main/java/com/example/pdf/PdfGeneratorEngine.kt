package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.example.data.prefs.ImageEnhancementFilter
import com.example.data.prefs.PdfExportQuality
import com.example.data.prefs.PdfMarginSize
import com.example.data.prefs.PdfPageSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ImagePageSpec(
    val id: String,
    val uri: Uri,
    val rotationDegrees: Int = 0,
    val filter: ImageEnhancementFilter = ImageEnhancementFilter.ORIGINAL
)

data class OrganizerPageSpec(
    val id: String,
    val sourceUri: Uri,
    val sourceName: String,
    val sourcePageIndex: Int,
    val rotationDegrees: Int = 0,
    val isSelected: Boolean = false
)

sealed class MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletItem(val text: String) : MarkdownBlock()
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock()
    data class Quote(val text: String) : MarkdownBlock()
    data class CodeBlock(val code: String) : MarkdownBlock()
    data object Divider : MarkdownBlock()
}

class PdfGeneratorEngine(private val context: Context) {

    suspend fun createPdfFromImages(
        pages: List<ImagePageSpec>,
        pageSize: PdfPageSize,
        marginSize: PdfMarginSize,
        quality: PdfExportQuality,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "At least one image is required" }
        outputFile.parentFile?.mkdirs()

        val pdfDocument = PdfDocument()
        try {
            val marginPt = when (marginSize) {
                PdfMarginSize.NONE -> 0f
                PdfMarginSize.SMALL -> 20f
                PdfMarginSize.NORMAL -> 42f
            }

            pages.forEachIndexed { index, spec ->
                coroutineContext.ensureActive()
                onProgress(index + 1, pages.size)

                val processed = decodeAndProcessImage(
                    uri = spec.uri,
                    userRotationDegrees = spec.rotationDegrees,
                    filter = spec.filter,
                    maxDimensionPx = quality.maxDimensionPx,
                    jpegQuality = quality.jpegQuality
                )

                try {
                    val (pageW, pageH) = when (pageSize) {
                        PdfPageSize.A4 -> {
                            if (processed.width > processed.height) 842 to 595 else 595 to 842
                        }
                        PdfPageSize.LETTER -> {
                            if (processed.width > processed.height) 792 to 612 else 612 to 792
                        }
                        PdfPageSize.FIT_IMAGE -> {
                            val baseW = 595
                            val ratio = processed.height.toFloat() / max(1, processed.width).toFloat()
                            val fitH = (baseW * ratio).roundToInt().coerceIn(200, 1600)
                            baseW to fitH
                        }
                    }

                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    val availW = (pageW - marginPt * 2f).coerceAtLeast(10f)
                    val availH = (pageH - marginPt * 2f).coerceAtLeast(10f)
                    val imgAspect = processed.width.toFloat() / max(1, processed.height).toFloat()
                    val boxAspect = availW / availH

                    val drawW: Float
                    val drawH: Float
                    if (imgAspect > boxAspect) {
                        drawW = availW
                        drawH = availW / imgAspect
                    } else {
                        drawH = availH
                        drawW = availH * imgAspect
                    }

                    val left = marginPt + (availW - drawW) / 2f
                    val top = marginPt + (availH - drawH) / 2f
                    val destRect = RectF(left, top, left + drawW, top + drawH)

                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(processed, null, destRect, paint)
                    pdfDocument.finishPage(page)
                } finally {
                    processed.recycle()
                }
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            outputFile
        } finally {
            pdfDocument.close()
        }
    }

    private fun decodeAndProcessImage(
        uri: Uri,
        userRotationDegrees: Int,
        filter: ImageEnhancementFilter,
        maxDimensionPx: Int,
        jpegQuality: Int
    ): Bitmap {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, boundsOpts)
        } ?: throw IllegalStateException("Cannot open image stream: $uri")

        val rawW = max(1, boundsOpts.outWidth)
        val rawH = max(1, boundsOpts.outHeight)
        var inSampleSize = 1
        while ((rawW / inSampleSize) > maxDimensionPx * 2 || (rawH / inSampleSize) > maxDimensionPx * 2) {
            inSampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            this.inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, decodeOpts)
        } ?: throw IllegalStateException("Failed to decode bitmap: $uri")

        val exifRotation = readExifRotation(uri)
        val totalRotation = (exifRotation + userRotationDegrees) % 360

        val maxDim = max(decoded.width, decoded.height)
        val scale = if (maxDim > maxDimensionPx) maxDimensionPx.toFloat() / maxDim.toFloat() else 1f

        val transformed = if (totalRotation != 0 || scale < 0.999f) {
            val matrix = Matrix().apply {
                if (scale < 0.999f) postScale(scale, scale)
                if (totalRotation != 0) postRotate(totalRotation.toFloat())
            }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated !== decoded) decoded.recycle()
            rotated
        } else {
            decoded
        }

        val filtered = if (filter != ImageEnhancementFilter.ORIGINAL) {
            val out = Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(createEnhancementMatrix(filter))
            }
            canvas.drawBitmap(transformed, 0f, 0f, paint)
            transformed.recycle()
            out
        } else {
            transformed
        }

        // Apply JPEG compression pass when Compact/Balanced is selected so output PDF size drops genuinely
        return if (jpegQuality < 90) {
            val baos = ByteArrayOutputStream()
            filtered.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
            filtered.recycle()
            val bytes = baos.toByteArray()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } else {
            filtered
        }
    }

    private fun readExifRotation(uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
    }

    suspend fun createPdfFromMarkdown(
        title: String,
        markdown: String,
        pageSize: PdfPageSize,
        includePageNumbers: Boolean,
        outputFile: File
    ): File = withContext(Dispatchers.IO) {
        outputFile.parentFile?.mkdirs()
        val blocks = parseMarkdown(markdown)
        val (pageW, pageH) = when (pageSize) {
            PdfPageSize.LETTER -> 612 to 792
            else -> 595 to 842
        }

        val marginH = 48f
        val marginTop = 52f
        val marginBottom = 52f
        val contentWidth = (pageW - marginH * 2f).roundToInt()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#121216")
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val h1Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#121216")
            textSize = 17f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val h2Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1F1F26")
            textSize = 14.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val h3Paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#32323C")
            textSize = 12.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#242429")
            textSize = 11.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val quotePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4B4B55")
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        }
        val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1C1C22")
            textSize = 10f
            typeface = Typeface.MONOSPACE
        }
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0DDD5")
            strokeWidth = 1f
        }
        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF4D3D")
            strokeWidth = 2.2f
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#76757D")
            textSize = 9.5f
            typeface = Typeface.MONOSPACE
        }

        // First pass: lay out into virtual pages to know exact page count if page numbers enabled
        data class DrawOp(val block: MarkdownBlock?, val isTitle: Boolean = false)

        val pdfDocument = PdfDocument()
        try {
            var currentPageNum = 1
            var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, currentPageNum).create())
            var canvas = page.canvas
            canvas.drawColor(Color.WHITE)
            var cursorY = marginTop
            val maxY = pageH - marginBottom

            fun finishAndStartNewPage() {
                if (includePageNumbers) {
                    val label = currentPageNum.toString()
                    val tw = footerPaint.measureText(label)
                    canvas.drawText(label, pageW - marginH - tw, pageH - 26f, footerPaint)
                }
                pdfDocument.finishPage(page)
                currentPageNum += 1
                page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, currentPageNum).create())
                canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                cursorY = marginTop
            }

            if (title.isNotBlank()) {
                val titleLayout = StaticLayout.Builder
                    .obtain(title.trim(), 0, title.trim().length, titlePaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1.12f)
                    .build()
                canvas.save()
                canvas.translate(marginH, cursorY)
                titleLayout.draw(canvas)
                canvas.restore()
                cursorY += titleLayout.height + 10f
                canvas.drawLine(marginH, cursorY, marginH + 42f, cursorY, accentPaint)
                canvas.drawLine(marginH + 46f, cursorY, pageW - marginH, cursorY, rulePaint)
                cursorY += 18f
            }

            fun drawStaticLayoutLineByLine(
                text: CharSequence,
                paint: TextPaint,
                leftOffset: Float = 0f,
                spaceAfter: Float = 8f,
                drawLeftAccentBar: Boolean = false,
                drawCodeBox: Boolean = false,
                prefixLabel: String? = null
            ) {
                val layoutWidth = (contentWidth - leftOffset).roundToInt().coerceAtLeast(80)
                val layout = StaticLayout.Builder
                    .obtain(text, 0, text.length, paint, layoutWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(2f, 1.28f)
                    .build()

                for (line in 0 until layout.lineCount) {
                    val lineTop = layout.getLineTop(line).toFloat()
                    val lineBottom = layout.getLineBottom(line).toFloat()
                    val lineHeight = lineBottom - lineTop

                    if (cursorY + lineHeight > maxY) {
                        finishAndStartNewPage()
                    }

                    if (drawCodeBox) {
                        val bgPaint = Paint().apply { color = Color.parseColor("#F4F2EC") }
                        canvas.drawRect(
                            marginH + leftOffset - 6f,
                            cursorY - 2f,
                            pageW - marginH,
                            cursorY + lineHeight + 2f,
                            bgPaint
                        )
                    }
                    if (drawLeftAccentBar) {
                        canvas.drawLine(
                            marginH + 4f,
                            cursorY,
                            marginH + 4f,
                            cursorY + lineHeight,
                            accentPaint
                        )
                    }
                    if (line == 0 && prefixLabel != null) {
                        canvas.drawText(prefixLabel, marginH, cursorY - layout.getLineAscent(0), paint)
                    }

                    canvas.save()
                    canvas.translate(marginH + leftOffset, cursorY - lineTop)
                    canvas.clipRect(0f, lineTop, layoutWidth.toFloat(), lineBottom)
                    layout.draw(canvas)
                    canvas.restore()

                    cursorY += lineHeight
                }
                cursorY += spaceAfter
            }

            for (block in blocks) {
                coroutineContext.ensureActive()
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val (paint, beforeSpace, afterSpace) = when (block.level) {
                            1 -> Triple(h1Paint, 10f, 8f)
                            2 -> Triple(h2Paint, 8f, 6f)
                            else -> Triple(h3Paint, 6f, 5f)
                        }
                        if (cursorY + 38f > maxY) finishAndStartNewPage()
                        cursorY += beforeSpace
                        drawStaticLayoutLineByLine(parseInlineFormatting(block.text), paint, spaceAfter = afterSpace)
                    }
                    is MarkdownBlock.Paragraph -> {
                        drawStaticLayoutLineByLine(parseInlineFormatting(block.text), bodyPaint, spaceAfter = 9f)
                    }
                    is MarkdownBlock.BulletItem -> {
                        drawStaticLayoutLineByLine(
                            text = parseInlineFormatting(block.text),
                            paint = bodyPaint,
                            leftOffset = 18f,
                            spaceAfter = 5f,
                            prefixLabel = "•"
                        )
                    }
                    is MarkdownBlock.NumberedItem -> {
                        drawStaticLayoutLineByLine(
                            text = parseInlineFormatting(block.text),
                            paint = bodyPaint,
                            leftOffset = 22f,
                            spaceAfter = 5f,
                            prefixLabel = "${block.number}."
                        )
                    }
                    is MarkdownBlock.Quote -> {
                        drawStaticLayoutLineByLine(
                            text = parseInlineFormatting(block.text),
                            paint = quotePaint,
                            leftOffset = 16f,
                            spaceAfter = 9f,
                            drawLeftAccentBar = true
                        )
                    }
                    is MarkdownBlock.CodeBlock -> {
                        drawStaticLayoutLineByLine(
                            text = block.code,
                            paint = codePaint,
                            leftOffset = 10f,
                            spaceAfter = 10f,
                            drawCodeBox = true
                        )
                    }
                    MarkdownBlock.Divider -> {
                        if (cursorY + 16f > maxY) finishAndStartNewPage()
                        cursorY += 6f
                        canvas.drawLine(marginH, cursorY, pageW - marginH, cursorY, rulePaint)
                        cursorY += 12f
                    }
                }
            }

            if (includePageNumbers) {
                val label = currentPageNum.toString()
                val tw = footerPaint.measureText(label)
                canvas.drawText(label, pageW - marginH - tw, pageH - 26f, footerPaint)
            }
            pdfDocument.finishPage(page)

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            outputFile
        } finally {
            pdfDocument.close()
        }
    }

    fun parseMarkdown(input: String): List<MarkdownBlock> {
        val lines = input.replace("\r\n", "\n").split("\n")
        val blocks = mutableListOf<MarkdownBlock>()
        var inCodeFence = false
        val codeBuffer = StringBuilder()
        val paragraphBuffer = StringBuilder()

        fun flushParagraph() {
            if (paragraphBuffer.isNotEmpty()) {
                blocks += MarkdownBlock.Paragraph(paragraphBuffer.toString().trim())
                paragraphBuffer.clear()
            }
        }

        val numberedRegex = Regex("^(\\d+)\\.\\s+(.+)$")

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed.startsWith("```")) {
                flushParagraph()
                if (inCodeFence) {
                    blocks += MarkdownBlock.CodeBlock(codeBuffer.toString().trimEnd())
                    codeBuffer.clear()
                    inCodeFence = false
                } else {
                    inCodeFence = true
                }
                continue
            }
            if (inCodeFence) {
                codeBuffer.appendLine(rawLine)
                continue
            }

            when {
                trimmed.isEmpty() -> flushParagraph()
                trimmed == "---" || trimmed == "***" -> {
                    flushParagraph()
                    blocks += MarkdownBlock.Divider
                }
                trimmed.startsWith("### ") -> {
                    flushParagraph()
                    blocks += MarkdownBlock.Heading(3, trimmed.removePrefix("### ").trim())
                }
                trimmed.startsWith("## ") -> {
                    flushParagraph()
                    blocks += MarkdownBlock.Heading(2, trimmed.removePrefix("## ").trim())
                }
                trimmed.startsWith("# ") -> {
                    flushParagraph()
                    blocks += MarkdownBlock.Heading(1, trimmed.removePrefix("# ").trim())
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flushParagraph()
                    blocks += MarkdownBlock.BulletItem(trimmed.drop(2).trim())
                }
                numberedRegex.matches(trimmed) -> {
                    flushParagraph()
                    val m = numberedRegex.matchEntire(trimmed)!!
                    blocks += MarkdownBlock.NumberedItem(m.groupValues[1], m.groupValues[2])
                }
                trimmed.startsWith("> ") -> {
                    flushParagraph()
                    blocks += MarkdownBlock.Quote(trimmed.removePrefix("> ").trim())
                }
                else -> {
                    if (paragraphBuffer.isNotEmpty()) paragraphBuffer.append(' ')
                    paragraphBuffer.append(trimmed)
                }
            }
        }
        flushParagraph()
        if (inCodeFence && codeBuffer.isNotEmpty()) {
            blocks += MarkdownBlock.CodeBlock(codeBuffer.toString().trimEnd())
        }
        return blocks
    }

    private fun parseInlineFormatting(text: String): CharSequence {
        val builder = SpannableStringBuilder()
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        val startIdx = builder.length
                        builder.append(text.substring(i + 2, end))
                        builder.setSpan(StyleSpan(Typeface.BOLD), startIdx, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 2
                    } else {
                        builder.append(text[i])
                        i++
                    }
                }
                text[i] == '*' -> {
                    val end = text.indexOf('*', i + 1)
                    if (end != -1) {
                        val startIdx = builder.length
                        builder.append(text.substring(i + 1, end))
                        builder.setSpan(StyleSpan(Typeface.ITALIC), startIdx, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 1
                    } else {
                        builder.append(text[i])
                        i++
                    }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        val startIdx = builder.length
                        builder.append(text.substring(i + 1, end))
                        builder.setSpan(TypefaceSpan("monospace"), startIdx, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        builder.setSpan(BackgroundColorSpan(Color.parseColor("#EFECE4")), startIdx, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 1
                    } else {
                        builder.append(text[i])
                        i++
                    }
                }
                else -> {
                    builder.append(text[i])
                    i++
                }
            }
        }
        return builder
    }

    suspend fun exportOrganizedPdf(
        pages: List<OrganizerPageSpec>,
        outputFile: File,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "No pages to export" }
        outputFile.parentFile?.mkdirs()

        val openRenderers = mutableMapOf<String, Pair<ParcelFileDescriptor, PdfRenderer>>()
        val pdfDocument = PdfDocument()

        try {
            pages.forEachIndexed { outIndex, spec ->
                coroutineContext.ensureActive()
                onProgress(outIndex + 1, pages.size)

                val key = spec.sourceUri.toString()
                val (_, renderer) = openRenderers.getOrPut(key) {
                    val pfd = if (spec.sourceUri.scheme == "file") {
                        ParcelFileDescriptor.open(File(spec.sourceUri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
                    } else {
                        context.contentResolver.openFileDescriptor(spec.sourceUri, "r")
                            ?: throw IllegalStateException("Cannot open ${spec.sourceUri}")
                    }
                    pfd to PdfRenderer(pfd)
                }

                if (spec.sourcePageIndex !in 0 until renderer.pageCount) return@forEachIndexed
                val srcPage = renderer.openPage(spec.sourcePageIndex)
                var renderedBitmap: Bitmap? = null
                try {
                    val ptW = max(1, srcPage.width)
                    val ptH = max(1, srcPage.height)
                    val normRot = ((spec.rotationDegrees % 360) + 360) % 360
                    val swap = normRot == 90 || normRot == 270
                    val outPtW = if (swap) ptH else ptW
                    val outPtH = if (swap) ptW else ptH

                    // Render at ~2.5x print crispness capped at 2100px to stay OOM-safe
                    val scale = min(2.5f, 2100f / max(ptW, ptH).toFloat()).coerceAtLeast(1f)
                    val bmpW = (ptW * scale).roundToInt().coerceAtLeast(1)
                    val bmpH = (ptH * scale).roundToInt().coerceAtLeast(1)

                    renderedBitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    renderedBitmap.eraseColor(Color.WHITE)
                    srcPage.render(renderedBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                    val pageInfo = PdfDocument.PageInfo.Builder(outPtW, outPtH, outIndex + 1).create()
                    val destPage = pdfDocument.startPage(pageInfo)
                    val canvas = destPage.canvas
                    canvas.drawColor(Color.WHITE)

                    val matrix = Matrix().apply {
                        postScale(ptW.toFloat() / bmpW.toFloat(), ptH.toFloat() / bmpH.toFloat())
                        when (normRot) {
                            90 -> {
                                postRotate(90f)
                                postTranslate(ptH.toFloat(), 0f)
                            }
                            180 -> {
                                postRotate(180f)
                                postTranslate(ptW.toFloat(), ptH.toFloat())
                            }
                            270 -> {
                                postRotate(270f)
                                postTranslate(0f, ptW.toFloat())
                            }
                        }
                    }
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    canvas.drawBitmap(renderedBitmap, matrix, paint)
                    pdfDocument.finishPage(destPage)
                } finally {
                    renderedBitmap?.recycle()
                    srcPage.close()
                }
            }

            FileOutputStream(outputFile).use { out ->
                pdfDocument.writeTo(out)
            }
            outputFile
        } finally {
            pdfDocument.close()
            openRenderers.values.forEach { (pfd, renderer) ->
                runCatching { renderer.close() }
                runCatching { pfd.close() }
            }
        }
    }

    companion object {
        fun createEnhancementMatrix(filter: ImageEnhancementFilter): ColorMatrix {
            return when (filter) {
                ImageEnhancementFilter.ORIGINAL -> ColorMatrix()
                ImageEnhancementFilter.BW_SCAN -> {
                    val gray = ColorMatrix().apply { setSaturation(0f) }
                    val contrast = 1.65f
                    val translate = (-0.5f * contrast + 0.5f) * 255f + 16f
                    val boost = ColorMatrix(
                        floatArrayOf(
                            contrast, 0f, 0f, 0f, translate,
                            0f, contrast, 0f, 0f, translate,
                            0f, 0f, contrast, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                    gray.postConcat(boost)
                    gray
                }
                ImageEnhancementFilter.HIGH_CONTRAST -> {
                    val contrast = 1.28f
                    val translate = (-0.5f * contrast + 0.5f) * 255f + 6f
                    ColorMatrix(
                        floatArrayOf(
                            contrast, 0f, 0f, 0f, translate,
                            0f, contrast, 0f, 0f, translate,
                            0f, 0f, contrast, 0f, translate,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                }
            }
        }
    }
}
