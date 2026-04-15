package com.drivesync.app.sync

import com.google.api.client.http.AbstractInputStreamContent
import java.io.InputStream

/**
 * An [AbstractInputStreamContent] that reports upload progress via a callback.
 */
class ProgressTrackingInputStreamContent(
    mimeType: String,
    private val inputStream: InputStream,
    private val length: Long,
    private val onProgress: (bytesUploaded: Long) -> Unit
) : AbstractInputStreamContent(mimeType) {

    override fun getLength(): Long = length

    override fun retrySupported(): Boolean = false

    override fun getInputStream(): InputStream = TrackingInputStream(inputStream, onProgress)

    private class TrackingInputStream(
        private val delegate: InputStream,
        private val onProgress: (Long) -> Unit
    ) : InputStream() {
        private var totalRead = 0L

        override fun read(): Int {
            val byte = delegate.read()
            if (byte != -1) {
                totalRead++
                onProgress(totalRead)
            }
            return byte
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val count = delegate.read(b, off, len)
            if (count > 0) {
                totalRead += count
                onProgress(totalRead)
            }
            return count
        }

        override fun close() = delegate.close()
    }
}
