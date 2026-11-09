package io.github.g00fy2.quickie

import kotlin.math.abs
import kotlin.math.max

/** Reject invalid suggestions and tiny zoom changes to avoid hunting between almost identical ratios. */
internal object ZoomPolicy {

  fun suggestedRatio(requested: Float, current: Float, maximum: Float): Float? {
    val invalid = !requested.isFinite() || !current.isFinite() || !maximum.isFinite()
    if (invalid || maximum < 1f) return null
    val target = requested.coerceIn(1f, maximum.coerceAtMost(6f))
    return target.takeIf { it > current + 0.05f }
  }

  fun forSmallCode(fraction: Float, current: Float, maximum: Float): Float? {
    if (!fraction.isFinite() || fraction !in 0.025f..0.28f) return null
    val requested = current * (0.32f / fraction).coerceAtMost(1.5f)
    return suggestedRatio(requested, current, maximum)
  }

  fun visibleMaximum(x: Float, y: Float, width: Float, height: Float, current: Float): Float {
    val extent = max(width + 2f * abs(x - 0.5f), height + 2f * abs(y - 0.5f))
    return current * 0.9f / extent
  }
}