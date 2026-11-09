package io.github.g00fy2.quickiesample

import android.animation.ValueAnimator
import android.os.Build
import android.view.animation.DecelerateInterpolator
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.g00fy2.quickie.config.BarcodeFormat
import io.github.g00fy2.quickiesample.databinding.ActivityMainBinding

internal class HomeScreen(
  private val activity: AppCompatActivity,
  private val binding: ActivityMainBinding,
  private val scan: (BarcodeFormat, Boolean) -> Unit,
  private val gallery: () -> Unit
) {

  var selectedFormat: BarcodeFormat = BarcodeFormat.FORMAT_ALL_FORMATS

  fun bind(animate: Boolean) {
    WindowCompat.setDecorFitsSystemWindows(activity.window, false)
    WindowCompat.getInsetsController(activity.window, binding.root).apply {
      isAppearanceLightStatusBars = true
      isAppearanceLightNavigationBars = true
    }
    ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
      val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
      view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
      WindowInsetsCompat.CONSUMED
    }
    bindFormats()
    binding.qrScannerButton.setOnClickListener { scan(BarcodeFormat.FORMAT_ALL_FORMATS, false) }
    binding.customScannerButton.setOnClickListener { scan(selectedFormat, true) }
    binding.galleryButton.setOnClickListener { gallery() }
    binding.addTileButton.setOnClickListener { AppShortcuts.requestTile(activity) }
    binding.licensesButton.setOnClickListener { showLicenses() }
    if (animate) binding.root.post { animateHome() }
    AppShortcuts.install(activity)
  }

  private fun bindFormats() {
    val labels = BarcodeFormat.entries.map {
      when (it) {
        BarcodeFormat.FORMAT_ALL_FORMATS -> activity.getString(R.string.format_all)
        BarcodeFormat.FORMAT_QR_CODE -> activity.getString(R.string.format_qr)
        else -> it.name.removePrefix("FORMAT_").replace('_', ' ')
      }
    }
    binding.barcodeFormatsAutoCompleteTextView.apply {
      setAdapter(ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, labels))
      setText(labels[selectedFormat.ordinal], false)
      setOnItemClickListener { _, _, position, _ -> selectedFormat = BarcodeFormat.entries[position] }
    }
  }

  private fun animateHome() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ValueAnimator.areAnimatorsEnabled()) return
    listOf(binding.homeHeader, binding.homeHero, binding.qrScannerButton, binding.secondaryActions)
      .forEachIndexed { index, view ->
        view.alpha = 0f
        view.translationY = 18 * activity.resources.displayMetrics.density
        view.animate().alpha(1f).translationY(0f).setStartDelay(index * 55L).setDuration(380L)
          .setInterpolator(DecelerateInterpolator(1.5f)).start()
      }
  }

  private fun showLicenses() {
    val text = activity.assets.open("licenses/NOTICE.txt").bufferedReader().use { it.readText() }
    MaterialAlertDialogBuilder(activity).setTitle(R.string.licenses_title).setMessage(text)
      .setPositiveButton(R.string.ok_action, null).show()
  }
}