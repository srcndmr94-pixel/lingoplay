package com.example.altyazi

/** A new confirmed source must not carry the previous subtitle's translation. */
object CaptionSwitch10427 {
    fun shouldClear(previousSource: String, nextSource: String, sameOcrUtterance: Boolean): Boolean =
        previousSource.isNotBlank() && nextSource.isNotBlank() &&
            previousSource != nextSource && !sameOcrUtterance
}
