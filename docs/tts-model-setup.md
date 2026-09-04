# TTS Model Setup Guide — VoiceLink

## Overview

VoiceLink's offline TTS engine uses **Piper/VITS** models running via **Sherpa-ONNX**.
Each language requires its own model directory with specific files.
Models are NOT bundled in the APK — they must be downloaded and installed separately.

---

## Directory Structure

Models must be placed in the app's internal storage:

```
/data/data/com.talkmitra.offlinevoice/files/models/tts/
├── espeak-ng-data/          ← Shared phonemiser data (required for all Piper models)
│   ├── ...
├── hi/                      ← Hindi
│   ├── model.onnx
│   ├── tokens.txt
│   └── config.json          (optional — voice metadata)
├── en/                      ← English
│   ├── model.onnx
│   └── tokens.txt
├── te/                      ← Telugu
│   ├── model.onnx
│   └── tokens.txt
├── ml/                      ← Malayalam
│   ├── model.onnx
│   └── tokens.txt
├── ta/                      ← Tamil
│   ├── model.onnx
│   └── tokens.txt
├── gu/                      ← Gujarati
│   ├── model.onnx
│   └── tokens.txt
├── mr/                      ← Marathi
│   ├── model.onnx
│   └── tokens.txt
├── kn/                      ← Kannada
│   ├── model.onnx
│   └── tokens.txt
├── or/                      ← Odia
│   ├── model.onnx
│   └── tokens.txt
└── bn/                      ← Bengali
    ├── model.onnx
    └── tokens.txt
```

### Required Files Per Language

| File | Description | Required |
|---|---|---|
| `model.onnx` | The VITS neural network model | ✅ Yes |
| `tokens.txt` | Token vocabulary for the model | ✅ Yes |
| `config.json` | Voice metadata (name, sample rate) | Optional |

### Shared Files

| File | Description | Required |
|---|---|---|
| `espeak-ng-data/` | eSpeak-NG phonemiser data directory | ✅ Yes (shared across all Piper models) |

---

## Step 1: Download eSpeak-NG Data

The eSpeak-NG data directory is shared by all Piper models.
Download once and place at the models root.

```bash
# Download from Sherpa-ONNX releases
wget https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/espeak-ng-data.tar.bz2

# Extract
tar xjf espeak-ng-data.tar.bz2
```

---

## Step 2: Download Language Models

### Hindi (hi)

```bash
# Official Piper model
wget https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/rohan/medium/hi_IN-rohan-medium.onnx
wget https://huggingface.co/rhasspy/piper-voices/resolve/main/hi/hi_IN/rohan/medium/hi_IN-rohan-medium.onnx.json

# Rename for VoiceLink
mv hi_IN-rohan-medium.onnx model.onnx
# Extract tokens from the JSON config or use Sherpa-ONNX conversion scripts
```

### English (en)

```bash
wget https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx
wget https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx.json

mv en_US-lessac-medium.onnx model.onnx
```

### Telugu (te)

```bash
wget https://huggingface.co/rhasspy/piper-voices/resolve/main/te/te_IN/padmavathi/medium/te_IN-padmavathi-medium.onnx
mv te_IN-padmavathi-medium.onnx model.onnx
```

### Malayalam (ml)

```bash
# Community model — check Hugging Face for latest
wget https://huggingface.co/rhasspy/piper-voices/resolve/main/ml/ml_IN/meera/medium/ml_IN-meera-medium.onnx
mv ml_IN-meera-medium.onnx model.onnx
```

### Other Languages

For Tamil, Gujarati, Bengali, Marathi, Kannada, and Odia:
- Search Hugging Face: `https://huggingface.co/models?search=piper+{language}`
- Check Sherpa-ONNX discussions: `https://github.com/k2-fsa/sherpa-onnx/discussions`
- Community models may require Sherpa-ONNX metadata conversion (see Step 3)

---

## Step 3: Convert Piper Models for Sherpa-ONNX (if needed)

Some Piper models from Hugging Face lack the metadata that Sherpa-ONNX expects.
Use the official conversion script:

```bash
# Clone sherpa-onnx
git clone https://github.com/k2-fsa/sherpa-onnx.git
cd sherpa-onnx

# Run the Piper conversion script
python3 scripts/vits/add-model-metadata.py \
    --model /path/to/model.onnx \
    --output /path/to/model-with-metadata.onnx \
    --model-type vits \
    --language hi \
    --voice hi_IN-rohan-medium \
    --sample-rate 22050
```

This adds the required ONNX metadata fields (`model_type`, `language`, `voice`, `sample_rate`).

---

## Step 4: Install Models on Device

### Via ADB (Development)

```bash
# Create the directory structure
adb shell mkdir -p /data/data/com.talkmitra.offlinevoice/files/models/tts/hi
adb shell mkdir -p /data/data/com.talkmitra.offlinevoice/files/models/tts/en
adb shell mkdir -p /data/data/com.talkmitra.offlinevoice/files/models/tts/espeak-ng-data

# Push shared eSpeak data
adb push espeak-ng-data/ /data/data/com.talkmitra.offlinevoice/files/models/tts/

# Push Hindi model
adb push model.onnx /data/data/com.talkmitra.offlinevoice/files/models/tts/hi/
adb push tokens.txt /data/data/com.talkmitra.offlinevoice/files/models/tts/hi/

# Push English model
adb push model.onnx /data/data/com.talkmitra.offlinevoice/files/models/tts/en/
adb push tokens.txt /data/data/com.talkmitra.offlinevoice/files/models/tts/en/
```

### Via File Manager

1. Copy model files to the device
2. Use a file manager with root access to place them in the app's internal storage
3. Alternatively, modify the app to use external storage (requires MANAGE_EXTERNAL_STORAGE permission)

---

## Step 5: Verify Installation

1. Launch VoiceLink
2. Navigate to **Settings → TTS Debug**
3. Tap **Refresh Model Info**
4. Check that installed languages show **"Available"** status
5. Select a language and tap **Speak** to test

### Expected Status

| Status | Meaning |
|---|---|
| ✅ **Loaded** | Model is in RAM and ready for instant synthesis |
| ⚠️ **Available** | Model files exist on disk but not yet loaded |
| ❌ **Not installed** | Model files not found — follow download steps above |

---

## Troubleshooting

### Model not detected

- Verify directory name matches language code exactly (`hi`, `en`, `te`, etc.)
- Check that both `model.onnx` AND `tokens.txt` exist in the language directory
- Check that `espeak-ng-data/` exists at the models root
- Verify file permissions: `adb shell ls -la /data/data/com.talkmitra.offlinevoice/files/models/tts/hi/`

### Native library not found

- Ensure the Sherpa-ONNX AAR is included in `app/libs/` or via Maven
- Check that the AAR supports your device's architecture (arm64-v8a or armeabi-v7a)
- The app falls back to stub mode (faint sine wave) if the native library is missing

### Out of memory

- Reduce `maxCachedModels` in `TTSConfig` (default: 2)
- Use `x_low` or `low` quality models instead of `medium`
- Close other apps to free RAM

### Poor pronunciation

- Verify the correct language model is loaded (not a wrong-language model renamed)
- Try a different quality level (higher = better pronunciation)
- eSpeak-NG may not handle all words correctly — this is a known limitation
