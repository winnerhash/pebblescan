package io.github.g00fy2.quickie

import android.annotation.SuppressLint
import androidx.core.view.isVisible
import io.github.g00fy2.quickie.databinding.QuickieOverlayViewBinding

internal class ZoomControls(private val binding: QuickieOverlayViewBinding) {

  private var gesture: FanZoomGesture? = null
  val isInteracting: Boolean get() = gesture?.isInteracting == true

  @SuppressLint("ClickableViewAccessibility") // FanZoomGesture calls performClick for taps and exposes range actions.
  fun bind(maximumRatio: Float, onRatio: (Float) -> Unit, onAutomatic: () -> Unit) {
    binding.zoomButton.isVisible = maximumRatio > 1f
    binding.zoomButton.setOnClickListener { onAutomatic() }
    if (maximumRatio > 1f) {
      val control = FanZoomGesture(
        binding.zoomButton,
        binding.root,
        binding.zoomFan,
        binding.bottomControls,
        maximumRatio,
        onRatio
      )
      gesture = control
      binding.zoomButton.setOnTouchListener(control)
      ZoomAccessibility(binding.zoomButton, maximumRatio, { control.ratio }, onRatio)
    }
  }

  fun update(ratio: Float, manual: Boolean) {
    val context = binding.root.context
    val label = context.getString(if (manual) R.string.quickie_zoom_manual else R.string.quickie_zoom_auto, ratio)
    val text = context.getString(R.string.quickie_zoom_control, label, context.getString(R.string.quickie_zoom_hold))
    if (binding.zoomButton.text != text) binding.zoomButton.text = text
    binding.zoomButton.contentDescription = context.getString(R.string.quickie_zoom_adjust) + ", " + label
    gesture?.ratio = ratio
  }
}