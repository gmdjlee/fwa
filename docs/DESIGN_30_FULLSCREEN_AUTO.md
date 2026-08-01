# DESIGN #30 — 전체화면 재생 자동 트리거 (A안)

> **Advisor 주석 (2026-07-31)**
>
> 이 문서의 §0~§8 은 **적대적 설계 감사 워크플로**(에이전트 50개 · 사실검증 3축 · 렌즈 4축 비평 →
> 지적 42건 → 반증 시도 → 생존 27건)의 산출물을 그대로 편입한 것이다. 감사 이전의 A안 초안은
> **BLOCKER 2건**(시간 기반 억제창이 자가유발 재진입을 막지 못함)을 갖고 있었고, 이 문서의 설계는
> 그 2건을 **패키지 단위 에피소드 래치**(`AutoTriggerLedger`)로 대체해 닫은 수정판이다.
>
> **착수 조건 조정 — §6 의 「W0 선결」 규정에 대한 예외 처리:**
> 감사 결과는 W0 실기기 프로브(W0-1·W0-3·W0-4·W0-7)를 본구현의 선결 조건으로 규정했으나,
> 구현 시점에 **실기기가 연결돼 있지 않다**(`adb devices` 결과 없음). 다음 근거로 구현을 선행하고
> W0 를 사후 측정으로 재배치한다:
>
> 1. **기능이 기본 OFF 다.** 사용자 토글(`ProfileStore.isFullscreenAutoEnabled`)의 기본값이 `false`
>    이고 게이트 2가 이를 최우선 검사하므로, 사용자가 명시적으로 켜기 전까지 이 기능은 **한 줄도
>    실행되지 않는다**(이벤트 최전방 선차단 포함). 즉 W0 미측정 상태로 병합해도 기존 동작 회귀가 0 이다.
> 2. **신호원 중립 설계다.** W0 가 술어(`FullscreenWindowJudge`)를 반증하면 판정기 구현체만 교체하면
>    되고 정책·게이트·래치·서비스 배선은 재사용된다(`FullscreenSignal` 3값 인터페이스).
> 3. **판정 규칙은 이미 실측 3표본으로 검증됐다.** Phase 0 프로브 런 ①(세로 비몰입) ·
>    런 ③(가로 몰입) · 런 ②(분할 활성) 전부에 대해 규칙이 올바른 값을 낸다(§2.1 표. 원본 덤프 =
>    `docs/DEVICE_FACTS.md` 「Phase 0 프로브 원본 측정」). W0 가 확인할 것은 *존재 여부*가 아니라 *경계 상태*(일시정지·컨트롤
>    표시·가로 상하 분할·셰이드)다.
> 4. **미측정 위험은 전부 fail-safe 방향이다.** 미디어 게이트가 One UI 에서 죽으면(R5) 기능이 조용히
>    미발화할 뿐 오발화하지 않는다. 술어가 플리커하면(D9) 래치가 진입당 1회로 묶는다.
>
> **따라서 §6 의 W0 항목은 전부 `[미검증]` 으로 `docs/DEVICE_FACTS.md` 에 등재하며,
> 사용자 토글을 켜기 전에 W0-1·W0-3·W0-4·W0-7 을 먼저 측정할 것을 강한 권고로 남긴다.**
> 구현 코드에는 W0 를 logcat 만으로 수행할 수 있도록 **신호 전이 로깅**을 넣는다(§9, Advisor 추가).

---

## 9. Advisor 추가 요구 — W0 를 logcat 으로 수행 가능하게 할 것

감사 설계에 없던 항목이다. 실기기 프로브를 위해 별도 빌드를 만들지 않아도 되도록, 아래 두 지점에
**전이에서만** 로그를 남긴다(매 샘플 로깅은 금지 — 최대 10Hz 스팸이 된다).

1. `ArrangerAccessibilityService.onWindowsChangedEvent` — `FullscreenPlaybackPolicy.lastSignal` 이
   **바뀐 경우에만**
   `Log.i(TAG, "fullscreen signal: $prev -> $next screen=${w}x${h} appFull=$n topBars=$m")`.
   `appFull`/`topBars` 는 판정 근거 개수로, W0-1~W0-6 의 창 목록 판정을 logcat 만으로 재구성하게 한다.
2. `evaluateFullscreenAutoTrigger` 의 미디어 게이트 — `MediaPlaybackProbe` 가 관측한 `usage` 목록을
   `Log.i(TAG, "fullscreen media probe: usages=$list")` 로 남긴다(W0-7 을 그대로 충족).

두 로그 모두 자동 트리거가 **켜져 있을 때만** 나온다(선차단 뒤에 위치). 기본 OFF 이므로 평시 무영향.

---

**작성 기준일 2026-07-31 / 대상 리포 `D:/wp_2026/fwa` (HEAD `a20b386`)**

---

## 0. 결함 ID 대조표 (이 문서 전용 라벨)

| ID | 심각도 | 요지 |
|---|---|---|
| D1 | BLOCKER | 시간 억제창은 상태 문제의 잘못된 해법 — 셰이드/잠금해제가 새 진입 엣지를 만들어 사용자의 명시적 해제를 무효화 |
| D2 | BLOCKER | 억제창 크기를 원리상 정할 수 없음 (해제→재진입 간격이 사용자 페이스) |
| D3 | MAJOR | 실패한 자동 세션이 스스로 재무장 엣지를 생산 (백오프·서킷브레이커 부재) |
| D4 | MINOR | `placementSource=="FLEX"` 를 트리거 기원 프록시로 재사용 → 오취소 + 잘못된 토스트 |
| D5 | MINOR | 콜드스타트(서비스 리바인드) 첫 샘플이 진입 엣지로 취급됨 |
| D6 | MINOR | UNKNOWN 무시 정책의 비대칭 → 이탈 유실 시 영구 래치 |
| D7 | MINOR | 게이트 체인 원자성 계약 미문서화 (suspend 삽입 시 토스트 경합) |
| D8 | MAJOR | P-1 반증 표본이 **포트레이트 좌우 분할** — 우리 산출물(가로 상하 분할) 창 목록 미측정 |
| D9 | MAJOR | 판정 술어가 일시정지/컨트롤 표시에 플리커할 가능성 — 술어 자체가 미측정 |
| D10 | MAJOR | systemui 소형 창(토스트/볼륨패널)이 상단 스트립과 교차해 오판 |
| D11 | MAJOR | 넷플릭스에 자동 발화 = 자사 온보딩 경고("재생 중 배치 금지") 위반 |
| D12 | MAJOR | `onAccessibilityEvent` 메인스레드 N+1 IPC — 세션 예산 잠식 |
| D13 | MINOR | '프로파일 보유' 게이트가 미확인 패키지명을 기능 영구 미발화로 격상 |
| D14 | MAJOR | 레버가 빌드타임 JSON 자산 — 사용자가 켤 수도 끌 수도 없음 |
| D15 | MAJOR | 자동 세션 실패 시 사용자를 Recents/피커에 유기, 복구 경로 0 |
| D16 | MAJOR | 「버블 중지」가 자동 트리거를 끄지 못함 — 되돌리기 표면 없이 화면을 바꿈 |
| D17 | MAJOR | 세로 영상(필러박스)에 발화하면 영상이 61% 축소되는데 "잔여 0px 성공" 보고 |
| D18 | MAJOR | 3초 디바운스 + 4~12초 세션 = 예고·취소 없는 7~15초 화면 강탈 |
| D19 | MAJOR | 자동 세션 성공/실패마다 진단 문구 `LENGTH_LONG` 토스트 |
| D20 | MAJOR | 되돌리기가 미공개 롱프레스 2스텝, 온보딩에 설명 0 |
| D21 | MINOR | IPC 를 지는 매퍼의 파일 소속 미지정 → 2172행 서비스로 흡수 |
| D22 | MINOR | 레버 기본 false + 토글 부재 = 실기기 검증 불가 |

---

## 1. 판정: **GO_WITH_CHANGES**

BLOCKER 2건(D1, D2)은 동일 근본 원인 — **"자가유발 재진입 억제창"이라는 시간 기반 대책** — 을 가리키며, 이는 설계 수정으로 완전히 닫힌다: 시간창을 폐기하고 **패키지 단위 에피소드 래치**(`AutoTriggerLedger`)로 교체한다. 래치는 발화 시점(성공·실패 무관)과 분할 해제 시점에 걸리고, 해제 조건이 시간이 아니라 사건(포그라운드 이탈 / 사용자 수동 트리거)이므로 D1 의 "셰이드 개폐가 새 엣지를 만든다", D2 의 "간격이 사용자 페이스라 상수 선택 불가"가 둘 다 성립하지 않게 된다. 나머지 MAJOR 14건도 전부 국소 수정으로 닫히거나(D3·D4·D7·D10·D11·D12·D14·D15·D16·D19·D20), 대상 축소·비목표 선언으로 노출을 제한한다(D17·D18). 단 **D8·D9 는 설계 분기점이 미측정 데이터 위에 서 있으므로, 본구현(W1) 착수 전 선행 프로브 차수(W0)를 필수 선결로 둔다** — W0 결과가 술어를 반증하면 판정기 구현체만 교체하고 정책·게이트·래치는 그대로 재사용할 수 있도록 신호원 중립 인터페이스로 설계한다. A안 초안 대비 실질 변경은 (a) 억제창→래치, (b) 부정 술어→긍정 술어(형상 기반, IPC 1회), (c) 게이트 3개 추가(bubble-off / autoArrange / latch), (d) `TriggerSource` 도입, (e) 사용자 토글 UI 신설이다.

---

## 2. 수정된 설계 (파일 단위)

### 2.1 신규 — `domain/`

#### `app/src/main/java/dev/dj/foldwindow/domain/FullscreenPlaybackPolicy.kt` (신규, **domain**)
**책임**: 전체화면 신호(3값)의 엣지 검출 + 비대칭 히스테리시스 + 진입 디바운스. `FlexModePolicy` 와 동형 — 시간은 `nowMs` 인자로만 받고 내부에 시계·센서·SDK 참조 0.

```kotlin
/** 전체화면 형상 신호. 신호원(창 목록/인셋)에 중립적이다 — 판정기가 이 값으로 압축해 넘긴다. */
enum class FullscreenSignal { FULLSCREEN, NOT_FULLSCREEN, UNKNOWN }

class FullscreenPlaybackPolicy(
    private val entryDebounceMs: Long = DEFAULT_ENTRY_DEBOUNCE_MS,
    private val exitHoldMs: Long = DEFAULT_EXIT_HOLD_MS,
) {
    companion object {
        const val DEFAULT_ENTRY_DEBOUNCE_MS = 3_000L
        const val DEFAULT_EXIT_HOLD_MS = 1_200L
    }

    /** 이번 진입 구간이 아직 살아 있는지. 서비스 조건 폴링의 종료 조건 */
    val isArmed: Boolean
    /** 마지막으로 흡수한 non-UNKNOWN 신호. 게이트 로깅·재검증용 */
    val lastSignal: FullscreenSignal

    /** @return 진입 엣지가 확정됐을 때만 재검증 예약 시각(nowMs + entryDebounceMs). 그 외 null */
    fun onSignal(signal: FullscreenSignal, nowMs: Long): Long?
    /** arm 을 소모하지 않는 조회. 미디어 재확인을 게이트 체인 밖에서 하기 위해 필요 */
    fun isTriggerReady(nowMs: Long): Boolean
    /** true 반환 시 arm 소모 + stable=FULLSCREEN 확정. 재무장은 이탈 후 재진입만 */
    fun shouldTriggerNow(nowMs: Long): Boolean
    fun disarm()
    fun reset()
}
```

**의미론 (핵심 3가지)**
1. **콜드스타트 보호(D5)**: 내부 `stable: FullscreenSignal?` 이 `null` 인 동안 첫 샘플은 **베이스라인으로만 기록하고 절대 arm 하지 않는다**. `CoverDismissPolicy` 의 `armed` 가드와 동일 형태.
2. **비대칭 히스테리시스(D9)**: `NOT_FULLSCREEN` 은 `exitHoldMs` 이상 **연속 유지**되어야 이탈로 확정된다. 그 미만의 깜빡임은 흡수되고 진입 디바운스 타이머를 리셋하지 않는다 → 컨트롤 표시/transient bar/창 목록 비원자 재구축(DEVICE_FACTS.md:186)이 가짜 진입 엣지를 만들지 못한다.
3. **UNKNOWN 무시의 안전화(D6)**: `UNKNOWN` 은 상태를 바꾸지 않는 무시 샘플이되, 판정기가 `UNKNOWN` 을 반환하는 경우를 **창 목록이 빈 경우 하나로만** 좁힌다(§2.2). 그 외 판정 불확실은 전부 `NOT_FULLSCREEN`(보수적)으로 접는다.

**닫는 결함**: D5, D6, D9, D2(부분)

---

#### `app/src/main/java/dev/dj/foldwindow/domain/FullscreenWindowJudge.kt` (신규, **domain**)
**책임**: 창 서술자 리스트 → `FullscreenSignal`. 순수 기하. `PaneGeometry` 선례를 따라 `IntRect` 만 다룬다. **`WindowKind` 같은 a11y 타입 열거형을 domain 에 들이지 않는다**(AccessibilityWindowInfo.TYPE_* 의 2차 SSOT 방지, D21).

```kotlin
/** 판정에 필요한 최소 서술자. platform 매퍼가 a11y 타입을 Boolean 하나로 압축해 넘긴다 */
data class WindowBox(val bounds: IntRect, val isApplication: Boolean)

object FullscreenWindowJudge {
    /** 상단 스트립 높이 = 화면 높이 × 이 비율 (함정 #2: 좌표 하드코딩 금지) */
    const val TOP_STRIP_FRACTION = 0.06f
    /** "화면을 통째로 덮는다" 판정 하한 (가로·세로 각 축 비율) */
    const val FULL_COVER_MIN_FRACTION = 0.99f
    /** 상단 시스템 바로 인정할 최소 폭 비율. 토스트·볼륨패널·자기 버블을 구조적으로 배제 */
    const val TOP_BAR_MIN_WIDTH_FRACTION = 0.80f

    fun judge(windows: List<WindowBox>, screen: IntRect): FullscreenSignal
}
```

**판정 규칙 (긍정 술어 — A안 초안의 부정 술어를 폐기)**
```
windows 가 비어 있으면            → UNKNOWN
아래 둘을 모두 만족하면            → FULLSCREEN
  (a) isApplication == true 이고 bounds 가 screen 을 각 축 ≥99% 덮는 창이 1개 이상 존재
  (b) isApplication == false 이면서
      bounds.top <= screen.height * TOP_STRIP_FRACTION 이고
      bounds.width >= screen.width * TOP_BAR_MIN_WIDTH_FRACTION 인 창이 0개
그 외                             → NOT_FULLSCREEN
```

**이 규칙이 실측 3표본에서 내는 값** (전부 검증 완료. 근거 덤프 = `docs/DEVICE_FACTS.md`
「Phase 0 프로브 원본 측정」 — 런 ① 세로 비몰입 / 런 ② 분할 활성 / 런 ③ 가로 몰입)

| 상태 | 근거 | (a) 전체덮음 | (b) 상단 전폭 비-APP | 판정 |
|---|---|---|---|---|
| 유튜브 가로 몰입 재생 | 런 ③ B절 `APPLICATION 0,0,2184,1968` | ✓ | 없음 | **FULLSCREEN** ✓ |
| 세로 비몰입 (엣지투엣지) | 런 ① B절 (APP 0,0,1968,2184 + `SYSTEM 0,0,1968,89`) | ✓ | 있음(폭 100%) | **NOT_FULLSCREEN** ✓ |
| 분할 활성 | 런 ② B절 (페인 991,0,… / 0,0,977,…) | ✗ | — | **NOT_FULLSCREEN** ✓ |
| systemui 소형 창 | 런 ② B절 `381,89,595,145` (폭 10.9%) | — | 폭 미달 → 무시 | 영향 없음 ✓ **D10 해결** |
| 런처 우측 SYSTEM 창 | 런 ① B절 `1915,235,1968,564` (폭 2.7%) | — | 폭 미달 → 무시 | 영향 없음 ✓ |
| 알림 셰이드 | 전폭 상단 비-APP 창 | — | 있음 | **NOT_FULLSCREEN** ✓ |
| 자기 버블(TYPE_SYSTEM, ~126px) | DEVICE_FACTS.md:174 | — | 폭 미달 → 무시 | 영향 없음 ✓ |

**부수 효과**: 판정에 `packageName` 이 전혀 필요 없다 → `root?.packageName` 노드 조회 0회 → 평가 1회당 바인더 왕복 **1회**(`windows` 뿐). D12 의 N+1 비용과 D6 의 "root 조회 null → 가짜 UNKNOWN" 이 동시에 소멸한다.

**닫는 결함**: D6, D8(분할 오판정 부분), D10, D12(부분), D21

---

#### `app/src/main/java/dev/dj/foldwindow/domain/AutoTriggerLedger.kt` (신규, **domain**)
**책임**: **D1·D2 의 유일한 해법.** 시간 억제창을 대체하는 패키지 단위 에피소드 래치 + 연속 실패 서킷브레이커. `TriggerSource` enum 을 같은 파일에 둔다(`FlexModePolicy.kt` 가 `FoldPosture` 를 동거시키는 선례).

```kotlin
enum class TriggerSource {
    MANUAL, SHORTCUT, FLEX_AUTO, FULLSCREEN_AUTO;
    val isAuto: Boolean get() = this == FLEX_AUTO || this == FULLSCREEN_AUTO
}

class AutoTriggerLedger(private val maxFailStreak: Int = DEFAULT_MAX_FAIL_STREAK) {
    companion object { const val DEFAULT_MAX_FAIL_STREAK = 2 }

    /** 이 패키지에 대해 자동 트리거가 이미 소진됐는가(재발화 금지) */
    fun isLatched(pkg: String): Boolean
    /** 연속 실패로 이 부팅 세션 동안 자동 트리거가 영구 비활성인가 */
    fun isDisabled(pkg: String): Boolean

    /** 자동 발화 직전 호출 — 성공·실패와 무관하게 래치한다 (D3) */
    fun onAutoFired(pkg: String)
    /** 자동 세션 터미널 보고 시 — 실패 스트릭 증감 */
    fun onAutoResult(pkg: String, success: Boolean)
    /** 사용자 수동 배치 = 자동화 재신뢰 신호 → 래치·스트릭 해제 */
    fun onManualTrigger(pkg: String)
    /** 포그라운드 전환. 래치 패키지를 떠나면 래치 해제 (유일한 시간 무관 해제 조건) */
    fun onForeground(pkg: String?)
    /** 우리 분할이 해제됐다 → 마지막 자동 대상 패키지를 재래치 (D1·D2 핵심) */
    fun onSplitDismissed()
    fun reset()
}
```

**래치 계약 (명시)**
- **세팅**: ① 자동 발화 직전(`onAutoFired`) ② 우리 분할 해제 관측 시(`onSplitDismissed`)
- **해제**: ① 포그라운드가 래치 패키지를 떠남 ② 사용자가 **수동으로** 배치를 트리거. **시간은 해제 조건이 아니다.**
- 결과: 셰이드 개폐·잠금해제·일시정지/재생·transient bar·해제 후 전체화면 자동복귀 — 이 모든 가짜/진짜 진입 엣지가 래치 게이트에서 조용히 죽는다. D1 의 시나리오("10분 뒤 알림 확인 → 재발화")와 D2 의 시나리오("20분 시청 후 해제 → 30초 뒤 재발화")가 **둘 다 차단된다.**
- 트레이드오프(명시): 같은 앱에서 다음 영상으로 넘어가도 자동 재발화하지 않는다. 탈출구는 버블 탭(수동)이며, 그것이 곧 재신뢰 신호로 래치를 푼다.

**닫는 결함**: **D1, D2**, D3

---

### 2.2 신규 — `platform/`

#### `app/src/main/java/dev/dj/foldwindow/platform/FullscreenSignalSampler.kt` (신규, **platform**)
**책임**: `AccessibilityWindowInfo` → `WindowBox` 매핑. `DividerLocator.applicationPaneRects` 와 동일 형태(`runCatching` 방어, 기하는 domain 위임). **IPC 를 지는 유일한 지점이며 파일 소속을 명시적으로 고정한다(D21).**

```kotlin
class FullscreenSignalSampler {
    /** package 조회(root 노드 IPC)를 하지 않는다 — type/bounds 만으로 판정 가능하다 */
    fun sample(windows: List<AccessibilityWindowInfo>, screen: IntRect): FullscreenSignal
}
```
- `w.type == AccessibilityWindowInfo.TYPE_APPLICATION` → `isApplication = true`
- `getBoundsInScreen` 실패 창은 리스트에서 **제외하지 말고 버린다**(그 결과 (a)가 거짓이 되면 자연히 보수적 `NOT_FULLSCREEN`)
- `windows` 가 비면 `judge` 가 `UNKNOWN` 반환

**닫는 결함**: D6, D12, D21

---

#### `app/src/main/java/dev/dj/foldwindow/platform/MediaPlaybackProbe.kt` (신규, **platform**)
**책임**: 무권한 미디어 재생 확증. **동기 함수**(`suspend` 금지 — D7), **무상태**, **결과 캐싱 금지**.

```kotlin
class MediaPlaybackProbe(context: Context) {
    /** USAGE_MEDIA 활성 재생 존재 여부. contentType 은 게이트 조건에서 제외(ExoPlayer 기본 UNKNOWN) */
    fun isMediaPlaying(): Boolean
}
```
구현 = `audioManager.activePlaybackConfigurations.any { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA }`, 전체를 `runCatching { … }.getOrDefault(false)` 로 감싼다.
**금지 사항(명시)**: `getPlayerState()`/`isActive()`/`getClientUid()` 리플렉션 호출 금지(전부 `@hide @SystemApi` 이고 비특권 사본에서는 익명화됨), `isPackagePlaying(pkg)` 류 API 신설 금지(패키지 귀속 불가), `registerAudioPlaybackCallback` 등록 금지(창 이벤트가 이미 wake 신호).
**게이트 의미 격하(명시)**: 이 게이트는 "대상 앱이 재생 중"이 아니라 **"기기에서 미디어 오디오가 나고 있음"** 이라는 약한 필요조건이다.

**닫는 결함**: D7

---

### 2.3 수정 — 기존 파일

#### `app/src/main/java/dev/dj/foldwindow/domain/Profiles.kt` (**domain**)
```kotlin
data class ProfileDefaults(
    …,
    /** A안 개발자 킬스위치. 기존 4레버와 동일하게 **부재=true**. 사용자 옵트인은 ProfileStore 토글이 담당 */
    val fullscreenAutoArrange: Boolean = true,
)

data class AppProfile(
    …,
    /** 전체화면 자동 배치 대상인가. **부재=false(옵트인)** — '프로파일 보유'와 '자동 대상'을 분리한다 */
    val autoArrange: Boolean = false,
)
```
`validate()` 는 Boolean 을 검사하지 않으므로 무변경.
**닫는 결함**: D11, D13, D14, D22

#### `app/src/main/java/dev/dj/foldwindow/data/WindowProfilesParser.kt` (**data**)
`DefaultsDto` 에 `val fullscreenAutoArrange: Boolean = true`, `ProfileDto` 에 `val autoArrange: Boolean = false` 추가 + `mapDefaults`/`mapProfile` 에 전달 한 줄씩.

#### `app/src/main/java/dev/dj/foldwindow/data/ProfileStore.kt` (**data**)
```kotlin
suspend fun isFullscreenAutoEnabled(): Boolean =
    safeRead(false) { …[KEY_FULLSCREEN_AUTO] ?: false }
suspend fun setFullscreenAutoEnabled(enabled: Boolean)
```
키 상수는 `ProfileStoreMapping` 에 `KEY_FULLSCREEN_AUTO = "fullscreen_auto_enabled"` 추가(기존 관례).
**닫는 결함**: D14, D22

#### `config/window_profiles.json` (**SSOT**)
`com.google.android.youtube` 프로파일에만 `"autoArrange": true` 추가. 나머지 4개는 키 부재(=false). `defaults` 블록은 무수정(레버 키 부재=true).
**닫는 결함**: D11, D13

#### `app/src/main/java/dev/dj/foldwindow/service/ArrangerAccessibilityService.kt` (**service**)
신규 필드:
```kotlin
private val fullscreenPolicy = FullscreenPlaybackPolicy()
private val autoLedger = AutoTriggerLedger()
private val fullscreenSampler = FullscreenSignalSampler()
private val mediaProbe by lazy { MediaPlaybackProbe(this) }   // DI 없음 — 프로젝트 관례
private var fullscreenCheckJob: Job? = null
@Volatile private var fullscreenLeverSnapshot: Boolean = false   // onServiceConnected 에서 1회 워밍
```
시그니처 변경:
```kotlin
fun startArrange(
    placementOverride: Placement?,
    aspectOverride: Float?,
    triggerSource: TriggerSource = TriggerSource.MANUAL,   // 기본값으로 기존 호출부 무변경
)
/** PanelActivity.onDestroy 훅 — 모든 분할 해제 경로를 포착한다 (D1·D2) */
fun onPanelDestroyed()
```
신규 private:
```kotlin
private fun onWindowsChangedEvent()                 // 이벤트 진입점 (선차단 포함)
private suspend fun awaitFullscreenTrigger(checkAtMs: Long)
private suspend fun evaluateFullscreenAutoTrigger()
private suspend fun recoverAfterAutoFailure(targetPackage: String)
```
기존 코드 수정:
| 위치 | 변경 | 닫는 결함 |
|---|---|---|
| `onAccessibilityEvent` (:284) | `TYPE_WINDOWS_CHANGED` 분기 신설. **최전방 선차단** — `fullscreenLeverSnapshot==false ‖ machineState!=Idle ‖ sessionInFlight ‖ dismissInFlight ‖ !FloatingLauncherService.isRunning` 이면 `windows` 를 읽기 전에 `return`. 통과 시에만 `fullscreenSampler.sample(safeWindows(), screenRect())` → `fullscreenPolicy.onSignal(...)` → 예약 job. `TYPE_WINDOW_STATE_CHANGED` 분기에 `autoLedger.onForeground(pkg)` 한 줄 추가 | D12, D1 |
| `startArrange` 가드 3개 (:322/:329/:337) | 토스트를 `if (!triggerSource.isAuto)` 로 감쌈. busy 가드에 `dismissInFlight` 추가 | D7, D19 |
| `Session` (:185) | `val triggerSource: TriggerSource` 필드 추가 | D4 |
| posture-exit 취소 (:835-840) | 조건을 `session?.triggerSource == TriggerSource.FLEX_AUTO` 로 교체. `placementSource` 는 배치 근거 전용으로 분리 | D4 |
| `reportTerminal` Done (:1875) | `if (!triggerSource.isAuto) toast(message)` — 자동은 `Log.i` 만 | D19 |
| `reportTerminal` Done last-success 저장 (:1888) | 조건에 `&& !triggerSource.isAuto` 추가 | D4 |
| `reportTerminal` Failed (:1913) | 자동이면 `Toast.LENGTH_SHORT` + `"자동 배치에 실패했습니다"`, 그리고 `autoLedger.onAutoResult(pkg,false)` + `scope.launch { recoverAfterAutoFailure(pkg) }` | D3, D15, D19 |
| `reportTerminal` Done | `autoLedger.onAutoResult(pkg, true)` | D3 |

`recoverAfterAutoFailure` 계약(ADR-2 준수): `performGlobalAction(GLOBAL_ACTION_BACK)` **1회**만 주입한 뒤 `activeAppPackage() == targetPackage` 를 150ms 간격 조건 폴링(데드라인 2500ms). 타임아웃 시 `Log.i` 만 남기고 추가 주입 없음. 고정 지연으로 성공을 가정하지 않는다.

#### `app/src/main/java/dev/dj/foldwindow/service/FloatingLauncherService.kt` (**service**)
`showMenu()` 의 `bubble_menu_settings` 앞에 토글 항목 1개 추가:
```kotlin
container.addMenuItem(getString(
    if (fullscreenAutoEnabled) R.string.bubble_menu_fullscreen_auto_on
    else R.string.bubble_menu_fullscreen_auto_off
)) { dismissMenuThenToggleFullscreenAuto() }
```
값은 메뉴 표시 직전 `store.isFullscreenAutoEnabled()` 스냅샷.
**닫는 결함**: D14, D16, D22

#### `app/src/main/java/dev/dj/foldwindow/ui/PanelActivity.kt` (**ui**)
`onDestroy()` 에 한 줄: `ArrangerAccessibilityService.instance?.onPanelDestroyed()`. 이 지점이 (a) 메뉴 해제 (b) 커버 자동 해제 (c) 자가 가드 finish (d) 사용자 BACK/디바이더 드래그 **네 경로를 모두 포착**하는 유일한 지점이다.
**닫는 결함**: D1, D2

#### `app/src/main/java/dev/dj/foldwindow/ui/OnboardingActivity.kt` + `res/values/strings.xml` (**ui**)
가이드 카드 1장 추가: `onboarding_guide_longpress` = "버블을 **길게 누르면** 위/아래 배치, **분할 해제**, 비율 선택, 전체화면 자동 배치 켜기/끄기 메뉴가 열립니다."
메뉴 문자열 2개 추가(`bubble_menu_fullscreen_auto_on/off`).
**닫는 결함**: D20, D14

---

## 3. 게이트 체인 최종 순서

### 3.1 트리거 대기 루프 — `awaitFullscreenTrigger(checkAtMs)`

```
delay(checkAtMs - now)                    // ADR-2: 예약 시각 도달 수단일 뿐
deadline = now + FULLSCREEN_TRIGGER_POLL_TIMEOUT_MS
while (fullscreenPolicy.isArmed && now < deadline) {
    fullscreenPolicy.onSignal(fullscreenSampler.sample(safeWindows(), screenRect()), now)   // 라이브 재검증
    if (fullscreenPolicy.isTriggerReady(now)) {
        if (!mediaProbe.isMediaPlaying()) { delay(POLL); continue }    // ← 미디어는 재시도, disarm 금지
        if (fullscreenPolicy.shouldTriggerNow(now)) { evaluateFullscreenAutoTrigger(); return }
    }
    delay(FULLSCREEN_POLL_INTERVAL_MS)
}
if (fullscreenPolicy.isArmed) { fullscreenPolicy.disarm(); Log.i(TAG, "…reason=trigger-poll-timeout") }
```
**미디어 게이트가 게이트 체인이 아니라 이 루프에 있는 이유(KDoc 필수)**: `isMediaPlaying()` 은 광고 전환·seek·언더런에서 순간적으로 요동치는 상태다. 다른 게이트(기하·포그라운드 등)처럼 즉시 disarm 하면 정당한 발화를 잃는다. 동시에, 게이트 체인 안에 두면 `busy`~`startArrange` 원자 구간이 깨진다(D7). 두 요구를 동시에 만족하는 위치는 루프뿐이다.

### 3.2 게이트 체인 — `evaluateFullscreenAutoTrigger()`

첫 실패에서 `fullscreenPolicy.disarm()` + `Log.i(TAG, "fullscreen auto-arrange skipped: reason=…")` 후 return. **토스트 없음**(`evaluateFlexAutoTrigger` KDoc :892-895 원칙 승계).

| # | 게이트 | 조건(실패) | 실패 시 동작 | 닫는 결함 |
|---|---|---|---|---|
| 1 | `lever-off` | `loadProfilesConfig()?.defaults?.fullscreenAutoArrange ?: true` == false | disarm + log | D14 |
| 2 | `user-toggle-off` | `store.isFullscreenAutoEnabled()` == false | disarm + log | D14, D18, D22 |
| — | **⚠ 이하 `startArrange` 까지 suspend 호출 금지 (원자 구간)** | | | D7 |
| 3 | `bubble-off` | `FloatingLauncherService.isRunning` == false | disarm + log | D16 |
| 4 | `busy` | `machineState != Idle ‖ sessionInFlight ‖ dismissInFlight` | disarm + log | D7 |
| 5 | `geometry-mismatch` | `!geometry.matchesScreen(screenRect())` | disarm + log(screen 크기) | Shorts·세로 차단 |
| 6 | `split-already-active` | `dividerLocator.isSplitActive(safeWindows(), screen)` | disarm + log | — |
| 7 | `display-off` | `displayState != Display.STATE_ON` | disarm + log | — |
| 8 | `foreground-unsuitable` | pkg == null ‖ 자기자신 ‖ homePackage ‖ `EXCLUDED_FOREGROUND_PACKAGES` | disarm + log(pkg) | — |
| 9 | `not-auto-target` | `config?.profiles?.firstOrNull { it.packageName == pkg }?.autoArrange != true` (config==null 이면 **실패**) | disarm + **`Log.i(… reason=not-auto-target pkg=$pkg)`** | D11, D13 |
| 10 | `latched` | `autoLedger.isLatched(pkg)` | disarm + log(pkg) | **D1, D2** |
| 11 | `auto-disabled` | `autoLedger.isDisabled(pkg)` | disarm + log(pkg, streak) | D3 |
| → | 통과 | `autoLedger.onAutoFired(pkg)` → `startArrange(null, null, TriggerSource.FULLSCREEN_AUTO)` | | |

**게이트 9 의 `config == null` 폴백 방향**: 레버 게이트의 `?: true` 관용구를 따르지 않고 **false(발화 안 함)** 로 한다 — 자산 파싱 실패 상태에서 자동으로 화면을 바꾸는 것은 정당화되지 않는다.
**게이트 9 의 로그에 실제 관측 패키지명을 반드시 넣는다** — 시드 JSON 의 미확인 패키지명(`com.wavve.wavve`)을 사용자 로그 1줄로 진단 가능하게 하는 유일한 수단이다(D13).

---

## 4. 상수와 근거

| 상수 | 값 | 위치 | 근거 |
|---|---|---|---|
| `DEFAULT_ENTRY_DEBOUNCE_MS` | `3_000L` | `FullscreenPlaybackPolicy` | 유튜브 플레이어 컨트롤 오염 실측(DEVICE_FACTS.md:230 G2 — 탭 직후 트리거 시 pre band 370/160 비대칭). 컨트롤 자동 숨김 이후로 pre-measure 를 밀어 MEASURED 채택률을 지킨다. 대조군 DEVICE_FACTS.md:231 G3(전체화면 재생 중 트리거 3/3 SNAP_AGREE). **컨트롤 자동 숨김 실제 시간은 [미검증]** — W0-2 로 확정 |
| `DEFAULT_EXIT_HOLD_MS` | `1_200L` | `FullscreenPlaybackPolicy` | **[미검증]** 창 목록 비원자적 재구축(DEVICE_FACTS.md:186) 흡수용. 기존 `WINDOWS_SETTLE_TIMEOUT_MS`(1200ms) 와 같은 눈금을 쓴다. W0-3/W0-6 (transient bar 자동 숨김 시간) 측정 후 조정 |
| `FULLSCREEN_POLL_INTERVAL_MS` | `250L` | 서비스 companion | `FLEX_ANGLE_POLL_INTERVAL_MS`(:2127) 와 동일 관례. 틱당 작업 = 바인더 1회(`windows`) + 산술. ADR-2 조건 폴링이지 고정 지연 아님 |
| `FULLSCREEN_TRIGGER_POLL_TIMEOUT_MS` | `5_000L` | 서비스 companion | **[미검증]** 절대 데드라인. 창 이벤트는 힌지 센서와 달리 "화면이 정적이면 오지 않으므로" 이탈이 관측되지 않을 수 있다 — 이 데드라인이 폴링의 무조건 종료 보장이다 |
| `AUTO_RECOVERY_POLL_INTERVAL_MS` | `150L` | 서비스 companion | 파일 내 다른 조건 폴링(150ms) 관례 |
| `AUTO_RECOVERY_TIMEOUT_MS` | `2_500L` | 서비스 companion | **[미검증]** BACK 주입 후 대상 앱 포그라운드 복귀 대기. 실측 E2E 전환 시간(4.2~4.7s 세션 중 1스텝) 대비 여유값 |
| `AutoTriggerLedger.DEFAULT_MAX_FAIL_STREAK` | `2` | `AutoTriggerLedger` | **[미검증]** 실기기 실패 클래스(#27 17차 4연속 전멸, #29 MENU menuStep4 3attempt 전멸)가 재현성 있음을 근거로 보수적으로 2회. 3회 이상 시도할 근거 없음 |
| `TOP_STRIP_FRACTION` | `0.06f` | `FullscreenWindowJudge` | 실측 상태바 높이 89px / 화면 1968px(가로) = 4.52%, /2184px(세로) = 4.07%. 여유 포함 6%. **비율로 계산(함정 #2)** |
| `TOP_BAR_MIN_WIDTH_FRACTION` | `0.80f` | `FullscreenWindowJudge` | 실측 상태바 폭 = 화면 폭 100%(런 ① `0,0,1968,89`). 배제 대상 최대 폭 = systemui 소형 창 214px/1968 = 10.9%(런 ② `381,89,595,145`), 런처 우측 창 53px = 2.7%. 80% 는 두 군집 사이 어디에 두어도 동일 결과이나 여유를 크게 잡음 |
| `FULL_COVER_MIN_FRACTION` | `0.99f` | `FullscreenWindowJudge` | 실측 전체화면 창 `0,0,2184,1968` = 정확히 100%. 분할 페인 최대 = 977/1968 = 49.6%. `geometry.matchesScreen` 의 ±1% 허용오차와 같은 눈금 |

---

## 5. JVM 단위 테스트 목록 (20개)

### `app/src/test/java/dev/dj/foldwindow/domain/FullscreenPlaybackPolicyTest.kt`
1. `first signal after construction records baseline and does not arm` — 생성 직후 `onSignal(FULLSCREEN, 0)` → 반환 `null`, `isArmed==false` (**D5**)
2. `not-fullscreen baseline then fullscreen arms and schedules entry debounce` — `(NOT,0)` → `(FULL,100)` 반환 `100+3000`, `isArmed==true`
3. `duplicate fullscreen samples inside candidate window do not reschedule` — `(FULL,100)`,`(FULL,200)` → 두 번째 `null`
4. `shouldTriggerNow is false before debounce elapses` — 경계값 `now=3099` false, `now=3100` true
5. `shouldTriggerNow consumes arm exactly once` — 연속 2회 호출 시 두 번째 false
6. `brief not-fullscreen blip shorter than exitHold does not reset entry debounce` — `(FULL,100)`,`(NOT,500)`,`(FULL,900)` → `shouldTriggerNow(3100)==true` (**D9**)
7. `not-fullscreen held for exitHold disarms and requires new entry edge` — `(FULL,100)`,`(NOT,500)`,`(NOT,1701)` → `isArmed==false`; 이후 `(FULL,2000)` 이 새 예약 반환 (경계: 1700 미만은 유지)
8. `fullscreen held stable does not produce a second entry edge` — 발화 후 `(FULL, …)` 반복이 전부 `null`
9. `unknown between fullscreen samples does not swallow the next entry edge` — `(FULL,100)`,`(UNKNOWN,500)`,`(NOT,600)`,`(NOT,1801)`,`(FULL,2000)` → 새 예약 반환 (**D6**)
10. `unknown never arms and never disarms` — `stable` 미변경, `isArmed` 불변
11. `isTriggerReady does not consume arm` — `isTriggerReady` 2회 true 후 `shouldTriggerNow` true
12. `disarm blocks trigger until exit and re-entry` — `disarm()` 후 `(FULL,…)` 반복 무반응, `(NOT × exitHold)` → `(FULL)` 에서 재무장
13. `reset restores cold-start guard` — `reset()` 후 첫 `FULLSCREEN` 이 arm 하지 않음

### `app/src/test/java/dev/dj/foldwindow/domain/FullscreenWindowJudgeTest.kt`
14. `empty window list yields UNKNOWN`
15. `real fullscreen dump yields FULLSCREEN` — 런 ③(가로 몰입) 3창 그대로(sidegesturepad 2 + APP 0,0,2184,1968 / screen 2184×1968)
16. `real portrait non-immersive dump yields NOT_FULLSCREEN` — 런 ①(세로 비몰입) 7창 그대로(전폭 `0,0,1968,89` 존재)
17. `real split dump yields NOT_FULLSCREEN` — 런 ②(분할 활성) 7창 그대로 (**D8**)
18. `narrow systemui window intersecting top strip is ignored` — `381,89,595,145` 를 fullscreen 덤프에 추가해도 `FULLSCREEN` 유지 (**D10**). 경계: 폭 = `screen.width*0.80 - 1` → 무시, `*0.80` → `NOT_FULLSCREEN`
19. `top strip boundary` — 상단 바 `top = height*0.06` → 교차 판정, `top = height*0.06 + 1` → 무시
20. `application window covering 98 percent is not full cover` — `FULL_COVER_MIN_FRACTION` 경계 (0.99 미만 → `NOT_FULLSCREEN`)

### `app/src/test/java/dev/dj/foldwindow/domain/AutoTriggerLedgerTest.kt`
21. `latched package blocks re-fire until foreground leaves` (**D1**)
22. `split dismissal re-latches last auto package` — `onAutoFired("yt")` → `onForeground("other")`(해제) → `onForeground("yt")` → `onSplitDismissed()` → `isLatched("yt")==true` (**D2**)
23. `manual trigger clears latch and fail streak`
24. `two consecutive auto failures disable the package` — 경계: 1회 실패 후 `isDisabled==false`, 2회 후 true; 성공 1회가 스트릭을 0으로 리셋 (**D3**)
25. `foreground null does not clear latch` — 창 전환 블립에서 래치가 조기 소멸하지 않음

### `app/src/test/java/dev/dj/foldwindow/data/WindowProfilesParserTest.kt` (수정)
26. 시드 동결: `assertEquals(true, config.defaults.fullscreenAutoArrange)` — **주석 필수**: "이 레버는 개발자 킬스위치라 부재=true(기존 4레버와 동일). 사용자 옵트인은 ProfileStore 토글이 담당한다"
27. 시드 동결: `youtube.autoArrange == true`, `netflix/tving/watcha/wavve.autoArrange == false` (**D11**)
28. `autoArrange` 명시 true/false 파싱 + 키 부재 시 false

### `app/src/test/java/dev/dj/foldwindow/ArchitectureTest.kt` (무수정, 자동 적용)
- 신규 domain 3파일이 `android|androidx|kotlinx.serialization` import 0 임을 기계 강제

---

## 6. 실기기 검증 항목 (W0 = 본구현 착수 전 **선결** / W1 = 구현 후)

| # | 차수 | 항목 | 확인 절차 (1줄) |
|---|---|---|---|
| W0-1 | 선결 | 가로 2184×1968 유튜브 몰입 재생(컨트롤 숨김) 창 목록 | 프로브 실행 → `judge` 규칙 수기 적용 → `FULLSCREEN` 확인, `docs/DEVICE_FACTS.md` 기록 |
| W0-2 | 선결 | 같은 상태에서 화면 1탭(컨트롤 표시) 창 목록 + 컨트롤 자동 숨김 소요 시간 | 탭 직후 프로브 → 상단 전폭 systemui 창 존부 확인, 스톱워치로 숨김 시간 측정 (**D9**, `ENTRY_DEBOUNCE` 근거) |
| W0-3 | 선결 | 일시정지 + 컨트롤 표시 창 목록 | 프로브 → 술어 값 확인. 여기서 `NOT_FULLSCREEN` 이면 D9 성립 → `EXIT_HOLD_MS` 를 컨트롤 표시 지속시간 이상으로 재산정 |
| W0-4 | 선결 | **우리 기능이 만든 가로 상하 분할** 창 목록 | 버블로 수동 배치 완료 후 프로브 → 상단 전폭 systemui 창 존부 + 페인 bounds 기록 (**D8**) |
| W0-5 | 선결 | 알림 셰이드 내린 상태 창 목록 | 셰이드 개방 상태에서 프로브 → 전폭 상단 비-APPLICATION 창 확인 |
| W0-6 | 선결 | 상단 스와이프 transient bar 상태 + 자동 숨김 시간 | 몰입 중 상단 스와이프 → 프로브 → 창 재등장 확인, 자동 숨김까지 시간 측정 |
| W0-7 | 선결 | `getActivePlaybackConfigurations()` 4상태 | 유튜브 재생 중 / 일시정지 / **스트림 볼륨 0** / 앱내 음소거 각각에서 `usage` 목록을 logcat 에 남김 (One UI 변형 확인, A14+ 뮤트 인지 필터 확인) |
| W0-8 | 선결 | Shorts 진입 시 `screenRect()` | Shorts 전체화면에서 logcat `screen=` 로그 확인 → 세로(1968×2184)면 게이트 5 로 자동 차단 확정 |
| W1-1 | 구현 후 | 자동 발화 E2E — 유튜브 가로 전체화면 진입 → 3s → 배치 완료 | logcat `fullscreen auto-arrange trigger:` → `arrange done:` 확인, 무토스트 확인 |
| W1-2 | 구현 후 | **P-1 루프 부재** | 자동 배치 → 분할 해제 → 전체화면 복귀 → 5분 방치 + 셰이드 3회 개폐 + 일시정지/재생 3회 → 재발화 0건, `reason=latched` 로그 확인 |
| W1-3 | 구현 후 | 래치 해제 경로 | 자동 배치 → 홈 → 유튜브 복귀 → 전체화면 → 재발화 1회 확인 |
| W1-4 | 구현 후 | 실패 복구 | 자동 세션을 강제 실패시킨 뒤(예: 세션 중 Recents 에서 임의 조작) BACK 주입으로 유튜브가 전면 복귀하는지 확인 (**D15**) |
| W1-5 | 구현 후 | 서킷브레이커 | 2회 연속 자동 실패 후 `reason=auto-disabled` 로그 + 재발화 0건 |
| W1-6 | 구현 후 | bubble-off 게이트 | 버블 중지 상태에서 전체화면 진입 → `reason=bubble-off` 로그 + 발화 0건 (**D16**) |
| W1-7 | 구현 후 | 사용자 토글 | 롱프레스 메뉴에서 켜기/끄기 → 각각 발화/미발화 확인, 재부팅 후 값 유지 확인 (**D14, D22**) |
| W1-8 | 구현 후 | 콜드스타트 | 유튜브 몰입 재생 중 접근성 서비스 껐다 켜기 → 즉시 발화 0건 확인 (**D5**) |
| W1-9 | 구현 후 | 세로 영상(직캠) 자동 발화 결과 | 발화 시 영상이 축소되는지 육안 확인 + 토스트 문구 기록 → **D17 실측 근거 확보**(v1.5 게이트 설계 입력) |
| W1-10 | 구현 후 | 넷플릭스 술어 값 | 넷플릭스 몰입 재생 중 프로브 → 술어 값 기록(발화는 `autoArrange=false` 로 차단됨을 로그로 확인) (**D11**) |
| W1-11 | 구현 후 | 메인 스레드 부담 | 자동 트리거 ON 상태에서 수동 배치 20회 → `ENTRY_STEP_FAILED` 발생률이 OFF 대조군 대비 증가하지 않음 (**D12**) |

---

## 7. 명시적 비목표

| 비목표 | 이유 |
|---|---|
| **WindowInsets 기반 2차 신호원** (버블 오버레이 `setOnApplyWindowInsetsListener`) | W0-1~W0-6 이 창 목록 술어를 확증하면 불필요. 확증 실패 시에만 v1.5 로 승격 — 신호원 중립 인터페이스(`FullscreenSignal`)라 정책·게이트·래치 재작성 없이 교체 가능 |
| **원탭 되돌리기** (분할 활성 중 버블 탭 의미를 `dismissSplit` 으로 전환) | 탭 의미를 무고지로 바꾸는 것 자체가 새 발견성 위험이고, `performDismissSplit` 은 비동기 2초 폴링이라 탭 의미를 동기적으로 고를 근거가 못 된다. v1 은 온보딩 가이드 카드로 대체 |
| **필러박스(세로 영상) 이득 판정 게이트** (D17) | 판별자가 `pre==null` 이면 DRM 앱(프로파일 5개 중 4개)이 통째로 오탐된다. 올바른 판별자는 열축(pillarbox) 양성 스캔인데 미구현이다. v1 은 자동 대상을 유튜브 1개로 좁히고 사용자 토글로 끌 수 있게 하는 것으로 노출을 제한, W1-9 실측 후 v1.5 에서 설계 |
| **「제안 + 원탭 수락」 UX 격하** (D18) | 버블 상태 전환 UI·펄스 애니메이션·수락 타임아웃이 별도 설계 표면을 만든다. v1 은 사용자 토글(기본 OFF) + 래치(진입당 1회) + 대상 1앱으로 노출을 최소화하고, W1 실사용 피드백 후 재검토 |
| **자동 대상 앱 확대** (넷플릭스·티빙·왓챠·웨이브) | 넷플릭스는 자사 온보딩이 "재생 중 배치 금지"를 명시하고 실측이 재생 세션 파괴를 3회+ 재현했다(D11). 나머지 3개는 패키지명조차 `[미확인]`(DEVICE_FACTS.md:329). 실측 없이 자동 대상에 넣지 않는다 |
| **`MediaSessionManager.getActiveSessions()`** | 알림 접근 권한(`NotificationListenerService`)이 필요해 두 번째 특수 접근 온보딩이 생긴다. 접근성 서비스 예외 경로는 AOSP 에 존재하지 않는다. 무권한 원칙과 충돌 |
| **기존 분할 재배치** (분할 활성 중 재조정) | 게이트 6(`split-already-active`)이 명시적으로 배제. 종전 범위 유지 |
| **온보딩 설정 화면 신설** | 토글 1개를 위해 새 화면을 만들지 않는다. 이미 존재하는 버블 롱프레스 메뉴에 항목 1개 추가 |
| **`startArrange(silent: Boolean)` 파라미터** | 공개 API 표면을 넓히고 "조용한 실패 금지" 원칙과 정면 충돌한다. `triggerSource.isAuto` 를 재사용해 동일 효과를 얻는다 |

---

## 8. 남은 위험

| # | 위험 | 완화책 | 잔여 |
|---|---|---|---|
| R1 | **W0 이 술어를 반증**할 수 있다 — 일시정지/컨트롤 표시에서 상태바가 복귀하면(D9) 진입 엣지가 사용자 조작마다 생긴다 | `EXIT_HOLD_MS` 를 실측 컨트롤 표시 지속시간 이상으로 상향 + 래치가 진입당 1회로 제한 | 컨트롤을 `EXIT_HOLD` 이상 띄웠다 숨기면 새 엣지 발생. 그러나 래치가 걸린 상태면 무해. **래치가 안 걸린 첫 회에만 유효한 위험** |
| R2 | **W0-4 가 "가로 상하 분할에서 상태바 복귀"를 보이면**(D8) 분할 진입 순간 이탈 엣지 → 해제 순간 진입 엣지가 실제로 발생한다 | 래치는 시간이 아니라 사건 기반이므로 **이 결과와 무관하게 성립한다**(`onSplitDismissed` 가 재래치) | 없음 — 래치 설계가 D8 의 두 갈래 모두에 대해 방어적이다 |
| R3 | **세로 영상 자동 축소**(D17) — 사용자가 직캠을 볼 때 영상이 61% 작아지고 "잔여 0px" 로 보고된다 | 대상 = 유튜브 1개 + 사용자 토글 기본 OFF + 래치로 진입당 1회 + 롱프레스 메뉴 안내(D20) | 유튜브에서 세로 콘텐츠를 자주 보는 사용자에게는 실질 피해. W1-9 실측 → v1.5 열축 게이트 필수 |
| R4 | **BACK 주입 복구가 예상과 다르게 동작**(D15) — 분할-선택 모달에서 BACK 이 전체화면 재생으로 깨끗이 복귀하는지 실측 기록 0 | 1회만 주입 + 조건 폴링 + 실패 시 로그만. 추가 주입 없음 | W1-4 로 확인. 복구가 무효면 사용자는 여전히 피커에 남지만, 오늘의 수동 실패와 동일 수준(회귀 아님) |
| R5 | **One UI 의 `AudioService` 변형**(사실검증 미확정) — `getActivePlaybackConfigurations()` 가 AOSP 와 다르게 동작할 수 있다 | 미디어 게이트는 루프 내 재시도(즉시 disarm 금지)이고, 실패해도 발화 누락(fail-safe)일 뿐 오발화가 아니다 | W0-7 로 확인. 항상 빈 목록이면 기능이 조용히 죽는다 → W0-7 이 W1 착수의 게이트 |
| R6 | **메인 스레드 IPC 증가**(D12) — 판정 1회당 `windows` 바인더 1회가 최대 10Hz | 이벤트 최전방 선차단(레버·busy·bubble-off) + package 조회 0회 + 세션 중 완전 스킵 | 정량 측정 없음. W1-11 A/B 로 확인 |
| R7 | **래치 트레이드오프** — 같은 앱에서 다음 영상으로 넘어가도 자동 재발화하지 않는다 | 버블 탭(수동)이 즉시 배치 + 래치 해제(재신뢰). 롱프레스 메뉴 안내 카드로 발견성 확보 | 사용자가 "가끔만 되는 기능"으로 인식할 수 있다. v1.5 에서 "미디어 무재생 관측 시 래치 해제" 추가 검토 |
| R8 | **`FLEX_AUTO` 와 `FULLSCREEN_AUTO` 동시 무장** — 노트북 자세로 거치한 채 전체화면 재생 시작 | 먼저 도달한 쪽이 `sessionInFlight=true`, 나중 쪽은 자기 busy 게이트에서 조용히 disarm. `triggerSource` 분리로 posture-exit 오취소도 해소(D4) | 없음 — 두 체인 모두 최상단 suspend 이후 동기라 원자성이 유지된다 |
| R9 | **`fullscreenAutoArrange` 킬스위치 상시 true** — 사용자 토글만이 실질 방어선 | 토글 기본값 false(DataStore), 부팅 후에도 유지. 킬스위치는 원격 회수 불가 상황(사이드로드 배포)에서 다음 릴리스의 즉시 무력화 수단으로만 존재 | 사용자가 토글을 켠 뒤 문제를 겪으면 같은 메뉴에서 끌 수 있다(D16 해결). 앱 삭제·접근성 OFF 가 유일 탈출구였던 상태는 해소됨 |

---

## 부록 — 작업 순서 (구현 브리프용)

```
W0  선행 프로브 차수 (실기기, 코드 변경 없음 또는 probe/ 로그 1줄)
    → W0-1 ~ W0-8 측정 → docs/DEVICE_FACTS.md 기록
    → 술어 확정 / ENTRY_DEBOUNCE·EXIT_HOLD 확정
W1a domain 3파일 신설 + JVM 테스트 28개  (실기기 불필요, 병렬 위임 가능)
W1b platform 2파일 신설                  (W1a 의 domain 타입 의존)
W1c data 3파일 수정 (Profiles/Parser/ProfileStore) + 시드 JSON  (W1a 와 병렬 가능)
W1d service 배선 + PanelActivity 훅 + FloatingLauncherService 메뉴 + 온보딩 카드
W1e 검증: ./gradlew :app:testDebugUnitTest / :app:assembleDebug / :app:lintDebug
W2  실기기 검증 차수 W1-1 ~ W1-11 → PROGRESS.md / DEVICE_FACTS.md 갱신
```

**착수 금지 조건**: W0-1, W0-3, W0-4, W0-7 중 하나라도 미측정이면 W1 를 시작하지 않는다. 이 네 항목이 각각 술어 성립(D9)·이탈 히스테리시스 상수·P-1 반증 재검(D8)·미디어 게이트 생존(R5)을 결정한다.