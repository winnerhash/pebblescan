package io.github.g00fy2.quickie

internal data class LocatedBarcode(val text: String, val start: ImagePoint, val end: ImagePoint) {
  fun mapped(transform: (ImagePoint) -> ImagePoint) = LocatedBarcode(text, transform(start), transform(end))

  fun normalized(width: Int, height: Int): ScanCode = ScanCode(
    CodePosition(
      (start.x + end.x) / 2f / width,
      (start.y + end.y) / 2f / height,
      kotlin.math.abs(end.x - start.x) / width,
      kotlin.math.abs(end.y - start.y) / height
    ),
    text
  )
}