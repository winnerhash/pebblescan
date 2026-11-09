package io.github.g00fy2.quickie

import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.OutputTransform

@androidx.annotation.OptIn(TransformExperimental::class)
internal object FrozenPreviewTransform {

  fun create(width: Int, height: Int, rotation: Int, source: OutputTransform, target: OutputTransform): Matrix {
    val rotate = Matrix().apply { setRotate(rotation.toFloat()) }
    val rect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    rotate.mapRect(rect)
    rotate.postTranslate(-rect.left, -rect.top)
    val preview = Matrix().also { CoordinateTransform(source, target).transform(it) }
    return Matrix().apply { setConcat(preview, rotate) }
  }
}