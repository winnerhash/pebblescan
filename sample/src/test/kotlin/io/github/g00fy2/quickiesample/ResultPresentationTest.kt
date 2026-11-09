package io.github.g00fy2.quickiesample

import io.github.g00fy2.quickie.content.QRContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ResultPresentationTest {

  @Test
  fun domainPreviewShowsActualHostAndKeepsOriginalUrl() {
    val original = "https://trusted.example@destination.example:8443/path?next=https%3A%2F%2Fother.example"
    val result = ResultPresentation.from(QRContent.Url(null, original, "", original))
    assertEquals("destination.example", result.hostname)
    assertEquals(original, result.text)
    assertEquals(original, result.url)
  }

  @Test
  fun internationalDomainsAreDisplayedWithoutAmbiguousUnicodeCharacters() {
    assertEquals("xn--fsqu00a.xn--0zwm56d", ResultPresentation("", "https://例子.测试/path").hostname)
    assertNull(ResultPresentation("", "not a valid url").hostname)
  }

  @Test
  fun rotationStatePreservesStructuredWifiAndBinaryContents() {
    val wifi = ResultPresentation.from(QRContent.Wifi(null, "WIFI:...", 2, "p;a:ss", "Network; name"))
    assertEquals(wifi, ResultPresentation.fromBundle(wifi.toBundle()))
    val binary = ResultPresentation.from(QRContent.Plain(byteArrayOf(0, 1, -1), null))
    assertEquals("00 01 FF", binary.text)
    assertNull(binary.url)
  }

  @Test
  fun emptyTextCannotHideARealBinaryPayload() {
    assertEquals("00 01 FF", ResultPresentation.from(QRContent.Plain(byteArrayOf(0, 1, -1), "")).text)
  }
}