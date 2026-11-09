package io.github.g00fy2.quickie

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.camera.view.PreviewView
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.OutputTransform

@androidx.annotation.OptIn(TransformExperimental::class)
internal class CameraCodeSelection(
  private val preview: PreviewView,
  private val overlay: QROverlayView,
  private val frozenPreview: FrozenPreviewView,
  private val onPauseChanged: (Boolean) -> Unit,
  private val onSelected: (ScanCode) -> Unit
) {

  private var tracker = ScanSelectionTracker()
  private var firstChoiceAt: Long? = null
  val requiresSelection get() = tracker.requiresSelection

  fun onFrame(frame: DetectedScanFrame, capture: () -> Bitmap) {
    val target = preview.outputTransform
    val busy = frozenPreview.isFrozen || overlay.isZoomInteracting
    if (busy || target == null) return
    if (overlay.width <= 0 || overlay.height <= 0) return
    val coordinates = PreviewCodeCoordinates(
      frame.transform,
      target,
      frame.width,
      frame.height,
      overlay.width,
      overlay.height
    )
    val visible = frame.codes.mapNotNull { code ->
      val position = coordinates.map(code.position)
      code.at(position).takeIf { overlay.isCodeVisible(position.x, position.y) }
    }
    val now = SystemClock.elapsedRealtime()
    val decision = tracker.observe(visible, frame.number, now)
    val choices = decision.choices.filter { it.code in visible }
    val selectable = tracker.requiresSelection && choices.isNotEmpty()
    firstChoiceAt = if (selectable) firstChoiceAt ?: now else null
    val allReady = visible.all { it.hasContent } && choices.size == visible.size
    val waited = firstChoiceAt?.let { now - it >= 350L } == true
    if (selectable && (allReady || waited)) freeze(frame, target, choices, capture)
    decision.automatic?.let(onSelected)
  }

  private fun freeze(
    frame: DetectedScanFrame,
    target: OutputTransform,
    choices: List<ConfirmedCode>,
    capture: () -> Bitmap
  ) {
    try {
      val bitmap = capture()
      val matrix = FrozenPreviewTransform.create(bitmap.width, bitmap.height, frame.rotation, frame.transform, target)
      frozenPreview.show(bitmap, matrix)
      onPauseChanged(true)
      overlay.setSelectionFrozen(true, ::resume)
      overlay.setCodeChoices(choices.map { CodeMarker(it.id, it.code.position.x, it.code.position.y) }) { id ->
        choices.firstOrNull { it.id == id }?.code?.let(onSelected)
      }
    } catch (ignored: RuntimeException) {
      // If a vendor cannot convert this frame, keep scanning instead of showing dots over a different picture.
    }
  }

  private fun resume() {
    tracker = ScanSelectionTracker()
    firstChoiceAt = null
    overlay.setCodeChoices(emptyList()) {}
    overlay.setSelectionFrozen(false) {}
    frozenPreview.clear()
    onPauseChanged(false)
  }
}