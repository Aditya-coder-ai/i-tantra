# Offline Voice Input — Part 1 & Part 2 (Audio Capture + VAD)

This Android project implements offline voice input and real-time Voice Activity Detection (VAD). It captures 16 kHz, mono, signed 16-bit PCM through Android's `AudioRecord` and segments incoming audio into discrete spoken utterances without any cloud or network dependency.

## Features

- **Part 1 (Capture Engine)**: Zero-allocation, high-priority background audio thread streaming 20 ms PCM chunks (320 samples @ 16 kHz).
- **Part 2 (VAD Engine)**:
  - Adaptive dynamic noise floor estimation.
  - Short-time RMS Log Energy & Zero-Crossing Rate (ZCR) feature extraction.
  - Pre-roll circular ring buffer (300 ms) to prevent clipping initial consonants/phonemes.
  - Hangover smoothing (400+ ms) to prevent choppy syllable breaks.
  - Automatic `SpeechSegment` emission with PCM buffer and metadata.

## Run

1. Open this folder in **Android Studio**.
2. Run the `app` configuration on a physical Android phone or emulator with microphone support.
3. Grant microphone permission when prompted.
4. Tap **Start recording** and speak. The UI will show:
   - Live microphone activity level.
   - Real-time VAD state (Silence vs Voice Detected / Speaking).
   - Captured speech utterances list with sample counts and durations.

## Downstream STT / ASR Integration (Part 3)

Consume captured speech segments via `VoiceActivityDetector.Listener.onSpeechEnd(segment)`.
Each `SpeechSegment` provides:
- `segment.samples`: `ShortArray` of 16-bit PCM.
- `segment.toByteArray()`: Little-endian `ByteArray` ready for on-device models (Whisper TFLite/ONNX, Sherpa-ONNX, Vosk).
- `segment.durationMs`: Utterance duration in milliseconds.

## VoiceLink UI prototype

The app launch activity now presents a standalone VoiceLink frontend using local mock state only.
It does not call the audio/VAD implementation, STT/TTS, networking, Bluetooth, or AI models.

Flow: `Splash → Onboarding → Home`; Home leads to Conversation, Language, Connection/Pairing,
Emergency, and Settings. Settings also provides System Status and Help/About. Hold the primary
PTT control to preview Listening → Converting speech → Sending → Delivered.

The Compose frontend is organised as follows:

- `ui/theme`: colours and typography.
- `ui/components`: shared PTT, status, waveform, cards, device cards, and message bubbles.
- `ui/screens`: each navigable application screen.
- `ui/mock`: preview-only data and state actions, to be replaced later by feature adapters.

The visual system uses an 8 dp spacing rhythm, 16–20 dp rounded surfaces, high-contrast status
labels, and large touch targets for emergency use. Connect future VoiceRecorder, VAD, STT/TTS,
and network implementations by replacing `MockVoiceLinkController`; UI components remain
independent of those services.
