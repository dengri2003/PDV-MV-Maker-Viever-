package com.example.pdf

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PdfPrintHelper {
    fun printPdf(context: Context, uri: Uri, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(
                    if (jobName.endsWith(".pdf", true)) jobName else "$jobName.pdf"
                )
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                try {
                    val input = if (uri.scheme == "file") {
                        FileInputStream(File(uri.path!!))
                    } else {
                        context.contentResolver.openInputStream(uri)
                    }
                    if (input == null) {
                        callback.onWriteFailed("Cannot open PDF for printing")
                        return
                    }
                    input.use { src ->
                        FileOutputStream(destination.fileDescriptor).use { dst ->
                            val buffer = ByteArray(16 * 1024)
                            while (true) {
                                if (cancellationSignal?.isCanceled == true) {
                                    callback.onWriteCancelled()
                                    return
                                }
                                val read = src.read(buffer)
                                if (read <= 0) break
                                dst.write(buffer, 0, read)
                            }
                            dst.flush()
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message ?: "Print failed")
                }
            }
        }
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }
}
