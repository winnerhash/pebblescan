package io.github.g00fy2.quickie

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ScanSelectionTrackerTest {

  @Test
  fun `multiple decoded codes require a click and retain separate results`() {
    val tracker = ScanSelectionTracker()
    val codes = listOf(code("left", 0.25f), code("right", 0.75f))
    assertTrue(tracker.observe(codes, 1, 0).choices.isEmpty())
    val ready = tracker.observe(codes.reversed(), 2, 100)
    assertNull(ready.automatic)
    assertEquals(setOf("left", "right"), ready.choices.map { it.code.text }.toSet())
    assertTrue(tracker.requiresSelection)
    assertNull(tracker.observe(codes, 3, 1000).automatic)
  }

  @Test
  fun `a second pending region prevents the first decoded code from opening automatically`() {
    val tracker = ScanSelectionTracker()
    tracker.observe(listOf(code("first", 0.25f)), 1, 0)
    assertNull(tracker.observe(listOf(code("first", 0.25f)), 2, 100).automatic)
    val mixed = listOf(code("first", 0.25f), code(null, 0.75f))
    assertNull(tracker.observe(mixed, 3, 200).automatic)
    assertNull(tracker.observe(mixed, 4, 500).automatic)
    assertTrue(tracker.requiresSelection)
  }

  @Test
  fun `flickering detections never turn multi code selection into an automatic popup`() {
    val tracker = ScanSelectionTracker()
    tracker.observe(listOf(code("a", 0.25f), code("b", 0.75f)), 1, 0)
    tracker.observe(listOf(code("a", 0.25f)), 2, 100)
    tracker.observe(emptyList(), 3, 1000)
    tracker.observe(listOf(code("a", 0.25f)), 4, 2000)
    assertNull(tracker.observe(listOf(code("a", 0.25f)), 5, 2500).automatic)
    assertTrue(tracker.requiresSelection)
  }

  @Test
  fun `identical payloads at different positions keep separate stable marker identities`() {
    val tracker = ScanSelectionTracker()
    val codes = listOf(code("same", 0.25f), code("same", 0.75f))
    tracker.observe(codes, 1, 0)
    val ready = tracker.observe(codes, 2, 100)
    val moved = tracker.observe(listOf(code("same", 0.74f), code("same", 0.26f)), 3, 180)
    assertEquals(2, ready.choices.size)
    assertEquals(ready.choices.map { it.id }.toSet(), moved.choices.map { it.id }.toSet())
    assertEquals(ready.choices.first().id, moved.choices.minBy { it.code.position.x }.id)
  }

  @Test
  fun `only a confirmed single code settles into an automatic result`() {
    val tracker = ScanSelectionTracker()
    val codes = listOf(code("single", 0.5f))
    assertNull(tracker.observe(codes, 1, 0).automatic)
    assertNull(tracker.observe(codes, 2, 100).automatic)
    assertNull(tracker.observe(codes, 3, 300).automatic)
    assertEquals("single", tracker.observe(codes, 4, 360).automatic?.text)
    assertFalse(tracker.requiresSelection)
  }

  @Test
  fun `binary codes confirm while empty potential boxes never become clickable`() {
    val tracker = ScanSelectionTracker()
    val binary = ScanCode(CodePosition(0.25f, 0.5f, 0.1f, 0.1f), bytes = byteArrayOf(1, 2, 3))
    tracker.observe(listOf(binary, code(null, 0.75f)), 1, 0)
    val ready = tracker.observe(listOf(binary, code(null, 0.75f)), 2, 100)
    assertEquals(1, ready.choices.size)
    assertTrue(ready.choices.first().code.bytes.contentEquals(byteArrayOf(1, 2, 3)))
    assertNull(ready.automatic)
  }

  @Test
  fun `markers expire when a code leaves the camera view`() {
    val tracker = ScanSelectionTracker()
    val codes = listOf(code("a", 0.25f), code("b", 0.75f))
    tracker.observe(codes, 1, 0)
    assertEquals(2, tracker.observe(codes, 2, 100).choices.size)
    assertTrue(tracker.observe(emptyList(), 3, 301).choices.isEmpty())
  }

  @Test
  fun `a new payload at the same location cannot inherit the previous confirmed dot`() {
    val tracker = ScanSelectionTracker()
    val codes = listOf(code("a", 0.25f), code("b", 0.75f))
    tracker.observe(codes, 1, 0)
    tracker.observe(codes, 2, 100)
    val changed = tracker.observe(listOf(code("new", 0.25f), code("b", 0.75f)), 3, 150)
    assertEquals(listOf("b"), changed.choices.map { it.code.text })
    val settled = tracker.observe(listOf(code("new", 0.25f), code("b", 0.75f)), 4, 250)
    assertEquals(setOf("new", "b"), settled.choices.map { it.code.text }.toSet())
    assertNotNull(settled.choices.firstOrNull { it.code.text == "new" })
  }

  private fun code(value: String?, x: Float) = ScanCode(CodePosition(x, 0.5f, 0.1f, 0.1f), value)
}