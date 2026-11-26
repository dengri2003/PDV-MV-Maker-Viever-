package com.example.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        bytes < kb -> "$bytes B"
        bytes < mb -> String.format(Locale.US, "%.1f KB", bytes / kb)
        bytes < gb -> String.format(Locale.US, "%.1f MB", bytes / mb)
        else -> String.format(Locale.US, "%.2f GB", bytes / gb)
    }
}

fun formatShortDate(epochMillis: Long): String {
    return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
        .format(Date(epochMillis))
}
