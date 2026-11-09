package io.github.g00fy2.quickie

import android.graphics.Bitmap
import android.media.Image
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.ImageProxyTransformFactory
import com.google.android.gms.tasks.TaskExecutors
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

@androidx.annotation.OptIn(TransformExperimental::class)
internal class QRCodeAnalyzer(
  private val barcodeFormats: IntArray,
  private val analysisExecutor: Executor,
  private val onFrame: (DetectedScanFrame, () -> Bitmap) -> Unit,
  private val onFailure: (Exception) -> Unit,
  private val onPassCompleted: (Boolean) -> Unit,
  private val onPotentialCodes: ((List<Barcode>, Int, Int) -> Unit)? = null
) : ImageAnalysis.Analyzer, AutoCloseable {

  private val schedule = BarcodeScanSchedule(barcodeFormats)
  private val scannerDelegate = lazy { createScanner(schedule.primaryFormats) }
  private val otherScannerDelegate = lazy { createScanner(barcodeFormats) }
  private val linearEnabled = LinearBarcodeDecoder.supports(barcodeFormats)
  private val rescue = CameraBarcodeRescue(barcodeFormats)
  private val imageFactory = BarcodeImageFactory()
  private val transformFactory = ImageProxyTransformFactory().apply { isUsingRotationDegrees = true }
  private var frameNumber = 0L

  @Volatile
  private var multipleSeen = false

  @Volatile
  private var closed = false

  @Volatile
  var isPaused = false

  @Volatile
  private var failureOccurred = false

  @Volatile
  private var failureTimestamp = 0L

  private fun createScanner(formats: IntArray): BarcodeScanner? {
    val builder = if (formats.size > 1) {
      BarcodeScannerOptions.Builder().setBarcodeFormats(formats.first(), *formats.drop(1).toIntArray())
    } else {
      BarcodeScannerOptions.Builder().setBarcodeFormats(formats.firstOrNull() ?: Barcode.FORMAT_QR_CODE)
    }
    return try {
      BarcodeScanning.getClient(builder.enableAllPotentialBarcodes().build())
    } catch (exception: Exception) {
      onFailure(exception)
      null
    }
  }

  @ExperimentalGetImage
  override fun analyze(imageProxy: ImageProxy) {
    val mediaImage = imageProxy.image
    val backingOff = failureOccurred && System.currentTimeMillis() - failureTimestamp < 1000L
    val inactive = closed || isPaused
    if (inactive || mediaImage == null || backingOff) {
      imageProxy.close()
      return
    }
    failureOccurred = false
    val scanner = if (multipleSeen || schedule.useOtherFormats()) otherScannerDelegate.value else scannerDelegate.value
    if (closed) scanner?.close()
    if (scanner == null || closed) imageProxy.close() else processImage(scanner, mediaImage, imageProxy, ++frameNumber)
  }

  private fun processImage(scanner: BarcodeScanner, mediaImage: Image, proxy: ImageProxy, number: Long) {
    try {
      val image = imageFactory.create(mediaImage, proxy)
      val rotated = proxy.imageInfo.rotationDegrees % 180 != 0
      val modelWidth = if (rotated) image.height else image.width
      val modelHeight = if (rotated) image.width else image.height
      val frame = DetectedScanFrame(
        emptyList(),
        transformFactory.getOutputTransform(proxy),
        if (rotated) proxy.height else proxy.width,
        if (rotated) proxy.width else proxy.height,
        number,
        proxy.imageInfo.rotationDegrees
      )
      scanner.process(image).addOnCompleteListener { task ->
        if (task.isSuccessful) {
          handleCodes(proxy, task.result, modelWidth, modelHeight, frame)
        } else {
          failureOccurred = true
          failureTimestamp = System.currentTimeMillis()
          finishFrame(proxy)
          if (!closed) onFailure(task.exception ?: IllegalStateException("Barcode detection failed"))
        }
      }
    } catch (exception: Exception) {
      proxy.close()
      if (!closed) onFailure(exception)
    }
  }

  private fun handleCodes(proxy: ImageProxy, codes: List<Barcode>, width: Int, height: Int, frame: DetectedScanFrame) {
    val model = codes.mapNotNull { code ->
      code.boundingBox?.let { box ->
        val valid = BarcodeResultPolicy.isDecoded(code.format, code.rawValue, code.rawBytes)
        ScanCode(
          CodePosition(
            box.exactCenterX() / width,
            box.exactCenterY() / height,
            box.width().toFloat() / width,
            box.height().toFloat() / height
          ),
          code.rawValue.takeIf { valid },
          code.rawBytes.takeIf { valid },
          code.takeIf { valid }
        )
      }
    }
    val observations = ScanCodeMerger.merge(model, emptyList())
    if (!closed && observations.none { it.hasContent }) onPotentialCodes?.invoke(codes, width, height)
    val complete = observations.isNotEmpty() && observations.all { it.hasContent }
    if (closed || !linearEnabled || complete) {
      dispatch(frame.copy(codes = observations), proxy)
    } else {
      supplement(proxy, codes, width, height, frame.copy(codes = observations))
    }
  }

  private fun supplement(proxy: ImageProxy, codes: List<Barcode>, width: Int, height: Int, frame: DetectedScanFrame) {
    try {
      analysisExecutor.execute {
        var results = emptyList<ScanCode>()
        try {
          if (!closed) results = rescue.decode(proxy, codes, width, height, thorough = frame.number % 4 == 1L)
        } catch (ignored: RuntimeException) {
          // A failed supplement leaves valid model observations available for explicit selection.
        } finally {
          dispatch(frame.copy(codes = ScanCodeMerger.merge(frame.codes, results)), proxy)
        }
      }
    } catch (ignored: RejectedExecutionException) {
      finishFrame(proxy)
    }
  }

  private fun dispatch(frame: DetectedScanFrame, proxy: ImageProxy) {
    if (frame.codes.size > 1) multipleSeen = true
    TaskExecutors.MAIN_THREAD.execute {
      try {
        if (!closed && !isPaused) onFrame(frame) { proxy.toBitmap() }
      } finally {
        finishFrame(proxy)
      }
    }
  }

  private fun finishFrame(image: ImageProxy) {
    image.close()
    TaskExecutors.MAIN_THREAD.execute { if (!closed) onPassCompleted(failureOccurred) }
  }

  override fun close() {
    closed = true
    if (scannerDelegate.isInitialized()) scannerDelegate.value?.close()
    if (otherScannerDelegate.isInitialized()) otherScannerDelegate.value?.close()
  }
}