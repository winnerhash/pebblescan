package io.github.g00fy2.quickie

import android.content.Intent
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_BYTES
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_PARCELABLE
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_TYPE
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_VALUE
import io.github.g00fy2.quickie.extensions.toParcelableContentType

internal fun ScanCode.toResultIntent(): Intent = Intent().apply {
  putExtra(EXTRA_RESULT_VALUE, text)
  putExtra(EXTRA_RESULT_BYTES, bytes)
  barcode?.let {
    putExtra(EXTRA_RESULT_TYPE, it.valueType)
    putExtra(EXTRA_RESULT_PARCELABLE, it.toParcelableContentType())
  }
}