package io.github.g00fy2.quickie

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContract

/** Scans an image URI the caller can read, including images received through the Android Sharesheet. */
public class ScanImageFromUri : ActivityResultContract<Uri, QRResult>() {

  override fun createIntent(context: Context, input: Uri): Intent =
    Intent(context, QRImageScannerActivity::class.java).apply {
      data = input
      clipData = ClipData.newRawUri("", input)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

  override fun parseResult(resultCode: Int, intent: Intent?): QRResult = ScanQRCode().parseResult(resultCode, intent)
}