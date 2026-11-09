package io.github.g00fy2.quickie

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class AutoZoomTrackerTest {

  @Test
  fun `empty scenes never trigger zoom in`() {
    val tracker = AutoZoomTracker()
    listOf(0L, 1000L, 5000L).forEach { assertNull(tracker.observe(null, it, 1f, 6f)) }
  }

  @Test
  fun `a stable small code zooms promptly but moving candidates do not`() {
    val tracker = AutoZoomTracker()
    val target = ZoomTarget(0.5f, 0.5f, 0.1f)
    assertNull(tracker.observe(target, 0, 1f, 6f))
    assertNull(tracker.observe(target, 80, 1f, 6f))
    assertEquals(1.5f, tracker.observe(target, 140, 1f, 6f))
    tracker.zoomRequested(140)
    assertNull(tracker.observe(target, 300, 1.5f, 6f))
    assertNull(tracker.observe(target.copy(x = 0.7f), 620, 1.5f, 6f))
    assertEquals(2.25f, tracker.observe(target.copy(x = 0.7f), 760, 1.5f, 6f))
  }

  @Test
  fun `losing the code restores a usable field of view after the grace period`() {
    val tracker = AutoZoomTracker()
    tracker.zoomRequested(1000)
    assertNull(tracker.observe(null, 2000, 3f, 6f))
    assertEquals(2f, tracker.observe(null, 2700, 3f, 6f))
    assertNull(tracker.observe(null, 2800, 2f, 6f))
    assertEquals(1f, tracker.observe(null, 3300, 1.4f, 6f))
  }

  @Test
  fun `candidate zoom respects device limits and leaves large codes alone`() {
    assertEquals(2f, ZoomPolicy.forSmallCode(0.1f, 1.5f, 2f))
    assertNull(ZoomPolicy.forSmallCode(0.5f, 1f, 6f))
    assertNull(ZoomPolicy.forSmallCode(0f, 1f, 6f))
    assertNull(ZoomPolicy.forSmallCode(Float.NaN, 1f, 6f))
  }

  @Test
  fun `interleaved barcode frames retain a recently seen target`() {
    val tracker = AutoZoomTracker()
    val target = ZoomTarget(0.5f, 0.5f, 0.1f)
    assertNull(tracker.observe(target, 1000, 1f, 6f))
    assertNull(tracker.observe(null, 1100, 1f, 6f))
    assertEquals(1.5f, tracker.observe(target, 1200, 1f, 6f))
  }

  @Test
  fun `off-center codes are not enlarged beyond the available edge space`() {
    val maximum = ZoomPolicy.visibleMaximum(0.8f, 0.5f, 0.15f, 0.04f, 1f)
    val tracker = AutoZoomTracker()
    val target = ZoomTarget(0.8f, 0.5f, 0.15f, maximum)
    assertNull(tracker.observe(target, 0, 1f, 6f))
    assertEquals(1.2f, requireNotNull(tracker.observe(target, 200, 1f, 6f)), 0.0001f)
  }
}