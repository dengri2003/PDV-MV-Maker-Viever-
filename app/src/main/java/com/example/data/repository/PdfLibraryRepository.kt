package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.example.data.db.PdfDocumentDao
import com.example.data.db.PdfDocumentEntity
import com.example.pdf.PdfRenderEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

class PdfLibraryRepository(
    private val context: Context,
    private val dao: PdfDocumentDao,
    private val renderEngine: PdfRenderEngine
) {
    val documentsFlow: Flow<List<PdfDocumentEntity>> = dao.observeAll()

    fun observeDocument(id: String): Flow<PdfDocumentEntity?> = dao.observeById(id)

    suspend fun getDocument(id: String): PdfDocumentEntity? = dao.getById(id)

    fun internalPdfsDir(): File = File(context.filesDir, "pdfs").apply { mkdirs() }

    fun createManagedOutputFile(rawName: String): File {
        val clean = sanitizePdfName(rawName)
        var candidate = File(internalPdfsDir(), clean)
        if (candidate.exists()) {
            val base = clean.removeSuffix(".pdf")
            candidate = File(internalPdfsDir(), "${base}_${System.currentTimeMillis() % 10000}.pdf")
        }
        return candidate
    }

    suspend fun registerInternalPdf(
        file: File,
        displayName: String,
        sourceType: String
    ): PdfDocumentEntity = withContext(Dispatchers.IO) {
        val uriString = Uri.fromFile(file).toString()
        val pageCount = renderEngine.getPageCount(uriString)
        val now = System.currentTimeMillis()
        val finalName = sanitizePdfName(displayName)
        val existing = dao.findByUri(uriString)
        val entity = PdfDocumentEntity(
            id = existing?.id ?: UUID.randomUUID().toString(),
            uriString = uriString,
            displayName = finalName,
            pageCount = pageCount,
            fileSizeBytes = file.length(),
            lastReadPage = existing?.lastReadPage ?: 0,
            bookmarksCsv = existing?.bookmarksCsv ?: "",
            createdAt = existing?.createdAt ?: now,
            lastOpenedAt = now,
            sourceType = sourceType,
            isInternalFile = true
        )
        dao.upsert(entity)
        entity
    }

    suspend fun importExternalUri(uri: Uri): Result<PdfDocumentEntity> = withContext(Dispatchers.IO) {
        runCatching {
            var persistableGranted = false
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                persistableGranted = true
            }

            val rawName = queryDisplayName(uri) ?: "Document_${System.currentTimeMillis() % 10000}.pdf"
            val cleanName = sanitizePdfName(rawName)

            val resolvedUriString: String
            val fileSize: Long
            val isInternal: Boolean

            if (persistableGranted) {
                val existing = dao.findByUri(uri.toString())
                if (existing != null) {
                    val refreshedCount = renderEngine.getPageCount(existing.uriString)
                    val updated = existing.copy(
                        pageCount = refreshedCount,
                        lastOpenedAt = System.currentTimeMillis()
                    )
                    dao.upsert(updated)
                    return@runCatching updated
                }
                resolvedUriString = uri.toString()
                fileSize = queryFileSize(uri)
                isInternal = false
            } else {
                // External transient URI (e.g. from ACTION_VIEW): copy to managed storage so it persists safely
                val dest = createManagedOutputFile(cleanName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Cannot read selected PDF")
                resolvedUriString = Uri.fromFile(dest).toString()
                fileSize = dest.length()
                isInternal = true
            }

            val pageCount = renderEngine.getPageCount(resolvedUriString)
            require(pageCount > 0) { "PDF has zero pages" }

            val now = System.currentTimeMillis()
            val entity = PdfDocumentEntity(
                id = UUID.randomUUID().toString(),
                uriString = resolvedUriString,
                displayName = cleanName,
                pageCount = pageCount,
                fileSizeBytes = fileSize,
                lastReadPage = 0,
                bookmarksCsv = "",
                createdAt = now,
                lastOpenedAt = now,
                sourceType = PdfDocumentEntity.SOURCE_IMPORTED,
                isInternalFile = isInternal
            )
            dao.upsert(entity)
            entity
        }
    }

    suspend fun updateReadingProgress(id: String, pageIndex: Int) = withContext(Dispatchers.IO) {
        dao.updateReadingProgress(id, pageIndex.coerceAtLeast(0), System.currentTimeMillis())
    }

    suspend fun toggleBookmark(id: String, pageIndex: Int): Boolean = withContext(Dispatchers.IO) {
        val doc = dao.getById(id) ?: return@withContext false
        val current = doc.bookmarkSet().toMutableSet()
        val added = if (current.contains(pageIndex)) {
            current.remove(pageIndex)
            false
        } else {
            current.add(pageIndex)
            true
        }
        val csv = current.sorted().joinToString(",")
        dao.updateBookmarks(id, csv)
        added
    }

    suspend fun renameDocument(id: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val doc = dao.getById(id) ?: return@withContext false
        val cleanName = sanitizePdfName(newName)
        var newUriString = doc.uriString
        if (doc.isInternalFile) {
            val oldFile = Uri.parse(doc.uriString).path?.let(::File)
            if (oldFile != null && oldFile.exists()) {
                val targetFile = File(oldFile.parentFile, cleanName)
                if (targetFile != oldFile && !targetFile.exists() && oldFile.renameTo(targetFile)) {
                    renderEngine.invalidateUri(doc.uriString)
                    newUriString = Uri.fromFile(targetFile).toString()
                }
            }
        }
        dao.updateNameAndUri(id, cleanName, newUriString)
        true
    }

    suspend fun deleteDocument(id: String) = withContext(Dispatchers.IO) {
        val doc = dao.getById(id) ?: return@withContext
        renderEngine.invalidateUri(doc.uriString)
        if (doc.isInternalFile) {
            Uri.parse(doc.uriString).path?.let(::File)?.delete()
        }
        dao.deleteById(id)
    }

    suspend fun getShareableFileProviderUri(doc: PdfDocumentEntity): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val authority = "${context.packageName}.fileprovider"
            val sourceUri = Uri.parse(doc.uriString)
            val fileToShare: File = if (sourceUri.scheme == "file" && sourceUri.path != null) {
                File(sourceUri.path!!)
            } else {
                val shareDir = File(context.cacheDir, "share").apply { mkdirs() }
                val dest = File(shareDir, sanitizePdfName(doc.displayName))
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(dest).use { output -> input.copyTo(output) }
                } ?: return@withContext null
                dest
            }
            FileProvider.getUriForFile(context, authority, fileToShare)
        }.getOrNull()
    }

    suspend fun copyPdfToUri(sourceUri: Uri, targetUri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val input = if (sourceUri.scheme == "file") {
                FileInputStream(File(sourceUri.path!!))
            } else {
                context.contentResolver.openInputStream(sourceUri)
            } ?: return@withContext false

            input.use { src ->
                context.contentResolver.openOutputStream(targetUri)?.use { dst ->
                    src.copyTo(dst)
                    dst.flush()
                } ?: return@withContext false
            }
            true
        }.getOrDefault(false)
    }

    fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }

    private fun queryFileSize(uri: Uri): Long {
        if (uri.scheme == "file") return uri.path?.let { File(it).length() } ?: 0L
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getLong(0) else 0L
                }
        }.getOrNull() ?: 0L
    }

    companion object {
        fun sanitizePdfName(raw: String): String {
            val trimmed = raw.trim().ifBlank { "Document" }
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            return if (trimmed.endsWith(".pdf", ignoreCase = true)) trimmed else "$trimmed.pdf"
        }
    }
}
