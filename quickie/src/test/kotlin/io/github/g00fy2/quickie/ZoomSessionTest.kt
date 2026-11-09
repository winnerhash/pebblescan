package io.github.g00fy2.quickie

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ZoomSessionTest {

  @Test
  fun `manual zoom survives completed requests and ignores automatic zoom out`() {
    val session = ZoomSession(6f)
    assertEquals(3f, session.manualRatio(3f))
    assertNull(session.complete())
    assertTrue(session.isManual)
    assertNull(session.automaticRatio(1f))
    assertFalse(session.isApplying)
  }

  @Test
  fun `only the latest drag position follows an in flight automatic request`() {
    val session = ZoomSession(6f)
    assertEquals(1.5f, session.automaticRatio(1.5f))
    assertNull(session.manualRatio(2f))
    assertNull(session.manualRatio(4f))
    assertNull(session.manualRatio(3f))
    assertEquals(3f, session.complete())
    assertNull(session.complete())
    assertFalse(session.isApplying)
    assertTrue(session.isManual)
  }

  @Test
  fun `pinch factors accumulate against requested zoom instead of stale camera state`() {
    val session = ZoomSession(6f)
    assertEquals(2f, session.manualScale(2f, 1f))
    assertNull(session.manualScale(1.5f, 1f))
    assertNull(session.manualScale(0.5f, 1f))
    assertEquals(1.5f, session.complete())
  }

  @Test
  fun `manual zoom respects hardware limits and allows zooming back to one`() {
    val session = ZoomSession(2.5f)
    assertEquals(2.5f, session.manualRatio(10f))
    session.complete()
    assertEquals(1f, session.manualRatio(0.2f))
  }

  @Test
  fun `invalid gestures leave automatic mode unchanged`() {
    val session = ZoomSession(6f)
    assertNull(session.manualRatio(Float.NaN))
    assertNull(session.manualRatio(Float.POSITIVE_INFINITY))
    assertNull(session.manualScale(0f, 1f))
    assertNull(session.manualScale(-1f, 1f))
    assertFalse(session.isManual)
    assertFalse(session.isApplying)
  }

  @Test
  fun `resume auto replaces queued manual zoom and starts at one`() {
    val session = ZoomSession(6f)
    session.manualRatio(2f)
    session.manualRatio(5f)
    assertNull(session.resumeAutomatic())
    assertFalse(session.isManual)
    assertEquals(1f, session.complete())
    session.complete()
    assertEquals(1.5f, session.automaticRatio(1.5f))
  }
}