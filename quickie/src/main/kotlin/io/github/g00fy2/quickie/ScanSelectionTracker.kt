package io.github.g00fy2.quickie

import kotlin.math.abs

internal data class ConfirmedCode(val id: Int, val code: ScanCode)
internal data class ScanDecision(val choices: List<ConfirmedCode>, val automatic: ScanCode?)

/** Once multiple positions are seen, selection remains explicit for the rest of the camera session. */
internal class ScanSelectionTracker {

  private class Track(val id: Int, var code: ScanCode, var lastSeen: Long) {
    val confirmation = ScanResultConfirmation()
    var confirmed = false
  }

  var requiresSelection = false
    private set
  private val tracks = mutableListOf<Track>()
  private var nextId = 0
  private var firstSingleAt: Long? = null
  private var singleIdentity: Any? = null

  fun observe(codes: List<ScanCode>, frame: Long, now: Long): ScanDecision {
    tracks.removeAll { track ->
      val changed = codes.any {
        val different = it.hasContent && it.identity != track.code.identity
        different && nearby(it.position, track.code.position)
      }
      now - track.lastSeen > 300L || changed
    }
    if (codes.size > 1) requiresSelection = true
    val used = mutableSetOf<Int>()
    for (code in codes.filter { it.hasContent }) {
      val track = tracks.firstOrNull {
        it.id !in used && it.code.identity == code.identity && nearby(it.code.position, code.position)
      } ?: Track(nextId++, code, now).also { tracks += it }
      track.code = code
      track.lastSeen = now
      track.confirmed = track.confirmation.accept(code.text, code.bytes, frame, now) || track.confirmed
      used += track.id
    }
    val choices = tracks.filter { it.confirmed && now - it.lastSeen <= 200L }.map { ConfirmedCode(it.id, it.code) }
    if (choices.size > 1) requiresSelection = true
    return ScanDecision(choices, automatic(codes, choices, now))
  }

  private fun automatic(codes: List<ScanCode>, choices: List<ConfirmedCode>, now: Long): ScanCode? {
    val only = codes.singleOrNull()?.takeIf { it.hasContent }
    if (requiresSelection || only == null) {
      firstSingleAt = null
      return null
    }
    if (only.identity != singleIdentity || firstSingleAt == null) {
      singleIdentity = only.identity
      firstSingleAt = now
    }
    val settled = now - requireNotNull(firstSingleAt) >= 350L
    return only.takeIf { settled && choices.any { choice -> choice.code.identity == it.identity } }
  }

  private fun nearby(a: CodePosition, b: CodePosition): Boolean =
    abs(a.x - b.x) < 0.08f && abs(a.y - b.y) < 0.08f
}