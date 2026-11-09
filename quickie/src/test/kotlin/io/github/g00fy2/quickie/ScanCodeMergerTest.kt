package io.github.g00fy2.quickie

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class ScanCodeMergerTest {

  @Test
  fun `EAN and UPC aliases for one physical code prefer the model payload`() {
    val position = CodePosition(0.5f, 0.5f, 0.3f, 0.2f)
    val model = ScanCode(position, "0012345678905")
    val line = ScanCode(CodePosition(0.51f, 0.52f, 0.2f, 0f), "012345678905")
    val merged = ScanCodeMerger.merge(listOf(model), listOf(line))
    assertEquals(1, merged.size)
    assertEquals(model.text, merged.first().text)
  }

  @Test
  fun `a supplement fills a pending region while preserving its marker center`() {
    val position = CodePosition(0.5f, 0.5f, 0.3f, 0.2f)
    val pending = ScanCode(position)
    val line = ScanCode(CodePosition(0.55f, 0.55f, 0.2f, 0f), "5901234123457")
    val merged = ScanCodeMerger.merge(listOf(pending), listOf(line))
    assertEquals(1, merged.size)
    assertEquals(line.text, merged.first().text)
    assertEquals(position, merged.first().position)
  }

  @Test
  fun `identical content at different locations is not collapsed into one choice`() {
    val a = ScanCode(CodePosition(0.25f, 0.5f, 0.1f, 0.1f), "same")
    val b = ScanCode(CodePosition(0.75f, 0.5f, 0.1f, 0.1f), "same")
    assertEquals(2, ScanCodeMerger.merge(listOf(a, b), emptyList()).size)
    assertEquals(1, ScanCodeMerger.merge(listOf(a, a), emptyList()).size)
  }
}