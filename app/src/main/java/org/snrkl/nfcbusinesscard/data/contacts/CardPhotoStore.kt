package org.snrkl.nfcbusinesscard.data.contacts

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Keeps imported contact photos in the app's own storage.
 *
 * An imported photo cannot be referenced where it came from. A contact's photo
 * URI needs `READ_CONTACTS` to read again later, and a `.vcf`'s photo is base64
 * inside a file the app has only momentary access to. Either way the reference
 * would be dead by the next launch, so the bytes are copied somewhere the app
 * owns.
 *
 * These are display-only, exactly as with a photo the operator picks by hand:
 * never serialised into a vCard, never sent over NFC, never in a QR code.
 */
internal object CardPhotoStore {

    private const val TAG = "CardPhotoStore"
    private const val DIRECTORY = "card-photos"

    /**
     * Largest photo kept, in bytes.
     *
     * A contact photo is typically well under this. The cap is here because the
     * source is arbitrary — a `.vcf` from another device can carry a
     * multi-megabyte image — and this is only ever drawn at avatar size.
     */
    private const val MAX_BYTES = 2 * 1024 * 1024

    /** Copies [bytes] into app storage, returning a URI, or null on failure. */
    fun save(context: Context, bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > MAX_BYTES) {
            if (bytes.size > MAX_BYTES) {
                Log.w(TAG, "Photo of ${bytes.size} bytes exceeds the cap; not stored")
            }
            return null
        }
        return runCatching {
            val directory = File(context.filesDir, DIRECTORY).apply { mkdirs() }
            val file = File(directory, "${UUID.randomUUID()}.img")
            file.writeBytes(bytes)
            Uri.fromFile(file).toString()
        }.onFailure { Log.w(TAG, "Could not store photo", it) }.getOrNull()
    }

    /** Copies a stream into app storage, capped. */
    fun save(context: Context, stream: InputStream): String? = runCatching {
        val bytes = stream.readAtMost(MAX_BYTES + 1)
        save(context, bytes)
    }.onFailure { Log.w(TAG, "Could not read photo stream", it) }.getOrNull()

    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val buffer = ByteArray(limit)
        var read = 0
        while (read < limit) {
            val count = read(buffer, read, limit - read)
            if (count <= 0) break
            read += count
        }
        return buffer.copyOf(read)
    }
}
