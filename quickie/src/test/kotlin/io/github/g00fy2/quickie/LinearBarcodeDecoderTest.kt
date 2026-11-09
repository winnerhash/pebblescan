package io.github.g00fy2.quickie

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatWriter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer
import javax.imageio.ImageIO

internal class LinearBarcodeDecoderTest {

  private val decoder = LinearBarcodeDecoder(intArrayOf(Barcode.FORMAT_ALL_FORMATS))

  @ParameterizedTest
  @ValueSource(strings = ["ean13-1", "ean8-1", "upca-2", "upce-1", "code128-1"])
  fun `real upstream retail images decode to their known values`(fixture: String) {
    val (source, expected) = fixture(fixture)
    assertEquals(expected, decoder.decode(source, thorough = true), fixture)
    assertEquals(expected, decoder.decode(source.rotateCounterClockwise(), thorough = true), "$fixture rotated")
  }

  @Test
  fun `strongly blurred product photos never yield a different product number`() {
    val (source, expected) = fixture("upca-1")
    val actual = decoder.decode(source, thorough = true)
    assertTrue(actual == null || actual == expected)
  }

  @Test
  fun `dense scanning finds a barcode outside the fast central sample rows`() {
    val text = "5901234123457"
    val barcode = MultiFormatWriter().encode(text, BarcodeFormat.EAN_13, 420, 80)
    val pixels = ByteArray(600 * 800) { 255.toByte() }
    for (y in 0 until barcode.height) {
      for (x in 0 until barcode.width) {
        pixels[(y + 80) * 600 + x + 90] = if (barcode[x, y]) 0 else 255.toByte()
      }
    }
    val source = CameraLuminanceSource(ByteBuffer.wrap(pixels), 600, 800, 600, 1)
    assertNull(decoder.decode(source))
    assertEquals(text, decoder.decode(source, thorough = true))
  }

  private fun fixture(fixture: String): Pair<CameraLuminanceSource, String> {
    val image = requireNotNull(javaClass.getResourceAsStream("/retail/$fixture.png")).use { ImageIO.read(it) }
    val expected = requireNotNull(javaClass.getResourceAsStream("/retail/$fixture.txt"))
      .bufferedReader().use { it.readText().trim() }
    val pixels = ByteArray(image.width * image.height) { index ->
      val rgb = image.getRGB(index % image.width, index / image.width)
      val gray = (((rgb shr 16) and 255) + 2 * ((rgb shr 8) and 255) + (rgb and 255)) / 4
      gray.toByte()
    }
    val source = CameraLuminanceSource(ByteBuffer.wrap(pixels), image.width, image.height, image.width, 1)
    return source to expected
  }

  @Test
  fun `EAN and Code 128 survive padded camera planes brightness changes and every orientation`() {
    val cases = mapOf(
      BarcodeFormat.EAN_13 to "5901234123457",
      BarcodeFormat.EAN_8 to "96385074",
      BarcodeFormat.UPC_A to "012345678905",
      BarcodeFormat.CODE_128 to "123456789012"
    )
    cases.forEach { (format, text) ->
      val matrix = MultiFormatWriter().encode(text, format, 420, 160)
      val stride = matrix.width * 2 + 19
      val buffer = ByteBuffer.allocateDirect(7 + stride * matrix.height)
      for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
          val gray = if (matrix[x, y]) 35 + x * 15 / matrix.width else 170 + x * 60 / matrix.width
          buffer.put(7 + y * stride + x * 2, gray.toByte())
        }
      }
      buffer.position(7)
      var source: LuminanceSource = CameraLuminanceSource(buffer, matrix.width, matrix.height, stride, 2)
      repeat(4) {
        assertEquals(text, decoder.decode(source), "$format orientation $it")
        source = source.rotateCounterClockwise()
      }
      assertEquals(7, buffer.position())
    }
  }

  @Test
  fun `blank frames and disabled formats do not produce product numbers`() {
    val source = CameraLuminanceSource(ByteBuffer.wrap(ByteArray(320 * 240) { 180.toByte() }), 320, 240, 320, 1)
    assertNull(decoder.decode(source))
    val qrOnly = LinearBarcodeDecoder(intArrayOf(Barcode.FORMAT_QR_CODE))
    assertFalse(qrOnly.enabled)
    assertNull(qrOnly.decode(source))
  }

  @Test
  fun `the line decoder retains narrow bar details lost in the lower resolution model preview`() {
    val width = 3840
    val height = 2160
    val text = "5901234123457"
    val barcode = MultiFormatWriter().encode(text, BarcodeFormat.EAN_13, 160, 80)
    val pixels = ByteArray(width * height) { 255.toByte() }
    for (y in 0 until barcode.height) {
      for (x in 0 until barcode.width) {
        val offset = ((height - barcode.height) / 2 + y) * width + (width - barcode.width) / 2 + x
        pixels[offset] = if (barcode[x, y]) 0 else 255.toByte()
      }
    }
    val original = CameraLuminanceSource(ByteBuffer.wrap(pixels), width, height, width, 1)
    assertEquals(text, decoder.decode(original, thorough = true))
    val luma = YuvPlane(ByteBuffer.wrap(pixels), width, 1)
    val chroma = YuvPlane(ByteBuffer.allocate(width * height / 4), width / 2, 1)
    val preview = YuvPreviewResampler().sample(width, height, listOf(luma, chroma, chroma))
    val reduced = CameraLuminanceSource(
      ByteBuffer.wrap(preview.pixels),
      preview.width,
      preview.height,
      preview.width,
      1
    )
    assertNull(decoder.decode(reduced, thorough = true))
    assertTrue(LinearBarcodeDecoder.supports(intArrayOf(Barcode.FORMAT_EAN_13)))
    assertFalse(LinearBarcodeDecoder.supports(intArrayOf(Barcode.FORMAT_QR_CODE)))
  }
}