package io.github.g00fy2.quickie

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageButton
import kotlin.math.roundToInt

internal data class CodeMarker(val id: Int, val x: Float, val y: Float)

/** Keep the displayed marker and its click action together while a finger is down. */
internal class CodeMarkers(private val owner: View, private val container: FrameLayout) {

  private val buttons = mutableMapOf<Int, AppCompatImageButton>()
  private val actions = mutableMapOf<View, () -> Unit>()
  private var touching = false
  private var deferred: (() -> Unit)? = null
  private val releaseFallback = Runnable { releaseInteraction() }
  private val size = (48 * owner.resources.displayMetrics.density).roundToInt()
  private val inset = (8 * owner.resources.displayMetrics.density).roundToInt()

  fun show(markers: List<CodeMarker>, onSelected: (Int) -> Unit) {
    if (markers.isEmpty()) {
      clear()
      return
    }
    if (touching) {
      deferred = { show(markers, onSelected) }
      return
    }
    val ids = markers.map { it.id }.toSet()
    buttons.keys.filterNot { it in ids }.forEach { id ->
      buttons.remove(id)?.let {
        actions.remove(it)
        container.removeView(it)
      }
    }
    markers.forEachIndexed { index, marker ->
      val button = buttons.getOrPut(marker.id) { createButton().also { container.addView(it) } }
      button.contentDescription = owner.context.getString(R.string.quickie_view_code, index + 1)
      actions[button] = { onSelected(marker.id) }
      position(button, marker)
    }
  }

  @SuppressLint("ClickableViewAccessibility") // Removed targets must no longer deliver a click or retain a touch.
  private fun clear() {
    owner.removeCallbacks(releaseFallback)
    touching = false
    deferred = null
    buttons.values.forEach {
      it.setOnClickListener(null)
      it.setOnTouchListener(null)
    }
    container.removeAllViews()
    buttons.clear()
    actions.clear()
  }

  @SuppressLint("ClickableViewAccessibility") // ImageButton handles performClick; this listener only defers rebinding.
  private fun createButton(): AppCompatImageButton = AppCompatImageButton(owner.context).apply {
    var pressedAction: (() -> Unit)? = null
    setBackgroundColor(android.graphics.Color.TRANSPARENT)
    setImageResource(R.drawable.quickie_code_dot)
    setPadding(inset, inset, inset, inset)
    elevation = 4 * resources.displayMetrics.density
    setOnClickListener {
      val selected = pressedAction ?: actions[this]
      pressedAction = null
      selected?.invoke()
      releaseInteraction()
    }
    setOnTouchListener { _, event ->
      when (event.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          owner.removeCallbacks(releaseFallback)
          touching = true
          pressedAction = actions[this]
        }
        MotionEvent.ACTION_UP -> owner.postDelayed(releaseFallback, 100L)
        MotionEvent.ACTION_CANCEL -> {
          pressedAction = null
          owner.post(releaseFallback)
        }
      }
      false
    }
  }

  private fun releaseInteraction() {
    owner.removeCallbacks(releaseFallback)
    touching = false
    val update = deferred
    deferred = null
    update?.invoke()
  }

  @SuppressLint("RtlHardcoded") // Camera coordinates are physical screen positions in both layout directions.
  private fun position(button: View, marker: CodeMarker) {
    button.layoutParams = FrameLayout.LayoutParams(size, size, Gravity.TOP or Gravity.LEFT).apply {
      leftMargin = (marker.x * owner.width - container.left - size / 2f).roundToInt()
      topMargin = (marker.y * owner.height - container.top - size / 2f).roundToInt()
    }
  }
}