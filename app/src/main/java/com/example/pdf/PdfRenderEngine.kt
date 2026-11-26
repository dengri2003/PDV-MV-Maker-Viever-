package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class PdfRenderKey(
    val uriString: String,
    val pageIndex: Int,
    val targetWidthPx: Int
)

class PdfRenderEngine(private val context: Context) {

    private data class RendererSession(
        val uriString: String,
        val pfd: ParcelFileDescriptor,
        val renderer: PdfRenderer,
        val mutex: Mutex = Mutex()
    ) {
        fun closeQuietly() {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }

    private val sessionPoolMutex = Mutex()
    private val sessions = LinkedHashMap<String, RendererSession>(4, 0.75f, true)
    private val maxOpenSessions = 3

    private val pageAspectCache = ConcurrentHashMap<String, Float>()
    private val pageCountCache = ConcurrentHashMap<String, Int>()

    private val pageBitmapCache: LruCache<String, Bitmap>
    private val thumbnailBitmapCache: LruCache<String, Bitmap>

    init {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        val pageCacheSizeKb = min(maxMemoryKb / 7, 48 * 1024).coerceAtLeast(8 * 1024)
        val thumbCacheSizeKb = min(maxMemoryKb / 16, 16 * 1024).coerceAtLeast(4 * 1024)

        pageBitmapCache = object : LruCache<String, Bitmap>(pageCacheSizeKb) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return (value.byteCount / 1024).coerceAtLeast(1)
            }
        }

        thumbnailBitmapCache = object : LruCache<String, Bitmap>(thumbCacheSizeKb) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return (value.byteCount / 1024).coerceAtLeast(1)
            }
        }
    }

    private suspend fun getOrOpenSession(uriString: String): RendererSession {
        return sessionPoolMutex.withLock {
            sessions[uriString]?.let { existing ->
                return@withLock existing
            }
            val uri = Uri.parse(uriString)
            val pfd = openParcelFileDescriptor(uri)
                ?: throw IllegalStateException("Cannot open file descriptor for $uriString")
            val renderer = try {
                PdfRenderer(pfd)
            } catch (t: Throwable) {
                runCatching { pfd.close() }
                throw t
            }
            val session = RendererSession(uriString, pfd, renderer)
            sessions[uriString] = session
            while (sessions.size > maxOpenSessions) {
                val eldestKey = sessions.keys.firstOrNull { it != uriString } ?: break
                sessions.remove(eldestKey)?.closeQuietly()
            }
            session
        }
    }

    private fun openParcelFileDescriptor(uri: Uri): ParcelFileDescriptor? {
        return if (uri.scheme == "file") {
            val path = uri.path ?: return null
            val file = File(path)
            if (!file.exists()) return null
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")
        }
    }

    suspend fun invalidateUri(uriString: String) {
        sessionPoolMutex.withLock {
            sessions.remove(uriString)?.closeQuietly()
        }
        pageCountCache.remove(uriString)
        val aspectPrefix = "$uriString#"
        pageAspectCache.keys.filter { it.startsWith(aspectPrefix) }.forEach {
            pageAspectCache.remove(it)
        }
        // Evict cached bitmaps for this URI
        synchronized(pageBitmapCache) {
            pageBitmapCache.snapshot().keys.filter { it.startsWith(aspectPrefix) }.forEach {
                pageBitmapCache.remove(it)
            }
        }
        synchronized(thumbnailBitmapCache) {
            thumbnailBitmapCache.snapshot().keys.filter { it.startsWith(aspectPrefix) }.forEach {
                thumbnailBitmapCache.remove(it)
            }
        }
    }

    suspend fun getPageCount(uriString: String): Int = withContext(Dispatchers.IO) {
        pageCountCache[uriString]?.let { return@withContext it }
        val session = getOrOpenSession(uriString)
        val count = session.mutex.withLock {
            session.renderer.pageCount
        }
        pageCountCache[uriString] = count
        count
    }

    suspend fun getPageAspectRatio(uriString: String, pageIndex: Int): Float =
        withContext(Dispatchers.IO) {
            val key = "$uriString#$pageIndex"
            pageAspectCache[key]?.let { return@withContext it }
            val session = getOrOpenSession(uriString)
            session.mutex.withLock {
                if (pageIndex < 0 || pageIndex >= session.renderer.pageCount) {
                    return@withLock 0.707f // A4 fallback
                }
                val page = session.renderer.openPage(pageIndex)
                try {
                    val ratio = if (page.height > 0) {
                        page.width.toFloat() / page.height.toFloat()
                    } else {
                        0.707f
                    }
                    pageAspectCache[key] = ratio
                    ratio
                } finally {
                    page.close()
                }
            }
        }

    fun getCachedPage(uriString: String, pageIndex: Int, targetWidthPx: Int): Bitmap? {
        val bucketWidth = bucketWidth(targetWidthPx)
        val key = "$uriString#$pageIndex#$bucketWidth"
        val exact = pageBitmapCache.get(key)
        if (exact != null && !exact.isRecycled) return exact

        // Fallback: check if any cached width for this page exists to avoid flash during zoom
        val prefix = "$uriString#$pageIndex#"
        var bestMatch: Bitmap? = null
        synchronized(pageBitmapCache) {
            for ((k, bmp) in pageBitmapCache.snapshot()) {
                if (k.startsWith(prefix) && !bmp.isRecycled) {
                    if (bestMatch == null || bmp.width > bestMatch!!.width) {
                        bestMatch = bmp
                    }
                }
            }
        }
        return bestMatch
    }

    fun getCachedThumbnail(uriString: String, pageIndex: Int, targetWidthPx: Int): Bitmap? {
        val key = "$uriString#$pageIndex#t$targetWidthPx"
        return thumbnailBitmapCache.get(key)?.takeIf { !it.isRecycled }
    }

    suspend fun renderPage(
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int
    ): Bitmap? = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val bucketWidth = bucketWidth(targetWidthPx).coerceIn(240, 2600)
        val cacheKey = "$uriString#$pageIndex#$bucketWidth"

        pageBitmapCache.get(cacheKey)?.takeIf { !it.isRecycled }?.let {
            return@withContext it
        }

        val session = getOrOpenSession(uriString)
        coroutineContext.ensureActive()

        session.mutex.withLock {
            coroutineContext.ensureActive()
            pageBitmapCache.get(cacheKey)?.takeIf { !it.isRecycled }?.let {
                return@withLock it
            }

            if (pageIndex < 0 || pageIndex >= session.renderer.pageCount) {
                return@withLock null
            }

            val page = session.renderer.openPage(pageIndex)
            try {
                coroutineContext.ensureActive()
                val pageW = max(1, page.width)
                val pageH = max(1, page.height)
                val aspect = pageW.toFloat() / pageH.toFloat()
                pageAspectCache["$uriString#$pageIndex"] = aspect

                val scale = bucketWidth.toFloat() / pageW.toFloat()
                var outW = bucketWidth
                var outH = (pageH * scale).roundToInt().coerceAtLeast(1)

                // Guard against extreme tall pages causing OOM
                val maxHeightPx = 3600
                if (outH > maxHeightPx) {
                    val clampFactor = maxHeightPx.toFloat() / outH.toFloat()
                    outH = maxHeightPx
                    outW = (outW * clampFactor).roundToInt().coerceAtLeast(1)
                }

                val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                if (coroutineContext.let { runCatching { it.ensureActive() }.isFailure }) {
                    bitmap.recycle()
                    throw CancellationException("Render cancelled")
                }

                pageBitmapCache.put(cacheKey, bitmap)
                bitmap
            } finally {
                page.close()
            }
        }
    }

    suspend fun renderThumbnail(
        uriString: String,
        pageIndex: Int,
        targetWidthPx: Int = 220
    ): Bitmap? = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val width = targetWidthPx.coerceIn(96, 480)
        val cacheKey = "$uriString#$pageIndex#t$width"

        thumbnailBitmapCache.get(cacheKey)?.takeIf { !it.isRecycled }?.let {
            return@withContext it
        }

        val session = runCatching { getOrOpenSession(uriString) }.getOrNull()
            ?: return@withContext null

        session.mutex.withLock {
            coroutineContext.ensureActive()
            thumbnailBitmapCache.get(cacheKey)?.takeIf { !it.isRecycled }?.let {
                return@withLock it
            }
            if (pageIndex < 0 || pageIndex >= session.renderer.pageCount) {
                return@withLock null
            }
            val page = session.renderer.openPage(pageIndex)
            try {
                coroutineContext.ensureActive()
                val pageW = max(1, page.width)
                val pageH = max(1, page.height)
                val aspect = pageW.toFloat() / pageH.toFloat()
                pageAspectCache["$uriString#$pageIndex"] = aspect

                val outH = (width / aspect).roundToInt().coerceIn(60, 720)
                val bitmap = Bitmap.createBitmap(width, outH, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                thumbnailBitmapCache.put(cacheKey, bitmap)
                bitmap
            } finally {
                page.close()
            }
        }
    }

    fun getCacheSizeBytes(): Long {
        return (pageBitmapCache.size().toLong() + thumbnailBitmapCache.size().toLong()) * 1024L
    }

    fun clearAllCaches() {
        pageBitmapCache.evictAll()
        thumbnailBitmapCache.evictAll()
    }

    private fun bucketWidth(rawWidth: Int): Int {
        // Snap width to 180px buckets so minor layout/zoom jitters reuse cache cleanly
        return ((rawWidth + 179) / 180) * 180
    }
}
