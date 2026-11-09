package io.github.g00fy2.quickie

import com.google.mlkit.vision.barcode.common.Barcode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BarcodeResultPolicyTest {

  @Test
  fun `unknown candidates empty strings and zero length buffers are not decoded results`() {
    assertFalse(BarcodeResultPolicy.isDecoded(Barcode.FORMAT_UNKNOWN, "123", null))
    assertFalse(BarcodeResultPolicy.isDecoded(Barcode.FORMAT_QR_CODE, null, null))
    assertFalse(BarcodeResultPolicy.isDecoded(Barcode.FORMAT_QR_CODE, "", byteArrayOf()))
    assertFalse(BarcodeResultPolicy.isDecoded(Barcode.FORMAT_CODE_128, " \n\t", null))
  }

  @Test
  fun `real text product digits and nonempty binary payloads remain valid`() {
    assertTrue(BarcodeResultPolicy.isDecoded(Barcode.FORMAT_QR_CODE, "hello", null))
    assertTrue(BarcodeResultPolicy.isDecoded(Barcode.FORMAT_EAN_13, "5901234123457", null))
    assertTrue(BarcodeResultPolicy.isDecoded(Barcode.FORMAT_QR_CODE, "", byteArrayOf(0, 1)))
  }
}