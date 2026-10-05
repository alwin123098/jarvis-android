package com.codotype.jarvis.model

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** Progress of an in-flight download. */
data class DownloadProgress(
    val specId: String,
    val bytesRead: Long,
    val totalBytes: Long,
    val done: Boolean = false
) {
    val fraction: Float
        get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

/**
 * Downloads model files from the Hugging Face Hub into the app's private
 * storage. Supports HTTP range resume so a dropped connection does not restart
 * a 1 GB download from zero.
 */
class ModelRepository(context: Context) {

    private val modelsDir = File(context.filesDir, "models").apply { mkdirs() }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    fun fileFor(spec: ModelSpec): File = File(modelsDir, spec.localName)

    fun isDownloaded(spec: ModelSpec): Boolean {
        val f = fileFor(spec)
        return f.exists() && f.length() > 0
    }

    fun delete(spec: ModelSpec) {
        fileFor(spec).delete()
    }

    /**
     * Downloads [spec] (resuming if a partial file exists). [onProgress] is
     * called on the IO dispatcher.
     */
    suspend fun download(
        spec: ModelSpec,
        onProgress: (DownloadProgress) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val target = fileFor(spec)
        val partFile = File(target.parentFile, target.name + ".part")
        var existing = if (partFile.exists()) partFile.length() else 0L

        try {
            val request = Request.Builder()
                .url(spec.downloadUrl)
                .apply { if (existing > 0) header("Range", "bytes=$existing-") }
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP ${response.code} for ${spec.displayName}")
                    )
                }
                val body = response.body
                    ?: return@withContext Result.failure(IllegalStateException("Empty body"))
                val total = existing + body.contentLength()

                RandomAccessFile(partFile, "rw").use { raf ->
                    if (existing > 0) raf.seek(existing)
                    val buf = ByteArray(64 * 1024)
                    body.byteStream().use { input ->
                        var read: Int
                        var lastEmit = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            coroutineContext.ensureActive()
                            raf.write(buf, 0, read)
                            existing += read
                            if (existing - lastEmit > 256 * 1024) {
                                lastEmit = existing
                                onProgress(DownloadProgress(spec.id, existing, total))
                            }
                        }
                    }
                }
                onProgress(DownloadProgress(spec.id, existing, total, done = true))
            }

            if (target.exists()) target.delete()
            partFile.renameTo(target)
            Result.success(target)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }
}
