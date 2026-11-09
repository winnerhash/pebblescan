package io.github.g00fy2.quickie

import com.google.zxing.LuminanceSource
import java.nio.ByteBuffer

/** Read only the rows requested by the decoder, including padded or interleaved camera planes. */
internal class CameraLuminanceSource(
  data: ByteBuffer,
  private val sourceWidth: Int,
  private val sourceHeight: Int,
  private val rowStride: Int,
  private val pixelStride: Int,
  private val turns: Int = 0
) : LuminanceSource(
  if (turns % 2 == 0) sourceWidth else sourceHeight,
  if (turns % 2 == 0) sourceHeight else sourceWidth
) {

  private val pixels = data.slice().asReadOnlyBuffer()

  init {
    require(sourceWidth > 0 && sourceHeight > 0)
    require(rowStride >= (sourceWidth - 1) * pixelStride + 1 && pixelStride > 0)
    require((sourceHeight - 1L) * rowStride + (sourceWidth - 1L) * pixelStride < pixels.remaining())
  }

  override fun getRow(y: Int, row: ByteArray?): ByteArray {
    require(y in 0 until height)
    val output = if (row != null && row.size >= width) row else ByteArray(width)
    for (x in 0 until width) {
      output[x] = pixel(x, y)
    }
    return output
  }

  fun pixel(x: Int, y: Int): Byte {
    if (x !in 0 until width || y !in 0 until height) return 255.toByte()
    val offset = when (turns % 4) {
      1 -> x * rowStride + (sourceWidth - 1 - y) * pixelStride
      2 -> (sourceHeight - 1 - y) * rowStride + (sourceWidth - 1 - x) * pixelStride
      3 -> (sourceHeight - 1 - x) * rowStride + y * pixelStride
      else -> y * rowStride + x * pixelStride
    }
    return pixels.get(offset)
  }

  override fun getMatrix(): ByteArray = ByteArray(width * height).also { matrix ->
    var row: ByteArray? = null
    for (y in 0 until height) {
      row = getRow(y, row)
      requireNotNull(row).copyInto(matrix, y * width, 0, width)
    }
  }

  override fun isRotateSupported(): Boolean = true

  override fun rotateCounterClockwise(): CameraLuminanceSource =
    CameraLuminanceSource(pixels, sourceWidth, sourceHeight, rowStride, pixelStride, (turns + 1) % 4)
}