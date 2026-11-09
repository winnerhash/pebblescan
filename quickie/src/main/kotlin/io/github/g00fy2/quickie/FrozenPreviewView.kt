package io.github.g00fy2.quickie

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.view.isVisible

/** Holds the decoded camera frame so the picture and its selection targets stop together. */
internal class FrozenPreviewView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null
) : View(context, attrs) {

  private var image: Bitmap? = null
  private val transform = Matrix()
  private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
  val isFrozen: Boolean get() = image != null

  fun show(bitmap: Bitmap, matrix: Matrix) {
    image = bitmap
    transform.set(matrix)
    isVisible = true
    invalidate()
  }

  fun clear() {
    image = null
    isVisible = false
    invalidate()
  }

  override fun onDraw(canvas: Canvas) {
    super.onDraw(canvas)
    canvas.drawColor(Color.BLACK)
    image?.let { canvas.drawBitmap(it, transform, paint) }
  }

  override fun onDetachedFromWindow() {
    clear()
    super.onDetachedFromWindow()
  }
}