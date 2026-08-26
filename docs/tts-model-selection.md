# TTS Model Selection — VoiceLink Part 6

## Selected Runtime: Sherpa-ONNX + Piper/VITS

### Why Sherpa-ONNX

| Criterion | Sherpa-ONNX + Piper | Coqui TTS | Android System TTS | eSpeak-NG |
|---|---|---|---|---|
| **Fully offline** | ✅ | ✅ | ⚠️ Device-dependent | ✅ |
| **Open source** | Apache 2.0 / MIT | MPL 2.0 | ❌ Proprietary | GPL-3.0 |
| **Android AAR** | ✅ Native arm64/armv7 | ❌ Manual ONNX export | ✅ System API | ⚠️ Requires native build |
| **Model size** | 15–60 MB per lang | 50–100 MB per lang | 0 (system) | < 5 MB total |
| **RAM usage** | ~50–150 MB | ~100–200 MB | Minimal | Minimal |
| **Indian languages** | Hindi, Telugu, Malayalam, English confirmed | Many (server-only) | Inconsistent | All 10 (robotic) |
| **Voice quality** | Good (neural VITS) | Good (neural VITS) | Varies | Poor (formant) |
| **Quantisation** | INT8 supported | Limited | N/A | N/A |
| **Streaming** | Sentence-level | No | Yes | Yes |
| **Inference speed** | RTF < 0.5 on mid-range | RTF ~0.3–1.0 | Real-time | Real-time |

### Decision

**Sherpa-ONNX + Piper/VITS** was selected because:

1. Same runtime ecosystem as the existing Vosk/Sherpa STT pipeline
2. Proven Android deployment via native AARs (arm64-v8a, armeabi-v7a)
3. ONNX Runtime Mobile optimised for low/mid-range devices
4. INT8 quantised models reduce size ~50% with minimal quality loss
5. Piper VITS architecture is lightweight (15–60 MB per language)
6. eSpeak-NG phonemiser is bundled and supports all 10 Indic scripts
7. Apache 2.0 + MIT licence — fully open source

---

## Per-Language Model Availability

> **IMPORTANT:** This table reflects research as of August 2026.
> Model availability changes as the community trains new voices.

| Language | Code | Piper Model Available | Source | Model Name | Status |
|---|---|---|---|---|---|
| **Hindi** | `hi` | ✅ Yes | Official Piper | `hi_IN-rohan-medium` | **Verified** |
| **English** | `en` | ✅ Yes | Official Piper | `en_US-lessac-medium` | **Verified** |
| **Telugu** | `te` | ✅ Yes | Official Piper | `te_IN-padmavathi-medium` | **Verified** |
| **Malayalam** | `ml` | ✅ Yes | Community | `ml_IN-meera-medium` | **Verified** |
| **Tamil** | `ta` | ⚠️ Community | Hugging Face | `ta_IN-community` | **Experimental** |
| **Gujarati** | `gu` | ⚠️ Community | Hugging Face | `gu_IN-community` | **Experimental** |
| **Bengali** | `bn` | ⚠️ Community | Hugging Face | `bn_IN-community` | **Experimental** |
| **Marathi** | `mr` | ❌ Not yet | — | — | **Pending** |
| **Kannada** | `kn` | ❌ Not yet | — | — | **Pending** |
| **Odia** | `or` | ❌ Not yet | — | — | **Pending** |

### Notes on Missing Languages

- **Marathi, Kannada, Odia**: eSpeak-NG (bundled with Piper) supports the scripts, but no neural VITS voice models exist yet
- The `TTSEngine` abstraction allows adding new models by simply placing files in the correct directory — no code changes required
- Community training efforts using AI4Bharat IndicTTS datasets may produce models in the future
- eSpeak-NG can serve as a low-quality fallback for these languages (robotic but intelligible)

---

## Model Specifications

### Piper VITS Architecture

- **Architecture**: VITS (Variational Inference with adversarial learning for end-to-end Text-to-Speech)
- **Format**: ONNX (Open Neural Network Exchange)
- **Phonemiser**: eSpeak-NG (grapheme-to-phoneme conversion)
- **Output**: PCM audio, 16-bit mono
- **Sample rate**: 22,050 Hz (standard for Piper models)

### Model Sizes (Approximate)

| Quality | Size | RTF (mid-range) | Notes |
|---|---|---|---|
| `x_low` | ~15 MB | ~0.1 | Very fast, lower quality |
| `low` | ~20 MB | ~0.15 | Fast, acceptable quality |
| `medium` | ~40 MB | ~0.3 | Recommended balance |
| `high` | ~60 MB | ~0.5 | Best quality, slower |

### Quantisation Options

| Level | Size Reduction | Quality Impact | Recommended |
|---|---|---|---|
| FP32 | Baseline | None | For testing |
| FP16 | ~50% | Negligible | ❌ Limited Android support |
| INT8 | ~50% | Minimal | ✅ **Production** |

---

## CPU / RAM Requirements

### Minimum Requirements

| Resource | Requirement |
|---|---|
| CPU | ARMv7 or ARM64 |
| RAM | 512 MB free (for 1 model) |
| Storage | 15–60 MB per language model |
| Android API | 23+ (Android 6.0+) |

### Expected Performance (mid-range device, e.g. Snapdragon 665)

| Metric | Value |
|---|---|
| Model load time | 1–3 seconds |
| RTF (medium model) | 0.2–0.5 |
| RAM per loaded model | 50–150 MB |
| Time to first audio | < 2 seconds |

---

## Streaming Support

Piper/VITS does not natively support token-level streaming.
The VoiceLink TTS engine implements **sentence-level streaming**:

```
"I need help. There is a fire."
       ↓
Sentence 1: "I need help."  → Synthesise → Play
Sentence 2: "There is a fire." → Synthesise → Play
```

This reduces perceived latency for multi-sentence messages.

---

## Known Limitations

1. Not all 10 Indian languages have neural TTS models
2. Piper VITS does not support token-level streaming
3. Voice quality varies between languages and model qualities
4. No voice cloning or speaker adaptation
5. eSpeak-NG phonemisation may produce incorrect pronunciation for some words
6. Model files must be downloaded separately (not bundled in APK)
7. INT8 quantisation tools require Python/desktop for conversion
