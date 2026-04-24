package com.github.walma.rtpplayer.ui

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import java.io.File

internal object RecordingStorage {
    private const val PREFS_NAME = "rtp_player_ui_prefs"
    private const val KEY_RECORDINGS_FOLDER_URI = "recordings_folder_uri"

    fun getSelectedFolderUri(context: Context): Uri? {
        val value = context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECORDINGS_FOLDER_URI, null)
        return value?.let(Uri::parse)
    }

    fun persistSelectedFolder(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECORDINGS_FOLDER_URI, uri.toString())
            .apply()
    }

    fun clearSelectedFolder(context: Context) {
        getSelectedFolderUri(context)?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
        }

        context
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_RECORDINGS_FOLDER_URI)
            .apply()
    }

    fun defaultRecordingDirectory(context: Context): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        return File(baseDir, "Recordings").apply { mkdirs() }
    }

    fun folderLabel(context: Context, uri: Uri?): String {
        if (uri == null) {
            return defaultRecordingDirectory(context).absolutePath
        }

        val documentFile = DocumentFile.fromTreeUri(context, uri)
        val folderName = documentFile?.name?.takeIf { it.isNotBlank() }
        val treeDocumentId = DocumentsContract.getTreeDocumentId(uri)
        val relativePath = treeDocumentId
            .substringAfter(':', "")
            .ifBlank { folderName ?: "selected folder" }

        return when {
            treeDocumentId.startsWith("primary:") -> "/storage/emulated/0/$relativePath"
            folderName != null -> folderName
            else -> uri.toString()
        }
    }

    fun saveRecording(
        context: Context,
        sourceFile: File,
        fileName: String,
        destinationFolderUri: Uri?,
    ): SaveResult {
        return if (destinationFolderUri == null) {
            SaveResult.FilePath(sourceFile)
        } else {
            copyToDocumentTree(
                context = context,
                contentResolver = context.contentResolver,
                sourceFile = sourceFile,
                fileName = fileName,
                destinationFolderUri = destinationFolderUri,
            )
        }
    }

    private fun copyToDocumentTree(
        context: Context,
        contentResolver: ContentResolver,
        sourceFile: File,
        fileName: String,
        destinationFolderUri: Uri,
    ): SaveResult {
        val targetFolder = DocumentFile.fromTreeUri(context, destinationFolderUri)

        if (targetFolder == null || !targetFolder.canWrite()) {
            return SaveResult.Error("Selected folder is unavailable")
        }

        targetFolder.findFile(fileName)?.delete()
        val targetFile = targetFolder.createFile("video/mp4", fileName)
            ?: return SaveResult.Error("Failed to create target file")

        return runCatching {
            contentResolver.openOutputStream(targetFile.uri)?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Failed to open target stream")

            sourceFile.delete()
            SaveResult.Document(targetFile.uri)
        }.getOrElse { error ->
            targetFile.delete()
            SaveResult.Error(error.message ?: "Failed to save recording")
        }
    }
}

internal sealed interface SaveResult {
    data class FilePath(val file: File) : SaveResult
    data class Document(val uri: Uri) : SaveResult
    data class Error(val message: String) : SaveResult
}
