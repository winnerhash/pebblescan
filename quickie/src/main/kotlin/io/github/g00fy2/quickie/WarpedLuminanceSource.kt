package io.github.g00fy2.quickie

import com.google.zxing.LuminanceSource
import com.google.zxing.common.PerspectiveTransform
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Transform only requested scan lines; never allocate another full-resolution camera image. */
internal class WarpedLuminanceSource private constructor(
  private val source: CameraLuminanceSource,
  width: Int,
  height: Int,
  private val transform: PerspectiveTransform
) : LuminanceSource(width, height) {

  private val coordinates = FloatArray(width * 2)

  fun mapPoint(point: ImagePoint): ImagePoint {
    val coordinates = floatArrayOf(point.x, point.y)
    transform.transformPoints(coordinates)
    return ImagePoint(coordinates[0], coordinates[1])
  }

  override fun getRow(y: Int, row: ByteArray?): ByteArray {
    require(y in 0 until height)
    val output = if (row != null && row.size >= width) row else ByteArray(width)
    for (x in 0 until width) {
      coordinates[x * 2] = x.toFloat()
      coordinates[x * 2 + 1] = y.toFloat()
    }
    transform.transformPoints(coordinates)
    for (x in 0 until width) {
      val px = coordinates[x * 2]
      val py = coordinates[x * 2 + 1]
      output[x] = if (px.isFinite() && py.isFinite()) source.pixel(px.roundToInt(), py.roundToInt()) else 255.toByte()
    }
    return output
  }

  override fun getMatrix(): ByteArray = ByteArray(width * height).also { output ->
    var row: ByteArray? = null
    for (y in 0 until height) {
      row = getRow(y, row)
      requireNotNull(row).copyInto(output, y * width, 0, width)
    }
  }

  companion object {
    fun rectified(source: CameraLuminanceSource, region: BarcodeRegion): WarpedLuminanceSource {
      val width = region.width.roundToInt().coerceIn(32, 1600)
      val height = region.height.roundToInt().coerceIn(16, 600)
      return fromCorners(source, width, height, region.corners, (width * 0.12f).roundToInt())
    }

    fun rotated(source: CameraLuminanceSource, degrees: Int): WarpedLuminanceSource {
      val angle = Math.toRadians(degrees.toDouble())
      val cosine = cos(angle).toFloat()
      val sine = sin(angle).toFloat()
      val cx = (source.width - 1) / 2f
      val cy = (source.height - 1) / 2f
      val corners = listOf(-cx to -cy, cx to -cy, cx to cy, -cx to cy).map { (x, y) ->
        ImagePoint(cx + cosine * x - sine * y, cy + sine * x + cosine * y)
      }
      return fromCorners(source, source.width, source.height, corners)
    }

    private fun fromCorners(
      source: CameraLuminanceSource,
      width: Int,
      height: Int,
      corners: List<ImagePoint>,
      padding: Int = 0
    ): WarpedLuminanceSource {
      val transform = PerspectiveTransform.quadrilateralToQuadrilateral(
        padding.toFloat(),
        0f,
        padding + width - 1f,
        0f,
        padding + width - 1f,
        height - 1f,
        padding.toFloat(),
        height - 1f,
        corners[0].x,
        corners[0].y,
        corners[1].x,
        corners[1].y,
        corners[2].x,
        corners[2].y,
        corners[3].x,
        corners[3].y
      )
      return WarpedLuminanceSource(source, width + padding * 2, height, transform)
    }
  }
}