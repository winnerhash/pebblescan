package io.github.g00fy2.quickie

import com.google.mlkit.vision.barcode.common.Barcode
import kotlin.math.abs
import kotlin.math.max

internal data class CodePosition(val x: Float, val y: Float, val width: Float, val height: Float) {
  fun contains(other: CodePosition): Boolean =
    abs(x - other.x) <= max(width / 2f, 0.025f) && abs(y - other.y) <= max(height / 2f, 0.025f)
}

internal class ScanCode(
  val position: CodePosition,
  val text: String? = null,
  val bytes: ByteArray? = null,
  val barcode: Barcode? = null
) {
  val hasContent = BarcodeResultPolicy.hasContent(text, bytes)
  val identity: Any = text?.takeIf { it.isNotBlank() } ?: bytes?.toList().orEmpty()

  fun at(position: CodePosition) = ScanCode(position, text, bytes, barcode)
}

/** Prefer model metadata when the line decoder reads the same physical code, including EAN/UPC aliases. */
internal object ScanCodeMerger {
  fun merge(model: List<ScanCode>, supplement: List<ScanCode>): List<ScanCode> {
    val result = mutableListOf<ScanCode>()
    for (code in model + supplement) {
      val existing = result.indexOfFirst { it.position.contains(code.position) }
      if (existing < 0) {
        result += code
      } else if (!result[existing].hasContent) {
        result[existing] = code.at(result[existing].position)
      }
    }
    return result
  }
}