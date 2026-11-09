package io.github.g00fy2.quickie

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.withTranslation
import androidx.core.view.isVisible
import kotlin.math.max
import kotlin.math.min

internal class FanZoomView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : View(context, attrs) {

  private val density = resources.displayMetrics.density
  private val ink = ContextCompat.getColor(context, R.color.quickie_ink)
  private val cream = ContextCompat.getColor(context, R.color.quickie_surface)
  private val mint = 0xFFBFE9D6.toInt()
  private val lavender = 0xFFDCD2EF.toInt()
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private val oval = RectF()
  private var geometry: FanZoomGeometry? = null
  private var ratio = 1f

  fun show(model: FanZoomGeometry) {
    geometry = model
    ratio = model.initialRatio
    isVisible = true
    invalidate()
  }

  fun update(value: Float) {
    ratio = value
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    val model = geometry ?: return
    canvas.withTranslation(-left.toFloat(), -top.toFloat()) {
      drawTrack(this, model)
      drawLabels(this, model)
      drawValue(this, model)
      drawKnob(this, model)
    }
  }

  private fun drawKnob(canvas: Canvas, model: FanZoomGeometry) {
    val knob = model.pointAt(ratio)
    paint.style = Paint.Style.FILL
    paint.color = ink
    canvas.drawCircle(knob.x, knob.y, 12f * density, paint)
    paint.color = mint
    canvas.drawCircle(knob.x, knob.y, 9f * density, paint)
  }

  private fun drawTrack(canvas: Canvas, model: FanZoomGeometry) {
    oval.set(
      model.center.x - model.radius,
      model.center.y - model.radius,
      model.center.x + model.radius,
      model.center.y + model.radius
    )
    paint.style = Paint.Style.STROKE
    paint.strokeCap = Paint.Cap.ROUND
    paint.strokeWidth = 40f * density
    paint.color = ink
    canvas.drawArc(oval, model.startDegrees, model.sweepDegrees, false, paint)
    paint.strokeWidth = 36f * density
    paint.color = cream
    canvas.drawArc(oval, model.startDegrees, model.sweepDegrees, false, paint)
    paint.strokeWidth = 6f * density
    paint.color = lavender
    canvas.drawArc(oval, model.startDegrees, model.sweepDegrees, false, paint)
    paint.color = mint
    canvas.drawArc(oval, model.startDegrees, model.sweepTo(ratio), false, paint)
  }

  private fun drawLabels(canvas: Canvas, model: FanZoomGeometry) {
    paint.style = Paint.Style.FILL
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    val ticks = listOf(1f, model.maximum)
    ticks.filter { it <= model.maximum }.distinct().forEach { value ->
      val point = model.pointAt(value, 28f * density)
      val text = context.getString(R.string.quickie_zoom_value, value)
      val halfWidth = paint.measureText(text) / 2f + 4f * density
      val x = point.x.coerceIn(model.bounds.left + halfWidth, model.bounds.right - halfWidth)
      val metrics = paint.fontMetrics
      oval.set(x - halfWidth, point.y + metrics.ascent - 3f * density, x + halfWidth, point.y + metrics.descent)
      paint.color = cream
      canvas.drawRoundRect(oval, 6f * density, 6f * density, paint)
      paint.color = ink
      canvas.drawText(text, x, point.y, paint)
    }
  }

  private fun drawValue(canvas: Canvas, model: FanZoomGeometry) {
    paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics)
    paint.textAlign = Paint.Align.CENTER
    val label = context.getString(R.string.quickie_zoom_value, ratio)
    val labelWidth = paint.measureText(label)
    val valueMetrics = paint.fontMetrics
    paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    val hint = context.getString(R.string.quickie_zoom_hint)
    val hintMetrics = paint.fontMetrics
    val halfWidth = min(
      max(labelWidth, paint.measureText(hint)) / 2f + 14f * density,
      (model.bounds.right - model.bounds.left) / 2f
    )
    val x = model.center.x.coerceIn(model.bounds.left + halfWidth, model.bounds.right - halfWidth)
    val bottom = model.center.y - model.radius - 60f * density
    val height = valueMetrics.descent - valueMetrics.ascent + hintMetrics.descent - hintMetrics.ascent + 24f * density
    oval.set(x - halfWidth, bottom - height, x + halfWidth, bottom)
    paint.style = Paint.Style.FILL
    paint.color = cream
    canvas.drawRoundRect(oval, 18f * density, 18f * density, paint)
    paint.color = ink
    paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 20f, resources.displayMetrics)
    canvas.drawText(label, x, oval.top + 10f * density - valueMetrics.ascent, paint)
    paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11f, resources.displayMetrics)
    canvas.drawText(hint, x, bottom - 10f * density - hintMetrics.descent, paint)
  }
}