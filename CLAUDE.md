# CLAUDE.md — FoldWindowArranger 프로젝트 규칙

## 이 프로젝트가 하는 일

갤럭시 Z Fold 7(내부 화면 2184×1968px)에서 **플로팅 아이콘 원터치**로 현재 앱을
**영상 종횡비에 정확히 맞는 분할 창**으로 재배치하고, 남는 공간(검은 띠)을
**위 또는 아래 한쪽으로 몰아주는** 안드로이드 앱.

기존 해법과의 차이:
- 화면 캡처(MediaProjection) 방식이 아니므로 **DRM 콘텐츠에서도 동작**한다
- 삼성 기본 「앱별 화면 비율」과 달리 **상시 고정이 아니라 필요할 때만** 적용된다
- 앱별 사전 설정 없이 **모든 앱에 동작**한다

---

## 역할 분담 (반드시 지킬 것)

이 세션의 최상위 모델은 **Advisor**다. Advisor는 판단에 집중하고 구현 노동은 위임한다.

### Advisor가 직접 하는 일
- 요구사항 분석, 작업 분해, 설계 결정
- Worker에게 줄 작업 브리프 작성
- 결과 검증, 최종 승인, 사용자 보고
- `PROGRESS.md` 갱신

### Worker에게 위임하는 일
- 코드 작성/수정, 테스트 작성 등 구현 작업 **전부**
- `Agent` 도구로 위임하고 `model`은 작업 난이도에 맞는 하위 모델을 지정한다
- 서로 독립적인 작업은 **한 메시지에 다중 `Agent` 호출**로 병렬 위임한다

### 브리프 작성 기준
- 이미 파악한 컨텍스트를 담아 Worker가 재탐색하지 않게 한다
- 근거, 프로젝트 컨벤션, 알려진 함정, **완료 기준(통과해야 할 테스트)** 을 반드시 포함한다
- 파일 경로는 절대 경로 또는 리포지토리 루트 기준 상대 경로로 명시한다

---

## 커뮤니케이션

- 사고와 작업 과정은 **영어**로, 사용자에게 보고하는 최종 답변은 **한국어**로 한다
- 코드는 아티팩트 또는 파일 경로로 제시한다
- 코드 주석은 한국어를 허용한다 (도메인 용어가 한국어라 가독성이 높다)

---

## 아키텍처 규칙

### 레이어링

```
domain/     순수 Kotlin. Android SDK 의존 금지. 100% JVM 단위 테스트 가능해야 한다.
            SplitPlanner, LetterboxDetector, 모델
platform/   Android SDK 래퍼. Bitmap→도메인 모델 변환, WindowMetrics 조회 등
probe/      Phase 0 진단 전용. 나중에 제거 가능하도록 격리
ui/         Compose. ViewModel은 domain만 알고 platform은 인터페이스로 받는다
service/    AccessibilityService, 오버레이 Foreground Service
data/       프로파일 영속화 (DataStore)
```

**철칙: `domain/` 에 `import android.*` 이 들어가면 리뷰 거부.**
계산 로직을 순수하게 유지하는 것이 이 프로젝트의 유일한 회귀 방어선이다.
실기기 없이 검증 가능한 표면적을 최대한 넓게 유지한다.

### 의존성

Jetpack Compose, Kotlin Coroutines/Flow, DataStore, kotlinx-serialization, androidx.window, Shizuku API.
`domain/` 은 kotlin-stdlib 외의 의존성을 갖지 않는다.

**DI 프레임워크는 쓰지 않는다.** 각 컴포넌트가 필요한 협력자를 직접 생성한다
(예: `by lazy { ProfileStore(this) }`). 이 규모에서는 수동 생성이 옳은 선택이며,
Hilt 도입은 이득 없는 부채다 — 새 코드도 이 관례를 따를 것.

---

## 알려진 함정 (반복 실수 방지)

### 1. 고정 지연(`postDelayed`) 절대 금지 — ADR-2
분할 진입 → 파트너 앱 실행 → 디바이더 이동은 기기 상태마다 타이밍이 다르다.
`delay(600)` 같은 코드는 재현성을 파괴한다.
**반드시 상태 머신 + 조건 폴링**으로 구현한다. 각 단계는 "성공 조건"을 확인하고 다음으로 넘어간다.
폴링은 타임아웃과 최대 시도 횟수를 갖고, 실패 시 명시적 실패 상태로 떨어진다.

### 2. 좌표 하드코딩 금지
디바이더 위치는 `AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER` 창의
`getBoundsInScreen()` 으로 얻는다. One UI 버전/언어/DPI에 따라 값이 달라진다.
휴리스틱 폴백을 쓰더라도 화면 크기 대비 비율로 계산한다.

### 3. `takeScreenshot()` 레이트 리밋
`AccessibilityService.takeScreenshot()` 은 대략 초당 1회로 제한된다.
폐루프 보정 시 연속 호출하면 조용히 실패한다. 실패 콜백을 반드시 처리하고 백오프를 건다.

### 4. HardwareBuffer 누수
`ScreenshotResult.hardwareBuffer` 는 사용 후 `close()` 해야 한다.
`Bitmap.wrapHardwareBuffer()` 로 감싼 뒤 `copy(ARGB_8888, false)` 로 복사하고 즉시 닫는다.

### 5. Android 16 적응형 동작
API 36 타겟 시 sw≥600dp 화면에서 `screenOrientation`, `maxAspectRatio`,
`resizeableActivity` 가 무시된다. 이 프로젝트에는 **유리한** 변화지만,
자체 Activity가 임의 크기로 리사이즈될 수 있다는 뜻이므로 고정 레이아웃 가정 금지.

### 6. 접근성 서비스 재시작
앱을 업데이트하면 접근성 서비스가 꺼진다. 개발 중 "왜 안 되지" 의 90%가 이것이다.
설치 스크립트에 안내 문구를 넣는다.

### 7. 검증된 상수를 근거 없이 바꾸지 말 것
`config/window_profiles.json` 과 `docs/DEVICE_FACTS.md` 의 수치는 실기기 측정값이다.
바꾸려면 새 측정 근거를 `docs/DEVICE_FACTS.md` 에 함께 기록한다.

---

## 상태 관리

- `TASK.md` — Phase별 작업 정의와 완료 기준. **읽기 전용에 가깝게** 유지
- `PROGRESS.md` — 현재 상태. 매 작업 완료 시 Advisor가 갱신
- `docs/DEVICE_FACTS.md` — 실기기에서 측정된 사실. Phase 0 리포트가 여기로 들어간다
- `config/window_profiles.json` — 앱별 프로파일 SSOT

---

## 완료 기준 (Definition of Done)

작업 하나가 끝났다고 말하려면:
1. `./gradlew :app:testDebugUnitTest` 통과
2. `./gradlew :app:assembleDebug` 통과
3. `./gradlew :app:lintDebug` 통과 (W0 에서 게이트화. baseline 에 없는 **신규** 경고만 잡힌다)
4. `domain/` 변경 시 대응 테스트 추가/갱신
5. `PROGRESS.md` 갱신
6. 실기기 검증이 필요한 항목은 `docs/DEVICE_FACTS.md` 에 "미검증" 으로 명시

빌드가 깨진 상태로 "완료" 보고 금지.

---

## 검증 명령

**전제 — `JAVA_HOME`:** `gradle.properties` 의 `org.gradle.java.home` 은 Gradle **데몬**의 JDK 만
지정한다. `gradlew` **런처 스크립트 자체**는 별도로 `JAVA_HOME` 을 요구하므로, 이 머신의 Git Bash 에서는
프리픽스가 없으면 `JAVA_HOME is not set` 로 실패하거나 조용히 실패한다(installDebug 가 구버전 APK 를
남겨 40분을 태운 실측 사례 있음). 아래 모든 gradlew 명령에 다음을 붙인다:

```bash
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
```

```bash
./gradlew :app:testDebugUnitTest        # 도메인 단위 테스트
./gradlew :app:assembleDebug            # 빌드
./gradlew :app:lintDebug                # lint 게이트 (W0 도입, lint-baseline.xml 기준선)
./gradlew :app:updateLintBaseline       # 기준선 갱신 — 근거 없이 실행 금지
./gradlew :app:installDebug             # 설치
adb shell am start -n dev.dj.foldwindow/.probe.ProbeActivity
adb shell settings put secure enabled_accessibility_services \
  dev.dj.foldwindow/dev.dj.foldwindow.probe.ProbeAccessibilityService   # 개발 편의용
adb pull /sdcard/Android/data/dev.dj.foldwindow/files/probe_report.md ./docs/
```
