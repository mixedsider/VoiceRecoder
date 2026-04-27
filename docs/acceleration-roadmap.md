# VoiceLog Acceleration Roadmap

## Current State

- STT runs through `WhisperTfliteEngine` with a TFLite `Interpreter` on CPU.
- STT preprocessing still does FFT and mel generation in Java before model invocation.
- Daily and per-recording summary generation runs through `llama.cpp` JNI on CPU.
- VAD uses ONNX Runtime without an accelerator execution provider.

## Phase 1 Completed

- Added engine interfaces under `app/src/main/kotlin/com/voicelog/inference/`.
- Kept the current CPU implementations behind those interfaces:
  - `WhisperTranscriptionEngine`
  - `LlamaSummaryEngine`
- Added worker-level telemetry in `ProcessingWorker` for:
  - WAV loading time
  - Whisper warm-up time
  - Whisper preprocessing, inference, decode, total time
  - LLM prompt token count
  - LLM generated token count
  - LLM time-to-first-token
  - LLM prompt/decode timing and derived tokens/sec
- Extended JNI metrics collection in `llama_jni.cpp` using `llama_perf_context` and `llama_perf_sampler`.

## Phase 2

- Add a second summary engine implementation backed by LiteRT-LM.
- Keep `llama.cpp` as the fallback path until LiteRT-LM is stable on target devices.
- Compare CPU and GPU backends using the same summary prompts and save benchmark logs per device.

## Phase 3

- Prototype STT acceleration separately from LLM acceleration.
- Measure whether the bottleneck is:
  - Java preprocessing
  - TFLite invocation
  - Post-decode
- If preprocessing dominates, move mel generation to native code or a runtime with compiled preprocessing.
- If inference dominates, test GPU delegate and NNAPI on supported devices.

## Phase 4

- Add device-aware backend selection:
  - CPU fallback for all devices
  - GPU default on supported devices
  - NPU opt-in on API 31+ devices after validation
- Only promote a backend after checking:
  - startup cost
  - steady-state latency
  - thermal stability
  - output quality regression

## Implementation Notes

- Google AI Edge Gallery currently uses `.task` and `.litertlm` model formats for accelerated LLM paths.
- The current VoiceLog summary model is `.gguf`, so LiteRT-LM requires a parallel model pipeline instead of a drop-in swap.
- The current app `minSdk` is 26, while the latest Google Gallery Android app uses `minSdk` 31. Plan NPU rollout accordingly.
