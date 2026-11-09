package io.github.g00fy2.quickiesample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.google.android.material.snackbar.Snackbar
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.QRResult.QRError
import io.github.g00fy2.quickie.QRResult.QRMissingPermission
import io.github.g00fy2.quickie.QRResult.QRSuccess
import io.github.g00fy2.quickie.QRResult.QRUserCanceled
import io.github.g00fy2.quickie.ScanCustomCode
import io.github.g00fy2.quickie.ScanImage
import io.github.g00fy2.quickie.ScanImageFromUri
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickie.config.ScannerConfig
import io.github.g00fy2.quickiesample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

  private lateinit var binding: ActivityMainBinding
  private lateinit var home: HomeScreen
  private var displayedResult: ResultPresentation? = null
  private var resultSheet: ScanResultSheet? = null
  private var pendingLaunch: Intent? = null
  private val camera = registerForActivityResult(ScanCustomCode(), ::handleResult)
  private val gallery = registerForActivityResult(ScanImage(), ::handleResult)
  private val sharedImage = registerForActivityResult(ScanImageFromUri(), ::handleResult)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    home = HomeScreen(this, binding, ::launchCamera) { gallery.launch(null) }
    home.selectedFormat = BarcodeFormat.entries.getOrNull(savedInstanceState?.getInt(SELECTED_FORMAT) ?: 0)
      ?: BarcodeFormat.FORMAT_ALL_FORMATS
    home.bind(savedInstanceState == null && intent.action == ACTION_HOME)
    val restored = ResultPresentation.fromBundle(savedInstanceState?.getBundle(RESULT))
    if (restored != null) {
      showResult(restored)
    } else if (savedInstanceState == null) {
      pendingLaunch = intent
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    resultSheet?.dismiss()
    dispatch(intent)
  }

  override fun onPostResume() {
    super.onPostResume()
    val launch = pendingLaunch ?: return
    pendingLaunch = null
    dispatch(launch)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putInt(SELECTED_FORMAT, home.selectedFormat.ordinal)
    outState.putBundle(RESULT, displayedResult?.toBundle())
    super.onSaveInstanceState(outState)
  }

  override fun onDestroy() {
    resultSheet?.dismiss()
    super.onDestroy()
  }

  private fun dispatch(launch: Intent) {
    when (launch.action) {
      ACTION_HOME -> Unit
      ACTION_GALLERY -> {
        ShortcutManagerCompat.reportShortcutUsed(this, "gallery")
        gallery.launch(null)
      }
      ACTION_SCAN -> {
        ShortcutManagerCompat.reportShortcutUsed(this, "scan")
        launchCamera()
      }
      Intent.ACTION_SEND -> {
        val uri = IntentCompat.getParcelableExtra(launch, Intent.EXTRA_STREAM, Uri::class.java)
          ?: launch.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (launch.type?.startsWith("image/") == true && uri?.scheme == "content") {
          sharedImage.launch(uri)
        } else {
          showMessage(getString(R.string.invalid_shared_image))
        }
      }
      else -> launchCamera()
    }
  }

  private fun launchCamera(format: BarcodeFormat = BarcodeFormat.FORMAT_ALL_FORMATS, wide: Boolean = false) {
    camera.launch(
      ScannerConfig.build {
        setBarcodeFormats(listOf(format))
        setOverlayStringRes(if (wide) R.string.scan_barcode else R.string.scan_any_code)
        setOverlayDrawableRes(if (wide) R.drawable.ic_scan_barcode else null)
        setShowTorchToggle(true)
        setShowGalleryButton(true)
        setShowCloseButton(true)
        setAutoZoomEnabled(true)
        setKeepScreenOn(true)
        setHorizontalFrameRatio(if (wide) 2.2f else 1f)
      }
    )
  }

  private fun handleResult(result: QRResult) {
    when (result) {
      is QRSuccess -> showResult(ResultPresentation.from(result.content))
      QRUserCanceled -> Unit
      QRMissingPermission -> showMessage(getString(R.string.missing_permission))
      is QRError -> showMessage(
        getString(R.string.scan_error, result.exception.localizedMessage ?: result.exception.javaClass.simpleName)
      )
    }
  }

  private fun showResult(result: ResultPresentation) {
    resultSheet?.dismiss()
    displayedResult = result
    resultSheet = ScanResultSheet(this, result, { launchCamera() }) { displayedResult = null }.also { it.show() }
  }

  private fun showMessage(text: String) {
    Snackbar.make(binding.root, text, Snackbar.LENGTH_LONG).setAction(R.string.ok_action) { }.show()
  }

  companion object {
    const val ACTION_SCAN = "io.github.g00fy2.quickiesample.SCAN"
    const val ACTION_GALLERY = "io.github.g00fy2.quickiesample.GALLERY"
    const val ACTION_HOME = "io.github.g00fy2.quickiesample.HOME"
    const val OPEN_SCANNER = "open_scanner"
    private const val SELECTED_FORMAT = "selected_format"
    private const val RESULT = "displayed_result"
  }
}