# VoiceLog

언어: 한국어 | [English](README.md)

VoiceLog는 온디바이스 Android 음성 기록 앱입니다. 음성을 녹음하고, 로컬 STT 모델로 텍스트를 변환한 뒤, 로컬 LLM 모델로 녹음별 요약과 일일 요약을 생성합니다.

현재 빌드는 개인정보를 기기 안에 유지하는 로컬 추론, CPU/GPU/NNAPI 가속 설정, 배터리 친화적인 백그라운드 처리, 그리고 가속 실패 시 안전한 CPU fallback에 초점을 맞추고 있습니다.

## 주요 기능

- 사람 목소리가 감지되면 녹음을 시작하는 음성 트리거 녹음.
- 음성 감지 후 60초 고정 길이 녹음 세그먼트 생성.
- 중복 녹음 및 겹치는 녹음 방지.
- Whisper TFLite 모델 기반 로컬 STT.
- LiteRT-LM 또는 llama.cpp GGUF 모델 기반 로컬 요약.
- 모델 선택, backend 선택, 다운로드, 삭제, 런타임 상태를 확인할 수 있는 상세 설정 화면.
- 시스템 기본값, 영어, 한국어를 선택할 수 있는 앱 언어 설정.
- 요청 backend와 실제 적용 backend, fallback 사유 표시.
- STT와 LLM 작업을 위한 배터리 친화 실행 정책.
- 녹음, 모델 다운로드, 처리 진행 상황을 위한 foreground 알림.
- 녹음, transcript, summary를 저장하는 Room 데이터베이스.

## 현재 모델 지원

### STT

| 모델 | 파일 | 용도 | Backend |
| --- | --- | --- | --- |
| Whisper Tiny | `whisper-tiny-transcribe-translate.tflite` | 가장 빠름, 낮은 인식률 | CPU, GPU, NNAPI/NPU 요청 |
| Whisper Base | `whisper-base-transcribe-translate.tflite` | 속도와 품질 균형 | CPU, GPU, NNAPI/NPU 요청 |
| Whisper Small | `whisper-small-transcribe-translate.tflite` | 기본 인식률 우선 모델 | CPU, GPU, NNAPI/NPU 요청 |

모든 STT 모델은 `filters_vocab_multilingual.bin`을 공통으로 사용합니다.

### LLM

| 모델 | 파일 | 용도 | Backend |
| --- | --- | --- | --- |
| Gemma 4 E2B IT LiteRT-LM | `gemma-4-E2B-it.litertlm` | 기본 GPU 지원 요약 모델 | CPU, GPU |
| Gemma 3 1B IT LiteRT-LM | `gemma3-1b-it-int4.litertlm` | 더 빠른 실험적 요약 모델 | CPU, GPU |
| Gemma 4 E2B GGUF | `gemma-4-e2b.gguf` | 호환성 fallback | CPU |

LLM NPU는 NPU 호환 `.litertlm` 모델이 추가될 때까지 설정에서 숨겨져 있습니다.

## 요구 사항

- JDK 17이 포함된 Android Studio.
- Android SDK 35.
- API 26 이상 Android 기기 또는 에뮬레이터.
- native llama.cpp 빌드를 위한 `arm64-v8a` 대상 기기.
- 모델 파일 다운로드를 위한 네트워크 연결.

현재 프로젝트에는 체크인된 Gradle wrapper가 없습니다. Android Studio Gradle Sync를 사용하거나, 프로젝트의 Android Gradle Plugin 버전과 호환되는 Gradle을 사용하세요.

## 빌드

Android Studio에서 프로젝트를 열고 `app` debug configuration을 실행합니다.

CLI 빌드:

```powershell
gradle :app:assembleDebug
```

단위 테스트:

```powershell
gradle :app:testDebugUnitTest
```

연결 기기 테스트:

```powershell
gradle :app:connectedDebugAndroidTest
```

## 첫 실행

1. 앱을 실행합니다.
2. 권한 요청이 뜨면 필요한 런타임 권한을 허용합니다.
3. 첫 실행 다이얼로그 또는 설정 화면에서 선택된 STT/LLM 모델을 다운로드합니다.
4. 설정 화면에서 STT 모델, LLM 모델, backend, 배터리 정책을 선택합니다.
5. 녹음 버튼을 누릅니다. VoiceLog는 음성이 들릴 때까지 기다린 뒤 60초 동안 녹음하고, 설정된 정책에 따라 STT와 요약 작업을 큐에 넣습니다.

## 권한

VoiceLog는 시작 시 필요한 런타임 권한을 한 번에 요청합니다.

- `RECORD_AUDIO`: 음성 녹음에 필요합니다.
- `POST_NOTIFICATIONS`: Android 13 이상에서만 필요하며, 녹음/다운로드/처리 진행 알림에 권장됩니다.

그 외 foreground service, 네트워크 다운로드, 부팅/전원 이벤트 처리를 위한 권한은 manifest에 선언되어 있습니다.

## 설정

설정 화면에서 다음 항목을 관리할 수 있습니다.

- 앱 언어 선택.
- STT backend: `Auto`, `CPU`, `GPU`, `NPU`.
- LLM backend: `Auto`, `CPU`, `GPU`.
- STT 모델 선택.
- LLM 모델 선택.
- 선택된 모델 상태와 파일 경로.
- 요청 backend와 실제 적용 backend.
- 가속 backend를 사용할 수 없을 때의 fallback 사유.
- 선택된 모델 다운로드.
- 모델 상태 새로고침.
- 선택된 모델 파일 삭제.
- STT와 요약 실행 정책.

## 실행 정책

| 정책 | 동작 |
| --- | --- |
| `Immediate` | 가능한 즉시 작업을 실행합니다. |
| `Battery-aware` | 배터리가 부족하지 않을 때 실행합니다. |
| `Charging only` | 충전 중일 때만 실행합니다. |
| `Manual` | 자동으로 작업을 큐에 넣지 않습니다. |

기본값:

- STT 정책: `Battery-aware`.
- LLM 요약 정책: `Charging only`.
- STT 모델: Whisper Small.
- LLM 모델: Gemma 4 E2B LiteRT-LM.
- LLM backend: 기본 CPU, GPU 선택 가능.

## 런타임 Fallback

VoiceLog는 사용자가 요청한 backend와 실제 적용된 backend를 분리해서 관리합니다.

예시:

- STT에서 `GPU`를 요청했지만 GPU delegate 초기화가 실패하면 실제 backend는 `CPU`가 됩니다.
- STT에서 `NPU`를 요청했지만 API 또는 기기가 지원하지 않으면 `GPU` 또는 `CPU`로 fallback합니다.
- GGUF 모델에서 LLM `GPU`를 요청하면 실제 backend는 `CPU`로 유지됩니다.

Fallback 사유는 설정 화면에 표시되며 처리 telemetry에도 포함됩니다.

## 프로젝트 구조

```text
app/src/main/kotlin/com/voicelog/
  db/               Room 데이터베이스, DAO, entity
  inference/        STT 및 요약 엔진 인터페이스/팩토리
  jni/              llama.cpp JNI 브리지
  service/          녹음 foreground service와 전원 receiver
  ui/               메인, 상세, 설정 화면
  util/             모델 카탈로그, preferences, runtime resolution, formatting
  worker/           모델 다운로드 및 처리 WorkManager 작업

app/src/main/java/com/voicelog/stt/
  WhisperTfliteEngine.java
  WhisperUtil.java

app/src/main/cpp/
  llama.cpp native integration

docs/
  final-acceleration-plan.md
```

## 검증 상태

최근 검증한 명령:

```powershell
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
gradle :app:connectedDebugAndroidTest
```

연결 테스트 기기:

```text
SM-X910 - Android 16
connected test 27개 통과
```

## 문제 해결

- 녹음이 시작되지 않으면 마이크 권한과 선택된 모델 준비 상태를 확인하세요.
- 모델이 없다는 안내가 나오면 설정 화면에서 선택된 모델 파일을 다운로드하세요.
- 요약이 대기 상태로 남아 있으면 요약 실행 정책과 충전 상태를 확인하세요.
- GPU 또는 NNAPI가 실패하면 설정 화면의 fallback 사유를 확인하세요. 앱은 CPU로 계속 동작해야 합니다.
- 인식률이 낮으면 먼저 Whisper Small을 사용하세요. Tiny와 Base는 주로 속도/균형 테스트용입니다.
- 다운로드가 실패하면 안정적인 Wi-Fi에서 다시 시도하고 foreground 다운로드 알림이 완료될 때까지 앱을 열어 두세요.

## 참고

- LiteRT-LM은 `runtimeOnly`로 포함되고 reflection으로 호출됩니다. 현재 공개 artifact가 앱의 Kotlin compiler보다 최신 Kotlin metadata로 빌드되어 있기 때문입니다.
- 앱은 STT와 LLM 모두에서 CPU를 안정 fallback 경로로 유지합니다.
- 모델 파일은 용량이 크기 때문에 repository에 포함하지 않습니다.
