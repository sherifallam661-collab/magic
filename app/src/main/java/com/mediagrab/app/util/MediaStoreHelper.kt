package com.mediagrab.app.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class PublishedMediaFile(
    val uri: String,
    val filePath: String?,
    val sizeBytes: Long
)

@Singleton
class MediaStoreHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun publishToMediaStore(
        sourceFile: File,
        displayName: String,
        mimeType: String,
        isVideo: Boolean,
        subFolder: String = "MediaGrab"
    ): PublishedMediaFile? = withContext(Dispatchers.IO) {
        if (!sourceFile.exists()) return@withContext null
        val fileSize = sourceFile.length()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val baseDir = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseDir/$subFolder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val itemUri = resolver.insert(collection, values) ?: return@withContext null

            try {
                resolver.openOutputStream(itemUri)?.use { out ->
                    sourceFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(itemUri, values, null, null)
                sourceFile.delete()
                PublishedMediaFile(
                    uri = itemUri.toString(),
                    filePath = null,
                    sizeBytes = fileSize
                )
            } catch (e: Exception) {
                resolver.delete(itemUri, null, null)
                null
            }
        } else {
            val baseDir = Environment.getExternalStoragePublicDirectory(
                if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC
            )
            val targetDir = File(baseDir, subFolder).apply { mkdirs() }
            val destFile = File(targetDir, displayName)
            sourceFile.copyTo(destFile, overwrite = true)
            sourceFile.delete()
            PublishedMediaFile(
                uri = Uri.fromFile(destFile).toString(),
                filePath = destFile.absolutePath,
                sizeBytes = fileSize
            )
        }
    }

    suspend fun deleteFromDevice(localUri: String?, filePath: String?): Boolean =
        withContext(Dispatchers.IO) {
            var deleted = false
            if (!localUri.isNullOrBlank()) {
                runCatching {
                    val uri = Uri.parse(localUri)
                    if (uri.scheme == "content") {
                        deleted = context.contentResolver.delete(uri, null, null) > 0
                    } else if (uri.scheme == "file" && uri.path != null) {
                        deleted = File(uri.path!!).delete()
                    }
                }
            }
            if (!deleted && !filePath.isNullOrBlank()) {
                runCatching { deleted = File(filePath).delete() }
            }
            deleted
        }
}
