package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey val id: String,
    val uriString: String,
    val displayName: String,
    val pageCount: Int,
    val fileSizeBytes: Long,
    val lastReadPage: Int = 0,
    val bookmarksCsv: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val sourceType: String = SOURCE_IMPORTED,
    val isInternalFile: Boolean = false
) {
    fun bookmarkSet(): Set<Int> {
        if (bookmarksCsv.isBlank()) return emptySet()
        return bookmarksCsv.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    }

    companion object {
        const val SOURCE_IMPORTED = "IMPORTED"
        const val SOURCE_IMAGES = "IMAGES"
        const val SOURCE_MARKDOWN = "MARKDOWN"
        const val SOURCE_MERGED = "MERGED"
    }
}
