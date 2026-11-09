package io.github.g00fy2.quickie

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.common.Barcode

internal class CameraBarcodeRescue(formats: IntArray) {

  private val tilted = TiltedBarcodeDecoder(formats)
  private val linear = LinearBarcodeDecoder(formats)

  fun decode(
    image: ImageProxy,
    codes: List<Barcode>,
    modelWidth: Int,
    modelHeight: Int,
    thorough: Boolean
  ): List<ScanCode> {
    val plane = image.planes.first()
    var source = CameraLuminanceSource(plane.buffer, image.width, image.height, plane.rowStride, plane.pixelStride)
    repeat((360 - image.imageInfo.rotationDegrees) % 360 / 90) { source = source.rotateCounterClockwise() }
    val regions = if (modelWidth > 0 && modelHeight > 0) {
      codes.filterNot {
        BarcodeResultPolicy.isDecoded(it.format, it.rawValue, it.rawBytes)
      }.take(4).mapNotNull { code ->
        code.cornerPoints?.map {
          ImagePoint(it.x.toFloat() * source.width / modelWidth, it.y.toFloat() * source.height / modelHeight)
        }?.let { BarcodeRegion.create(it, source.width, source.height) }
      }
    } else {
      emptyList()
    }
    val results = regions.mapNotNull { region ->
      tilted.rectify(source, listOf(region))?.normalized(source.width, source.height)
    }
    val modelDecoded = codes.any { BarcodeResultPolicy.isDecoded(it.format, it.rawValue, it.rawBytes) }
    return if (results.isNotEmpty() || modelDecoded) {
      results
    } else {
      val fallback = linear.decodeLocated(source) ?: tilted.decodeLocated(source, emptyList())
        ?: if (thorough) linear.decodeLocated(source, thorough = true) else null
      listOfNotNull(fallback?.normalized(source.width, source.height))
    }
  }
}