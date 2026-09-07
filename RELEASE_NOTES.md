# VoiceRecoder v1.0.0

VoiceRecoder v1.0.0 is the first release focused on private, fully on-device voice logging.

## Highlights

- Records voice and processes STT and LLM summaries locally after models are downloaded.
- Keeps voice data on the device and supports offline operation for recording, transcription, and summarization.
- Adds detailed inference settings for CPU, GPU, and NNAPI-backed STT fallback.
- Adds model management with download, retry, delete, and model status flows.
- Adds battery-aware processing policies for background transcription and summarization.
- Adds local LLM summary acceleration support with safe CPU fallback.
- Adds Korean and English app language support.
- Adds README documentation in both English and Korean.

## Notes

- Some models must be downloaded before offline processing can run.
- LLM NPU selection is intentionally hidden until compatible public LiteRT-LM NPU models are available.
- If a debug build is already installed, Android may block release APK installation because debug and release signatures differ. Uninstall the debug build first if you want to test the release APK on the same device.

## Verification

- Release APK signing verified with APK Signature Scheme v2.
- Release package generation succeeded after increasing Gradle packaging memory.
- Direct release update install was blocked on the connected device because an existing `com.voicelog` install used a different signature.
