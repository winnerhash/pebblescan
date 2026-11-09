package io.github.g00fy2.quickie

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ScanResultConfirmationTest {

  @Test
  fun `empty frames never confirm even when repeated`() {
    val confirmation = ScanResultConfirmation()
    repeat(20) {
      assertFalse(confirmation.accept("", byteArrayOf(), it.toLong(), it * 100L))
    }
  }

  @Test
  fun `two decoders observing the same frame cannot confirm each other`() {
    val confirmation = ScanResultConfirmation()
    assertFalse(confirmation.accept("5901234123457", null, 1, 1000))
    assertFalse(confirmation.accept("5901234123457", "5901234123457".toByteArray(), 1, 1100))
    assertTrue(confirmation.accept("5901234123457", null, 2, 1150))
  }

  @Test
  fun `different and stale readings do not accumulate into success`() {
    val confirmation = ScanResultConfirmation()
    assertFalse(confirmation.accept("noise A", null, 1, 1000))
    assertFalse(confirmation.accept("noise B", null, 2, 1100))
    assertFalse(confirmation.accept("noise A", null, 3, 1200))
    assertFalse(confirmation.accept("noise A", null, 4, 2000))
    assertTrue(confirmation.accept("noise A", null, 5, 2100))
  }

  @Test
  fun `steady readings need time to settle while binary codes compare their contents`() {
    val confirmation = ScanResultConfirmation()
    assertFalse(confirmation.accept("hello", null, 1, 0))
    assertFalse(confirmation.accept("hello", null, 2, 20))
    assertTrue(confirmation.accept("hello", null, 3, 100))
    assertFalse(confirmation.accept(null, byteArrayOf(0, 1), 4, 200))
    assertTrue(confirmation.accept("", byteArrayOf(0, 1), 5, 300))
  }
}