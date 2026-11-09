package io.github.g00fy2.quickie

import android.media.Image
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import kotlin.math.max

internal class BarcodeImageFactory {

  private val resampler = YuvPreviewResampler()

  fun create(image: Image, proxy: ImageProxy): InputImage {
    val rotation = proxy.imageInfo.rotationDegrees
    if (max(proxy.width, proxy.height) <= 1920) return InputImage.fromMediaImage(image, rotation)
    return try {
      val planes = proxy.planes.map { YuvPlane(it.buffer, it.rowStride, it.pixelStride) }
      val preview = resampler.sample(proxy.width, proxy.height, planes)
      InputImage.fromByteArray(
        preview.pixels,
        preview.width,
        preview.height,
        rotation,
        InputImage.IMAGE_FORMAT_NV21
      )
    } catch (ignored: IllegalArgumentException) {
      // Let ML Kit read the original image if a vendor uses an unexpected plane layout.
      InputImage.fromMediaImage(image, rotation)
    }
  }
}