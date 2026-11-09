package io.github.g00fy2.quickie

/** Manual intent wins over detection, including while a previous camera request is still in flight. */
internal class ZoomSession(private val maximum: Float) {

  var isManual = false
    private set
  var isApplying = false
    private set
  private var desiredRatio: Float? = null
  private var queuedRatio: Float? = null

  fun manualRatio(ratio: Float): Float? {
    if (!ratio.isFinite()) return null
    isManual = true
    desiredRatio = ratio.coerceIn(1f, maximum)
    queuedRatio = desiredRatio
    return takeNext()
  }

  fun manualScale(factor: Float, current: Float): Float? {
    if (!factor.isFinite() || factor <= 0f) return null
    return manualRatio((desiredRatio ?: current) * factor)
  }

  fun automaticRatio(ratio: Float): Float? {
    if (isManual || !ratio.isFinite()) return null
    queuedRatio = ratio.coerceIn(1f, maximum)
    return takeNext()
  }

  fun resumeAutomatic(): Float? {
    isManual = false
    desiredRatio = null
    queuedRatio = 1f
    return takeNext()
  }

  fun complete(): Float? {
    isApplying = false
    return takeNext()
  }

  private fun takeNext(): Float? {
    val next = queuedRatio
    if (isApplying || next == null) return null
    queuedRatio = null
    isApplying = true
    return next
  }
}