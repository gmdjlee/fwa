package dev.dj.foldwindow.domain

/**
 * #20 클릭-사이클 에스컬레이션에서 쓰는 클릭 디스패치 메커니즘.
 * (docs/DESIGN_20_CLICK_CYCLE.md §2-1)
 */
enum class ClickMechanism {
    /** 노드 중심 좌표에 접근성 제스처(탭)를 디스패치 — 손가락 입력과 동일한 히트테스트 경로를 탄다. */
    GESTURE_TAP,

    /** `AccessibilityNodeInfo.performAction(ACTION_CLICK)` — 히트테스트를 우회해 노드 identity 로 직접 라우팅된다. */
    A11Y_ACTION,
}

/**
 * #20 클릭-사이클 에스컬레이션 계획. 사이클(0-based)마다 어떤 메커니즘으로 클릭을 디스패치할지,
 * 노드 탐색/결과 검증에 각각 얼마의 폴링 예산을 줄지를 명시한다. 순수 데이터 — 실행 로직은
 * `platform/SplitEntry.clickUntilCondition` / `platform/PaneSwapper.swap` 이 담당한다.
 *
 * ## 근거 (docs/DESIGN_20_CLICK_CYCLE.md §1, §2-1)
 *
 * [code-certain] AOSP `View.performAccessibilityActionInternal` 의 ACTION_CLICK 처리는
 * `isClickable` 이면 `performClickInternal()` 을 부르고 그 반환값을 버린 채 무조건 `true` 를
 * 돌려준다. 즉 **ACTION_CLICK 의 true 는 "노드가 살아있고 클릭 가능하다"만 보장**할 뿐, 히트테스트·
 * z-order·부모 `onInterceptTouchEvent`/`onTouchEvent` 같은 하류 터치 파이프라인이 실제로
 * 실행됐는지는 보장하지 못한다. 실측 실패(피커 탭 무효 2건, "창 전환" 무효 2건)는 전부
 * ACTION_CLICK=true 였다 — 클릭 자체는 실행됐지만 하류에서 무효화된 것으로 추정된다.
 *
 * 좌표 기반 제스처 탭은 손가락 입력과 동일한 히트테스트 경로를 타므로 이 실패 클래스를
 * 구조적으로 우회한다. 대신 제스처 탭은 히트테스트 기반이라 **자기 앱의 터치 가능 오버레이가
 * 화면 위에 떠 있으면 그 오버레이가 탭을 삼킨다**는, ACTION_CLICK 에는 없던 새 실패 모드가
 * 생긴다 — 이 가드는 `SplitEntry.clickUntilCondition` 이 수행한다.
 *
 * ## 프로파일별 순서 근거
 *
 * [PICKER] (step3/menuStep4 피커 탭, gesture-first): 실측 최악 조합(4/7 세션 실패) +
 * "startActivityFromRecents 오라우팅" 실패 클래스가 a11y 클릭 전용(노드 identity 라우팅이라
 * 히트테스트 없이 인접한 비-피커 노드로 샐 수 있음) + 런처 창 대상 제스처 탭 인접 실증(디바이더
 * 핸들 탭 6/6 성공) — gesture 를 먼저 시도한다. 마지막 사이클만 A11Y_ACTION 으로 남겨 제스처
 * 탭이 (미측정) 거부되는 경우의 사다리를 유지한다.
 *
 * [POPUP_SWITCH] ("전환" 팝업 클릭, a11y-first): 동일 팝업 계열에서 ACTION_CLICK 이 6/6
 * 성공(유튜브 페인 스왑 성공 포함) — 검증된 메커니즘을 검증된 컨텍스트에서 1순위로 유지한다.
 *
 * 롤백 레버: 메커니즘 순서는 데이터일 뿐이다. 실기기에서 gesture-first 가 새 실패 클래스를
 * 만들면 [PICKER] 를 `[A11Y_ACTION, GESTURE_TAP, GESTURE_TAP]` 으로 한 줄만 바꾸면 된다.
 *
 * @property findSliceMs 사이클 1회당 [find] 노드 탐색에 배정하는 폴링 예산(ms).
 * @property verifySliceMs 사이클 1회당 디스패치 후 성공 조건 수렴을 기다리는 폴링 예산(ms).
 *   [MIN_VERIFY_SLICE_MS] 이상이어야 한다.
 * @property mechanisms 사이클 인덱스(0-based)별 클릭 메커니즘. 인덱스가 리스트 길이를 넘으면
 *   (음수 포함) 마지막 원소로 클램프한다([mechanismFor]).
 */
data class ClickCyclePlan(
    val findSliceMs: Long,
    val verifySliceMs: Long,
    val mechanisms: List<ClickMechanism>,
) {
    init {
        require(findSliceMs > 0) { "findSliceMs 는 양수여야 한다: $findSliceMs" }
        // doubleTapTimeout(~300ms) 을 확실히 초과해야 연속된 사이클의 탭이 더블탭으로 오인되지 않는다.
        require(verifySliceMs >= MIN_VERIFY_SLICE_MS) {
            "verifySliceMs 는 doubleTapTimeout 초과 보장을 위해 최소 ${MIN_VERIFY_SLICE_MS}ms 이상이어야 한다: $verifySliceMs"
        }
        require(mechanisms.isNotEmpty()) { "mechanisms 는 최소 1개 이상이어야 한다" }
    }

    /** [cycle](0-based) 사이클에 사용할 메커니즘. 리스트 길이를 넘으면(음수 포함) 마지막 원소로 클램프한다. */
    fun mechanismFor(cycle: Int): ClickMechanism = mechanisms.getOrElse(cycle) { mechanisms.last() }

    companion object {
        /** doubleTapTimeout(~300ms) 초과 보장 하한 — 연속 탭이 더블탭으로 오인되는 것을 막는다. */
        const val MIN_VERIFY_SLICE_MS = 400L

        /** step3/menuStep4 파트너 피커 탭 전용 (클래스 KDoc "프로파일별 순서 근거" 참고). */
        val PICKER = ClickCyclePlan(
            findSliceMs = 600L,
            verifySliceMs = 800L,
            mechanisms = listOf(ClickMechanism.GESTURE_TAP, ClickMechanism.GESTURE_TAP, ClickMechanism.A11Y_ACTION),
        )

        /** PaneSwapper "전환" 팝업 클릭 전용 (클래스 KDoc "프로파일별 순서 근거" 참고). */
        val POPUP_SWITCH = ClickCyclePlan(
            findSliceMs = 600L,
            verifySliceMs = 800L,
            mechanisms = listOf(ClickMechanism.A11Y_ACTION, ClickMechanism.GESTURE_TAP, ClickMechanism.GESTURE_TAP),
        )
    }
}
