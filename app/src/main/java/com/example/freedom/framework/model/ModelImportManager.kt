package com.example.freedom.framework.model

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Handles the physical import of a local model file into FREEDOM private storage.
 *
 * Pipeline:
 * 1. User selects a .litertlm file via Android SAF (Storage Access Framework)
 * 2. ContentResolver opens the URI → streams bytes into FREEDOM private storage
 * 3. SHA-256 is computed over the written bytes (single-pass)
 * 4. A [LocalModelManifest] is created and returned
 *
 * Design Principles:
 * - The organizationId is always provided by the authenticated session, NEVER derived from the file
 * - SHA-256 is computed locally from actual bytes, NEVER trusted from user metadata
 * - Only .litertlm files are accepted; format is validated by extension
 * - The model file is stored in context.filesDir/freedom_models/ (app-private)
 */
class ModelImportManager(private val context: Context) {

    companion object {
        private const val TAG = "ModelImportManager"
        private const val MODELS_DIR = "freedom_models"
        private const val LITERTLM_EXTENSION = ".litertlm"
        private const val BUFFER_SIZE = 8192
    }

    sealed class ImportResult {
        data class Success(val manifest: LocalModelManifest) : ImportResult()
        data class Error(val message: String, val cause: Throwable? = null) : ImportResult()
    }

    /**
     * Import a model file from a SAF URI into FREEDOM private storage.
     *
     * @param uri SAF URI obtained from ActivityResultContracts.OpenDocument
     * @param organizationId The authenticated organization's ID (from session, never from file)
     * @param displayName Optional human-readable name; defaults to file name without extension
     * @return [ImportResult.Success] with manifest, or [ImportResult.Error] with message
     */
    suspend fun importModel(
        uri: Uri,
        organizationId: String,
        displayName: String? = null
    ): ImportResult = withContext(Dispatchers.IO) {
        try {
            // 1. Resolve file name from URI
            val fileName = resolveFileName(uri)
            if (fileName == null) {
                return@withContext ImportResult.Error("Could not determine file name from the selected file.")
            }

            // 2. Validate extension
            if (!fileName.lowercase().endsWith(LITERTLM_EXTENSION)) {
                return@withContext ImportResult.Error(
                    "Only .litertlm files are supported. Selected: $fileName"
                )
            }

            // 3. Ensure private models directory exists
            val modelsDir = File(context.filesDir, MODELS_DIR)
            if (!modelsDir.exists()) modelsDir.mkdirs()

            // 4. Generate unique model ID and destination file
            val modelId = "model-${UUID.randomUUID()}"
            val destFile = File(modelsDir, "$modelId$LITERTLM_EXTENSION")

            // 5. Copy bytes from SAF URI → destFile, compute SHA-256 in a single pass
            val digest = MessageDigest.getInstance("SHA-256")
            var fileSize = 0L

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        fileSize += bytesRead
                    }
                }
            } ?: return@withContext ImportResult.Error("Could not open the selected file. Please try again.")

            if (fileSize == 0L) {
                destFile.delete()
                return@withContext ImportResult.Error("The selected file appears to be empty.")
            }

            // 6. Compute hex SHA-256
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }

            // 7. Build manifest
            val resolvedDisplayName = displayName?.takeIf { it.isNotBlank() }
                ?: fileName.removeSuffix(LITERTLM_EXTENSION)

            val manifest = LocalModelManifest(
                modelId = modelId,
                displayName = resolvedDisplayName,
                source = ModelSource.USER_IMPORTED,
                runtimeType = ModelRuntimeType.LITERT_LM,
                artifactType = ModelArtifactType.LITERTLM,
                localPath = destFile.absolutePath,
                fileName = fileName,
                fileSize = fileSize,
                sha256 = sha256,
                organizationId = organizationId,
                status = ModelLifecycleStatus.REGISTERED
            )

            Log.i(TAG, "Model imported: $modelId, size=${fileSize}B, sha256=${sha256.take(16)}...")
            ImportResult.Success(manifest)

        } catch (e: Exception) {
            Log.e(TAG, "Model import failed", e)
            ImportResult.Error("Import failed: ${e.message}", e)
        }
    }

    /**
     * Delete the physical model file from FREEDOM private storage.
     * Does not affect Room data or any operational records.
     */
    fun deleteModelFile(manifest: LocalModelManifest): Boolean {
        return try {
            val file = File(manifest.localPath)
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete model file: ${manifest.localPath}", e)
            false
        }
    }

    /**
     * Resolve the file name from a SAF URI using ContentResolver.
     */
    private fun resolveFileName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            // Fall back to last path segment
            uri.lastPathSegment
        }
    }
}
