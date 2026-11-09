package io.github.g00fy2.quickie

import java.nio.ByteBuffer
import kotlin.math.max

internal class YuvPlane(buffer: ByteBuffer, val rowStride: Int, val pixelStride: Int) {
  val pixels: ByteBuffer = buffer.slice().asReadOnlyBuffer()
}

internal data class Nv21Preview(val pixels: ByteArray, val width: Int, val height: Int)

/** Keep model input bounded while the line decoder continues to read the original high-resolution Y plane. */
internal class YuvPreviewResampler(private val maximumEdge: Int = 1920) {

  private var output = byteArrayOf()

  fun sample(width: Int, height: Int, planes: List<YuvPlane>): Nv21Preview {
    require(width >= 2 && height >= 2 && planes.size == 3)
    require(maximumEdge >= 2)
    val step = (max(width, height) + maximumEdge - 1) / maximumEdge
    val targetWidth = (width / step / 2 * 2).coerceAtLeast(2)
    val targetHeight = (height / step / 2 * 2).coerceAtLeast(2)
    val lumaSize = targetWidth * targetHeight
    val size = lumaSize * 3 / 2
    if (output.size != size) output = ByteArray(size)
    copyPlane(planes[0], width, height, targetWidth, targetHeight, 0, 1)
    copyPlane(planes[2], width / 2, height / 2, targetWidth / 2, targetHeight / 2, lumaSize, 2)
    copyPlane(planes[1], width / 2, height / 2, targetWidth / 2, targetHeight / 2, lumaSize + 1, 2)
    return Nv21Preview(output, targetWidth, targetHeight)
  }

  @Suppress("LongParameterList") // Plane geometry and NV21 byte offsets must remain explicit.
  private fun copyPlane(
    plane: YuvPlane,
    width: Int,
    height: Int,
    targetWidth: Int,
    targetHeight: Int,
    offset: Int,
    spacing: Int
  ) {
    require(plane.rowStride > 0 && plane.pixelStride > 0)
    require((height - 1L) * plane.rowStride + (width - 1L) * plane.pixelStride < plane.pixels.remaining())
    for (y in 0 until targetHeight) {
      val sourceY = ((y + 0.5f) * height / targetHeight).toInt().coerceAtMost(height - 1)
      val row = sourceY * plane.rowStride
      for (x in 0 until targetWidth) {
        val sourceX = ((x + 0.5f) * width / targetWidth).toInt().coerceAtMost(width - 1)
        output[offset + (y * targetWidth + x) * spacing] = plane.pixels.get(row + sourceX * plane.pixelStride)
      }
    }
  }
}