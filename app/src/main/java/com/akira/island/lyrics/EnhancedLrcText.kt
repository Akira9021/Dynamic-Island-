package com.akira.island.lyrics

/** Remove only valid Enhanced LRC word clocks; preserve lyric text and ordinary angle brackets. */
internal fun enhancedLrcText(text: String): String = wordClock.replace(text) { match ->
    val seconds = match.groupValues[2].toDoubleOrNull()
    if (seconds != null && seconds >= 0.0 && seconds < 60.0) "" else match.value
}

private val wordClock = Regex("""<(\d{1,3}):(\d{1,2}(?:\.\d{1,3})?)>""")
