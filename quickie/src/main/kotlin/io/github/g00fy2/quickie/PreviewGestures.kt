package io.github.g00fy2.quickie

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector

internal class PreviewGestures(context: Context, onTap: (Float, Float) -> Unit) {

  var onScale: ((Float) -> Unit)? = null
  private var multiplePointers = false
  private val scaleDetector = ScaleGestureDetector(
    context,
    object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
      override fun onScale(detector: ScaleGestureDetector): Boolean {
        onScale?.invoke(detector.scaleFactor)
        return true
      }
    }
  ).apply { isQuickScaleEnabled = false }
  private val tapDetector = GestureDetector(
    context,
    object : GestureDetector.SimpleOnGestureListener() {
      override fun onDown(event: MotionEvent) = true

      override fun onSingleTapUp(event: MotionEvent): Boolean {
        if (!multiplePointers) onTap(event.x, event.y)
        return true
      }
    }
  )

  fun onTouch(event: MotionEvent) {
    if (event.actionMasked == MotionEvent.ACTION_DOWN) multiplePointers = false
    if (event.pointerCount > 1) multiplePointers = true
    scaleDetector.onTouchEvent(event)
    tapDetector.onTouchEvent(event)
  }
}