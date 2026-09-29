package com.tcloud.core.common

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Extension functions and utilities used across the app.
 */

/** Format bytes into human-readable size string */
@JvmName("formatFileSizeExtension")
fun Long.formatFileSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(this.toDouble()) / Math.log10(1024.0)).toInt()
    val formatter = DecimalFormat("#,##0.##")
    return "${formatter.format(this / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}

fun formatFileSize(bytes: Long): String = bytes.formatFileSize()

/** Format timestamp into readable date string */
fun Long.formatDate(): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}

/** Format timestamp into readable date-time string */
fun Long.formatDateTime(): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}

/** Format duration in milliseconds to MM:SS or HH:MM:SS */
fun Long.formatDuration(): String {
    val totalSeconds = this / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** Generate a unique TCloud object ID */
fun generateObjectId(): String = UUID.randomUUID().toString()

/** Determine MIME category from MIME type */
fun String?.toFileCategory(): FileCategory {
    if (this == null) return FileCategory.OTHER
    return when {
        startsWith("image/") -> FileCategory.PHOTOS
        startsWith("video/") -> FileCategory.VIDEOS
        startsWith("audio/") -> FileCategory.AUDIO
        startsWith("application/pdf") ||
        startsWith("application/msword") ||
        startsWith("application/vnd.openxmlformats") ||
        startsWith("application/vnd.ms-") ||
        startsWith("text/") -> FileCategory.DOCUMENTS
        startsWith("application/zip") ||
        startsWith("application/x-rar") ||
        startsWith("application/x-7z") ||
        startsWith("application/gzip") ||
        startsWith("application/x-tar") -> FileCategory.ARCHIVES
        else -> FileCategory.OTHER
    }
}

/** Get a display-friendly name for a MIME type */
fun String?.mimeDisplayName(): String {
    if (this == null) return "Unknown"
    return when {
        this == "image/jpeg" -> "JPEG Image"
        this == "image/png" -> "PNG Image"
        this == "image/webp" -> "WebP Image"
        this == "image/gif" -> "GIF Image"
        this == "image/heic" || this == "image/heif" -> "HEIC Image"
        this == "video/mp4" -> "MP4 Video"
        this == "video/x-matroska" -> "MKV Video"
        this == "video/webm" -> "WebM Video"
        this == "video/3gpp" -> "3GP Video"
        this == "audio/mpeg" -> "MP3 Audio"
        this == "audio/aac" -> "AAC Audio"
        this == "audio/mp4" -> "M4A Audio"
        this == "audio/wav" || this == "audio/x-wav" -> "WAV Audio"
        this == "audio/flac" -> "FLAC Audio"
        this == "audio/ogg" -> "OGG Audio"
        this == "application/pdf" -> "PDF Document"
        this == "application/msword" -> "Word Document"
        contains("wordprocessingml") -> "Word Document"
        contains("spreadsheetml") -> "Excel Spreadsheet"
        contains("presentationml") -> "PowerPoint Presentation"
        this == "text/plain" -> "Text File"
        this == "text/csv" -> "CSV File"
        this == "application/zip" -> "ZIP Archive"
        contains("x-7z") -> "7Z Archive"
        contains("x-rar") -> "RAR Archive"
        contains("gzip") -> "GZ Archive"
        contains("x-tar") -> "TAR Archive"
        else -> substringAfter("/").uppercase()
    }
}

/** Check if MIME type is previewable */
fun String?.isPreviewable(): Boolean {
    if (this == null) return false
    return when {
        startsWith("image/") -> true
        startsWith("video/") -> true
        startsWith("audio/") -> true
        this == "application/pdf" -> true
        startsWith("text/") -> true
        else -> false
    }
}
