package io.github.g00fy2.quickie

import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BarcodeScanScheduleTest {

  @Test
  fun `default primary passes include QR and all common retail formats`() {
    val all = BarcodeScanSchedule(intArrayOf(Barcode.FORMAT_ALL_FORMATS))
    val qr = BarcodeScanSchedule(intArrayOf(Barcode.FORMAT_QR_CODE))
    val common = setOf(
      Barcode.FORMAT_QR_CODE,
      Barcode.FORMAT_EAN_13,
      Barcode.FORMAT_EAN_8,
      Barcode.FORMAT_UPC_A,
      Barcode.FORMAT_UPC_E,
      Barcode.FORMAT_CODE_128
    )
    assertTrue(all.primaryFormats.toSet().containsAll(common))
    assertTrue(all.useOtherFormats())
    assertTrue(List(16) { all.useOtherFormats() }.count { it } >= 2)
    assertTrue(List(12) { qr.useOtherFormats() }.none { it })
  }

  @Test
  fun `an explicitly selected barcode format is checked on every frame`() {
    val selected = BarcodeScanSchedule(intArrayOf(Barcode.FORMAT_EAN_13))
    assertArrayEquals(intArrayOf(Barcode.FORMAT_EAN_13), selected.primaryFormats)
    assertFalse(selected.useOtherFormats())
  }
}