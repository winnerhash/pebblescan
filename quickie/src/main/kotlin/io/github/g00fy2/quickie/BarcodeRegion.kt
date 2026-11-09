package io.github.g00fy2.quickie

import kotlin.math.abs
import kotlin.math.hypot

internal data class ImagePoint(val x: Float, val y: Float) {
  fun distance(other: ImagePoint): Float = hypot(x - other.x, y - other.y)
}

/** Clockwise corners in the oriented original image, rather than the smaller model input. */
internal class BarcodeRegion private constructor(val corners: List<ImagePoint>) {

  val width = (corners[0].distance(corners[1]) + corners[3].distance(corners[2])) / 2f
  val height = (corners[0].distance(corners[3]) + corners[1].distance(corners[2])) / 2f

  fun quarterTurn() = BarcodeRegion(listOf(corners[1], corners[2], corners[3], corners[0]))

  companion object {
    fun create(points: List<ImagePoint>, width: Int, height: Int): BarcodeRegion? {
      if (points.size != 4) return null
      val inside = points.all {
        val finite = it.x.isFinite() && it.y.isFinite()
        finite && it.x in 0f..width.toFloat() && it.y in 0f..height.toFloat()
      }
      val turns = points.indices.map { index ->
        val a = points[index]
        val b = points[(index + 1) % 4]
        val c = points[(index + 2) % 4]
        (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
      }
      val convex = turns.all { it > 16f } || turns.all { it < -16f }
      val area = abs(
        points.indices.sumOf { index ->
          val a = points[index]
          val b = points[(index + 1) % 4]
          (a.x * b.y - b.x * a.y).toDouble()
        }
      ) / 2
      val valid = inside && convex && area >= 256
      return if (valid) {
        val horizontal = points[0].distance(points[1]) >= points[1].distance(points[2])
        BarcodeRegion(if (horizontal) points else listOf(points[1], points[2], points[3], points[0]))
      } else {
        null
      }
    }
  }
}