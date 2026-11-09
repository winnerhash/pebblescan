package io.github.g00fy2.quickie

import kotlin.math.abs

internal data class ZoomTarget(val x: Float, val y: Float, val fraction: Float, val maximum: Float = 6f)

/** Only zoom toward a stable detected code; never enlarge an empty scene on a timer. */
internal class AutoZoomTracker {

  private var previous: ZoomTarget? = null
  private var firstSeenAt = 0L
  private var lastSeenAt = 0L
  private var lastZoomAt = -1000L

  fun observe(target: ZoomTarget?, now: Long, current: Float, maximum: Float): Float? {
    if (target == null) {
      if (now - lastSeenAt > 600L) previous = null
      return withoutTarget(now, current)
    }
    lastSeenAt = now
    val last = previous
    val samePosition = last != null && abs(last.x - target.x) < 0.12f && abs(last.y - target.y) < 0.12f
    if (!samePosition) firstSeenAt = now
    previous = target
    return if (now - firstSeenAt < 120L || now - lastZoomAt < 450L) {
      null
    } else {
      ZoomPolicy.forSmallCode(target.fraction, current, maximum.coerceAtMost(target.maximum))?.also {
        lastZoomAt = now
        previous = null
      }
    }
  }

  private fun withoutTarget(now: Long, current: Float): Float? {
    val lostCode = current > 1.05f && now - lastSeenAt >= 1600L
    return if (lostCode && now - lastZoomAt >= 450L) {
      lastZoomAt = now
      (current / 1.5f).coerceAtLeast(1f)
    } else {
      null
    }
  }

  fun zoomRequested(now: Long) {
    lastZoomAt = now
    lastSeenAt = now
    previous = null
  }
}