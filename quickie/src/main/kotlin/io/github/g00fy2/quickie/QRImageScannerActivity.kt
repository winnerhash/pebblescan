package io.github.g00fy2.quickie

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_BYTES
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_EXCEPTION
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_PARCELABLE
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_TYPE
import io.github.g00fy2.quickie.QRScannerActivity.Companion.EXTRA_RESULT_VALUE
import io.github.g00fy2.quickie.QRScannerActivity.Companion.RESULT_ERROR
import io.github.g00fy2.quickie.extensions.toParcelableContentType
import java.util.concurrent.Executors

internal class QRImageScannerActivity : AppCompatActivity() {

  private val imageExecutor = Executors.newSingleThreadExecutor()
  private var imageUri: Uri? = null
  private val pickImage = registerForActivityResult(PickVisualMedia()) { uri ->
    if (uri == null) {
      finish()
    } else {
      imageUri = uri
      scanImage(uri)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.quickie_image_scanner_activity)
    if (savedInstanceState == null) {
      imageUri = intent.data
      val uri = imageUri
      if (uri == null) pickImage.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) else scanImage(uri)
    } else {
      imageUri = savedInstanceState.getString(IMAGE_URI)?.let(Uri::parse)
      imageUri?.let(::scanImage)
    }
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putString(IMAGE_URI, imageUri?.toString())
    super.onSaveInstanceState(outState)
  }

  override fun onDestroy() {
    imageExecutor.shutdown()
    super.onDestroy()
  }

  private fun scanImage(uri: Uri) {
    imageExecutor.execute {
      try {
        // File access and image decoding can be slow, especially with a cloud media provider.
        val image = InputImage.fromFilePath(this, uri)
        val scanner = BarcodeScanning.getClient()
        try {
          scanner.process(image)
            .addOnSuccessListener { codes ->
              if (!isDestroyed && !isFinishing) {
                showCodes(codes.filter { BarcodeResultPolicy.isDecoded(it.format, it.rawValue, it.rawBytes) })
              }
            }
            .addOnFailureListener(::onFailure)
            .addOnCompleteListener { scanner.close() }
        } catch (exception: Exception) {
          scanner.close()
          onFailure(exception)
        }
      } catch (exception: Exception) {
        onFailure(exception)
      }
    }
  }

  private fun showCodes(codes: List<Barcode>) {
    when (codes.size) {
      0 -> onFailure(IllegalArgumentException(getString(R.string.quickie_no_barcode_found)))
      1 -> onSuccess(codes.first())
      else -> AlertDialog.Builder(this)
        .setTitle(R.string.quickie_choose_barcode)
        .setItems(codes.map { it.rawValue ?: getString(R.string.quickie_binary_barcode) }.toTypedArray()) { _, which ->
          onSuccess(codes[which])
        }
        .setOnCancelListener { finish() }
        .show()
    }
  }

  private fun onSuccess(code: Barcode) {
    setResult(
      RESULT_OK,
      Intent().apply {
        putExtra(EXTRA_RESULT_BYTES, code.rawBytes)
        putExtra(EXTRA_RESULT_VALUE, code.rawValue)
        putExtra(EXTRA_RESULT_TYPE, code.valueType)
        putExtra(EXTRA_RESULT_PARCELABLE, code.toParcelableContentType())
      }
    )
    finish()
  }

  private fun onFailure(exception: Exception) {
    runOnUiThread {
      if (!isDestroyed && !isFinishing) {
        setResult(RESULT_ERROR, Intent().putExtra(EXTRA_RESULT_EXCEPTION, exception))
        finish()
      }
    }
  }

  companion object {
    private const val IMAGE_URI = "quickie-image-uri"
  }
}