package com.tcloud.sync.scanner

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import com.tcloud.core.common.FileCategory
import com.tcloud.core.common.FileStatus
import com.tcloud.core.common.generateObjectId
import com.tcloud.core.common.toFileCategory
import com.tcloud.core.database.dao.BackupRuleDao
import com.tcloud.core.database.dao.FileEntryDao
import com.tcloud.core.database.entity.FileEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans device media using MediaStore for efficient, incremental file discovery.
 * Does NOT brute-force scan the filesystem.
 */
@Singleton
class MediaScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileEntryDao: FileEntryDao,
    private val backupRuleDao: BackupRuleDao
) {
    /**
     * Scan for new or modified files since the last scan.
     */
    suspend fun scanForNewFiles(): List<FileEntry> = withContext(Dispatchers.IO) {
        val newFiles = mutableListOf<FileEntry>()
        val enabledCategories = backupRuleDao.getEnabledRules().map { it.category }.toSet()

        if (enabledCategories.contains(FileCategory.PHOTOS) ||
            enabledCategories.contains(FileCategory.SCREENSHOTS)
        ) {
            newFiles.addAll(scanMediaStore(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image"))
        }

        if (enabledCategories.contains(FileCategory.VIDEOS)) {
            newFiles.addAll(scanMediaStore(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video"))
        }

        if (enabledCategories.contains(FileCategory.AUDIO)) {
            newFiles.addAll(scanMediaStore(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "audio"))
        }

        if (enabledCategories.contains(FileCategory.DOCUMENTS) ||
            enabledCategories.contains(FileCategory.DOWNLOADS)
        ) {
            newFiles.addAll(scanMediaStore(MediaStore.Files.getContentUri("external"), "file"))
        }

        // Filter out files already indexed
        val filteredFiles = newFiles.filter { newFile ->
            fileEntryDao.findByPath(newFile.path) == null
        }

        // Insert into database
        if (filteredFiles.isNotEmpty()) {
            fileEntryDao.insertAll(filteredFiles)
        }

        filteredFiles
    }

    /**
     * Scan a specific MediaStore collection.
     */
    private fun scanMediaStore(uri: android.net.Uri, mediaType: String): List<FileEntry> {
        val files = mutableListOf<FileEntry>()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.DURATION
        )

        val selection = "${MediaStore.MediaColumns.SIZE} > 0"
        val sortOrder = "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                val widthCol = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
                val durationCol = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val path = cursor.getString(dataCol) ?: continue
                    val mimeType = cursor.getString(mimeCol) ?: "application/octet-stream"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateAddedCol) * 1000
                    val dateModified = cursor.getLong(dateModifiedCol) * 1000
                    val width = if (widthCol >= 0) cursor.getIntOrNull(widthCol) else null
                    val height = if (heightCol >= 0) cursor.getIntOrNull(heightCol) else null
                    val duration = if (durationCol >= 0) cursor.getLongOrNull(durationCol) else null

                    val category = mimeType.toFileCategory()
                    val contentUri = ContentUris.withAppendedId(uri, id).toString()

                    files.add(
                        FileEntry(
                            id = generateObjectId(),
                            name = name,
                            path = path,
                            mimeType = mimeType,
                            size = size,
                            createdAt = dateAdded,
                            modifiedAt = dateModified,
                            localUri = path,
                            status = FileStatus.LOCAL_ONLY,
                            category = category,
                            width = width,
                            height = height,
                            duration = duration
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Handle permission denied or other errors gracefully
            android.util.Log.e("MediaScanner", "Scan error: ${e.message}")
        }

        return files
    }

    private fun Cursor.getIntOrNull(columnIndex: Int): Int? {
        return if (isNull(columnIndex)) null else getInt(columnIndex)
    }

    private fun Cursor.getLongOrNull(columnIndex: Int): Long? {
        return if (isNull(columnIndex)) null else getLong(columnIndex)
    }
}
