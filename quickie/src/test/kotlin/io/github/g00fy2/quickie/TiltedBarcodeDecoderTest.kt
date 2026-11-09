package io.github.g00fy2.quickie

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.PerspectiveTransform
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.ByteBuffer
import java.util.Random
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

internal class TiltedBarcodeDecoderTest {

  private val formats = intArrayOf(Barcode.FORMAT_ALL_FORMATS)
  private val text = "5901234123457"

  @ParameterizedTest
  @ValueSource(ints = [25, 40, 65, 115, 150])
  fun `angle sweep recovers a thin barcode that axis aligned rows miss`(angle: Int) {
    val radians = Math.toRadians(angle.toDouble())
    val corners = listOf(-260f to -40f, 260f to -40f, 260f to 40f, -260f to 40f).map { (x, y) ->
      ImagePoint(
        450f + (x * cos(radians) - y * sin(radians)).toFloat(),
        350f + (x * sin(radians) + y * cos(radians)).toFloat()
      )
    }
    val source = render(corners)
    assertNull(LinearBarcodeDecoder(formats).decode(source, thorough = true), "old decoder at $angle degrees")
    val decoder = TiltedBarcodeDecoder(formats)
    val results = List(5) { decoder.decode(source, emptyList()) }
    assertTrue(results.any { it == text }, "no result at $angle degrees: $results")
    assertTrue(results.all { it == null || it == text })
    assertEquals(text, decoder.decode(source, emptyList()), "retain the useful direction for frame confirmation")
    val located = requireNotNull(decoder.decodeLocated(source, emptyList()))
    assertEquals(450f, (located.start.x + located.end.x) / 2f, 60f, "marker x in the source image")
    assertEquals(350f, (located.start.y + located.end.y) / 2f, 60f, "marker y in the source image")
  }

  @Test
  fun `four corner rectification handles projective width changes and diagonal edges`() {
    val corners = perspectiveCorners()
    val source = render(corners)
    val region = requireNotNull(BarcodeRegion.create(corners, source.width, source.height))
    val rectified = WarpedLuminanceSource.rectified(source, region)
    assertEquals(text, LinearBarcodeDecoder(formats).decode(rectified))
  }

  @ParameterizedTest
  @ValueSource(strings = ["EAN_8", "UPC_A", "CODE_128"])
  fun `rectification preserves other supported product formats`(formatName: String) {
    val format = BarcodeFormat.valueOf(formatName)
    val payload = when (format) {
      BarcodeFormat.EAN_8 -> "96385074"
      BarcodeFormat.UPC_A -> "012345678905"
      else -> "123456789012"
    }
    val source = render(perspectiveCorners(), format, payload)
    val region = requireNotNull(BarcodeRegion.create(perspectiveCorners(), source.width, source.height))
    assertEquals(payload, TiltedBarcodeDecoder(formats).decode(source, listOf(region)))
  }

  @Test
  fun `rectification works with padded interleaved camera buffers in every orientation`() {
    val rendered = render(perspectiveCorners())
    val stride = rendered.width * 2 + 19
    val buffer = ByteBuffer.allocateDirect(7 + stride * rendered.height)
    for (y in 0 until rendered.height) {
      for (x in 0 until rendered.width) buffer.put(7 + y * stride + x * 2, rendered.pixel(x, y))
    }
    buffer.position(7)
    var source = CameraLuminanceSource(buffer, rendered.width, rendered.height, stride, 2)
    var corners = perspectiveCorners()
    repeat(4) {
      val region = requireNotNull(BarcodeRegion.create(corners, source.width, source.height))
      assertEquals(text, TiltedBarcodeDecoder(formats).decode(source, listOf(region)), "orientation $it")
      corners = corners.map { point -> ImagePoint(point.y, source.width - 1f - point.x) }
      source = source.rotateCounterClockwise()
    }
    assertEquals(7, buffer.position())
  }

  @Test
  fun `blank and deterministic noise frames do not yield product digits`() {
    val decoder = TiltedBarcodeDecoder(formats)
    val noise = ByteArray(320 * 240).also { Random(7).nextBytes(it) }
    val blank = ByteArray(320 * 240) { 180.toByte() }
    for (pixels in listOf(blank, noise)) {
      val source = CameraLuminanceSource(ByteBuffer.wrap(pixels), 320, 240, 320, 1)
      val region = requireNotNull(
        BarcodeRegion.create(
          listOf(ImagePoint(20f, 30f), ImagePoint(290f, 50f), ImagePoint(280f, 180f), ImagePoint(40f, 210f)),
          320,
          240
        )
      )
      repeat(10) { assertNull(decoder.decode(source, listOf(region))) }
    }
  }

  @Test
  fun `invalid or folded candidate quadrilaterals are rejected`() {
    val a = ImagePoint(10f, 10f)
    val b = ImagePoint(180f, 10f)
    val c = ImagePoint(180f, 80f)
    val d = ImagePoint(10f, 80f)
    assertNull(BarcodeRegion.create(listOf(a, b, c), 200, 100))
    assertNull(BarcodeRegion.create(listOf(a, c, b, d), 200, 100))
    assertNull(BarcodeRegion.create(listOf(a, a, a, a), 200, 100))
    assertNull(BarcodeRegion.create(listOf(a, b, c, ImagePoint(Float.NaN, 80f)), 200, 100))
    assertNull(BarcodeRegion.create(listOf(a, b, c, ImagePoint(-1f, 80f)), 200, 100))
  }

  private fun perspectiveCorners(): List<ImagePoint> {
    val angle = Math.toRadians(20.0)
    return listOf(-350f to -230f, 350f to -30f, 200f to 60f, -230f to 100f).map { (x, y) ->
      ImagePoint(
        450f + (x * cos(angle) - y * sin(angle)).toFloat(),
        350f + (x * sin(angle) + y * cos(angle)).toFloat()
      )
    }
  }

  private fun render(
    corners: List<ImagePoint>,
    format: BarcodeFormat = BarcodeFormat.EAN_13,
    payload: String = text
  ): CameraLuminanceSource {
    val barcode = MultiFormatWriter().encode(payload, format, 520, 80)
    val transform = PerspectiveTransform.quadrilateralToQuadrilateral(
      corners[0].x,
      corners[0].y,
      corners[1].x,
      corners[1].y,
      corners[2].x,
      corners[2].y,
      corners[3].x,
      corners[3].y,
      0f,
      0f,
      barcode.width - 1f,
      0f,
      barcode.width - 1f,
      barcode.height - 1f,
      0f,
      barcode.height - 1f
    )
    val coordinates = FloatArray(900 * 2)
    val pixels = ByteArray(900 * 700) { 255.toByte() }
    for (y in 0 until 700) {
      for (x in 0 until 900) {
        coordinates[x * 2] = x.toFloat()
        coordinates[x * 2 + 1] = y.toFloat()
      }
      transform.transformPoints(coordinates)
      for (x in 0 until 900) {
        val bx = coordinates[x * 2].roundToInt()
        val by = coordinates[x * 2 + 1].roundToInt()
        if (bx in 0 until barcode.width && by in 0 until barcode.height && barcode[bx, by]) pixels[y * 900 + x] = 0
      }
    }
    return CameraLuminanceSource(ByteBuffer.wrap(pixels), 900, 700, 900, 1)
  }
}