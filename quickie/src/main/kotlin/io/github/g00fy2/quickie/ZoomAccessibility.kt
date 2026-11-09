package io.github.g00fy2.quickie

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.SeekBar
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat

internal class ZoomAccessibility(
  private val button: View,
  private val maximum: Float,
  private val current: () -> Float,
  private val onRatio: (Float) -> Unit
) : AccessibilityDelegateCompat() {

  init {
    ViewCompat.setAccessibilityDelegate(button, this)
    button.setOnKeyListener { _, keyCode, event ->
      val value = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> current() - 0.1f
        KeyEvent.KEYCODE_DPAD_RIGHT -> current() + 0.1f
        KeyEvent.KEYCODE_MOVE_HOME -> 1f
        KeyEvent.KEYCODE_MOVE_END -> maximum
        else -> null
      }
      if (value != null && event.action == KeyEvent.ACTION_DOWN) onRatio(value.coerceIn(1f, maximum))
      value != null
    }
  }

  override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
    super.onInitializeAccessibilityNodeInfo(host, info)
    info.className = SeekBar::class.java.name
    info.rangeInfo = RangeInfoCompat(RangeInfoCompat.RANGE_TYPE_FLOAT, 1f, maximum, current())
    info.addAction(AccessibilityActionCompat.ACTION_SET_PROGRESS)
    info.addAction(AccessibilityActionCompat.ACTION_SCROLL_FORWARD)
    info.addAction(AccessibilityActionCompat.ACTION_SCROLL_BACKWARD)
    info.addAction(
      AccessibilityActionCompat(
        AccessibilityNodeInfoCompat.ACTION_CLICK,
        button.context.getString(R.string.quickie_zoom_resume)
      )
    )
  }

  override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
    val value = when (action) {
      AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD -> current() + 0.1f
      AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD -> current() - 0.1f
      AccessibilityActionCompat.ACTION_SET_PROGRESS.id ->
        args?.getFloat(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_PROGRESS_VALUE, Float.NaN)
      else -> null
    }
    return if (value != null && value.isFinite()) {
      onRatio(value.coerceIn(1f, maximum))
      true
    } else {
      super.performAccessibilityAction(host, action, args)
    }
  }
}