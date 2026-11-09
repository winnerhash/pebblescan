package io.github.g00fy2.quickie

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.isVisible
import kotlin.math.abs
import kotlin.math.hypot

internal class FanZoomGesture(
  private val button: View,
  private val owner: View,
  private val fan: FanZoomView,
  private val controls: View,
  private val maximum: Float,
  private val onRatio: (Float) -> Unit
) : View.OnTouchListener, View.OnAttachStateChangeListener {

  var ratio = 1f
  var isInteracting = false
    private set
  private var downX = 0f
  private var downY = 0f
  private var geometry: FanZoomGeometry? = null
  private var requested = 1f
  private var dragging = false
  private val slop = ViewConfiguration.get(button.context).scaledTouchSlop
  private val open = Runnable { show() }

  init {
    button.addOnAttachStateChangeListener(this)
  }

  // A short tap calls performClick; holding or sliding consumes the complete gesture.
  @SuppressLint("ClickableViewAccessibility")
  override fun onTouch(view: View, event: MotionEvent): Boolean {
    if (event.pointerCount > 1) {
      finish()
    } else {
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> begin(event)
        MotionEvent.ACTION_MOVE -> move(event)
        MotionEvent.ACTION_UP -> {
          val clicked = isInteracting && geometry == null
          finish()
          if (clicked) button.performClick()
        }
        MotionEvent.ACTION_CANCEL -> finish()
      }
    }
    return true
  }

  private fun begin(event: MotionEvent) {
    downX = event.rawX
    downY = event.rawY
    dragging = false
    isInteracting = true
    button.isPressed = true
    button.parent.requestDisallowInterceptTouchEvent(true)
    button.postDelayed(open, 220L)
  }

  private fun show() {
    if (!isInteracting || geometry != null) return
    val position = IntArray(2)
    val parentPosition = IntArray(2)
    button.getLocationOnScreen(position)
    owner.getLocationOnScreen(parentPosition)
    val anchor = FanZoomPoint(
      position[0] - parentPosition[0] + button.width / 2f,
      position[1] - parentPosition[1] + button.height / 2f
    )
    val margin = 8f * button.resources.displayMetrics.density
    val bounds = FanZoomBounds(
      owner.paddingLeft + margin,
      owner.paddingTop + margin,
      owner.width - owner.paddingRight - margin,
      owner.height - owner.paddingBottom - margin
    )
    requested = ratio.coerceIn(1f, maximum)
    geometry = FanZoomGeometry(anchor, requested, maximum, bounds, button.resources.displayMetrics.density)
    geometry?.let(fan::show)
    controls.alpha = 0f
    button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
  }

  private fun move(event: MotionEvent) {
    if (!isInteracting) return
    val x = event.rawX - downX
    val y = event.rawY - downY
    if (hypot(x, y) > slop) {
      dragging = true
      show()
    }
    if (!dragging) return
    geometry?.let { model ->
      model.ratioAt(FanZoomPoint(model.pointerStart.x + x, model.pointerStart.y + y))?.let { value ->
        if (abs(value - requested) >= 0.02f) {
          requested = value
          fan.update(value)
          onRatio(value)
        }
      }
    }
  }

  private fun finish() {
    button.removeCallbacks(open)
    isInteracting = false
    button.isPressed = false
    geometry = null
    fan.isVisible = false
    controls.alpha = 1f
    button.parent?.requestDisallowInterceptTouchEvent(false)
  }

  override fun onViewAttachedToWindow(view: View) = Unit

  override fun onViewDetachedFromWindow(view: View) = finish()
}