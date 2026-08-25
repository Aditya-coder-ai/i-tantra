package com.itantra.offlinevoice.audio

/** State of a single [AudioRecorder] instance. */
enum class RecordingState {
    IDLE,
    RECORDING,
    PAUSED,
    STOPPED,
    ERROR
}
