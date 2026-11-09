package io.github.g00fy2.quickiesample

import android.os.Bundle
import io.github.g00fy2.quickie.content.QRContent
import java.net.IDN
import java.net.URI

internal data class ResultPresentation(
  val text: String,
  val url: String? = null,
  val wifiName: String? = null,
  val wifiPassword: String? = null
) {

  val hostname: String?
    get() = url?.let { value ->
      try {
        val uri = URI(value)
        val host = uri.host ?: uri.rawAuthority?.substringAfterLast('@')?.substringBefore(':')
        host?.let { if (it.startsWith('[')) it else IDN.toASCII(it) }
      } catch (ignored: IllegalArgumentException) {
        null
      } catch (ignored: java.net.URISyntaxException) {
        null
      }
    }

  fun toBundle(): Bundle = Bundle().apply {
    putString("text", text)
    putString("url", url)
    putString("wifiName", wifiName)
    putString("wifiPassword", wifiPassword)
  }

  companion object {
    fun from(content: QRContent): ResultPresentation {
      val text = content.rawValue?.takeIf { it.isNotBlank() }
        ?: content.rawBytes?.joinToString(" ") { "%02X".format(it) }.orEmpty()
      return ResultPresentation(
        text = text,
        url = (content as? QRContent.Url)?.url,
        wifiName = (content as? QRContent.Wifi)?.ssid,
        wifiPassword = (content as? QRContent.Wifi)?.password
      )
    }

    fun fromBundle(bundle: Bundle?): ResultPresentation? = bundle?.getString("text")?.let {
      ResultPresentation(it, bundle.getString("url"), bundle.getString("wifiName"), bundle.getString("wifiPassword"))
    }
  }
}