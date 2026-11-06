package com.example.data.cloud

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Result of an artifact download or offline cache check.
 */
sealed class ArtifactDownloadResult {
    data class Success(val file: File, val sizeBytes: Long, val fromCache: Boolean) : ArtifactDownloadResult()
    data class Failure(val error: String, val isRetryable: Boolean = true) : ArtifactDownloadResult()
    data class Progress(val bytesDownloaded: Long, val totalBytes: Long) : ArtifactDownloadResult()
    object Cancelled : ArtifactDownloadResult()
}

/**
 * Download progress callback.
 */
fun interface DownloadProgressListener {
    fun onProgress(bytesDownloaded: Long, totalBytes: Long)
}

/**
 * Service that downloads and manages offline-cached learning artifacts (PDFs, JSON study packs, audio),
 * validating SHA-256 integrity, verifying host allowlists, preventing path traversal, and safely managing storage.
 */
class CloudArtifactDownloader(
    private val context: Context,
    private val client: OkHttpClient = defaultClient(),
    private val roomRepository: CatalogRoomRepository? = null
) {
    private val cacheBaseDir: File by lazy {
        File(context.filesDir, "noorix_artifacts").apply { mkdirs() }
    }

    /**
     * Checks if a valid cached artifact exists for the given record.
     */
    fun getCachedFile(record: CloudArtifactRecord): File? {
        val target = getTargetFile(record)
        if (!target.exists() || target.length() == 0L) return null

        if (record.sha256.isNotBlank()) {
            val actualHash = computeFileSha256(target)
            if (!actualHash.equals(record.sha256, ignoreCase = true)) {
                target.delete()
                return null
            }
        }
        return target
    }

    /**
     * Downloads an artifact with progress reporting and checksum verification, saving it
     * into a version-aware local file.
     */
    suspend fun downloadArtifact(
        record: CloudArtifactRecord,
        allowedHosts: Set<String> = CloudContentConfig.DEFAULT_ALLOWED_HOSTS,
        progressListener: DownloadProgressListener? = null,
        topicId: String = ""
    ): ArtifactDownloadResult = withContext(Dispatchers.IO) {
        if (record.downloadUrl.isBlank()) {
            return@withContext ArtifactDownloadResult.Failure("Missing download URL for artifact '${record.artifactId}'", isRetryable = false)
        }

        // Host security check
        val uri = runCatching { Uri.parse(record.downloadUrl) }.getOrNull()
        val host = uri?.host?.lowercase() ?: ""
        val isAllowedHost = allowedHosts.any { allowed -> host == allowed || host.endsWith(".$allowed") }
        if (!isAllowedHost) {
            return@withContext ArtifactDownloadResult.Failure("Host '$host' is not in approved HTTPS download allowlist", isRetryable = false)
        }

        val targetFile = getTargetFile(record)
        val tempFile = File(cacheBaseDir, "${targetFile.name}.downloading.${System.currentTimeMillis()}")

        try {
            val request = Request.Builder()
                .url(record.downloadUrl)
                .addHeader("User-Agent", "NoorixStudent-Android/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val code = response.code
                    val isRetryable = code in setOf(408, 429, 500, 502, 503, 504)
                    return@withContext ArtifactDownloadResult.Failure("Download failed with HTTP $code: ${response.message}", isRetryable = isRetryable)
                }

                val body = response.body
                    ?: return@withContext ArtifactDownloadResult.Failure("Empty response body from server", isRetryable = true)

                val contentLength = body.contentLength()
                var bytesCopied = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            progressListener?.onProgress(bytesCopied, contentLength)
                        }
                        output.flush()
                    }
                }

                // Verify SHA-256 if provided
                if (record.sha256.isNotBlank()) {
                    val computedHash = computeFileSha256(tempFile)
                    if (!computedHash.equals(record.sha256, ignoreCase = true)) {
                        tempFile.delete()
                        return@withContext ArtifactDownloadResult.Failure(
                            "Checksum mismatch for artifact ${record.artifactId}: expected ${record.sha256}, got $computedHash",
                            isRetryable = true
                        )
                    }
                }

                // Atomic rename to targetFile
                if (targetFile.exists()) targetFile.delete()
                val renamed = tempFile.renameTo(targetFile)
                if (!renamed) {
                    // Fallback copy if rename fails
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                // Store metadata into Room
                roomRepository?.recordDownloadedArtifact(
                    record = record,
                    file = targetFile,
                    topicId = topicId
                )

                ArtifactDownloadResult.Success(
                    file = targetFile,
                    sizeBytes = targetFile.length(),
                    fromCache = false
                )
            }
        } catch (e: CancellationException) {
            tempFile.delete()
            ArtifactDownloadResult.Cancelled
        } catch (e: Exception) {
            tempFile.delete()
            ArtifactDownloadResult.Failure("Download error: ${e.message ?: e.javaClass.simpleName}", isRetryable = true)
        }
    }

    /**
     * Safely deletes a cached artifact file and removes its metadata from Room.
     */
    fun removeCachedArtifact(record: CloudArtifactRecord): Boolean {
        val target = getTargetFile(record)
        roomRepository?.let { repo ->
            kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                repo.removeDownloadedArtifact(record.artifactId)
            }
        }
        return if (target.exists()) target.delete() else true
    }

    /**
     * Clears all cached artifacts and returns reclaimed bytes.
     */
    fun clearAllArtifactCache(): Long {
        var reclaimed = 0L
        cacheBaseDir.listFiles()?.forEach { file ->
            reclaimed += file.length()
            file.delete()
        }
        return reclaimed
    }

    /**
     * Calculates total storage used by cached artifacts.
     */
    fun getCachedStorageBytes(): Long {
        return cacheBaseDir.listFiles()?.sumOf { it.length() } ?: 0L
    }

    fun getTargetFile(record: CloudArtifactRecord): File {
        // Prevent path traversal and sanitize file name
        val safeId = record.artifactId.ifBlank { "art" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeVersion = record.version.ifBlank { "v1" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val safeExt = record.format.ifBlank { "bin" }.replace(Regex("[^A-Za-z0-9]"), "")
        val fileName = "${safeId}_${safeVersion}.${safeExt}"
        return File(cacheBaseDir, fileName)
    }

    private fun computeFileSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun defaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }
}
