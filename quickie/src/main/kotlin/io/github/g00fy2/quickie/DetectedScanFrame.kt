package io.github.g00fy2.quickie

import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.OutputTransform

@androidx.annotation.OptIn(TransformExperimental::class)
internal data class DetectedScanFrame(
  val codes: List<ScanCode>,
  val transform: OutputTransform,
  val width: Int,
  val height: Int,
  val number: Long,
  val rotation: Int
)