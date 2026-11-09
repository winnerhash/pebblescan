package io.github.g00fy2.quickie

/** Bounded fallback after model detection, with two additional directions per undecoded frame. */
internal class TiltedBarcodeDecoder(formats: IntArray) {

  private val decoder = LinearBarcodeDecoder(formats)
  private var direction = 0
  private var preferred: Int? = null

  fun decode(source: CameraLuminanceSource, regions: List<BarcodeRegion>): String? =
    decodeLocated(source, regions)?.text

  fun decodeLocated(source: CameraLuminanceSource, regions: List<BarcodeRegion>): LocatedBarcode? {
    if (!decoder.enabled) return null
    return rectify(source, regions) ?: sweep(source)
  }

  fun rectify(source: CameraLuminanceSource, regions: List<BarcodeRegion>): LocatedBarcode? {
    for (region in regions.take(2)) {
      val text = readWarped(WarpedLuminanceSource.rectified(source, region))
        ?: readWarped(WarpedLuminanceSource.rectified(source, region.quarterTurn()))
      if (text != null) return text
    }
    return null
  }

  private fun sweep(source: CameraLuminanceSource): LocatedBarcode? {
    val recent = preferred?.let { readDirection(source, it) }
    if (recent != null) return recent
    preferred = null
    return nextDirections(source)
  }

  private fun nextDirections(source: CameraLuminanceSource): LocatedBarcode? {
    repeat(2) {
      val step = direction++ % (angles.size * 2)
      val text = readDirection(source, step)
      if (text != null) {
        preferred = step
        return text
      }
    }
    return null
  }

  private fun readDirection(source: CameraLuminanceSource, step: Int): LocatedBarcode? {
    val oriented = if (step < angles.size) source else source.rotateCounterClockwise()
    val result = readWarped(WarpedLuminanceSource.rotated(oriented, angles[step % angles.size]))
    return if (step < angles.size) result else result?.mapped { ImagePoint(source.width - 1f - it.y, it.x) }
  }

  private fun readWarped(source: WarpedLuminanceSource): LocatedBarcode? {
    return decoder.decodeLocated(source)?.mapped(source::mapPoint)
  }

  companion object {
    private val angles = intArrayOf(-15, 15, -30, 30, 45)
  }
}