# VoiceLog Final Acceleration Plan

## Priorities

### P0: Detailed Settings and Model Management

- Add a dedicated settings experience for:
  - STT backend selection
  - LLM backend selection
  - model selection
  - model download, delete, and validation
  - actual runtime status and fallback reporting
- Expose `Auto`, `CPU`, `GPU`, and `NPU` as user-selectable backend targets.
- Keep runtime fallback explicit.
  - Example: `Selected NPU, running on GPU because the device/backend is unsupported`
- Move model management into settings so the app no longer depends only on the first-run download dialog.

### P1: Battery Optimization

- Split execution policy between STT and LLM.
- Extend policy levels beyond `always` and `charging only`:
  - immediate
  - battery-aware
  - charging only
  - manual
- Batch summary work when possible.
- Prefer low-power defaults for LLM summarization.
- Promote a backend only after checking thermal stability and battery drain.

### P2: STT Speed Optimization

- Keep the current Whisper TFLite path as the baseline.
- Add delegate experiments in this order:
  - GPU delegate
  - NNAPI
- Continue tracking:
  - preprocessing time
  - inference time
  - decode time
- If preprocessing dominates, move mel/FFT work out of Java.

### P3: LLM Speed Optimization

- Keep the current `llama.cpp` CPU path as the compatibility baseline.
- Add lighter and heavier model choices under a shared summary-engine interface.
- Reduce prompt cost by:
  - limiting transcript length
  - reusing per-recording summaries for daily summaries
  - capping generation length by policy

### P4: LLM NPU Optimization

- Implement NPU as a second LLM path instead of forcing it into the current GGUF pipeline.
- Use LiteRT-LM or another Android-native accelerated runtime behind a new `SummaryEngine`.
- Gate NPU behind:
  - API level
  - model format support
  - backend availability
  - device validation results

## Product Direction

- Default STT behavior:
  - backend: `Auto`
  - model: current Whisper Small TFLite
  - policy: battery-aware
- Default LLM behavior:
  - backend: `CPU`
  - model: Gemma 4 E2B LiteRT-LM GPU-capable model
  - policy: charging only
- NPU should launch as an advanced or experimental option first.

## Implementation Sequence

1. Build the settings and model-management foundation.
2. Add capability detection and runtime fallback reporting.
3. Expand battery policy and scheduling controls.
4. Optimize STT with delegate experiments and preprocessing work.
5. Add multiple LLM model choices on the current CPU path.
6. Add a GPU-capable LiteRT-LM summary engine.
7. Add an NPU-capable LiteRT-LM summary engine.

## Current Next Step

- Completed in this implementation pass:
  - detailed settings for backend/model selection, status, download/retry, refresh, and selected model deletion
  - expanded model catalog metadata with engine kind, supported backends, min API, experimental flag, and LiteRT-LM Gemma3 1B fast model
  - runtime backend resolver with requested/effective backend, available backend list, engine kind, and fallback reason
  - battery policies: immediate, battery-aware, charging only, and manual
  - WorkManager constraints plus Worker-side battery/policy checks
  - Whisper TFLite GPU delegate and NNAPI requests with CPU fallback and telemetry
  - Gemma 4 E2B LiteRT-LM GPU-capable model catalog entry using the Gallery allowlist download URL pattern
  - llama.cpp summary defaults tuned to 2048 context and capped generation length
  - prompt limiting for per-recording and daily summaries, with daily summaries preferring per-recording summaries
  - LiteRT-LM summary engine path isolated behind `SummaryEngineFactory`
  - STT model catalog expanded to Whisper Tiny, Base, and Small TFLite choices, each sharing the multilingual vocab auxiliary download
- Implementation note:
  - `litertlm-android:0.10.0` is packaged as `runtimeOnly` and invoked by reflection because the artifact is compiled with newer Kotlin metadata than the app's Kotlin 2.0.21 compiler supports.
  - The public Gemma 4 LiteRT-LM model added here supports CPU/GPU. LLM NPU selection is hidden from settings until an NPU-compatible `.litertlm` model is added.
  - Whisper Small remains the default accuracy-focused STT model; Tiny and Base are exposed for speed/balance testing from settings.
- Verified:
  - `:app:assembleDebug`
  - `:app:testDebugUnitTest`
  - `:app:connectedDebugAndroidTest` on `SM-X910 - 16` with 27 tests
