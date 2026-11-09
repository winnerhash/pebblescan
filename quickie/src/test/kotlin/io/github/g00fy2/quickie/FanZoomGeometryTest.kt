package io.github.g00fy2.quickie

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FanZoomGeometryTest {

  @Test
  fun `dial remains centred on the button without changing the starting zoom`() {
    listOf(1f, 1.5f, 3f, 6f).forEach { ratio ->
      val model = geometry(ratio)
      assertEquals(model.anchor.x, model.center.x, 0.01f)
      assertEquals(model.anchor.x, model.pointerStart.x, 0.01f)
      assertEquals(model.anchor.y, model.pointerStart.y, 0.01f)
      assertEquals(ratio, model.ratioAt(model.pointerStart) ?: 0f, 0.001f)
      assertTrue(model.pointAt(1f).x >= model.anchor.x - 72f)
      assertTrue(model.pointAt(model.maximum).x <= model.anchor.x + 72f)
    }
  }

  @Test
  fun `arc can reach both limits and intermediate values in either direction`() {
    val model = geometry(3f)
    listOf(1f, 1.2f, 2f, 4f, 6f, 3f, 1f).forEach { ratio ->
      assertEquals(ratio, model.ratioAt(model.pointAt(ratio)) ?: 0f, 0.001f)
    }
    val belowMinimum = FanZoomPoint(model.center.x - model.radius * 2, model.center.y - 1)
    val aboveMaximum = FanZoomPoint(model.center.x + model.radius * 2, model.center.y - 1)
    assertEquals(1f, model.ratioAt(belowMinimum) ?: 0f, 0.001f)
    assertEquals(6f, model.ratioAt(aboveMaximum) ?: 0f, 0.001f)
  }

  @Test
  fun `crossing the centre or passing below it cannot flip the zoom`() {
    val model = geometry(3f)
    assertNull(model.ratioAt(model.center))
    assertNull(model.ratioAt(FanZoomPoint(model.center.x, model.center.y + 50f)))
    assertNull(model.ratioAt(FanZoomPoint(Float.NaN, 500f)))
    assertNull(model.ratioAt(FanZoomPoint(100f, Float.NEGATIVE_INFINITY)))
  }

  @Test
  fun `thumb arc fits compact phones landscape and inset layouts`() {
    listOf(320f to 640f, 393f to 800f, 800f to 393f).forEach { (width, height) ->
      listOf(1f, 2.5f, 6f).forEach { ratio ->
        val bounds = FanZoomBounds(20f, 32f, width - 20f, height - 28f)
        val model = FanZoomGeometry(FanZoomPoint(width * 0.58f, height - 74f), ratio, 6f, bounds, 1f)
        listOf(1f, 2f, 3f, 6f).forEach { tick ->
          val point = model.pointAt(tick)
          assertTrue(point.x in (bounds.left + 12f)..(bounds.right - 12f))
          assertTrue(point.y in (bounds.top + 12f)..(bounds.bottom - 12f))
        }
      }
    }
  }

  @Test
  fun `smaller camera limit uses the full arc`() {
    val model = FanZoomGeometry(
      FanZoomPoint(220f, 750f),
      1.7f,
      2.5f,
      FanZoomBounds(8f, 32f, 385f, 792f),
      1f
    )
    assertEquals(1f, model.ratioAt(model.pointAt(1f)) ?: 0f, 0.001f)
    assertEquals(2.5f, model.ratioAt(model.pointAt(2.5f)) ?: 0f, 0.001f)
  }

  private fun geometry(ratio: Float) = FanZoomGeometry(
    FanZoomPoint(220f, 750f),
    ratio,
    6f,
    FanZoomBounds(8f, 32f, 385f, 792f),
    1f
  )
}