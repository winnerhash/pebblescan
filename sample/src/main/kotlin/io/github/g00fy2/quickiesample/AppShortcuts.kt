package io.github.g00fy2.quickiesample

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.g00fy2.quickiesample.quicksettingstile.QuickieTileService

internal object AppShortcuts {

  fun install(activity: AppCompatActivity) {
    val shortcuts = listOf(
      Triple("scan", R.string.shortcut_scan, MainActivity.ACTION_SCAN),
      Triple("gallery", R.string.gallery_card_title, MainActivity.ACTION_GALLERY)
    ).map { (id, label, action) ->
      val icon = if (id == "scan") R.mipmap.ic_launcher else R.drawable.ic_photo_playful
      ShortcutInfoCompat.Builder(activity, id)
        .setShortLabel(activity.getString(label))
        .setIcon(IconCompat.createWithResource(activity, icon))
        .setIntent(
          Intent(activity, MainActivity::class.java).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        .build()
    }
    ShortcutManagerCompat.setDynamicShortcuts(activity, shortcuts)
  }

  fun requestTile(activity: AppCompatActivity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      activity.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(activity, QuickieTileService::class.java),
        activity.getString(R.string.shortcut_scan),
        Icon.createWithResource(activity, R.drawable.ic_qs_qrcode),
        activity.mainExecutor
      ) { result ->
        if (result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED &&
          result != StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
        ) {
          showTileHelp(activity)
        }
      }
    } else {
      showTileHelp(activity)
    }
  }

  private fun showTileHelp(activity: AppCompatActivity) {
    if (activity.isFinishing || activity.isDestroyed) return
    MaterialAlertDialogBuilder(activity).setTitle(R.string.add_tile)
      .setMessage(R.string.add_tile_help).setPositiveButton(R.string.ok_action, null).show()
  }
}