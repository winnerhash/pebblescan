package io.github.g00fy2.quickie

/** A potential box or a single noisy frame must not finish a camera session. */
internal class ScanResultConfirmation {

  private var previousText: String? = null
  private var previousBytes: ByteArray? = null
  private var firstSeenAt = 0L
  private var lastSeenAt = -1000L
  private var lastFrame = -1L

  @Synchronized
  fun accept(text: String?, bytes: ByteArray?, frame: Long, now: Long): Boolean {
    if (!BarcodeResultPolicy.hasContent(text, bytes)) {
      lastSeenAt = -1000L
      return false
    }
    val same = if (!text.isNullOrBlank()) text == previousText else bytes.contentEquals(previousBytes)
    val expired = now < lastSeenAt || now - lastSeenAt > 600L
    val repeated = same && !expired && frame != lastFrame
    if (!same || expired) firstSeenAt = now
    previousText = text
    previousBytes = if (text.isNullOrBlank()) bytes?.copyOf() else null
    lastFrame = frame
    lastSeenAt = now
    return repeated && now - firstSeenAt >= 80L
  }
}