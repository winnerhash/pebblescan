package io.github.g00fy2.quickiesample

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.view.LayoutInflater
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import io.github.g00fy2.quickiesample.databinding.SheetScanResultBinding

internal class ScanResultSheet(
  private val activity: AppCompatActivity,
  private val result: ResultPresentation,
  private val onScanAgain: () -> Unit,
  onDismiss: () -> Unit
) {

  private val binding = SheetScanResultBinding.inflate(LayoutInflater.from(activity))
  private val dialog = BottomSheetDialog(activity)

  init {
    dialog.setContentView(binding.root)
    dialog.setOnDismissListener { onDismiss() }
    dialog.behavior.maxHeight = (activity.resources.displayMetrics.heightPixels * 0.9f).toInt()
    dialog.behavior.skipCollapsed = true
    bindContent()
    bindActions()
  }

  fun show() {
    dialog.show()
    dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
  }

  fun dismiss() {
    dialog.dismiss()
  }

  private fun bindContent() {
    binding.resultTitle.setText(
      when {
        result.wifiName != null -> R.string.wifi_result
        result.url != null -> R.string.link_result
        else -> R.string.scan_result
      }
    )
    binding.resultText.text = result.text.ifEmpty { activity.getString(R.string.empty_result) }
    binding.resultOrigin.isVisible = result.hostname != null
    binding.resultOrigin.text = result.hostname
    binding.wifiFields.isVisible = result.wifiName != null
    binding.wifiName.text = result.wifiName
    binding.wifiPassword.text = result.wifiPassword?.ifEmpty { activity.getString(R.string.no_wifi_password) }
    binding.openLinkButton.isVisible = !result.url.isNullOrBlank()
  }

  private fun bindActions() {
    binding.copyButton.setOnClickListener { copy(result.text) }
    binding.copyWifiName.setOnClickListener { copy(result.wifiName.orEmpty()) }
    binding.copyWifiPassword.setOnClickListener { copy(result.wifiPassword.orEmpty()) }
    binding.copyWifiPassword.isEnabled = !result.wifiPassword.isNullOrEmpty()
    binding.shareButton.setOnClickListener {
      val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, result.text)
      open(Intent.createChooser(share, activity.getString(R.string.share_action)))
    }
    binding.openLinkButton.setOnClickListener { result.url?.let { open(Intent(Intent.ACTION_VIEW, it.toUri())) } }
    binding.scanAgainButton.setOnClickListener {
      dismiss()
      onScanAgain()
    }
    binding.closeResultButton.setOnClickListener { dismiss() }
  }

  private fun copy(text: String) {
    activity.getSystemService(ClipboardManager::class.java)
      .setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.scan_result), text))
    Snackbar.make(binding.root, R.string.copied, Snackbar.LENGTH_SHORT).show()
  }

  private fun open(intent: Intent) {
    try {
      activity.startActivity(intent)
    } catch (ignored: ActivityNotFoundException) {
      Snackbar.make(binding.root, R.string.no_browser, Snackbar.LENGTH_LONG).show()
    }
  }
}