package io.github.g00fy2.quickie

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ZoomPolicyTest {

  @Test
  fun `auto zoom never exceeds either device capability or the quality cap`() {
    assertEquals(2f, ZoomPolicy.suggestedRatio(8f, 1f, 2f))
    assertEquals(6f, ZoomPolicy.suggestedRatio(20f, 1f, 30f))
  }

  @Test
  fun `invalid ratios and zoom hunting do not trigger camera requests`() {
    assertNull(ZoomPolicy.suggestedRatio(Float.NaN, 1f, 6f))
    assertNull(ZoomPolicy.suggestedRatio(Float.POSITIVE_INFINITY, 1f, 6f))
    assertNull(ZoomPolicy.suggestedRatio(2f, 2f, 6f))
    assertNull(ZoomPolicy.suggestedRatio(2.02f, 2f, 6f))
    assertNull(ZoomPolicy.suggestedRatio(1f, 3f, 6f))
    assertNull(ZoomPolicy.suggestedRatio(3f, 1f, 1f))
  }
}