# IMPROVEMENT_PLAN — 2026-07-29 리뷰 개선 계획

> 대응 리뷰: `docs/CODE_REVIEW_2026-07-29.md` (항목 번호 F/S/P/M/T 는 그 문서와 1:1 대응)
> 이 문서는 **계획 전용**이다. 착수 시 각 항목에 `[완료]` + 커밋 해시를 덧붙인다.
> `PROGRESS.md` 는 웨이브 단위로만 갱신한다(항목 단위로 쪼개지 말 것).

---

## 0. 계획 수립 원칙

이 프로젝트의 최대 자산은 **실기기 캠페인 17~19차의 검증 이력**이다. 따라서 개선 계획은
"고칠 것"이 아니라 **"재검증 비용"** 을 기준으로 정렬한다.

| 원칙 | 내용 |
|---|---|
| **P-1 재검증 비용으로 묶는다** | 같은 실기기 시나리오를 요구하는 수정끼리 한 웨이브로 묶어, 실기기 세션 1회로 여러 수정을 커버한다 |
| **P-2 검증된 경로는 마지막에 건드린다** | 진입 레시피·드래그·측정 파이프라인은 검증 이력이 가장 두꺼우므로 안전망(테스트)을 먼저 깐 뒤 손댄다 |
| **P-3 동작 등가 우선** | 같은 결함을 고치는 두 방법 중 "현재 동작을 바꾸지 않는 쪽"을 택한다 (F6·P1 이 이 원칙으로 결정됨) |
| **P-4 웨이브마다 DoD 로 닫는다** | 각 웨이브 끝은 CLAUDE.md DoD 충족 상태 = 언제든 중단 가능한 지점 |
| **P-5 의존성 상향은 v1 범위 밖** | AGP/Kotlin/Compose/window 상향은 검증된 경로 전부를 재검증 대상으로 만든다 → v1.5 일괄 |

---

## 1. 항목별 확정 해결책

### 🔴 S1 — exported 리시버 릴리스 노출

**해결책: debug 소스셋 분리.** 클래스는 `main` 에 그대로 두고 **매니페스트 선언만** 옮긴다.

신규 `app/src/debug/AndroidManifest.xml`:
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <receiver android:name=".service.ArrangeTriggerReceiver" android:exported="true">
            <intent-filter><action android:name="dev.dj.foldwindow.ARRANGE" /></intent-filter>
        </receiver>
        <!-- probe 3종도 함께 이동 (M5) -->
        <activity android:name=".probe.ProbeActivity" ... />
        <service  android:name=".probe.ProbeAccessibilityService" ... />
        <receiver android:name=".probe.ProbeTriggerReceiver" ... />
        <provider android:name="androidx.core.content.FileProvider" ... />
    </application>
</manifest>
```
main 매니페스트에서는 해당 5개 선언을 삭제한다.

**근거:** (b) signature 권한은 `adb shell am broadcast` 를 막아 **모든 캠페인 절차를 무효화**한다.
(c) `BuildConfig.DEBUG` 가드는 컴포넌트 자체는 계속 노출한다.
(a)는 **디버그 빌드의 adb 트리거를 100% 보존하면서** 릴리스에서 완전히 소멸시킨다 — 유일하게 손실 없는 선택.

**부수 효과:** M5(probe 릴리스 탑재) 동시 해소. 릴리스에서 접근성 서비스 1개·런처 아이콘 1개로 정상화.
프로브는 개발 빌드에 남으므로 F2 2단계(세로 기하 실측)에 계속 쓸 수 있다.

**영향:** 매니페스트만. 런타임 코드 경로 무변경.
**재검증:** 디버그 빌드에서 `am broadcast -a dev.dj.foldwindow.ARRANGE` 1회 동작 확인.

---

### 🔴 S4 — `PanelActivity` extra 위조 가능

**해결책: 프로세스 로컬 1회용 토큰.** boolean extra 를 토큰 문자열로 **교체**한다.

```kotlin
// PanelActivity.companion
const val EXTRA_FINISH_TOKEN = "dev.dj.foldwindow.EXTRA_FINISH_TOKEN"

@Volatile private var finishToken: String? = null

/** 서비스가 finish 인텐트를 만들기 직전 1회 호출 */
fun issueFinishToken(): String = UUID.randomUUID().toString().also { finishToken = it }

/** 일치 시 소비(1회용). 불일치·부재는 전부 false */
private fun consumeFinishToken(raw: String?): Boolean {
    val cur = finishToken ?: return false
    if (raw == null || raw != cur) return false
    finishToken = null
    return true
}
```
`requestsFinish()` → `consumeFinishToken(this?.getStringExtra(EXTRA_FINISH_TOKEN))`
서비스 측(`performDismissSplit`) → `putExtra(EXTRA_FINISH_TOKEN, PanelActivity.issueFinishToken())`

**근거:** `callingActivity` 는 `startActivityForResult` 전용이라 null 이고, `referrer` 는
Service 컨텍스트 발 `startActivity` 에서 신뢰할 수 없다. **발신자 신원 대신 비밀값**으로 검증한다.
`PanelActivity` 와 `ArrangerAccessibilityService` 는 **동일 프로세스**이므로 static 필드로 충분하다.

**폴백 경로 정합성 확인 (회귀 없음):**
`dismissSplit()` 은 서비스 인스턴스 위에서 호출되므로 **프로세스가 살아 있음이 전제**다.
즉 토큰 발급 시점과 소비 시점이 항상 같은 프로세스 → 정상 경로는 100% 통과한다.

**보너스 — #28 자가 치유:** base intent 에 토큰이 박혀도 프로세스가 바뀌면 토큰이 불일치해
**무시된다.** 즉 "영구 실패 루프" 라는 결함 클래스 자체가 구조적으로 소멸한다.
(`hasPanelTask()` 사전 확인은 그대로 유지 — 불필요한 태스크 생성을 막는 별개 목적이다.)

**영향:** `PanelActivity.kt`(3곳) + `ArrangerAccessibilityService.performDismissSplit`(1곳) + KDoc 갱신.
**재검증:** B-1/B-2(이미 `[미검증]` 잔여 경로) — 인위 유도로 1회 확인, 실패해도 v1 차단 아님.

---

### 🔴 F3 + F4 + S2 + S3 — Shizuku 셸 계층 (일괄)

네 항목 모두 같은 3파일이므로 **하나의 변경으로 묶는다.**

**해결책 ①: AIDL 을 argv + 타임아웃으로 전환** (S2·S3 동시 해소)
```aidl
interface IShellExec {
    String run(in String[] argv, long timeoutMs);
}
```
`sh -c` 가 사라지므로 **셸 파싱 자체가 없어진다** → 작은따옴표 의존(S3) 소멸,
클래스명의 `$` 문제도 원천 소멸.

**해결책 ②: 허용 목록** (S2)
```kotlin
private val ALLOWED = mapOf("am" to setOf("start", "stack", "task"))
private fun isAllowed(argv: Array<String>) =
    argv.size >= 2 && ALLOWED[argv[0]]?.contains(argv[1]) == true
```
실제 필요한 3종(`am start` / `am stack list` / `am task resize`)만 통과.

**해결책 ③: 단일 스트림 + 경계된 대기** (F3·F4)
```kotlin
val p = ProcessBuilder(argv.toList()).redirectErrorStream(true).start()  // F4: 파이프 1개
p.outputStream.close()
val sb = StringBuilder()
val reader = Thread {
    runCatching { p.inputStream.bufferedReader().use { r ->
        r.forEachLine { synchronized(sb) { sb.appendLine(it) } } } }
}.apply { isDaemon = true; start() }

if (!p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {   // F3: 실효 타임아웃
    p.destroyForcibly()                                // 스트림이 닫혀 reader 도 풀린다
    reader.join(500)
    return "-1\ntimeout after ${timeoutMs}ms"
}
reader.join(1000)
return "${p.exitValue()}\n$sb"
```
**읽기를 별도 스레드로 뺀 이유:** 자식이 stdout 을 닫지 않으면 `readText()` 가 영원히 블록돼
`waitFor(timeout)` 에 도달조차 못 한다. 읽기와 대기를 분리해야 타임아웃이 실효를 갖는다.

**해결책 ④: 바인드 래치 해제** (F5)
```kotlin
val bound = withTimeoutOrNull(BIND_TIMEOUT_MS) { ... }
if (bound != true) binding = false      // ← 추가. 다음 호출이 재바인드할 수 있게 한다
return bound ?: false
```

**호출부 변경** (`ArrangerAccessibilityService.performStartPopup`):
```kotlin
ShizukuShell.exec(arrayOf("am", "start", "--windowingMode", "5", "-n", component.flattenToString()), ...)
ShizukuShell.exec(arrayOf("am", "stack", "list"), ...)
ShizukuShell.exec(arrayOf("am", "task", "resize", "$taskId",
                          "${bounds.left}", "${bounds.top}", "${bounds.right}", "${bounds.bottom}"), ...)
```

**필수 부수 작업:** `versionCode` 를 올린다. `Shizuku.UserServiceArgs.version(BuildConfig.VERSION_CODE)`
가 UserService 프로세스 재생성을 결정하므로, AIDL 이 바뀌었는데 버전이 같으면 **구 바이너리가
재사용돼 `AbstractMethodError`** 가 난다. (M4 의 versionCode 갱신과 목적이 일치한다.)

**영향:** `IShellExec.aidl`, `ShellExecUserService.kt`, `ShizukuShell.kt`, `performStartPopup`.
**재검증(필수):** P4-1 E2E — 유튜브 1회 + 넷플릭스 1회, bounds 정확도 확인. 17차 절차 그대로 재사용.
결과를 `DEVICE_FACTS.md` 에 기록하고, 재검증 전까지는 `[미검증]` 표기.

---

### 🔴 F2 — 기하 하드코딩 / 실화면 불일치

**해결책: 2단계.** v1 은 가드만, 근본 수정은 v1.5.

**1단계(v1) — 명시적 실패 가드.** `startArrange` 최상단(세션 상태를 건드리기 전):
```kotlin
val screen = screenRect()
if (!geometryMatches(screen)) {
    Log.w(TAG, "startArrange: 화면 기하 불일치 — screen=${screen.width}x${screen.height} " +
               "expected=${geometry.usableWidth}x${geometry.usableHeight} (v1 미지원)")
    toast("이 화면 방향/디스플레이는 아직 지원하지 않습니다")
    return
}
```
```kotlin
private fun geometryMatches(screen: IntRect): Boolean {
    val tol = GEOMETRY_TOLERANCE_FRACTION   // 0.01
    return abs(screen.width  - geometry.usableWidth)  <= geometry.usableWidth  * tol &&
           abs(screen.height - geometry.usableHeight) <= geometry.usableHeight * tol
}
```
`evaluateFlexAutoTrigger` 게이트 체인에도 동일 검사를 추가하되 **토스트 없이 로그만**
(`reason=geometry-mismatch`) — 자동 트리거는 "조용한 실패 금지" 원칙 비대상이라는 기존 선례를 따른다.

**근거:** 현재 코드는 세로에서 **조용히 틀린 곳으로 디바이더를 옮긴다.** 가드는 이를
"명시적 미지원"으로 바꾼다 — 15줄, 가로 검증 경로에는 **영향 0**(가로에서는 항상 통과).
`startPopup` 은 `PopupPlanner.plan(screen.width, screen.height, ...)` 로 이미 실화면을 쓰므로 대상 아님.

**2단계(v1.5) — 실화면 기반 기하 생성.** `WindowGeometry` 를 `screenRect()` 에서 만들고
`dividerThickness`/`minPaneHeight` 만 실측 상수로 남긴다. **선행 조건 = 세로 분할의
디바이더 두께·최소 페인 높이 실측**(현재 값은 "세로 좌우분할 측정 → 가로 대칭 가정 `[미검증]`" 이다).
이는 코드 작업이 아니라 **프로브 측정 작업**이므로 v1 범위 밖. → `PROGRESS.md` C 항목으로 이관.

**재검증:** 가로 배치 1회 정상 + 세로에서 토스트 1회.

---

### 🟠 F1 — `WindowGeometry` 필드 간 불변식 부재

**해결책: `init` 에 require 추가.**
```kotlin
init {
    require(usableWidth > 0 && usableHeight > 0) { "usable size must be positive" }
    require(dividerThickness >= 0) { "dividerThickness must be >= 0" }
    require(minPaneHeight >= 0) { "minPaneHeight must be >= 0" }
    // 추가: 두 페인이 최소 높이를 동시에 만족할 수 없는 기하는 계획 불가
    require(allocatableHeight >= 2 * minPaneHeight) {
        "allocatableHeight($allocatableHeight) < 2 * minPaneHeight($minPaneHeight) — 분할 불가 기하"
    }
}
```
**클램프가 아니라 require 인 이유:** 음수 `panelH` 를 0으로 클램프하면 "말이 안 되는 계획"을
조용히 만들어낸다 — 조용한 실패 금지 원칙 위반. 계획 불가는 계획 불가로 드러내야 한다.

**주의:** `SplitPlannerTest` 가 퇴화 기하를 쓰는 케이스가 있으면 새 예외를 기대하도록 갱신해야 한다
(Worker 가 테스트 실행으로 확인). Fold 7 실측값은 `1954 >= 362` 로 여유롭게 통과한다.

**신규 테스트:** 경계 정확히(`allocatable == 2*minPane` 통과), 1 부족(예외), 정상(통과).

---

### 🟠 F6 — `dispatch()` 재진입

**해결책: 이벤트 큐 + 단일 드레인 루프.** (동작 등가 — P-3 원칙)
```kotlin
private var dispatching = false
private val pendingEvents = ArrayDeque<ArrangeEvent>()

private fun dispatch(event: ArrangeEvent) {
    if (dispatching) { pendingEvents.addLast(event); return }   // 중첩 호출은 큐로
    dispatching = true
    try {
        var e: ArrangeEvent? = event
        while (e != null) {
            val transition = ArrangeStateMachine.reduce(machineState, e, arrangeConfig)
            if (transition.state != machineState) Log.i(TAG, "transition: ...")
            machineState = transition.state
            transition.effects.forEach { executeEffect(it) }    // 중첩 dispatch → 큐 적재
            val terminal = machineState
            if (terminal is ArrangeState.Done || terminal is ArrangeState.Failed) {
                reportTerminal(terminal); cleanupSession()
            }
            e = pendingEvents.removeFirstOrNull()
        }
    } finally {
        dispatching = false
        pendingEvents.clear()   // 예외 이탈 시 잔여 이벤트는 폐기(상태 불명)
    }
}
```

**동작 등가 검증 (Start 경로 추적):**
```
기존: dispatch(Start) → CheckingSplit → effect → [중첩] dispatch(SplitStateResult) → EnteringSplit
      → 중첩 종료 → 바깥이 machineState(=EnteringSplit) 로 터미널 검사
신규: dispatch(Start) → CheckingSplit → effect → SplitStateResult 큐 적재
      → CheckingSplit 터미널 검사(비터미널) → 큐 pop → reduce(CheckingSplit, SplitStateResult)
      → EnteringSplit → 터미널 검사(비터미널) → 종료
```
**최종 상태 동일, 이벤트별로 터미널 검사가 정확히 1회씩** 수행된다.
디스패처 홉이 없으므로 **타이밍 영향 0** (`Dispatchers.Main.immediate` 특성 유지).

**얻는 것:** effect 2개짜리 전이를 추가하거나 dispatch 말미에 로직을 넣어도 안전해진다.

---

### 🟠 P1 + M3 — 트리 순회 비용 + `platform/` 헬퍼 3중 중복

두 항목이 같은 파일군이므로 **함께 처리한다.**

**해결책 ①(P1): 단일 순회 다중 셀렉터 평가 — 우선순위 보존**
```kotlin
private fun firstMatch(logLabel: String, roots: List<AccessibilityNodeInfo>,
                       selectors: List<Pair<String, (AccessibilityNodeInfo) -> Boolean>>)
        : AccessibilityNodeInfo? {
    var bestIndex = Int.MAX_VALUE
    var bestNode: AccessibilityNodeInfo? = null
    var budget = MAX_NODES_VISITED
    for (root in roots) {
        walk(root, maxDepth = MAX_TREE_DEPTH) { node ->
            if (budget-- <= 0) return@walk false                 // 예산 소진 → 순회 중단
            selectors.forEachIndexed { i, (_, pred) ->
                if (i < bestIndex && runCatching { pred(node) }.getOrDefault(false)) {
                    bestIndex = i; bestNode = node
                }
            }
            bestIndex != 0                                        // 0순위 매치 시 조기 종료
        }
        if (bestIndex == 0) break
    }
    bestNode?.let { Log.i(TAG, "$logLabel matched via selector [${selectors[bestIndex].first}]") }
    return bestNode
}
```
**동작 등가 근거:** `i < bestIndex` 가 **엄격 부등호**이므로 같은 셀렉터 인덱스에서는
**먼저 만난 노드가 유지**된다 = 기존 "셀렉터별 DFS 첫 매치" 와 동일. 셀렉터 우선순위도 그대로.

**순회는 재귀를 유지한다**(스택 변환 안 함). `PaneSwapper` 의 `ArrayDeque.removeLast()` 방식은
자식을 역순으로 방문하므로 **DFS 순서가 달라져** 노드 선택이 바뀔 수 있다 — P-3 위반.
깊이 상한(`MAX_TREE_DEPTH`)과 노드 예산만 추가한다.

**효과:** `findCardIconNode` 기준 폴링 주기당 트리 순회 **3회 → 1회**, 상한 없는 재귀 → 경계 있는 재귀.

**해결책 ②(M3): 공용 추출**
- `platform/NodeActions.kt` — `walk` / `clickableAncestorOrSelf` / `tapNodeCenter` / `tapPoint` / `dispatchTap`
- `platform/Polling.kt` — `pollUntil` / `pollForValue` / `clickWhenFound`
- `SplitEntry` · `DividerPopupRotator` · `PaneSwapper` 가 공유. 상수(`POLL_INTERVAL_MS=150`,
  `TAP_DURATION_MS=50`, `MAX_NODES_VISITED`)도 여기로 단일화.

**근거:** 현재 `MAX_NODES_VISITED` 가 `PaneSwapper` 에만 있는 것이 이미 발생한 드리프트다.
추출하지 않으면 P1 수정을 3곳에 하고, 다음 수정에서 또 어긋난다.

**재검증:** 진입 레시피 직격 — DRAG 세션 1 + MENU 세션 1 필수.

---

### 🟠 M1 — `ArrangerAccessibilityService` 세션 상태 캡슐화

**해결책: `Session` 클래스 1개로 묶고 필드 하나로 관리.**
```kotlin
private class Session(
    // 시작 시점 확정 — val
    val targetPackage: String,
    val targetLabel: String?,
    val desiredPlacement: Placement,
    val placementSource: String,
    val presetAspect: Float,
    val requireAgreement: Boolean,
    val cacheAspectEnabled: Boolean,
    val cachedAspect: Float?,
    val preMeasurement: AspectMeasurement?,
    val entryRecipe: EntryRecipe,
    val config: ArrangeConfig,
) {
    // 세션 중 변이 — var
    var effectivePlacement: Placement = desiredPlacement
    var plan: SplitPlan? = null
    var resolvedAspect: ResolvedAspect? = null
    var aspectConfirmed: Boolean = false
    var consensusAdoptedAspect: Float? = null
    var lastHandle: DividerHandle? = null
}

private var session: Session? = null
```
`cleanupSession()`:
```kotlin
private fun cleanupSession() {
    tickJob?.cancel(); tickJob = null
    machineState = ArrangeState.Idle
    session = null                                    // ← 14줄이 1줄로
    FloatingLauncherService.instance?.setBubbleHiddenForArrange(false)
}
```
`dispatch` 의 config 조회: `session?.config ?: ArrangeConfig()`

**근거:** 현재 `cleanupSession()` 은 14개 필드를 손으로 리셋한다. **필드를 추가하면서
리셋 한 줄을 잊는 것**이 유일하고도 확실한 실패 모드이며, 컴파일러도 테스트도 이를 잡지 못한다.
`session = null` 로 만들면 그 버그 클래스가 **구조적으로 존재할 수 없게** 된다.

**제외(같이 하지 말 것):** 플렉스/커버/팝업/측정 파이프라인 분리는 **이번에 하지 않는다.**
파일 크기는 그대로 두고 **상태 캡슐화만** 한다 — 순수 기계적 변환이라 리뷰·검증이 가능하다.
책임 분리는 v1.5 별건.

**영향:** ~40개 참조 지점. **이 계획 전체에서 회귀 위험 최대.** 반드시 단독 커밋.
**재검증:** DRAG / MENU / 취소 / 분할 해제 4경로 스모크.

---

### 🟠 P2 — 부팅 경로 메인 스레드 `runBlocking`

**해결책: 기본 위치로 즉시 부착 + 비동기 재배치.**
```kotlin
override fun onCreate() {
    ...
    // runBlocking 제거 — 기본 위치로 먼저 뜨고, 저장 위치는 도착하는 대로 반영한다.
    serviceScope.launch {
        val pos = store.bubblePosition() ?: return@launch
        cachedBubbleX = pos.first; cachedBubbleY = pos.second
        applyCachedBubblePosition()          // 뷰가 아직 없으면 캐시만 갱신하고 반환
    }
}

private fun applyCachedBubblePosition() {
    val view = bubbleView ?: return
    val params = layoutParams ?: return
    val bounds = windowManager.currentWindowMetrics.bounds
    params.x = (cachedBubbleX ?: params.x).coerceIn(0, (bounds.width() - view.width).coerceAtLeast(0))
    params.y = (cachedBubbleY ?: params.y).coerceIn(0, (bounds.height() - view.height).coerceAtLeast(0))
    if (bubbleAttached) runCatching { windowManager.updateViewLayout(view, params) }
}
```
**근거:** 이 경로는 `BootReceiver → startForegroundService` 직후, 즉 ANR 판정이 가장 가혹한
구간에서 실행되며, 최초 실행 시에는 `SharedPreferencesMigration` 까지 동기로 돈다.
사용자 체감 손실은 **부팅 직후 한두 프레임 동안 기본 위치** 뿐이다.

**주의:** `onStartCommand → addBubbleIfNeeded()` 와의 경합 — 위 함수는 뷰 유무 양쪽에서 안전해야 한다.

---

### 🟡 P4 — `getPixels` 교차축 과다 복사

**해결책: `getPixels` 의 `stride` 인자 활용.**
`toLetterboxScan` 은 행 스캔에서 `colStride=8` 로 1/8만 쓰면서 행 전체를 복사한다.
`getPixels(pixels, offset, stride, x, y, width, height)` 의 `stride` 로 필요한 열만 받도록 바꾼다.
`toPillarboxScan` 도 동일(열 안에서 `rowStride=8`).

**주의:** 이 함수는 **모든 측정의 입력**이다. **T1 테스트를 먼저 깐 뒤에** 손댄다(P-2 원칙).
raw AR 이 stride 변경에 불변임을 테스트가 보장한 상태에서만 수정한다.

**우선순위 낮음:** 세션당 2~3회 호출이라 실측 병목이 아니다. 여유 있을 때.

---

### 🟡 F7 / F8 / F9 — 소규모 정정

| # | 해결책 |
|---|---|
| **F7** | `if (cont.isActive) cont.resume(bmp) else bmp?.recycle()` — `ArrangerAccessibilityService.kt:1882`, `ProbeAccessibilityService.kt:239` 2곳 |
| **F8** | `ArrangeStateMachine.kt:163` 조건에 `&& state !is ArrangeState.Idle` 추가 + 테스트 1개(`Idle + Cancel → Idle 유지, effects 없음`) |
| **F9** | **필드 개명은 v1.5.** v1 은 `reportTerminal` 메시지만 구분: 잔여가 `arrangeConfig.residualTolerancePx` 를 초과하면 `"배치 완료 · 잔여 Npx (허용치 초과)"` 로 표기. 3줄, 리듀서·테스트 무변경 |

**F9 를 v1.5 로 미루는 이유:** `ArrangeState.Done.verified` 를 개명하면 `ArrangeStateMachineTest`
전반을 손대야 한다. 실제 문제는 "사용자가 진짜 성공과 구분 못 한다" 이므로 **메시지 구분만으로
사용자 측 문제는 100% 해소**된다. 필드 의미론 정리는 안전망이 두꺼워진 뒤 별건으로.

---

### 🟡 M4 / M2 / M6 / 스타일 — 문서·메타데이터

| # | 해결책 |
|---|---|
| **M4 버전** | `versionCode` 2 이상으로, `versionName "0.4.0"` — **F3 AIDL 변경의 필수 선행**(UserService 재생성 트리거) |
| **M4 robolectric** | T1 에서 실제 사용하게 되므로 미사용 상태 해소 |
| **M4 `panelIntent`** | `EntryContext` 에서 필드 삭제 + `handlePerformEntryStep` 의 인텐트 생성 제거 (읽는 곳 0) |
| **M4 KDoc** | `SplitEntry.tapPoint` 의 "menuStep5 핸들 탭에 사용" 삭제(→ `DividerPopupRotator` 로 이동됨) |
| **M4 아이콘** | `android:icon` 지정 — `ic_bubble` 재사용 또는 어댑티브 아이콘 신규 |
| **M2** | CLAUDE.md 「의존성」 절에서 **Hilt 삭제**, "DI 프레임워크 미사용 — 컴포넌트가 `ProfileStore` 를 직접 생성" 으로 정정. 추가로 「검증 명령」 절에 `JAVA_HOME` 전제 명시(`gradlew` 런처는 `org.gradle.java.home` 과 별개로 `JAVA_HOME` 을 요구한다) |
| **M6** | v1 은 minify off 유지. `proguard-rules.pro` 에 `ResizeModeDetector` 리플렉션 keep 규칙만 미리 작성해두고 주석으로 "minify 활성화 시 필요" 명시 |
| **스타일** | dp 리터럴 상수화, `MENU_ITEM_TEXT_COLOR = -1` → `Color.WHITE`, 로그 태그 `PanelActivity` → `FWPanelActivity` |

---

### 🟡 M7 — lint 게이트

**해결책: "고칠 것은 고치고, 의도적인 것만 baseline" — 통째 baseline 금지.**

| lint ID | 처리 |
|---|---|
| `PropertyEscape` (gradle.properties) | **수정** — `C\:/Program Files/Android/Android Studio/jbr` |
| `MissingApplicationIcon` | **수정** — M4 아이콘 |
| `ObsoleteSdkInt` ×2 | **수정** — `SDK_INT < R` 죽은 분기 제거 (minSdk=30 이라 항상 거짓) |
| `ClickableViewAccessibility` ×2 | **수정** — 버블 `ImageView` 에 `performClick()` 오버라이드 + 탭 시 호출. 접근성 앱이 자기 버블을 스크린리더로 못 누르는 상태다 |
| `ExportedReceiver` ×2 | **자동 해소** — S1 |
| `DataExtractionRules` | **수정** — `@xml/data_extraction_rules` 추가(전부 비활성, `allowBackup="false"` 와 일치) |
| `SoonBlockedPrivateApi` | **억제** — `ResizeModeDetector.kt:52` 에 `@Suppress` + "폴백 비트가 이미 대응" 주석 |
| `DiscouragedPrivateApi` | **억제** — 동일 |
| `GradleDependency` / `NewerVersionAvailable` ×12 | **baseline** — P-5 에 따라 v1 범위 밖 |
| `RedundantLabel` | **수정** — 매니페스트 한 줄 |

이후 `lint { baseline = file("lint-baseline.xml") }` + `updateLintBaseline`,
그리고 **CLAUDE.md DoD 에 `./gradlew :app:lintDebug` 추가**.

---

### 🟡 T1 / T2 — 테스트 안전망

**T1 — Robolectric 배선 + `ScreenshotSampler` 테스트**

`app/build.gradle.kts`:
```kotlin
testImplementation(libs.robolectric)
testOptions { unitTests { isReturnDefaultValues = true; isIncludeAndroidResources = true } }
```
Robolectric 4.14.1 이 compileSdk 36 을 못 다루면 테스트 클래스에 `@Config(sdk = [34])`,
그래도 안 되면 4.16.1 로 상향(버전 카탈로그만 수정).

신규 `app/src/test/java/dev/dj/foldwindow/platform/ScreenshotSamplerTest.kt` — 5종:

| # | 테스트 | 잡는 회귀 |
|---|---|---|
| 1 | 순흑 상하 띠 합성 Bitmap → `toLetterboxScan` → `resolveAspect` 가 기대 AR | 행축 파이프라인 |
| 2 | 좌우 필러박스 합성 → `toPillarboxScan` → `resolveAspectPillarbox` 가 기대 AR | 열축 파이프라인 |
| 3 | **동일 콘텐츠의 전치 쌍이 양축에서 같은 AR 을 낸다** | `scaledWidth=w/rowStride` ↔ `scaledHeight=h/colStride` 대응 붕괴 |
| 4 | `rowStride ∈ {1,2,4}` 에서 raw AR 이 허용오차 내 불변 | stride 도입/변경 회귀 · **P4 의 전제조건** |
| 5 | 초소형 Bitmap(예: 4×4)에서 margin coerce 가 예외 없이 동작 | 경계 붕괴 |

**T2 — 아키텍처 순수성 테스트** (`app/src/test/.../ArchitectureTest.kt`)
```kotlin
@Test fun `domain has no forbidden imports`() {
    val forbidden = Regex("""^import\s+(android|androidx|kotlinx\.serialization)\b""", RegexOption.MULTILINE)
    val offenders = File("src/main/java/dev/dj/foldwindow/domain")
        .walkTopDown().filter { it.extension == "kt" }
        .filter { forbidden.containsMatchIn(it.readText()) }
        .map { it.name }.toList()
    assertEquals(emptyList<String>(), offenders)
}
```
(단위 테스트의 작업 디렉터리는 모듈 루트 `app/` 이므로 상대 경로가 해석된다.)

**근거:** CLAUDE.md 의 **"철칙"** 이 지금은 사람의 리뷰에만 의존한다. 5줄로 영구 기계화된다.
`data/ProfileStoreMapping` 에도 같은 규칙을 적용할 수 있다(현재 `domain` 만 참조 — 통과).

---

### ⏸ v1.5 이관 (이번 계획 범위 밖 — 근거 포함)

| # | 항목 | 이관 근거 |
|---|---|---|
| **F2 2단계** | 실화면 기반 `WindowGeometry` | **세로 분할 디바이더 두께·최소 페인 높이 실측이 선행 조건.** 코드가 아니라 측정 작업 |
| **F9 개명** | `Done.verified` → `measured` + `withinTolerance` | 사용자 측 문제는 v1 메시지 구분으로 이미 해소. 리듀서 테스트 전면 개정 비용이 이득을 초과 |
| **M1 2단계** | 플렉스/커버/팝업/측정 파이프라인 분리 | 상태 캡슐화만으로 위험의 절반이 사라진다. 나머지는 이득 대비 회귀 위험이 크다 |
| **M5 2단계** | `probe/` 완전 삭제 | S1 로 릴리스 노출은 해소됨. F2 2단계 측정에 프로브가 필요하므로 그때까지 개발 빌드에 보존 |
| **M6** | minify/난독화 활성화 | 리플렉션 keep 규칙 검증에 실기기 릴리스 빌드 세션이 필요 |
| **P3** | `minSdk 30 → 33` | **제품 결정 필요** — 다만 F2 가드가 도입되면 앱은 2184×1968 화면에서만 동작하므로 minSdk 30 은 이미 의미가 없다. 승인 시 `AccessibilityNodeInfo` 미회수 문제·`ObsoleteSdkInt`·POST_NOTIFICATIONS 분기가 함께 정리된다 |
| **의존성 일괄 상향** | AGP 8.11→9.3, Kotlin 2.1→2.4, Compose BOM, `androidx.window 1.3→1.5` | P-5. 특히 `androidx.window` 는 `FoldStateMonitor` 의 UiContext 폴백 체인이 버전 의존적이라 **P3-5/P4-3 전체 재검증**을 유발한다. v1 출시 후 일괄 |
| **T3** | `androidTest` 계측 테스트 | 실기기 캠페인이 현재 이 역할을 하고 있고 기록도 충실하다 |

---

## 2. 실행 웨이브

각 웨이브는 **CLAUDE.md DoD 충족 상태로 종료**한다 = 언제든 중단 가능한 지점.

### W0 — 무위험 정리 · 게이트 정비 · 아키텍처 테스트 · **[완료 2026-07-29 · `ffc545d`]**
**항목:** M7(lint 수정 7종 + baseline) · M4(버전·아이콘·`panelIntent`·KDoc) · M2(CLAUDE.md 정정) · M6(proguard 규칙 준비) · T2(순수성 테스트) · 스타일 4건
**실기기:** **불필요**
**DoD:** `testDebugUnitTest` PASS · `assembleDebug` PASS · **`lintDebug` PASS(신규)**
**규모:** Worker 1세션

**결과:** DoD 3종 PASS — 테스트 **285**(282 + T2 3) · `assembleDebug` · `lintDebug`("no new issues, 17 warnings filtered by baseline"). 런타임 동작 변경 0줄, 실기기 재검증 불필요.
독립 검증 CONDITIONAL PASS → 권고 2건(`UseKtx` 실수정 · 소실 주석 1줄 복원) 반영 후 종료. 상세 = `PROGRESS.md` 「개선 웨이브 진행」 절.

**계획 대비 편차 3건 (후속 웨이브에서 참고할 것):**
1. **baseline 17건** — 계획 표는 `GradleDependency`/`NewerVersionAvailable` "×12" 로 적었으나 실측은 13건이고 `AndroidGradlePluginVersion`×2 가 추가로 나왔다. 전부 P-5(의존성 상향 = v1.5) 동일 범주라 baseline 편입.
2. **`ExportedReceiver`×2 는 "S1 로 자동 해소" 가 아니었다** — S1 은 W1 항목이라 W0 시점에 `app/src/debug/` 가 없다. baseline 임시 편입했으므로 **W1 종료 시 baseline 에서 이 2건을 제거**해야 게이트가 정직해진다.
3. **`res/xml/backup_rules.xml` + `android:fullBackupContent` 추가** — minSdk 30(<31)이라 `dataExtractionRules` 단독으로는 lint 갭이 안 닫힌다. 계획에 없던 파일이지만 `allowBackup="false"` 와 정합(전부 제외), 런타임 영향 0.

### W1 — 보안 차단 · **[완료 2026-07-29 · `be3f337`]**
**항목:** S1(debug 소스셋 분리, probe 포함) · S4(finish 토큰) · F7(bitmap recycle) · F8(Idle+Cancel)
**실기기:** 디버그 빌드 adb 트리거 1회 · 메뉴 경유 분할 해제 1회 → **미실시 [미검증]**, `DEVICE_FACTS.md` W1 절에 4항목 등재
**DoD:** 위 + 릴리스 APK 에 `ArrangeTriggerReceiver`/probe 부재를 `aapt dump badging` 으로 확인
**규모:** Worker 1세션

**결과:** DoD 전부 PASS — 테스트 **286**(285 + F8 1) · `assembleDebug` · `lintDebug`("no new issues, 15 warnings filtered by baseline") · `assembleRelease`.
릴리스 부재 확인은 `aapt` 대신 **병합 릴리스 매니페스트 직접 검색**으로 대체(더 확정적) — `ArrangeTriggerReceiver`·`ProbeTriggerReceiver`·`ProbeActivity`·`ProbeAccessibilityService`·`FileProvider` **0건**, 디버그 병합 매니페스트에는 5종 전부 존재.
독립 검증(qa-verifier) **PASS** — 억제 정당성을 대조 실험(`lintRelease` 통과 vs `lintDebug` 실패)으로 실증.

**계획 대비 편차 3건:**
1. **`ExportedReceiver` 2건은 "S1 로 자동 해소" 가 아니었다** (W0 편차 2번의 연장). `lintDebug` 는 debug 병합 매니페스트를 보므로 리시버는 여전히 존재하고 **위치만 `src/debug/` 로 바뀌어 신규 경고가 된다.** → debug 매니페스트의 두 `<receiver>` 에 `tools:ignore="ExportedReceiver"` + 근거 주석. baseline 에서는 계획대로 2건 삭제(17 → 15).
2. **lint `ForegroundServiceType` 오탐이 새로 발생** — debug 소스셋에 `foregroundServiceType` 없는 `<service>`(=`ProbeAccessibilityService`, AccessibilityService 라 없는 게 정상)가 **별도 매니페스트 파일**로 생기자 검사기가 `FloatingLauncherService.startForeground()` 호출을 잘못 연결. 동일 코드로 `lintRelease`(매니페스트 1개)는 통과 → 오탐 확정. `startForegroundCompat()` 에 `@SuppressLint` + 진단 근거 주석(baseline 미사용).
3. **main 매니페스트 주석 2곳의 클래스명 리터럴 제거** — AGP 머저가 XML 주석을 병합 결과에 보존하는 것이 확인돼, 주석 속 `probe.ProbeActivity`/`probe.ProbeTriggerReceiver` 리터럴이 **릴리스 매니페스트까지 따라 들어갔다.** debug 매니페스트 경로 표기로 대체(내용 정확도는 오히려 개선 — "S1 이후 debug 전용" 반영).

**부수:** `Intent?.requestsFinish()` → `consumesFinishRequest()` 개명 + 부작용 KDoc. 토큰 소비라는 부작용을 가지는데 순수 질의처럼 읽혀 이중 호출 함정이 있었다.

### W2 — Shizuku 셸 하드닝
**항목:** F3 · F4 · F5 · S2 · S3 일괄
**실기기:** **필수** — P4-1 E2E 재검증(유튜브 1 + 넷플릭스 1, bounds 정확도), 17차 절차 재사용
**DoD:** 위 + `DEVICE_FACTS.md` 재검증 결과 기록. 재검증 전까지 `[미검증]` 표기
**규모:** Worker 1세션 + 실기기 세션 1회

### W3 — 기하 정합성 · 도메인 불변식 · **[완료 2026-07-29]**
**항목:** F2 1단계(가드) · F1(require + 테스트 3종)
**실기기:** 가로 배치 1회 정상 · 세로 트리거 시 명시적 실패 토스트 1회
**DoD:** 위 + `PROGRESS.md` C 항목에 "F2 2단계 = 세로 기하 실측 선행" 등재
**규모:** Worker 1세션

**결과:** DoD PASS — 테스트 **311**(304 + 7) · `assembleDebug` · `lintDebug`("no new issues, 15 warnings filtered", baseline 무변경) · `SplitPlannerTest` **70 삽입 / 0 삭제**(기존 케이스 편집 0 — 새 불변식을 통과시키려 기댓값을 완화한 곳 없음). 실기기 2항목은 `DEVICE_FACTS.md` W3 절에 `[미검증]`.
독립 검증(qa-verifier) **CONDITIONAL PASS**(조건 = `PROGRESS.md` §C 등재 자체, 코드 결함 0 — Advisor 가 반영해 해소) — 변조 3종(`matchesScreen` 무조건 true / **`&&`→`||`** / `require` 무력화) 전부 사전 예측과 정확히 일치했고, 특히 `&&`→`||` 가 1건 포착돼 폭·높이 **분리 단언**의 실효를 실증.

**계획 대비 편차 1건:**
1. **판정 함수를 서비스 private 이 아니라 `domain/` 으로 올렸다.** 위 §F2 는 `private fun geometryMatches()` 를 서비스 안에 두는 형태로 썼으나, 그러면 **톨러런스 경계가 실기기 없이는 검증 불가**하다. `WindowGeometry.matchesScreen(screen, toleranceFraction = GEOMETRY_TOLERANCE_FRACTION)` 멤버로 올려 JVM 단위 테스트 대상화 + `ArchitectureTest` 로 순수성 기계 강제(ADR-4). W2 편차 1번(`ShellCommandPolicy` → `domain/`)과 같은 취지. 검증자 판정 "더 나은 설계".

**부수:** `evaluateFlexAutoTrigger` 의 신규 게이트가 만든 `screen` 지역변수를 바로 아래 `isSplitActive(safeWindows(), screenRect())` 가 재사용하도록 정리(중복 호출 제거, 동작 등가 — 두 지점 사이 suspend 지점 없음 + `Dispatchers.Main.immediate` 단일 스레드). `startPopup` KDoc 에 "이 가드 비대상 — `PopupPlanner` 가 이미 실화면을 직접 읽음" 근거 명시.

### W4 — 테스트 안전망 (W5·W7 의 선행 조건) · **[완료 2026-07-29 · `0f53af2`]**
**항목:** T1(Robolectric 배선 + `ScreenshotSampler` 5종)
**실기기:** 불필요
**DoD:** `testDebugUnitTest` PASS (282 → 약 290)
**규모:** Worker 1세션

**결과:** DoD PASS — 테스트 **304**(299 + 5) · `assembleDebug` · `lintDebug`("no new issues, 15 warnings filtered") · **프로덕션 diff 0줄**(`ScreenshotSampler.kt`·`LetterboxDetector.kt` 무변경 — 이 웨이브의 요건).
독립 검증(qa-verifier) **PASS** — 5개 기댓값을 소스에서 전부 수기 재도출(잘못된 기댓값도 통과하는 위험을 겨냥) + 워커가 쓰지 않은 변조(`impliedAspect` 분자·분모 반전)로 비-공허성 재확인(6건 FAILED, 사전 예측과 일치).

**계획 대비 편차 2건:**
1. **테스트 3 은 계획서 문구대로는 구현 불가 — 요구 자체가 기하학적 모순이었다.** `resolveAspect` 의 raw = (frame 폭)/(content 높이), `resolveAspectPillarbox` 의 raw = (content 폭)/(frame 높이) 이므로 이미지를 리터럴 전치하면 두 항이 자리를 바꿔 **`raw_pillarbox ≡ 1/raw_letterbox`** 가 강제된다(A: 144×270, content 81 → 144/81 vs 81/144 로 실수 확인). → **프레임 치수만 전치**(144×270 → 270×144) + 콘텐츠 밴드는 B 안에서 독립 배치로 같은 16:9 재현, stride 를 명시 교차 배정(entries 축 1 / 교차축 8), **`scanA.width == scanB.width == 144` 직접 단언**으로 원래 겨냥한 회귀군(`scaledWidth=w/rowStride` ↔ `scaledHeight=h/colStride`)을 그대로 포착. 검증자 CONFIRMED.
2. **lint baseline 수동 편집이 불필요했다.** robolectric 의존성 추가가 신규 경고를 만들 것으로 예상했으나, `NewerVersionAvailable`(4.14.1→4.16.1) 은 **W0 시점부터 이미 baseline 에 있었다**(`lint-baseline.xml:148-150`) — lint 의 카탈로그 신선도 검사는 위치가 `libs.versions.toml` 버전 선언 라인이라 **실제 사용 여부와 무관하게** 카탈로그 항목을 스캔한다. `@Config(sdk = [34])` 도 1차 시도 통과라 4.16.1 상향 불필요.

**부수 발견 [미수정, v1.5]:** `toLetterboxScan:25` / `toPillarboxScan:113,115` 의 `coerceIn(0, w/2 - 1)` 은 `w==1` 이면 빈 범위로 `IllegalArgumentException`. 실입력에서 도달 불가라 W4 범위 밖으로 두고 `PROGRESS.md` C 항목에 등재 — **P4 가 이 함수를 손댈 때 함께 정리**한다.

### W5 — 구동부 안정화
**항목:** F6(이벤트 큐) · F9 v1 대응(터미널 메시지 구분)
**실기기:** DRAG 세션 1 + MENU 세션 1 무회귀
**DoD:** 위 + 전이 로그가 기존과 동일 순서인지 logcat 대조
**규모:** Worker 1세션 + 실기기 세션 1회

### W6 — 세션 상태 캡슐화 ⚠ 최대 위험 · 단독 커밋
**항목:** M1 1단계(`Session` 클래스)
**실기기:** DRAG / MENU / 취소 / 분할 해제 **4경로** 스모크
**DoD:** 위 + 순수 기계적 변환임을 diff 로 확인(동작 변경 0줄)
**규모:** Worker 1세션(집중) + 실기기 세션 1회

### W7 — 성능 · 중복 정리
**항목:** P1(단일 순회 + 예산) · M3(`NodeActions`/`Polling` 추출) · P2(runBlocking 제거) · P4(getPixels stride)
**실기기:** 진입 레시피 직격 — DRAG 1 + MENU 1 + 부팅 후 버블 복귀 1
**DoD:** 위 + 노드 탐색 로그의 `matched via selector [...]` 가 기존과 동일 셀렉터를 고르는지 대조
**규모:** Worker 1~2세션 + 실기기 세션 1회

---

## 3. 요약

| 웨이브 | 항목 수 | 실기기 세션 | 회귀 위험 |
|---|---|---|---|
| W0 무위험 정리 | 4군 | — | 없음 |
| W1 보안 | 4 | 0.5회 | 낮음 |
| W2 Shizuku | 5 | 1회 | 중 (팝업 경로 한정) |
| W3 기하·불변식 | 2 | 0.5회 | 낮음 |
| W4 테스트 | 1 | — | 없음 |
| W5 구동부 | 2 | 1회 | 중 |
| W6 세션 캡슐화 | 1 | 1회 | **높음** |
| W7 성능·중복 | 4 | 1회 | 중 |
| **합계** | **23항목** | **5회** | — |

**리뷰 지적 39건 중 23건을 v1 에서 처리하고 16건을 v1.5 로 이관**한다.
이관분은 전부 (a) 실측 선행 필요, (b) 제품 결정 필요, (c) 이득 대비 재검증 비용 초과 중 하나에 해당한다.

**최소 실행 경로:** W0 + W1 만으로 **보안 결함 2건(S1·S4)과 lint 게이트가 해소**되고 실기기 부담은 0.5회다.
시간이 없으면 여기서 끊어도 릴리스 가능 상태가 된다.

---

## 4. 착수 시 규약

- 웨이브 1개 = **커밋 1개 이상, PROGRESS.md 갱신 1회**. 항목 단위로 PROGRESS 를 쪼개지 말 것
- 실기기 재검증이 필요한 웨이브는 **검증 전까지 `DEVICE_FACTS.md` 에 `[미검증]` 명시** (CLAUDE.md DoD 5)
- W6 은 **다른 어떤 변경과도 섞지 않는다** — 순수 기계적 변환이어야 diff 리뷰가 성립한다
- 각 웨이브의 Worker 브리프에는 이 문서의 해당 절과 `CODE_REVIEW_2026-07-29.md` 의 대응 항목 번호를
  근거로 첨부한다(재탐색 방지)
- **검증된 상수는 이 계획의 어떤 항목에서도 바꾸지 않는다** (CLAUDE.md 함정 #7).
  F1 의 `require` 는 상수를 바꾸는 것이 아니라 불변식을 명시하는 것이다
