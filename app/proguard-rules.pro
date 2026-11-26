# Keep line numbers & source file metadata for readable crash stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room database & entities
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Application class
-keep class com.example.PdfMvApplication { *; }

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
