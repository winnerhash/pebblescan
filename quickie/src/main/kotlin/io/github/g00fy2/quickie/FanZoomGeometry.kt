package io.github.g00fy2.quickie

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

internal data class FanZoomPoint(val x: Float, val y: Float)

internal data class FanZoomBounds(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Keep the complete dial centred on the zoom button, regardless of the starting zoom. */
internal class FanZoomGeometry(
  val anchor: FanZoomPoint,
  val initialRatio: Float,
  val maximum: Float,
  val bounds: FanZoomBounds,
  density: Float
) {
  private val edge = cos(Math.PI / 4).toFloat()
  private val initialFraction = ln(initialRatio) / ln(maximum)
  private val horizontalRoom = (min(anchor.x - bounds.left, bounds.right - anchor.x) - 40f * density) / edge
  val radius = min(
    min(96f * density, horizontalRoom),
    (bounds.bottom - anchor.y - 12f * density) / (1f - edge)
  ).coerceAtLeast(24f * density)
  val center = FanZoomPoint(anchor.x, anchor.y + radius)
  val pointerStart = pointAt(initialRatio)
  val startDegrees = angleAt(1f) * 180f / Math.PI.toFloat()
  val sweepDegrees = sweepTo(maximum)

  fun sweepTo(ratio: Float): Float = (angleAt(ratio) - angleAt(1f)) * 180f / Math.PI.toFloat()

  fun pointAt(ratio: Float, offset: Float = 0f): FanZoomPoint {
    val angle = angleAt(ratio)
    return FanZoomPoint(center.x + (radius + offset) * cos(angle), center.y + (radius + offset) * sin(angle))
  }

  fun ratioAt(point: FanZoomPoint): Float? {
    val x = point.x - center.x
    val y = point.y - center.y
    val finite = x.isFinite() && y.isFinite()
    if (!finite || y >= 0f || hypot(x, y) < radius * 0.4f) return null
    val relative = ((atan2(y, x) - MIDDLE) / HALF_SWEEP).coerceIn(-1f, 1f)
    val fraction = if (relative < 0f) {
      initialFraction * (1f + relative)
    } else {
      initialFraction + (1f - initialFraction) * relative
    }
    return maximum.pow(fraction).coerceIn(1f, maximum)
  }

  private fun angleAt(ratio: Float): Float {
    val fraction = ln(ratio.coerceIn(1f, maximum)) / ln(maximum)
    val relative = when {
      fraction < initialFraction -> (fraction - initialFraction) / initialFraction
      fraction > initialFraction -> (fraction - initialFraction) / (1f - initialFraction)
      else -> 0f
    }
    return MIDDLE + relative * HALF_SWEEP
  }

  companion object {
    private val MIDDLE = (-Math.PI / 2).toFloat()
    private val HALF_SWEEP = (Math.PI / 4).toFloat()
  }
}