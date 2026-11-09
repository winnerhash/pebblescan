package io.github.g00fy2.quickie

import android.graphics.RectF
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.OutputTransform

@androidx.annotation.OptIn(TransformExperimental::class)
internal class PreviewCodeCoordinates(
  source: OutputTransform,
  target: OutputTransform,
  private val sourceWidth: Int,
  private val sourceHeight: Int,
  private val viewWidth: Int,
  private val viewHeight: Int
) {

  private val transform = CoordinateTransform(source, target)

  fun map(position: CodePosition): CodePosition {
    val box = RectF(
      (position.x - position.width / 2f) * sourceWidth,
      (position.y - position.height / 2f) * sourceHeight,
      (position.x + position.width / 2f) * sourceWidth,
      (position.y + position.height / 2f) * sourceHeight
    )
    transform.mapRect(box)
    return CodePosition(
      box.centerX() / viewWidth,
      box.centerY() / viewHeight,
      box.width() / viewWidth,
      box.height() / viewHeight
    )
  }
}