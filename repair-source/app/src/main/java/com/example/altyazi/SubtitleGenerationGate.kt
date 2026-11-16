package com.example.altyazi

/**
 * An exact subtitle request ID, independent of OCR text.
 * A repeating sentence is a fresh event, and a stopped stream invalidates all jobs.
 * Thread-safe: OCR/capture and translation completion may run on different threads.
 */
class SubtitleGenerationGate {
    private var serial: Long = 0
    private var active: Long = 0

    @Synchronized fun newSubtitle(): Long {
        if (serial == Long.MAX_VALUE) { // avoid a positive generation ever being reused
            serial = 0
        }
        active = ++serial
        return active
    }

    @Synchronized fun invalidate() {
        if (serial == Long.MAX_VALUE) serial = 0
        active = ++serial
    }

    @Synchronized fun isCurrent(id: Long): Boolean = id > 0 && id == active
}
