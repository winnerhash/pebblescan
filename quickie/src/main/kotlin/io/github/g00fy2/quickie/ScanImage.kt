package io.github.g00fy2.quickie

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/**
 * Opens the system photo picker and scans the selected image for any supported barcode format.
 * No camera or storage permission is required. If several codes are found, the user chooses one.
 */
public class ScanImage : ActivityResultContract<Nothing?, QRResult>() {

  override fun createIntent(context: Context, input: Nothing?): Intent =
    Intent(context, QRImageScannerActivity::class.java)

  override fun parseResult(resultCode: Int, intent: Intent?): QRResult = ScanQRCode().parseResult(resultCode, intent)
}