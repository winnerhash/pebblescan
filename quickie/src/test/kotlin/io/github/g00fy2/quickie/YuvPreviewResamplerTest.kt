package io.github.g00fy2.quickie

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer

internal class YuvPreviewResamplerTest {

  @Test
  fun `padded planes retain color order and buffer offsets when making NV21 previews`() {
    val y = plane(8, 4, 0)
    val u = plane(4, 2, 100)
    val v = plane(4, 2, 150)
    val preview = YuvPreviewResampler(4).sample(8, 4, listOf(y.first, u.first, v.first))
    assertEquals(4, preview.width)
    assertEquals(2, preview.height)
    val expected = intArrayOf(11, 13, 15, 17, 31, 33, 35, 37, 161, 111, 163, 113)
    assertArrayEquals(expected.map { it.toByte() }.toByteArray(), preview.pixels)
    listOf(y, u, v).forEach { assertEquals(3, it.second.position()) }
  }

  @Test
  fun `4K camera frames yield a 1080p model input in either orientation`() {
    listOf(3840 to 2160, 2160 to 3840).forEach { (width, height) ->
      val y = YuvPlane(ByteBuffer.allocate(width * height), width, 1)
      val chroma = YuvPlane(ByteBuffer.allocate(width * height / 4), width / 2, 1)
      val preview = YuvPreviewResampler().sample(width, height, listOf(y, chroma, chroma))
      assertEquals(width / 2, preview.width)
      assertEquals(height / 2, preview.height)
      assertEquals(1920 * 1080 * 3 / 2, preview.pixels.size)
    }
  }

  @Test
  fun `preview storage is reused after processing each frame and handles nonstandard sizes`() {
    val resampler = YuvPreviewResampler(6)
    val y = YuvPlane(ByteBuffer.allocate(14 * 10), 14, 1)
    val chroma = YuvPlane(ByteBuffer.allocate(7 * 5), 7, 1)
    val first = resampler.sample(14, 10, listOf(y, chroma, chroma))
    val next = resampler.sample(14, 10, listOf(y, chroma, chroma))
    assertSame(first.pixels, next.pixels)
    assertTrue(next.width <= 6 && next.height <= 6)
    assertTrue(next.width % 2 == 0 && next.height % 2 == 0)
  }

  private fun plane(width: Int, height: Int, base: Int): Pair<YuvPlane, ByteBuffer> {
    val stride = width * 2 + 5
    val buffer = ByteBuffer.allocate(3 + stride * height)
    for (y in 0 until height) {
      for (x in 0 until width) buffer.put(3 + y * stride + x * 2, (base + y * 10 + x).toByte())
    }
    buffer.position(3)
    buffer.limit(3 + (height - 1) * stride + (width - 1) * 2 + 1)
    return YuvPlane(buffer, stride, 2) to buffer
  }
}