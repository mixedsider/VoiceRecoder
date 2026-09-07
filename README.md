# VoiceRecoder

Language: [한국어](README.ko.md) | English

VoiceRecoder is an on-device Android voice logging app. It records speech, transcribes it with local STT models, and generates per-recording and daily summaries with local LLM models.

Unlike cloud-first recording apps, VoiceRecoder is designed to solve the full workflow locally. After the required models are downloaded, recording, transcription, and summarization can run without an internet connection, keeping voice data on the device.

The current build focuses on private local inference, configurable acceleration, battery-aware background processing, and safe CPU fallback when GPU or NNAPI acceleration is unavailable.

## Features

- Voice-triggered recording that starts when speech is detected.
- Fixed 60-second recording segments after voice detection.
- Duplicate and overlapping recording prevention.
- Local STT with Whisper TFLite models.
- Local summary generation with LiteRT-LM or llama.cpp GGUF models.
- Detailed settings for model selection, backend selection, download, delete, and runtime status.
- App language selection for system default, English, and Korean.
- Runtime backend fallback reporting.
- Battery-aware processing policies for STT and LLM work.
- Foreground notifications for recording, model downloads, and processing.
- Room database storage for recordings, transcripts, and summaries.

## Current Model Support

### STT

| Model | File | Purpose | Backends |
| --- | --- | --- | --- |
| Whisper Tiny | `whisper-tiny-transcribe-translate.tflite` | Fastest, lower accuracy | CPU, GPU, NNAPI/NPU request |
| Whisper Base | `whisper-base-transcribe-translate.tflite` | Balanced speed and quality | CPU, GPU, NNAPI/NPU request |
| Whisper Small | `whisper-small-transcribe-translate.tflite` | Default accuracy-focused model | CPU, GPU, NNAPI/NPU request |

All STT models share `filters_vocab_multilingual.bin`.

### LLM

| Model | File | Purpose | Backends |
| --- | --- | --- | --- |
| Gemma 4 E2B IT LiteRT-LM | `gemma-4-E2B-it.litertlm` | Default GPU-capable summary model | CPU, GPU |
| Gemma 3 1B IT LiteRT-LM | `gemma3-1b-it-int4.litertlm` | Faster experimental summary model | CPU, GPU |
| Gemma 4 E2B GGUF | `gemma-4-e2b.gguf` | Compatibility fallback | CPU |

LLM NPU is intentionally hidden from settings until an NPU-compatible `.litertlm` model is added.

## Requirements

- Android Studio with JDK 17.
- Android SDK 35.
- Android device or emulator with API 26+.
- `arm64-v8a` target device for native llama.cpp builds.
- Network access for downloading model files.

The project currently does not include a checked-in Gradle wrapper. Use Android Studio Gradle sync or an installed Gradle compatible with the Android Gradle Plugin version in this project.

## Build

Open the project in Android Studio and run the `app` debug configuration.

CLI build:

```powershell
gradle :app:assembleDebug
```

Unit tests:

```powershell
gradle :app:testDebugUnitTest
```

Connected Android tests:

```powershell
gradle :app:connectedDebugAndroidTest
```

## First Run

1. Launch the app.
2. Grant the runtime permissions when prompted.
3. Download the selected STT and LLM models from the first-run dialog or from Settings.
4. Open Settings to choose STT model, LLM model, backend, and battery policy.
5. Tap the record button. VoiceRecoder waits for speech, records a 60-second segment, then queues STT and summary work according to the configured policies.

## Permissions

VoiceRecoder requests required runtime permissions together on startup:

- `RECORD_AUDIO`: required for voice recording.
- `POST_NOTIFICATIONS`: Android 13+ only, recommended for recording/download/processing progress.

Manifest-only permissions are also used for foreground services, network downloads, and boot/power events.

## Settings

Settings exposes:

- App language selection.
- STT backend: `Auto`, `CPU`, `GPU`, `NPU`.
- LLM backend: `Auto`, `CPU`, `GPU`.
- STT model selection.
- LLM model selection.
- Selected model status and file path.
- Requested backend vs effective backend.
- Fallback reason when acceleration is unavailable.
- Download selected models.
- Refresh model status.
- Delete selected model files.
- STT and summary execution policies.

## Execution Policies

| Policy | Behavior |
| --- | --- |
| `Immediate` | Run eligible work as soon as possible. |
| `Battery-aware` | Run when battery is not low. |
| `Charging only` | Run only while charging. |
| `Manual` | Do not automatically enqueue work. |

Defaults:

- STT policy: `Battery-aware`.
- LLM summary policy: `Charging only`.
- STT model: Whisper Small.
- LLM model: Gemma 4 E2B LiteRT-LM.
- LLM backend: CPU by default, with GPU selectable.

## Runtime Fallback

VoiceRecoder separates requested backend from effective backend.

Examples:

- STT `GPU` requested, GPU delegate fails, effective backend becomes `CPU`.
- STT `NPU` requested on unsupported API/device, effective backend falls back to `GPU` or `CPU`.
- LLM `GPU` requested for a GGUF model, effective backend remains `CPU`.

Fallback reasons are shown in Settings and included in processing telemetry.

## Project Structure

```text
app/src/main/kotlin/com/voicelog/
  db/               Room database, DAOs, entities
  inference/        STT and summary engine interfaces/factories
  jni/              llama.cpp JNI bridge
  service/          Recording foreground service and power receiver
  ui/               Main, detail, and settings screens
  util/             Model catalog, preferences, runtime resolution, formatting
  worker/           Model download and processing WorkManager jobs

app/src/main/java/com/voicelog/stt/
  WhisperTfliteEngine.java
  WhisperUtil.java

app/src/main/cpp/
  llama.cpp native integration

docs/
  final-acceleration-plan.md
```

## Verification Status

Latest verified commands:

```powershell
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
gradle :app:connectedDebugAndroidTest
```

Connected test device:

```text
SM-X910 - Android 16
27 connected tests passed
```

## Troubleshooting

- If recording does not start, check microphone permission and selected model readiness.
- If the app says models are missing, open Settings and download the selected model files.
- If summary stays queued, check the summary execution policy and whether the device is charging.
- If GPU or NNAPI fails, check Settings for the fallback reason. The app should continue on CPU.
- If recognition quality is low, use Whisper Small first. Tiny and Base are mainly for speed/balance testing.
- If downloads fail, retry on a stable Wi-Fi connection and keep the app open until the foreground download notification completes.

## Notes

- LiteRT-LM is included as `runtimeOnly` and invoked by reflection because the published artifact currently uses newer Kotlin metadata than this app's Kotlin compiler.
- The app keeps CPU as the stable fallback path for both STT and LLM flows.
- Model files are large and are intentionally not committed to the repository.
