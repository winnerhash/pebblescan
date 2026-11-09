package io.github.g00fy2.quickiesample

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.core.content.pm.ShortcutManagerCompat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(org.robolectric.RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w393dp-h800dp")
class AppFlowsTest {

  private var controller: ActivityController<MainActivity>? = null

  @After
  fun tearDown() {
    controller?.pause()?.stop()?.destroy()
  }

  @Test
  fun normalLaunchOpensCameraAndPublishesBothShortcuts() {
    val activity = create(Intent(Intent.ACTION_MAIN))
    assertTrue(shadowOf(activity).nextStartedActivity.component?.className.orEmpty().endsWith("QRScannerActivity"))
    assertEquals(setOf("scan", "gallery"), ShortcutManagerCompat.getDynamicShortcuts(activity).map { it.id }.toSet())
  }

  @Test
  fun coldLaunchAcceptsTheFirstScannerResultWithoutReopeningFromHome() {
    val activity = create(Intent(Intent.ACTION_MAIN))
    val launched = shadowOf(activity).nextStartedActivity
    val result = Intent().putExtra("quickie-value", "first scan works")
    shadowOf(activity).receiveResult(launched, Activity.RESULT_OK, result)
    val dialog = ShadowDialog.getLatestDialog()
    assertEquals("first scan works", dialog.findViewById<TextView>(R.id.result_text).text.toString())
    assertNull(shadowOf(activity).nextStartedActivity)
  }

  @Test
  fun galleryShortcutSkipsCamera() {
    val activity = create(Intent(MainActivity.ACTION_GALLERY))
    val launched = shadowOf(activity).nextStartedActivity
    assertTrue(launched.component?.className.orEmpty().endsWith("QRImageScannerActivity"))
    assertNull(launched.data)
  }

  @Test
  fun imageShareForwardsTheSameUriAndReadGrant() {
    val uri = Uri.parse("content://screenshots.test/image/42")
    val share = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    val activity = create(share)
    val launched = shadowOf(activity).nextStartedActivity
    assertEquals(uri, launched.data)
    assertEquals(uri, launched.clipData?.getItemAt(0)?.uri)
    assertTrue(launched.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertTrue(launched.component?.className.orEmpty().endsWith("QRImageScannerActivity"))
  }

  @Test
  fun runningAppAcceptsClipDataSharesWithoutOpeningTheCamera() {
    val activity = create(Intent(MainActivity.ACTION_HOME))
    assertNull(shadowOf(activity).nextStartedActivity)
    val uri = Uri.parse("content://photos.test/shared/7")
    controller?.newIntent(
      Intent(Intent.ACTION_SEND).setType("image/jpeg").apply { clipData = ClipData.newRawUri("", uri) }
    )
    assertEquals(uri, shadowOf(activity).nextStartedActivity.data)
  }

  @Test
  fun malformedShareDoesNotFallBackToOpeningCamera() {
    val activity = create(Intent(Intent.ACTION_SEND).setType("image/png"))
    assertNull(shadowOf(activity).nextStartedActivity)
  }

  @Test
  fun restoredHomeDoesNotRelaunchScannerBehindPendingActivityResults() {
    val activity = create(Intent(MainActivity.ACTION_HOME))
    val state = Bundle()
    controller?.saveInstanceState(state)?.pause()?.stop()?.destroy()
    assertTrue(activity.isDestroyed)
    controller = Robolectric.buildActivity(MainActivity::class.java, Intent(Intent.ACTION_MAIN))
      .create(state).start().resume().visible()
    assertNull(shadowOf(requireNotNull(controller).get()).nextStartedActivity)
  }

  @Test
  fun wifiFieldsAreSeparateAndCopyThePasswordWithoutOpeningAnything() {
    val activity = create(Intent(MainActivity.ACTION_HOME))
    val result = ResultPresentation("WIFI:...", wifiName = "Home", wifiPassword = "secret")
    val sheet = ScanResultSheet(activity, result, { }, { })
    sheet.show()
    val dialog = ShadowDialog.getLatestDialog()
    assertEquals("Home", dialog.findViewById<TextView>(R.id.wifi_name).text.toString())
    assertEquals("secret", dialog.findViewById<TextView>(R.id.wifi_password).text.toString())
    dialog.findViewById<View>(R.id.copy_wifi_password).performClick()
    val clipboard = activity.getSystemService(ClipboardManager::class.java)
    assertEquals("secret", clipboard.primaryClip?.getItemAt(0)?.text.toString())
    assertEquals(View.GONE, dialog.findViewById<View>(R.id.open_link_button).visibility)
    assertNull(shadowOf(activity).nextStartedActivity)
    sheet.dismiss()
  }

  @Test
  fun longResultsRemainScrollableWithVisibleActions() {
    val activity = create(Intent(MainActivity.ACTION_HOME))
    var scanningAgain = false
    val result = ResultPresentation("A long result.\n".repeat(200))
    val sheet = ScanResultSheet(activity, result, { scanningAgain = true }, { })
    sheet.show()
    shadowOf(Looper.getMainLooper()).idle()
    val decor = requireNotNull(ShadowDialog.getLatestDialog().window?.decorView)
    decor.measure(
      View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
      View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY)
    )
    decor.layout(0, 0, 393, 800)
    val text = decor.findViewById<View>(R.id.result_text)
    assertTrue((text?.measuredHeight ?: 0) > 0)
    val scroll = requireNotNull(decor.findViewById<View>(R.id.result_scroll))
    assertTrue(scroll.measuredHeight > 0)
    assertTrue(
      "Text height ${text?.measuredHeight}, scroll height ${scroll.measuredHeight}",
      requireNotNull(text).measuredHeight > scroll.measuredHeight
    )
    val scanAgain = requireNotNull(decor.findViewById<View>(R.id.scan_again_button))
    val position = IntArray(2)
    scanAgain.getLocationOnScreen(position)
    assertTrue(position[1] + scanAgain.height <= 800)
    assertFalse(scanningAgain)
    scanAgain.performClick()
    assertTrue(scanningAgain)
  }

  private fun create(intent: Intent): MainActivity {
    controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
    return requireNotNull(controller).get()
  }
}