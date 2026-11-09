package io.github.g00fy2.quickie

import com.google.mlkit.vision.barcode.common.Barcode
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.Result
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.oned.MultiFormatOneDReader

/** A second offline decoder for retail codes, without copying full camera frames or bypassing checksums. */
internal class LinearBarcodeDecoder(formats: IntArray) {

  private val selected = if (formats.contains(Barcode.FORMAT_ALL_FORMATS)) {
    supported.values.toList()
  } else {
    formats.map { supported[it] }.filterNotNull()
  }
  val enabled = selected.isNotEmpty()
  private val hints: Map<DecodeHintType, Any> = mapOf(DecodeHintType.POSSIBLE_FORMATS to selected)
  private val thoroughHints = hints + (DecodeHintType.TRY_HARDER to true)
  private val reader = MultiFormatOneDReader(hints)

  fun decode(source: LuminanceSource, thorough: Boolean = false): String? = decodeLocated(source, thorough)?.text

  fun decodeLocated(source: LuminanceSource, thorough: Boolean = false): LocatedBarcode? {
    if (!enabled) return null
    val fast = read(source, hints) ?: if (source.isRotateSupported) {
      read(source.rotateCounterClockwise(), hints)?.mapped { ImagePoint(source.width - 1f - it.y, it.x) }
    } else {
      null
    }
    return fast ?: if (thorough) read(source, thoroughHints) else null
  }

  private fun read(source: LuminanceSource, hints: Map<DecodeHintType, Any>): LocatedBarcode? = try {
    located(reader.decode(BinaryBitmap(GlobalHistogramBinarizer(source)), hints))
  } catch (ignored: ReaderException) {
    null
  } finally {
    reader.reset()
  }

  private fun located(result: Result): LocatedBarcode? {
    val points = result.resultPoints.orEmpty()
    if (result.text.isNullOrBlank() || points.size < 2) return null
    return LocatedBarcode(
      result.text,
      ImagePoint(points.first().x, points.first().y),
      ImagePoint(points.last().x, points.last().y)
    )
  }

  companion object {
    private val supported = mapOf(
      Barcode.FORMAT_EAN_13 to BarcodeFormat.EAN_13,
      Barcode.FORMAT_EAN_8 to BarcodeFormat.EAN_8,
      Barcode.FORMAT_UPC_A to BarcodeFormat.UPC_A,
      Barcode.FORMAT_UPC_E to BarcodeFormat.UPC_E,
      Barcode.FORMAT_CODE_128 to BarcodeFormat.CODE_128
    )

    fun supports(formats: IntArray): Boolean = formats.any { it == Barcode.FORMAT_ALL_FORMATS || it in supported }
  }
}