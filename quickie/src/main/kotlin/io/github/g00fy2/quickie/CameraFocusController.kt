package io.github.g00fy2.quickie

import androidx.camera.core.Camera
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import java.util.concurrent.TimeUnit

internal class CameraFocusController(private val camera: Camera, private val preview: PreviewView) {

  fun focus(x: Float = preview.width / 2f, y: Float = preview.height / 2f) {
    if (preview.width == 0 || preview.height == 0) return
    val point = preview.meteringPointFactory.createPoint(x, y)
    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
      .setAutoCancelDuration(3, TimeUnit.SECONDS).build()
    if (camera.cameraInfo.isFocusMeteringSupported(action)) {
      camera.cameraControl.startFocusAndMetering(action)
    }
  }
}