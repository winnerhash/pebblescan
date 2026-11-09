package io.github.g00fy2.quickie

import com.google.mlkit.vision.barcode.common.Barcode

/** Check QR and common product formats on every primary pass; periodically include rarer formats. */
internal class BarcodeScanSchedule(formats: IntArray) {

  val checksOtherFormats = formats.contains(Barcode.FORMAT_ALL_FORMATS)
  val primaryFormats = if (checksOtherFormats) {
    intArrayOf(
      Barcode.FORMAT_QR_CODE,
      Barcode.FORMAT_EAN_13,
      Barcode.FORMAT_EAN_8,
      Barcode.FORMAT_UPC_A,
      Barcode.FORMAT_UPC_E,
      Barcode.FORMAT_CODE_128
    )
  } else {
    formats
  }
  private var pass = 0

  fun useOtherFormats(): Boolean {
    val first = pass++ == 0
    return checksOtherFormats && (first || pass % 8 == 0)
  }
}