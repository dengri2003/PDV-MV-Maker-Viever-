package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDocumentDao {
    @Query("SELECT * FROM pdf_documents ORDER BY lastOpenedAt DESC")
    fun observeAll(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<PdfDocumentEntity?>

    @Query("SELECT * FROM pdf_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PdfDocumentEntity?

    @Query("SELECT * FROM pdf_documents WHERE uriString = :uriString LIMIT 1")
    suspend fun findByUri(uriString: String): PdfDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: PdfDocumentEntity)

    @Query("UPDATE pdf_documents SET lastReadPage = :lastReadPage, lastOpenedAt = :lastOpenedAt WHERE id = :id")
    suspend fun updateReadingProgress(id: String, lastReadPage: Int, lastOpenedAt: Long)

    @Query("UPDATE pdf_documents SET bookmarksCsv = :bookmarksCsv WHERE id = :id")
    suspend fun updateBookmarks(id: String, bookmarksCsv: String)

    @Query("UPDATE pdf_documents SET displayName = :displayName, uriString = :uriString WHERE id = :id")
    suspend fun updateNameAndUri(id: String, displayName: String, uriString: String)

    @Query("DELETE FROM pdf_documents WHERE id = :id")
    suspend fun deleteById(id: String)
}
