# CODE_REVIEW — 2026-07-29 전체 코드 리뷰

> 이 문서는 **리뷰 기록 전용**이다. `PROGRESS.md`·`TASK.md`·`DEVICE_FACTS.md` 와 독립적으로 유지한다.
> 여기 적힌 항목은 **아직 아무것도 수정하지 않았다** — 착수 시 각 항목에 처리 결과를 덧붙인다.

**리뷰 범위:** `app/src/main` 전체 Kotlin 55파일(13,039줄) + 매니페스트 + 리소스 + Gradle 설정 + `config/window_profiles.json` + 테스트 15파일
**리뷰 방식:** 전문(全文) 정독 + 빌드/테스트/lint 실행 검증
**검증 커맨드 결과:**

| 커맨드 | 결과 |
|---|---|
| `:app:testDebugUnitTest` | **PASS — 282 tests, 0 failures** |
| `:app:assembleDebug` | **PASS** |
| `:app:lintDebug` | **FAIL — 3 errors, 27 warnings** (DoD 에 미포함된 게이트) |
| `domain/` 순수성 (`import android.*` 0건) | **PASS** — kotlin.math 외 의존 없음 |
| 네트워크·텔레메트리 송신 코드 | **0건** (INTERNET 권한 없음) |

---

## 0. 총평

**품질 수준은 개인 프로젝트 기준으로 매우 높다.** 특히 다음 세 가지는 유지할 것:

1. **`domain/` 순수성이 실제로 지켜지고 있다.** 13개 도메인 파일 전부 `kotlin.math` 외 의존이 없고, 대응 테스트가 전부 존재한다(282 PASS). "실기기 없이 검증 가능한 표면적을 넓게" 라는 설계 의도가 코드에 그대로 구현돼 있다.
2. **실측 근거의 코드 내 보존.** 상수마다 측정 일자·표본·반증 이력이 KDoc 에 남아 있어 "왜 이 값인가"를 되묻을 필요가 없다. 이 프로젝트의 가장 큰 자산이다.
3. **ADR-2(고정 지연 금지) 준수.** 전 경로가 조건 폴링 + 데드라인 + 명시적 실패다. 예외 허용 지점(레이트리밋 백오프, 제스처 duration, UI 애니메이션)마다 왜 예외인지 주석이 붙어 있다.

**반면 구조적 부채가 한 곳에 집중돼 있다.** `ArrangerAccessibilityService.kt` 2,023줄 / 세션 가변 필드 ~25개가 전체 위험의 대부분이다(§4 M1).

**즉시 손봐야 할 것은 보안 2건(S1, S4)과 기능 3건(F2, F3, F5)이다.** 나머지는 계획적으로 처리해도 된다.

---

## 1. 기능 · 버그 · 에지케이스

### 🔴 F2. 배치 계산 기하가 하드코딩 — 세로/커버 화면에서 오배치

**심각도: 높음 (사용자가 기기를 세로로 들고 버블을 탭하면 즉시 재현)**

`ArrangerAccessibilityService.kt:198` 의 `geometry` 는 **고정 상수**다:

```kotlin
private val geometry = WindowGeometry.foldSevenLandscape()   // 2184×1968 고정
```

그런데 같은 파일의 다른 모든 판정은 **실시간 화면**을 쓴다 (`screenRect()`, `ArrangerAccessibilityService.kt:1805`).
`SplitPlanner.plan(geometry, ...)` 은 4곳(`:1052`, `:1430`, `:1554`, `:1697`)에서 전부 이 고정값으로
`dividerCenterY` 를 산출한다. 즉:

- **세로(1968×2184)에서 트리거** → `idealVideoH = 2184/aspect` 로 계산되는데 실제 페인 폭은 1968 → 목표 Y 가 틀림
- **커버 화면(1080×2520)에서 트리거** → 전혀 다른 기하로 계산
- `DividerDragger.drag` 는 `targetY` 를 실제 `screen` 으로 클램프만 할 뿐(`DividerDragger.kt:45`) 오차를 잡지 못함

**코드 전체를 grep 한 결과 orientation / display 정합성을 확인하는 가드가 한 곳도 없다.**
`EntryContext.screen` KDoc 은 "landscape 2184×1968" 을 전제로 서술돼 있지만 강제하는 코드는 없다.

**수정 방향(택1):**
- (a) `beginSession` 진입 시 `screenRect()` 와 `geometry` 의 폭·높이가 허용 오차 안인지 확인하고, 불일치면 명시적 실패 + 토스트 (조용한 실패 금지 원칙에 부합, 최소 변경)
- (b) `screenRect()` 로부터 `WindowGeometry` 를 생성하고 `dividerThickness`/`minPaneHeight` 만 실측 상수로 유지 (근본 수정, 커버 화면/타 기기 확장 대비)

---

### 🔴 F3. `ShizukuShell.exec` 의 타임아웃이 실제로 동작하지 않음

**심각도: 높음 (팝업 모드 영구 불능으로 귀결)**

```kotlin
// ShizukuShell.kt:100-110
suspend fun exec(command: String, timeoutMs: Long): String? = withContext(Dispatchers.IO) {
    withTimeoutOrNull(timeoutMs) {
        ...
        runCatching { binder?.run(command) }   // ← 블로킹 바인더 트랜잭션
    }
}
```

`binder.run(command)` 는 **suspend 함수가 아닌 블로킹 바인더 호출**이다. `withTimeoutOrNull` 은
중단점(suspension point)에서만 취소를 적용할 수 있으므로, 이 호출이 걸리면 타임아웃이 발동해도
코루틴은 반환되지 않는다. 원격 쪽(`ShellExecUserService.kt:31`)은 `process.waitFor()` 를
**타임아웃 없이** 호출한다.

**귀결:** `am` 명령 하나가 걸리면 → `Dispatchers.IO` 스레드 1개 무기한 점유 →
`performStartPopup` 이 반환되지 않음 → `popupInFlight` 가 `true` 로 고착 →
**프로세스 재시작 전까지 팝업 모드가 영구 불능**. `SHELL_EXEC_TIMEOUT_MS = 5_000L`
(`ArrangerAccessibilityService.kt:1997`)은 사실상 장식이다.

**수정 방향:** `ShellExecUserService.run` 안에서 `process.waitFor(timeout, TimeUnit.MILLISECONDS)`
+ 실패 시 `destroyForcibly()`. 타임아웃 값은 AIDL 인자로 전달.

---

### 🔴 F5. `ShizukuShell.ensureBound` 가 영구 고착될 수 있음

**심각도: 중 (F3 과 같은 결과, 다른 경로)**

```kotlin
// ShizukuShell.kt:113-130
private suspend fun ensureBound(): Boolean {
    if (binder != null) return true
    if (!binding) {                       // ← binding==true 면 재바인드 자체를 건너뜀
        binding = true
        runCatching { Shizuku.bindUserService(...) }
            .onFailure { binding = false }  // 던진 경우에만 리셋
    }
    val bound = withTimeoutOrNull(BIND_TIMEOUT_MS) {
        while (binder == null && binding) { delay(BIND_POLL_INTERVAL_MS) }
        binder != null
    }
    return bound ?: false                 // ← 타임아웃 경로에서 binding 리셋 없음
}
```

`bindUserService` 가 예외 없이 반환됐지만 `onServiceConnected` 콜백이 끝내 오지 않으면:
`binding` 이 `true` 로 남는다 → 이후 모든 `ensureBound()` 가 `if (!binding)` 에서 걸려
**재바인드를 시도하지 않고** 3초 폴링만 반복한 뒤 실패한다. 복구 경로가 없다
(`onServiceDisconnected` 는 연결이 성립한 적이 있어야 온다).

**수정 방향:** `val bound = withTimeoutOrNull(...)` 직후 `if (bound != true) binding = false`.

---

### 🟠 F4. `ShellExecUserService.run` 파이프 데드락 가능

**심각도: 중 (현재 사용하는 `am` 명령에서는 출력이 작아 미발현)**

```kotlin
// ShellExecUserService.kt:23-30
BufferedReader(...process.inputStream).useLines { ... }   // stdout 을 EOF 까지 소진
BufferedReader(...process.errorStream).useLines { ... }   // 그 다음에야 stderr
```

자식 프로세스가 stdout 을 닫기 전에 stderr 파이프 버퍼(보통 64KB)를 채우면 자식이 블록되고,
부모는 stdout EOF 를 기다리며 블록된다 — 전형적 상호 대기다. F3 의 무기한 `waitFor()` 와
결합하면 복구 불가 상태가 된다.

**수정 방향:** `ProcessBuilder(argv).redirectErrorStream(true).start()` 로 단일 스트림화.

---

### 🟠 F1. `SplitPlanner.plan` 이 `IllegalArgumentException` 을 던질 수 있음

**심각도: 중 (현재 하드코딩 기하로는 도달 불가, 그러나 F2 를 (b)로 고치면 즉시 도달 가능)**

```kotlin
// SplitPlanner.kt:118-122
val lowerBound = geom.minPaneHeight
val upperBound = (geom.allocatableHeight - geom.minPaneHeight).coerceAtLeast(lowerBound)
val videoH = idealVideoH.coerceIn(lowerBound, upperBound)
val panelH = geom.allocatableHeight - videoH        // ← 음수가 될 수 있다
```

`minPaneHeight > allocatableHeight` 이면 `videoH = minPaneHeight`, `panelH < 0` 이 되고
`IntRect` 의 `require(bottom >= top)`(`SplitPlanner.kt:20`)에서 예외가 난다.

재현 예: `usableHeight=100, dividerThickness=10, minPaneHeight=95`
→ `allocatable=90`, `videoH=95`, `panelH=-5`
→ TOP 분기 `panelRect = IntRect(l, top+105, r, top+100)` → **throw**.

`WindowGeometry.init` 은 각 필드의 부호만 검사할 뿐 **필드 간 불변식이 없다**(`SplitPlanner.kt:40-44`).
이 파일이 "유일한 회귀 방어선"이라는 위상을 생각하면 방어를 넣는 것이 맞다.

**수정 방향:** `WindowGeometry.init` 에
`require(allocatableHeight >= 2 * minPaneHeight) { ... }` 추가 + 대응 테스트.

---

### 🟠 F6. `dispatch()` 재진입 — 현재는 무해하지만 구조적 지뢰

**심각도: 중 (지금은 발현 안 함 / 한 줄만 바뀌면 발현)**

`scope` 가 `Dispatchers.Main.immediate` 이므로(`ArrangerAccessibilityService.kt:91`),
`scope.launch { }` 블록은 **첫 중단점 전까지 호출자 스택에서 동기 실행**된다.
`handleQuerySplitState`(`:1324-1330`)는 중단점 없이 곧바로 `dispatch()` 를 부른다:

```
dispatch(Start)
  ├ machineState = CheckingSplit
  ├ executeEffect(QuerySplitState)
  │    └ dispatch(SplitStateResult)      ← 중첩 dispatch, machineState 를 EnteringSplit 로 변경
  └ val terminal = machineState          ← 바깥 dispatch 가 "내가 만든 상태"가 아닌 값을 읽는다
```

**현재 안전한 이유는 우연에 가깝다:** (1) 리듀서의 모든 `Transition` 이 effect 를 0~1개만 낸다,
(2) 중첩 dispatch 가 터미널에 닿으면 `cleanupSession()` 이 `machineState` 를 `Idle` 로 되돌려
바깥의 터미널 검사가 자연히 거짓이 된다.

**둘 중 하나만 깨져도 이중 `reportTerminal`(토스트 2회) 또는 정리된 세션 위에서의 effect 실행이 된다.**
전이 하나에 effect 를 2개 넣거나, `dispatch` 말미에 로직을 추가하는 순간이다.

**수정 방향:** effect 를 큐에 넣고 최상위 dispatch 만 드레인하거나,
`executeEffect` 를 `scope.launch(start = CoroutineStart.UNDISPATCHED 아님)` 으로 강제 디스패치.
`ArrangeStateMachine` 자체는 순수 리듀서로 문제없다 — 구동부만의 문제다.

---

### 🟡 F7. `captureScreen()` 이 취소 시 Bitmap 을 회수하지 않음

```kotlin
// ArrangerAccessibilityService.kt:1874-1883
val bmp = try { Bitmap.wrapHardwareBuffer(...)?.copy(ARGB_8888, false) }
          finally { buffer.close() }        // 함정 #4 대응 — 정상
if (cont.isActive) cont.resume(bmp)         // ← isActive==false 면 bmp 가 미아가 된다
```

`HardwareBuffer` 는 제대로 닫지만, 코루틴이 이미 취소된 경우 복사본 `Bitmap` 은 어느 호출자의
`try/finally recycle()` 에도 도달하지 못한다. `invokeOnCancellation` 도 없다.
전면 스크린샷 1장 = 약 17MB(2184×1968×4B) 라 GC 압박이 무시할 수준은 아니다.
`ProbeAccessibilityService.kt:222-249` 도 동일 패턴.

**수정 방향:** `if (cont.isActive) cont.resume(bmp) else bmp?.recycle()`.

---

### 🟡 F8. 리듀서: `Idle` + `Cancel` → `Failed(CANCELLED)`

```kotlin
// ArrangeStateMachine.kt:163-165
if (event is ArrangeEvent.Cancel && state !is Done && state !is Failed) {
    return Transition(Failed(CANCELLED), emptyList())   // ← Idle 도 여기 걸린다
}
```

`ArrangeState.Idle` 의 계약("Start 외 모든 이벤트 무시")과 어긋난다. 시작하지도 않은 세션이
`Failed` 로 떨어지면 `dispatch` 가 `reportTerminal` 을 호출해 **"배치 실패: 사용자 취소"** 토스트가 뜬다.
서비스의 `cancelArrange()` 가 `machineState == Idle` 을 먼저 걸러(`:310`) 실제로는 도달하지 않지만,
가드가 리듀서 밖에 있다는 것 자체가 순수 리듀서의 자기완결성 원칙에 어긋난다.
`ArrangeStateMachineTest` 에도 이 케이스가 없다(entering/dragging/terminal 만 검증).

**수정 방향:** 조건에 `&& state !is ArrangeState.Idle` 추가 + 테스트 1개.

---

### 🟡 F9. `Done(verified=true)` 인데 잔여가 허용치를 넘는 경우가 있다

`reduceVerifying`(`ArrangeStateMachine.kt:362-377`)의 두 분기 —
`closedLoopCorrection == false` 일 때와 이미 1회 보정한 뒤 —
는 `residual > residualTolerancePx` 인데도 `verified = true` 를 낸다.
`reportTerminal`(`:1721-1728`)은 이를 **"배치 완료 · 잔여 Npx"** 로 출력한다.

주석상 의도된 동작("보정 안 하되 잔여값은 정직 보고")이지만, 결과적으로 `verified` 의 의미가
"허용치 이내"가 아니라 "측정에 성공"이 된다. 로그·토스트에서 **진짜 성공과 구분되지 않는다.**

**수정 방향(비파괴):** 필드를 `measured` 로 개명하거나 `withinTolerance: Boolean` 을 추가.
동작 변경 없이 의미론만 정리 — v1.5 후보.

---

## 2. 보안

> 총평: **데이터 유출 위험은 없다.** 네트워크 권한·HTTP 클라이언트·애널리틱스·하드코딩 시크릿 0건.
> 스크린샷은 전부 로컬 처리 후 `recycle()` 되고, 로그에는 픽셀이 아니라 집계 통계(raw/conf/band px)만 남는다.
> `allowBackup="false"` 로 백업 유출 경로도 차단돼 있다. **문제는 전부 "노출된 컴포넌트" 쪽이다.**

### 🔴 S1. exported 브로드캐스트 리시버 2개가 권한 없이 릴리스에 포함

**심각도: 높음**

```xml
<!-- AndroidManifest.xml:55-61 -->
<receiver android:name=".probe.ProbeTriggerReceiver" android:exported="true">
<!-- AndroidManifest.xml:99-105 -->
<receiver android:name=".service.ArrangeTriggerReceiver" android:exported="true">
```

둘 다 `android:permission` 이 없다. lint 도 `ExportedReceiver` 로 2건 경고한다.

**설치된 임의의 앱**이 다음을 보낼 수 있다:

```
sendBroadcast(Intent("dev.dj.foldwindow.ARRANGE").putExtra("aspect", 3.9f))
sendBroadcast(Intent("dev.dj.foldwindow.probe.RUN_PROBE"))
```

귀결:
- **`ARRANGE`**: 임의 앱이 접근성 서비스에게 분할 진입·제스처 주입·종횡비 강제·세션 취소를 지시할 수 있다.
  접근성 서비스가 가진 권한(제스처 주입, 전면 스크린샷, 창 내용 조회)을 빌려 쓰는 **혼동된 대리자(confused deputy)** 다.
- **`RUN_PROBE`**: 임의 앱이 **전면 스크린샷 촬영을 트리거**하고 창 덤프 리포트를
  `getExternalFilesDir` 에 쓰게 만들 수 있다.

매니페스트 주석은 "adb 디버그 도구라 허용"이라 적고 있으나, **릴리스 빌드에서도 그대로 살아 있다**
(디버그 소스셋 분리·`BuildConfig.DEBUG` 검사 없음).

**수정 방향(택1, (a) 권장):**
- (a) 두 `<receiver>` 선언을 `app/src/debug/AndroidManifest.xml` 로 이동 — 릴리스에서 완전 소멸, adb 편의는 그대로 유지
- (b) `android:permission` 에 `protectionLevel="signature"` 커스텀 권한 부여 (adb 로는 못 쏘게 되므로 개발 편의 손실)
- (c) `onReceive` 최상단에 `if (!BuildConfig.DEBUG) return` (가장 싸지만 컴포넌트 자체는 계속 노출)

---

### 🟠 S4. exported `PanelActivity` 가 임의 앱의 `EXTRA_FINISH_PANEL` 을 신뢰

**심각도: 중 (자체 DoS — 영구 고착)**

`PanelActivity` 는 파트너 피커 노출을 위해 `exported="true"` + MAIN/LAUNCHER 여야 한다
(`AndroidManifest.xml:69-80`, 근거 타당). 그런데 `onCreate` 가 **발신자 확인 없이**
extra 를 신뢰한다(`PanelActivity.kt:72, 146`).

`EXTRA_FINISH_PANEL` 의 KDoc(`PanelActivity.kt:162-171`)이 스스로 계약을 명시한다:

> **태스크를 새로 만들 수 있는 인텐트에는 이 extra 를 절대 실으면 안 된다** — base intent 에 보존돼
> 이후 카드를 탭할 때마다 즉시 finish 되는 **영구 실패 루프**가 된다.

이 계약은 **자기 코드에만 강제돼 있다**(`hasPanelTask()` 사전 확인). 임의의 서드파티 앱이
`FLAG_ACTIVITY_NEW_TASK` + 이 extra 로 `PanelActivity` 를 실행하면 그 영구 실패 루프를
외부에서 주입할 수 있다 — 결함 #28 을 공격자가 재현시키는 것과 같다.

**수정 방향:** `onCreate`/`onNewIntent` 에서 발신자 검증
(`referrer?.host == packageName` 또는 `callingActivity?.packageName == packageName`),
또는 extra 대신 비-exported 경로(서비스 바인더 호출 / 앱 내부 브로드캐스트)로 교체.

---

### 🟠 S2. `ShellExecUserService` 가 shell UID 에서 임의 셸 실행을 무제한 노출

**심각도: 중 (Shizuku 가 바인드 주체를 중개하므로 직접 노출은 아님)**

```aidl
// IShellExec.aidl
interface IShellExec { String run(String command); }
```
```kotlin
// ShellExecUserService.kt:22
Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
```

**허용 목록이 전혀 없다.** 실제로 필요한 것은 3종뿐이다:
`am start --windowingMode 5 -n <component>` / `am stack list` / `am task resize <id> <l> <t> <r> <b>`.

현재 설계에서는 이 앱의 어떤 코드 경로든(향후 버그·주입 포함) **uid 2000(shell) 권한의 임의 명령 실행**을
얻는다. 최소 권한 원칙 위반이다.

**수정 방향:** AIDL 을 `String run(in String[] argv)` 로 바꾸고 `sh -c` 를 제거
(→ 셸 파싱 자체가 사라져 S3 도 동시 해소), 그 위에 첫 인자 `am` + 두 번째 인자
`start|stack|task` 허용 목록을 UserService 쪽에 강제.

---

### 🟡 S3. 셸 명령 문자열 보간

```kotlin
// ArrangerAccessibilityService.kt:538-541
"am start --windowingMode 5 -n '${component.flattenToString()}'"
```

작은따옴표로 `$` 확장은 막았고(주석에 근거 명시 — YouTube `Shell$HomeActivity`), 자바 식별자에
`'` 가 올 수 없어 **현재 실질 위험은 없다**. 다만 "패키지매니저에서 온 값이니 안전"이라는 전제에
의존하는 패턴이라, S2 의 argv 전환으로 함께 없애는 것이 맞다.

---

### ✅ S5. 프라이버시 — 확인 결과 문제 없음

- 접근성 서비스가 전면 스크린샷(DRM·금융 화면 포함 가능)을 캡처하지만 **전부 로컬 처리 후 `recycle()`**
- 로그에 남는 것은 `raw`/`snapped`/`conf`/`band px` 등 **집계값뿐** — 픽셀·텍스트 내용 없음
- `strings.xml` 의 접근성 서비스 설명이 "수집한 정보는 기기 밖으로 전송하지 않습니다" 로 고지 — 사실과 일치
- `probe_report.md`(외부 파일 디렉터리 + FileProvider 공유)에는 패키지명·창 bounds·기기 정보만 포함
- INTERNET 권한 없음, 네트워크 라이브러리 없음, 하드코딩 시크릿 없음, `allowBackup="false"`

**조치 불필요.** 다만 lint `DataExtractionRules` 경고대로 Android 12+ 용
`android:dataExtractionRules` 를 명시하면 의도가 더 분명해진다.

---

## 3. 성능

### 🟠 P1. 접근성 트리 전체 순회 × 셀렉터 수 × 150ms — 메인 스레드

`SplitEntry.firstMatch`(`:556-569`)는 **셀렉터마다 트리를 처음부터 다시 순회**한다:

```kotlin
for ((name, predicate) in selectors) {
    val node = findNode(roots, predicate)   // ← 매 셀렉터가 전체 DFS
    ...
}
```

`findCardIconNode` 는 셀렉터 3개(`:443-473`) → **폴링 1주기당 최대 3회 전체 트리 DFS**.
폴링 간격은 150ms 이고, 이 코루틴은 `Dispatchers.Main.immediate` 에서 돈다 —
Recents 화면 노드 트리는 수백~수천 노드다.

추가로:
- `SplitEntry.searchNode`(`:539-550`)·`DividerPopupRotator.searchNode`(`:77-88`)는 **재귀 + 노드 상한 없음**
  (`PaneSwapper.searchClickableNodes` 만 `MAX_NODES_VISITED = 500` 을 갖는다 — 일관성 부재).
  병적으로 깊은 트리에서 StackOverflow 가능.

**수정 방향:** ① 트리를 **1회만** 순회하며 모든 셀렉터를 노드별로 평가(우선순위는 매치 결과 정렬로 처리)
② `searchNode` 3구현에 공통 노드 예산 도입 ③ 순회를 `Dispatchers.Default` 로 이동.

---

### 🟠 P2. `FloatingLauncherService.onCreate` 의 메인 스레드 `runBlocking`

```kotlin
// FloatingLauncherService.kt:159
val position = runCatching { runBlocking { store.bubblePosition() } }
```

KDoc 이 트레이드오프를 명시하고 있지만(버블 뷰 생성 전 위치 확정 필요), 이 경로는
**`BootReceiver` → `startForegroundService` 직후 부팅 컨텍스트에서 실행**된다.
최초 접근 시에는 `SharedPreferencesMigration`(레거시 `bubble_prefs` 이관 + 원본 삭제)까지
동기적으로 돌아간다. ANR 창구로는 가장 나쁜 조합이다.

**수정 방향:** 기본 위치로 즉시 부착하고, 비동기 읽기가 끝나면 `updateViewLayout` 으로 이동.
사용자 체감은 "부팅 직후 한 프레임 다른 위치" 뿐이다.

---

### 🟡 P3. `AccessibilityNodeInfo` 미회수 + `minSdk = 30`

`recycle()` 호출이 코드 전체에 0건이다. API 33+ 에서는 no-op 이므로 실기기(Fold 7, API 36)에서는
무해하지만, **`minSdk = 30`**(`app/build.gradle.kts:16`)이므로 API 30~32 기기에서는 폴링 주기마다
노드 객체가 누적된다. 실기기 타깃이 API 36 고정이라면 `minSdk` 를 올리는 것이 가장 정직하다
(`takeScreenshot()` 가 API 30+ 이라는 현 근거는 실제 타깃과 무관하게 낮게 잡혀 있다).

부수 효과: lint 가 지적한 `ObsoleteSdkInt` 2건
(`ArrangerAccessibilityService.kt:1865`, `ProbeAccessibilityService.kt:223` 의
`SDK_INT < R` 검사는 절대 참이 되지 않는 죽은 분기)도 함께 정리된다.

---

### 🟡 P4. `getPixels` 가 필요량의 약 8배를 복사

- `toLetterboxScan`(`ScreenshotSampler.kt:37`): 행마다 `x1-x0` 픽셀 전부를 읽고 `colStride=8` 로 1/8만 사용
- `toPillarboxScan`(`:129`): 열마다 `h` 픽셀 전부를 읽고 `rowStride=8` 로 1/8만 사용

2184×1968 기준 스캔 1회당 약 200만 int 복사. 세션당 2~3회라 병목은 아니지만,
`getPixels` 의 `stride` 인자를 활용하면 거의 공짜로 줄일 수 있다.
`rowStride`/`colStride` 를 이용한 스캔 축 축소는 이미 올바르게 돼 있으니
**교차축만** 손보면 된다.

---

## 4. 설계 · 유지보수성

### 🔴 M1. `ArrangerAccessibilityService` 2,023줄 / 세션 가변 필드 25개

이 파일 하나가 동시에 다음 **7가지 책임**을 진다:

1. 상태 머신 구동부(dispatch/effect 실행) 2. 세션 상태 보관소 3. 측정 파이프라인(pre/confirm/verify)
4. 플렉스 자동 트리거 5. 커버 자동 해제 6. 팝업(Shizuku) 오케스트레이션 7. 패널 태스크 관리 + 기하 헬퍼

가장 구체적인 위험은 `cleanupSession()`(`:1286-1308`)이 **14개 필드를 손으로 하나씩 리셋**한다는 점이다.
세션 필드를 추가하면서 여기에 한 줄 넣는 것을 잊는 순간, **이전 세션 값이 다음 세션으로 누수**된다 —
정적 분석·테스트 어느 쪽도 잡지 못하는 종류의 버그다.

**수정 방향(우선순위 순):**
- (1) `data class SessionContext(...)` 로 세션 필드를 묶고 `private var session: SessionContext? = null` 하나로 관리
  → `cleanupSession()` 은 `session = null` 한 줄이 되고, 누수 자체가 구조적으로 불가능해진다
- (2) 플렉스/커버 트리거 → `AutoTriggerCoordinator`, 팝업 → `PopupOrchestrator` 로 분리
- (3) 측정 파이프라인(pre/confirm/verify + crop + 로깅) → `AspectMeasurementPipeline` 로 분리

(1)만 해도 위험의 절반이 사라진다. 실기기 검증 자산이 큰 코드이므로 **한 번에 다 하지 말 것.**

---

### 🟠 M2. CLAUDE.md 의 "Hilt DI" 기술이 사실과 다름

CLAUDE.md 「의존성」 절은 `Hilt DI` 를 명시하지만, `app/build.gradle.kts:48-66` 에 Hilt 의존성이 없고
플러그인도 없다. 실제로는 모든 컴포넌트가 `by lazy { ProfileStore(this) }` 로 직접 생성한다
(`PanelActivity.kt:67` 은 이를 "기존 파일 전반에 Hilt 등 DI 없음" 이라고 정확히 기록하고 있다).

**이 규모에서는 수동 생성이 옳은 선택이다.** 문서를 코드에 맞춰 수정할 것 —
반대 방향(Hilt 도입)은 이득 없는 부채다.

---

### 🟠 M3. `platform/` 헬퍼 3중 중복

| 헬퍼 | 위치 |
|---|---|
| `clickableAncestorOrSelf` | `SplitEntry.kt:755` · `DividerPopupRotator.kt:121` |
| `tapNodeCenter` / `tapPoint` | `SplitEntry.kt:769,777` · `DividerPopupRotator.kt:131,138` · `PaneSwapper.kt:240,228`(변형) |
| `searchNode`(재귀 DFS) | `SplitEntry.kt:539` · `DividerPopupRotator.kt:77` |
| `pollUntil` | `SplitEntry.kt:576` · `DividerPopupRotator.kt:146` |
| `clickWhenFound` | `SplitEntry.kt:713` · `DividerPopupRotator.kt:91`(축약판) |

거의 동일한 코드가 3파일에 흩어져 있어 **P1 같은 수정을 3곳에 해야 한다**(그리고 실제로
`MAX_NODES_VISITED` 는 한 곳에만 적용돼 있다 — 이미 드리프트가 발생했다).

**수정 방향:** `platform/NodeActions.kt`(탐색·클릭·탭) + `platform/Polling.kt`(pollUntil/pollForValue)로 추출.
셀렉터 상수는 이미 `EntrySelectors`/`DividerPopupRotator.companion` 으로 잘 분리돼 있으니 그 관례를 확장.

---

### 🟡 M4. 낡은 메타데이터 / 죽은 참조

| 항목 | 위치 | 내용 |
|---|---|---|
| 버전 | `app/build.gradle.kts:19-20` | `versionCode = 1`, `versionName = "0.1.0-phase0"` — Phase 4 완료 코드베이스 |
| Robolectric | `gradle/libs.versions.toml:15,29` | 버전 카탈로그에 선언돼 있으나 **어떤 소스셋에서도 사용 안 함** |
| `EntryContext.panelIntent` | `SplitEntry.kt:811` | 매 스텝 생성·전달되지만 더 이상 읽는 곳이 없음(KDoc 이 인지하고 유지 중) |
| `SplitEntry.tapPoint` KDoc | `SplitEntry.kt:776` | "menuStep5 핸들 탭에 사용" — 해당 로직은 `DividerPopupRotator` 로 이동했다 |
| 앱 아이콘 | `AndroidManifest.xml:21` | `android:icon` 미지정 — lint `MissingApplicationIcon` |

---

### 🟡 M5. Phase 0 프로브가 릴리스에 그대로 탑재

`probe/` 패키지(4파일, 626줄)는 **두 번째 접근성 서비스** + exported 리시버 + LAUNCHER 액티비티다.
사용자에게는 접근성 설정에 항목이 2개, 앱 서랍에 아이콘이 2개 보인다.
CLAUDE.md 는 "나중에 제거 가능하도록 격리" 를 명시했고 격리 자체는 잘 돼 있다 — **이제 제거할 시점이다.**
S1 의 절반(`ProbeTriggerReceiver`)도 함께 해소된다.

---

### 🟡 M6. 릴리스 빌드에 축소·난독화 없음

`app/build.gradle.kts:23-25` `release { isMinifyEnabled = false }`, ProGuard 규칙 파일 없음.
`ResizeModeDetector` 가 `ApplicationInfo.privateFlags` 를 리플렉션하므로,
minify 를 켤 때는 keep 규칙이 필요하다는 점을 미리 기록해 둔다.

---

### 🟡 M7. lint 가 DoD 에 없고 현재 실패 상태

`./gradlew :app:lintDebug` → **3 errors, 27 warnings, BUILD FAILED**.

에러 3건:
1. `SoonBlockedPrivateApi` — `ResizeModeDetector.kt:52` 의
   `PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE` 리플렉션은 **API 36 타깃에서 예외를 던진다.**
   코드는 이미 `runCatching` + `FALLBACK_UNRESIZEABLE_BIT` 로 정확히 대응하고 있으므로
   **기능적 문제는 없다** — lint 억제(`@Suppress` 또는 baseline)만 필요하다.
2~3. `PropertyEscape` — `gradle.properties:10` / `local.properties:3` 의 Windows 경로 이스케이프.
   `local.properties` 는 git 제외 대상이므로 실질적으로는 `gradle.properties` 1건.
   (참고: `org.gradle.java.home` 이 있어도 `gradlew` **런처 자체**는 `JAVA_HOME` 을 요구한다 —
   bash 에서 `./gradlew` 실행 시 `JAVA_HOME is not set` 로 실패한다. CLAUDE.md 검증 커맨드 절에
   이 전제를 명시하면 좋겠다.)

주요 경고: `ExportedReceiver`×2(→S1), `ClickableViewAccessibility`×2(버블 `ImageView` 가
`setOnTouchListener` 만 있고 `performClick` 미호출 — 스크린리더 사용자가 버블을 탭할 수 없다),
`MissingApplicationIcon`, `ObsoleteSdkInt`×2(→P3), `DataExtractionRules`.

**의존성 노후화(경고 12건):** Compose BOM `2024.12.01` → `2026.06.01`, AGP `8.11.1` → `9.3.1`,
Kotlin `2.1.0` → `2.4.10`, `androidx.window 1.3.0` → `1.5.1`(FoldingFeature 관련 수정 다수 가능),
coroutines `1.9.0` → `1.11.0`. 실기기 검증 자산이 큰 프로젝트이므로 **한 번에 올리지 말 것** —
`androidx.window` 만 먼저 올리고 폴드 회귀를 확인하는 순서를 권한다.

**수정 방향:** `lint { baseline = file("lint-baseline.xml") }` 로 현재 상태를 기준선화한 뒤,
`lintDebug` 를 DoD 검증 커맨드에 추가해 **신규 경고만** 잡히게 한다.

---

## 5. 테스트

### ✅ 강점

- **도메인 커버리지 파일 단위 100%** — `domain/` 13파일 + `data/` 매핑 2파일 전부 대응 테스트 존재, 282 PASS
- 경계값·실패 경로 중심 (예: `LetterboxDetectorTest` 445줄, `FlexModePolicyTest` 351줄)
- 상태 머신 테스트가 스테일 이벤트·터미널 흡수까지 검증

### 🟠 T1. `ScreenshotSampler` 가 미테스트 — 가장 아쉬운 공백

`toLetterboxScan`/`toPillarboxScan` 은 `Bitmap` 만 걸쳐 있을 뿐 **본질은 순수 산술**이고,
**모든 측정의 입력**이다. 특히 다음 대응 관계는 어긋나면 종횡비 역산이 조용히 깨진다:

```
toLetterboxScan : scaledWidth  = w / rowStride   (entries 축 = 행)
toPillarboxScan : scaledHeight = h / colStride   (entries 축 = 열)
```

주석은 "어긋나면 resolveAspectPillarbox 역산이 깨진다" 고 경고하지만 **이를 지키는 테스트가 없다.**
`robolectric` 은 이미 버전 카탈로그에 있다(M4) — `testImplementation(libs.robolectric)` 한 줄이면
합성 `Bitmap`(순흑 띠 + 회색 콘텐츠)으로 정확한 회귀 테스트를 쓸 수 있다.

같은 이유로 `data/ProfileStore` 도 테스트 가치가 높다(DataStore 는 인메모리 테스트 하네스 제공).

### 🟡 T2. `domain/` 순수성 규칙을 강제하는 테스트가 없다

CLAUDE.md 의 **"철칙: `domain/` 에 `import android.*` 이 들어가면 리뷰 거부"** 는 현재
사람의 리뷰에만 의존한다. 실제로는 잘 지켜지고 있지만(검증 완료: 0건),
소스 파일을 읽어 검사하는 5줄짜리 JVM 테스트로 기계화하면 영구히 보장된다:

```kotlin
@Test fun `domain has no android imports`() {
    val offenders = File("src/main/java/dev/dj/foldwindow/domain").walkTopDown()
        .filter { it.extension == "kt" }
        .filter { it.readText().contains(Regex("""^import\s+android""", RegexOption.MULTILINE)) }
        .map { it.name }.toList()
    assertEquals(emptyList<String>(), offenders)
}
```

### 🟡 T3. 계측 테스트(androidTest) 소스셋 자체가 없음

`platform/`·`service/` 는 실기기 캠페인(17~19차)이 대신하고 있고 그 기록도 훌륭하다.
다만 캠페인은 사람 시간이 든다 — `DividerLocator`·`PaneGeometry` 조합처럼
가짜 `AccessibilityWindowInfo` 로 검증 가능한 부분은 계측 테스트로 옮길 여지가 있다. (v1.5 후보)

---

## 6. 코드 스타일

전반적으로 **일관성이 높다.** 지적할 것이 거의 없다.

- 네이밍·포맷·KDoc 밀도가 파일 간 균일. `kotlin.code.style=official` 준수
- 주석이 "무엇을" 이 아니라 "왜 + 어떤 실측 근거로" 를 설명 — 모범적
- 한국어 주석 + 영어 식별자 혼용이 CLAUDE.md 규칙과 일치

개선 여지 (전부 사소):

1. **매직 넘버 일부가 상수화되지 않음** — `FloatingLauncherService` 의 dp 값들
   (`14`, `4`, `10`, `24` at `:325,585,637-638`)이 인라인. 상수화 관례가 다른 곳(`BUBBLE_SIZE_DP`)과 불일치
2. **`MENU_ITEM_TEXT_COLOR = -1`**(`FloatingLauncherService.kt:851`) — 주석으로 설명은 돼 있으나
   `android.graphics.Color.WHITE` 를 쓰면 주석 자체가 불필요
3. **KDoc 이 매우 길다** — 근거 보존 가치가 크므로 유지가 맞지만,
   `ArrangerAccessibilityService.performDismissSplit` 처럼 KDoc 25줄 / 본문 80줄인 곳은
   실측 이력을 `docs/DEVICE_FACTS.md` 로 옮기고 코드에는 요약 + 링크만 남기는 편이 읽기 쉽다
4. **로그 태그가 파일마다 다름**(`FWArranger`, `FWSplitEntry`, `PanelActivity`, `FWProbe`...) —
   `PanelActivity` 만 `FW` 접두사가 없다. `logcat -s` 필터링 시 걸린다

---

## 7. 우선순위 제안

착수 순서. 각 항목은 서로 독립적이다.

### 1순위 — 릴리스 전 필수 (보안/기능 결함)

| # | 항목 | 예상 규모 |
|---|---|---|
| S1 | exported 리시버 2개 → `src/debug/AndroidManifest.xml` 이동 | 매니페스트만, 30분 |
| F3+F4 | `ShellExecUserService` 에 `waitFor(timeout)` + `redirectErrorStream` | 1파일, 1시간 |
| F5 | `ensureBound` 타임아웃 시 `binding = false` | 1줄 |
| S4 | `PanelActivity` extra 발신자 검증 | 1파일, 1시간 |
| F2 | 기하 정합성 가드 (최소안: 불일치 시 명시적 실패) | 1파일, 1~2시간 |

### 2순위 — 다음 작업 사이클

| # | 항목 |
|---|---|
| M1(1) | `SessionContext` 추출 — `cleanupSession()` 누수 위험 제거 |
| T1 | Robolectric 배선 + `ScreenshotSampler` 테스트 |
| F1 | `WindowGeometry` 필드 간 불변식 `require` + 테스트 |
| F6 | `dispatch()` effect 큐 드레인 |
| M7 | lint baseline 생성 + DoD 편입 |
| S2 | AIDL argv 전환 + `am` 허용 목록 (F3 수정과 함께 하면 효율적) |
| M5 | `probe/` 제거 |

### 3순위 — 여유 있을 때

P1(트리 순회 1회화) · P2(runBlocking 제거) · M3(헬퍼 추출) · T2(순수성 테스트) ·
F7 · F8 · F9 · M2/M4(문서·메타데이터) · P3(minSdk) · P4 · 스타일 4건

---

## 부록 — 리뷰 대상 파일 규모

```
총 13,039줄 / Kotlin 55파일 (main 40 + test 15)

main 상위:
  service/ArrangerAccessibilityService.kt   2,023   ← M1
  service/FloatingLauncherService.kt          894
  platform/SplitEntry.kt                      860
  domain/ArrangeStateMachine.kt               392
  domain/LetterboxDetector.kt                 362
  ui/PanelActivity.kt                         354

test: 15파일 / 282 tests / 0 failures
```
