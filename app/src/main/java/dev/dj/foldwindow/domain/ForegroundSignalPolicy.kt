package dev.dj.foldwindow.domain

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙, ArchitectureTest 로 기계 검증).
 *
 * [#31, 21차 실기기 2026-08-01] `ArrangerAccessibilityService.onAccessibilityEvent` 의
 * TYPE_WINDOW_STATE_CHANGED 판정 술어를 도메인으로 올린다 — `ShellCommandPolicy`·`PanelTaskPolicy`·
 * `WindowGeometry.matchesScreen`·`BestMatchTracker` 와 같은 선례다. 서비스 안에 남겨 두면 JVM 에서
 * 검증할 수 없고, 실제로 #31 은 실기기 분리 실험(홈 경유 vs Chrome 경유) 없이는 원인을 못 짚었다.
 *
 * **#31 의 정체**: 종전 서비스 코드는 포그라운드 신호를 `pkg !in EXCLUDED_FOREGROUND_PACKAGES` 로
 * 한 번 거른 **뒤에야** `AutoTriggerLedger.onForeground` 에 넘겼다. 그런데 그 집합에는
 * `com.sec.android.app.launcher`(= 홈 화면)가 들어 있어, 「자동 배치 → 분할 해제 → 홈 → 대상 앱
 * 복귀 → 재생」 이라는 가장 흔한 경로에서 **래치의 유일한 시간 무관 해제 조건이 아예 호출되지
 * 않았다**(설계서 §6 W1-3 명세 불성립, `reason=latched` 2회 재현).
 *
 * 해법은 제외 목록을 손대는 것이 **아니다** — 그 목록은 2026-07-25 실측(오버레이·런처가 세션 중
 * `lastForegroundPkg` 를 오염)으로 만들어진 값이고 CLAUDE.md 함정 #7 의 대상이다. 대신 여기서
 * **두 신호를 분리**한다: 「추적용」([tracksAsForeground], 구 동작 그대로)과 「래치 해제용」
 * ([releasesLatch], 홈만 예외로 허용).
 */

/**
 * 포그라운드 전환 이벤트(`TYPE_WINDOW_STATE_CHANGED`)를 **어느 소비자에게 흘릴 것인가**를 정하는
 * 순수 정책.
 *
 * 같은 이벤트가 두 가지 서로 다른 목적에 쓰이는데, 종전에는 한 술어가 둘을 겸했다:
 * - **추적**: 배치 대상 앱 후보(`lastForegroundPkg`) 갱신. 오염원(런처·SystemUI·오버레이)을
 *   전부 배제해야 세션이 엉뚱한 패키지를 잡지 않는다 → [tracksAsForeground]
 * - **해제**: 자동 트리거 래치([AutoTriggerLedger.onForeground])의 해제 근거. "대상 앱을
 *   떠났다"의 증거이므로 **홈 이동도 유효한 증거**다 → [releasesLatch]
 *
 * 스레딩: 상태 없는 `object` 라 스레드 안전하다. 실제 호출은 서비스 메인 스레드에서만 일어난다.
 */
object ForegroundSignalPolicy {

    /**
     * 이 패키지를 `lastForegroundPkg`(배치 대상 후보) 로 기록해도 되는가.
     *
     * **구 동작을 문자 그대로 동결한 것이다** — `pkg != null && pkg != selfPackage &&
     * pkg !in excluded`. 여기서 걸러지는 [excluded] 는 2026-07-25 실측 근거를 가진 목록이며
     * (오버레이·런처가 세션 중 추적을 오염), 이 함수는 그 근거를 그대로 승계한다.
     *
     * @param pkg 관측된 포그라운드 패키지. null 은 "식별 실패" 이므로 기록하지 않는다.
     * @param selfPackage 우리 앱 패키지(`PanelActivity` 등 자기 창).
     * @param excluded `EXCLUDED_FOREGROUND_PACKAGES`.
     */
    fun tracksAsForeground(pkg: String?, selfPackage: String, excluded: Set<String>): Boolean {
        if (pkg == null) return false
        if (pkg == selfPackage) return false
        return pkg !in excluded
    }

    /**
     * 이 포그라운드 신호를 자동 트리거 래치의 **해제 근거**([AutoTriggerLedger.onForeground])로
     * 흘릴 것인가. 판정 순서 자체가 계약이다:
     *
     * 1. `pkg == null` → false. "모르는 것은 해제 근거가 되지 못한다"([AutoTriggerLedger.onForeground]
     *    KDoc) — 창 전환 중의 순간적 미상 블립이 래치를 조기 소멸시키면 P-1 루프가 되살아난다.
     * 2. `pkg == selfPackage` → false. 우리 [dev.dj.foldwindow.ui.PanelActivity] 가 분할 페인으로
     *    전면에 뜨는 것은 "사용자가 대상 앱을 떠났다"가 아니라 **우리가 만든 화면**이다.
     * 3. [sessionActive] → false. **이 가드가 없으면 안 된다.** 우리 분할 진입 경로(step1~3)가
     *    Recents = 런처를 지나므로, 4번 규칙과 결합하면 **실패한 자동 세션이 스스로 래치를 풀고**
     *    같은 실패를 반복하게 된다 — 설계서 D3 가 막으려던 바로 그 시나리오다. 자동 실패 후의
     *    BACK 복구 구간(`recoverAfterAutoFailure`)도 여기 포함돼야 한다: 그 구간은 상태 머신이
     *    이미 Idle 로 돌아간 뒤에 돌기 때문이다.
     * 4. `homePackage != null && pkg == homePackage` → **true**. #31 수정의 본체. 홈 이동은
     *    "대상 앱을 떠났다"의 가장 흔한 형태이며, [excluded] 에 런처가 들어 있다는 이유로
     *    이 증거를 버리면 안 된다. [homePackage] 가 null(런처 해석 실패)이면 이 예외는 적용되지
     *    않고 5번으로 내려간다 — **fail-safe = 구 동작**이다(미해제 = 오발화 없음).
     *
     *    여기서 true 는 "해제하라"가 아니라 **"래치 판정에 흘려보내라"** 다. 홈 이벤트가 실제로
     *    래치를 푸는지는 [AutoTriggerLedger.onForeground] 의 sticky 판정이 정한다 — 분할 해제로
     *    걸린 sticky 래치는 홈으로 풀리지 않는다(22차 실측: 해제 전환 중 One UI 가 노출하는 런처
     *    블립이 재래치를 0.3초 만에 무효화해 P-1 루프가 회귀했다). 그래서 호출부는 이 함수의
     *    반환값과 함께 `isHome` 을 ledger 에 넘겨야 한다.
     * 5. 그 외 → `pkg !in excluded`.
     *
     * **`com.android.systemui` 가 5번에서 계속 걸러지는 것이 중요하다.** 알림 셰이드를 내리면
     * systemui 가 `TYPE_WINDOW_STATE_CHANGED` 를 낸다(21차 실측 W1-2: 셰이드 개폐만으로 전체화면
     * 진입 엣지가 재생성된다). 셰이드가 래치를 푼다면 「셰이드 내림 → 올림 → 재발화」 로 P-1 루프가
     * 그대로 되살아난다. 4번에서 **홈만** 예외 처리하는 이유가 이것이다.
     *
     * @param pkg 관측된 포그라운드 패키지.
     * @param selfPackage 우리 앱 패키지.
     * @param homePackage 해석된 홈 런처 패키지. **null 이면 재허용하지 않는다**(구 동작 폴백).
     * @param excluded `EXCLUDED_FOREGROUND_PACKAGES`.
     * @param sessionActive 배치 세션·분할 해제·자동 실패 복구 중 하나라도 진행 중인가.
     */
    fun releasesLatch(
        pkg: String?,
        selfPackage: String,
        homePackage: String?,
        excluded: Set<String>,
        sessionActive: Boolean,
    ): Boolean {
        if (pkg == null) return false
        if (pkg == selfPackage) return false
        if (sessionActive) return false
        if (homePackage != null && pkg == homePackage) return true
        return pkg !in excluded
    }
}
