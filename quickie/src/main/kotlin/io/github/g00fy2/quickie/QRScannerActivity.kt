package io.github.g00fy2.quickie

import android.Manifest.permission.CAMERA
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import com.google.mlkit.vision.barcode.common.Barcode
import io.github.g00fy2.quickie.config.ParcelableScannerConfig
import io.github.g00fy2.quickie.databinding.QuickieScannerActivityBinding
import io.github.g00fy2.quickie.utils.MlKitErrorHandler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class QRScannerActivity : AppCompatActivity() {

  private lateinit var binding: QuickieScannerActivityBinding
  private lateinit var analysisExecutor: ExecutorService
  private var barcodeFormats = intArrayOf(Barcode.FORMAT_QR_CODE)
  private var hapticFeedback = true
  private var showTorchToggle = false
  private var showCloseButton = false
  private var useFrontCamera = false
  private var showGalleryButton = false
  private var autoZoom = false
  private var galleryActive = false
  private var resultDelivered = false
  private var activeAnalyzer: QRCodeAnalyzer? = null
  private var activeAnalysis: ImageAnalysis? = null
  private val scanGallery = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
    galleryActive = false
    if (result.resultCode != RESULT_CANCELED) {
      resultDelivered = true
      setResult(result.resultCode, result.data)
      finish()
    }
  }

  @Suppress("unused")
  internal var errorDialog: Dialog? = null
    set(value) {
      field = value
      value?.show()
      value?.setOnKeyListener { dialog, keyCode, _ ->
        @SuppressLint("GestureBackNavigation")
        if (keyCode == KeyEvent.KEYCODE_BACK) {
          finish()
          dialog.dismiss()
          true
        } else {
          false
        }
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val appThemeLayoutInflater = applicationInfo.theme.let { appThemeRes ->
      if (appThemeRes != 0) layoutInflater.cloneInContext(ContextThemeWrapper(this, appThemeRes)) else layoutInflater
    }
    binding = QuickieScannerActivityBinding.inflate(appThemeLayoutInflater)
    setContentView(binding.root)
    binding.previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

    setupEdgeToEdgeUI()
    applyScannerConfig()
    galleryActive = savedInstanceState?.getBoolean(GALLERY_ACTIVE) == true
    binding.overlayView.setGalleryVisibilityAndOnClick(showGalleryButton) {
      galleryActive = true
      scanGallery.launch(Intent(this, QRImageScannerActivity::class.java))
    }

    analysisExecutor = Executors.newSingleThreadExecutor()

    requestCameraPermissionIfMissing { granted ->
      if (granted) {
        binding.previewView.doOnLayout { if (!isFinishing && !isDestroyed) startCamera() }
      } else {
        setResult(RESULT_MISSING_PERMISSION, null)
        finish()
      }
    }
  }

  override fun onDestroy() {
    activeAnalysis?.clearAnalyzer()
    activeAnalyzer?.close()
    super.onDestroy()
    analysisExecutor.shutdown()
  }

  override fun onSaveInstanceState(outState: Bundle) {
    outState.putBoolean(GALLERY_ACTIVE, galleryActive)
    super.onSaveInstanceState(outState)
  }

  private fun startCamera() {
    val cameraProviderFuture = try {
      ProcessCameraProvider.getInstance(this)
    } catch (e: Exception) {
      onFailure(e)
      return
    }

    cameraProviderFuture.addListener({
      if (isDestroyed || isFinishing) return@addListener
      val cameraProvider = try {
        cameraProviderFuture.get()
      } catch (e: Exception) {
        onFailure(e)
        return@addListener
      }

      val preview = Preview.Builder().setResolutionSelector(
        ResolutionSelector.Builder()
          .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build()
      ).build().also { it.surfaceProvider = binding.previewView.surfaceProvider }
      val imageAnalysis = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setResolutionSelector(
          ResolutionSelector.Builder().setResolutionStrategy(
            ResolutionStrategy(
              if (LinearBarcodeDecoder.supports(barcodeFormats)) Size(3840, 2160) else Size(1920, 1080),
              ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
            )
          ).setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY).build()
        )
        .build()

      cameraProvider.unbindAll()

      val cameraSelector =
        if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA

      try {
        val group = UseCaseGroup.Builder().addUseCase(preview).addUseCase(imageAnalysis)
        binding.previewView.viewPort?.let { group.setViewPort(it) }
        val camera = cameraProvider.bindToLifecycle(this, cameraSelector, group.build())
        attachAnalyzer(camera, imageAnalysis)
        binding.overlayView.visibility = View.VISIBLE
        binding.overlayView.setCloseVisibilityAndOnClick(showCloseButton) { finish() }
        if (showTorchToggle && camera.cameraInfo.hasFlashUnit()) {
          binding.overlayView.setTorchVisibilityAndOnClick(true) { camera.cameraControl.enableTorch(it) }
          camera.cameraInfo.torchState.observe(this) { binding.overlayView.setTorchState(it == TorchState.ON) }
        } else {
          binding.overlayView.setTorchVisibilityAndOnClick(false)
        }
      } catch (e: Exception) {
        binding.overlayView.visibility = View.INVISIBLE
        onFailure(e)
      }
    }, ContextCompat.getMainExecutor(this))
  }

  private fun attachAnalyzer(camera: Camera, analysis: ImageAnalysis) {
    val focus = CameraFocusController(camera, binding.previewView)
    binding.previewView.previewStreamState.observe(this) {
      if (it == PreviewView.StreamState.STREAMING) focus.focus()
    }
    binding.overlayView.setFocusAction { x, y -> focus.focus(x, y) }
    val zoom = if (autoZoom) CameraZoomController(this, camera, binding.overlayView) { focus.focus() } else null
    val selection = CameraCodeSelection(
      binding.previewView,
      binding.overlayView,
      binding.frozenPreview,
      onPauseChanged = { activeAnalyzer?.isPaused = it },
      onSelected = { deliverResult(it.toResultIntent()) }
    )
    activeAnalysis = analysis
    activeAnalyzer = QRCodeAnalyzer(
      barcodeFormats = barcodeFormats,
      analysisExecutor = analysisExecutor,
      onFrame = { frame, capture ->
        val leaving = isFinishing || isDestroyed
        if (!leaving && !galleryActive && !resultDelivered) selection.onFrame(frame, capture)
      },
      onFailure = { onFailure(it) },
      onPassCompleted = { onPassCompleted(it) },
      onPotentialCodes = { codes, width, height ->
        if (!selection.requiresSelection && codes.size <= 1) zoom?.onPotentialCodes(codes, width, height)
      }
    ).also { analysis.setAnalyzer(analysisExecutor, it) }
  }

  private fun deliverResult(data: Intent) {
    val leaving = isFinishing || isDestroyed
    if (leaving || galleryActive || resultDelivered) return
    val hasContent = BarcodeResultPolicy.hasContent(
      data.getStringExtra(EXTRA_RESULT_VALUE),
      data.getByteArrayExtra(EXTRA_RESULT_BYTES)
    )
    if (!hasContent) return
    activeAnalysis?.clearAnalyzer()
    resultDelivered = true
    binding.overlayView.isHighlighted = true
    if (hapticFeedback) {
      @Suppress("DEPRECATION")
      val flags = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING or HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
      binding.overlayView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, flags)
    }
    setResult(RESULT_OK, data)
    finish()
  }

  private fun onFailure(exception: Exception) {
    runOnUiThread {
      if (!isDestroyed && !isFinishing) {
        if (galleryActive || resultDelivered) return@runOnUiThread
        setResult(RESULT_ERROR, Intent().putExtra(EXTRA_RESULT_EXCEPTION, exception))
        if (!MlKitErrorHandler.isResolvableError(this, exception)) finish()
      }
    }
  }

  private fun onPassCompleted(failureOccurred: Boolean) {
    if (!isFinishing) binding.overlayView.isLoading = failureOccurred
  }

  private fun setupEdgeToEdgeUI() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    ViewCompat.setOnApplyWindowInsetsListener(binding.overlayView) { v, insets ->
      insets.getInsets(WindowInsetsCompat.Type.systemBars()).let { v.setPadding(it.left, it.top, it.right, it.bottom) }
      WindowInsetsCompat.CONSUMED
    }
  }

  private fun applyScannerConfig() {
    intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_CONFIG, ParcelableScannerConfig::class.java) }?.let {
      barcodeFormats = it.formats
      binding.overlayView.setCustomText(it.stringRes)
      binding.overlayView.setCustomIcon(it.drawableRes)
      binding.overlayView.setHorizontalFrameRatio(it.horizontalFrameRatio)
      hapticFeedback = it.hapticFeedback
      showTorchToggle = it.showTorchToggle
      useFrontCamera = it.useFrontCamera
      showCloseButton = it.showCloseButton
      showGalleryButton = it.showGalleryButton
      autoZoom = it.autoZoom

      if (it.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }

  private fun requestCameraPermissionIfMissing(onResult: ((Boolean) -> Unit)) {
    if (ContextCompat.checkSelfPermission(this, CAMERA) == PackageManager.PERMISSION_GRANTED) {
      onResult(true)
    } else {
      registerForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(it) }.launch(CAMERA)
    }
  }

  companion object {
    private const val GALLERY_ACTIVE = "gallery_active"
    const val EXTRA_CONFIG = "quickie-config"
    const val EXTRA_RESULT_BYTES = "quickie-bytes"
    const val EXTRA_RESULT_VALUE = "quickie-value"
    const val EXTRA_RESULT_TYPE = "quickie-type"
    const val EXTRA_RESULT_PARCELABLE = "quickie-parcelable"
    const val EXTRA_RESULT_EXCEPTION = "quickie-exception"
    const val RESULT_MISSING_PERMISSION = RESULT_FIRST_USER + 1
    const val RESULT_ERROR = RESULT_FIRST_USER + 2
  }
}