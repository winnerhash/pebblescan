package io.github.g00fy2.quickie

import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.common.Barcode
import kotlin.math.max

internal class CameraZoomController(
  private val activity: QRScannerActivity,
  private val camera: Camera,
  private val overlay: QROverlayView,
  private val onZoomChanged: () -> Unit
) {

  private val tracker = AutoZoomTracker()
  private val executor = ContextCompat.getMainExecutor(activity)
  private val maximum = (camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f).coerceIn(1f, 6f)
  private val session = ZoomSession(maximum)
  private val focusAfterZoom = Runnable {
    if (!activity.isFinishing && !activity.isDestroyed) onZoomChanged()
  }

  init {
    overlay.setZoomActions(
      maximum,
      onRatio = { ratio ->
        session.manualRatio(ratio)?.let(::setZoom)
        updateState()
      },
      onScale = { factor ->
        session.manualScale(factor, currentRatio())?.let(::setZoom)
        updateState()
      },
      onAutomatic = {
        tracker.zoomRequested(SystemClock.elapsedRealtime())
        session.resumeAutomatic()?.let(::setZoom)
        updateState()
      }
    )
    camera.cameraInfo.zoomState.observe(activity) { updateState() }
  }

  fun onPotentialCodes(codes: List<Barcode>, width: Int, height: Int) {
    if (session.isManual || session.isApplying || overlay.isZoomInteracting) return
    if (width <= 0 || height <= 0) return
    val current = currentRatio()
    val target = codes.mapNotNull { it.boundingBox }.map {
      val x = it.exactCenterX() / width
      val y = it.exactCenterY() / height
      val widthFraction = it.width().toFloat() / width
      val heightFraction = it.height().toFloat() / height
      ZoomTarget(
        x,
        y,
        max(widthFraction, heightFraction),
        ZoomPolicy.visibleMaximum(x, y, widthFraction, heightFraction, current)
      )
    }.filter { it.x in 0.2f..0.8f && it.y in 0.15f..0.85f }
      .minByOrNull { (it.x - 0.5f) * (it.x - 0.5f) + (it.y - 0.5f) * (it.y - 0.5f) }
    tracker.observe(target, SystemClock.elapsedRealtime(), current, maximum)
      ?.let(session::automaticRatio)?.let(::setZoom)
  }

  private fun setZoom(ratio: Float) {
    if (activity.isFinishing || activity.isDestroyed) return
    overlay.removeCallbacks(focusAfterZoom)
    try {
      tracker.zoomRequested(SystemClock.elapsedRealtime())
      val task = camera.cameraControl.setZoomRatio(ratio)
      task.addListener({
        var succeeded = false
        try {
          @Suppress("IgnoredReturnValue") // The future has no result value; get() reports failure or cancellation.
          task.get()
          succeeded = true
        } catch (ignored: Exception) {
          // A zoom cancellation should never stop scanning, e.g. when opening the gallery or leaving the camera.
        } finally {
          finishZoom(succeeded)
        }
      }, executor)
    } catch (ignored: Exception) {
      finishZoom(false)
    }
  }

  private fun finishZoom(succeeded: Boolean) {
    val next = session.complete()
    if (next != null) {
      setZoom(next)
    } else if (succeeded && !activity.isFinishing && !activity.isDestroyed) {
      overlay.postDelayed(focusAfterZoom, 200L)
    }
  }

  private fun currentRatio() = camera.cameraInfo.zoomState.value?.zoomRatio ?: 1f

  private fun updateState() = overlay.setZoomState(currentRatio(), session.isManual)
}