package io.github.g00fy2.quickie

import io.github.g00fy2.quickie.config.BarcodeFormat

internal object BarcodeResultPolicy {

  private val decodedFormats = BarcodeFormat.entries.filter { it != BarcodeFormat.FORMAT_ALL_FORMATS }
    .map { it.value }.toSet()

  fun hasContent(text: String?, bytes: ByteArray?): Boolean = !text.isNullOrBlank() || bytes?.isNotEmpty() == true

  fun isDecoded(format: Int, text: String?, bytes: ByteArray?): Boolean =
    format in decodedFormats && hasContent(text, bytes)
}