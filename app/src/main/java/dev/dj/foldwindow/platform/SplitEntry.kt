package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import dev.dj.foldwindow.domain.BestMatchTracker
import dev.dj.foldwindow.domain.ClickCyclePlan
import dev.dj.foldwindow.domain.ClickMechanism
import dev.dj.foldwindow.domain.IntRect
import dev.dj.foldwindow.domain.PaneGeometry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * P2-3: 분할 화면 진입 (docs/DEVICE_FACTS.md 2026-07-25 재검증 반영).
 *
 * `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` 은 이 기기에서 분할 전/중 모두 false 를 반환함이
 * 실기기 3회 실행으로 확정됐다 (docs/DEVICE_FACTS.md #6 FAILS). Recents 를 여는 것까지는 맞았지만,
 * 리사이저블 앱 기본 경로에서 **카드 팝업 메뉴의 "분할 화면으로 열기" 경로는 [반증]됐다** —
 * 가로(시청) 상태에서도 좌우(L/R) 분할을 만들어 상하(T/B) 분할이 필요한 이 앱에는 무용하다
 * (실기기 확인, 2026-07-25). 대신 **카드 아이콘을 화면 상단 가장자리로 홀드-드래그**하면
 * 상하(T/B) 분할-선택 상태가 만들어짐이 `input draganddrop` 으로 실증됐다.
 *
 * 이후(2026-07-25) 넷플릭스류 `PRIVATE_FLAG_ACTIVITIES_RESIZE_MODE_UNRESIZEABLE` 앱에서는
 * 드래그 레시피가 One UI 에 의해 팝업(프리폼)으로 라우팅되어 상하 분할-선택 진입 자체가
 * 불가능함이 확인됐다. 이 앱들을 위해 **MENU 레시피**(카드 메뉴 "분할 화면으로 열기" →
 * 좌우 분할 선택 → 피커 탭 → 디바이더 핸들 탭 → 팝업 "시계 방향으로 회전" → 상하 전환)를
 * 우회 경로로 확정했다 — "메뉴 경로는 좌우만 만든다"는 [반증]은 여전히 유효하지만,
 * **회전 단계와 결합하면 UNRESIZEABLE 앱의 유일한 진입 경로**가 된다.
 *
 * 진입 레시피는 [EntryRecipe] 로 분기된다:
 *  - [EntryRecipe.DRAG] (3단계, 리사이저블 앱 기본): Recents 열기 → 카드 상단 드래그(분할-선택
 *    진입) → 파트너 배치.
 *  - [EntryRecipe.MENU] (5단계, UNRESIZEABLE 전용): Recents 열기 → 카드 메뉴 탭(분할 메뉴 노출)
 *    → "분할 화면으로 열기" 탭(좌우 분할-선택 진입) → 피커에서 파트너 탭 → 디바이더 핸들 탭 →
 *    "시계 방향으로 회전" 탭(상하 전환).
 *
 * 이 클래스는 ArrangeStateMachine 을 모른다 — `ArrangeEffect.PerformEntryStep(step)` 을 받은
 * 서비스 레이어가 step 번호 하나씩 호출하고, 반환된 성공/실패를 ArrangeEvent.EntryStepResult 로
 * 머신에 되먹임한다. 상태(재시도 횟수, 타임아웃 판정)는 전부 머신 쪽 책임이다.
 *
 * ADR-2: 고정 지연 금지. 모든 대기는 (조건 폴링 + 타임아웃) 으로 구현한다.
 *
 * [#20, 2026-07-25] step3/menuStep4 의 피커 탭은 [ClickCyclePlan] 기반 클릭-사이클
 * 에스컬레이션([clickUntilCondition])으로 재구현됐다 (docs/DESIGN_20_CLICK_CYCLE.md).
 */
class SplitEntry(
    private val service: AccessibilityService,
    /**
     * [#20] 제스처 탭(GESTURE_TAP)은 손가락 입력과 동일한 히트테스트 경로를 타므로, 자기 앱의
     * 터치 가능 오버레이(플로팅 버블/확장 메뉴)가 화면 위에 떠 있으면 그 오버레이가 탭을
     * 가로채 대상 노드까지 도달하지 못한다 — ACTION_CLICK 에는 없던 새 실패 모드다(함정 #22
     * 계열). platform 계층은 service/ 를 몰라야 하므로(레이어링 유지) 판정 자체는 이 람다로
     * 주입받는다 — 서비스가 `FloatingLauncherService.hasAttachedOverlayWindow()` 를 넘긴다.
     */
    private val ownOverlayVisible: () -> Boolean,
) {

    /** 핸들 탭 → "시계 방향으로 회전" 팝업 클릭 로직은 서비스 레이어의 위치 교정 폴백과 공유한다. */
    private val rotator = DividerPopupRotator(service)

    /**
     * [ctx.recipe] 레시피의 step 하나를 실행하고, 그 단계의 성공 조건을 [timeoutMs] 안에서
     * [POLL_INTERVAL_MS] 간격으로 폴링해 확인한다.
     *
     * 예외는 삼키고 false 로 변환한다 (CancellationException 은 구조적 동시성을 지키기 위해 재던짐).
     * 잘못된 step 번호는 즉시 false.
     */
    suspend fun performStep(step: Int, ctx: EntryContext, timeoutMs: Long): Boolean {
        return try {
            when (ctx.recipe) {
                EntryRecipe.DRAG -> performDragStep(step, ctx, timeoutMs)
                EntryRecipe.MENU -> performMenuStep(step, ctx, timeoutMs)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "performStep: recipe=${ctx.recipe} step=$step 실행 중 예외", e)
            false
        }
    }

    private suspend fun performDragStep(step: Int, ctx: EntryContext, timeoutMs: Long): Boolean =
        when (step) {
            1 -> step1OpenRecents(ctx, timeoutMs)
            2 -> step2DragToTopEdge(ctx, timeoutMs)
            3 -> step3PlacePartner(ctx, timeoutMs)
            else -> {
                Log.w(TAG, "performDragStep: invalid step number $step (1..3 만 유효)")
                false
            }
        }

    private suspend fun performMenuStep(step: Int, ctx: EntryContext, timeoutMs: Long): Boolean =
        when (step) {
            1 -> step1OpenRecents(ctx, timeoutMs) // DRAG 레시피와 동일 — Recents 열기
            2 -> menuStep2TapCardIcon(ctx, timeoutMs)
            3 -> menuStep3TapSplitMenuNode(ctx, timeoutMs)
            4 -> menuStep4TapPartnerInPicker(ctx, timeoutMs)
            5 -> menuStep5RotateDivider(ctx, timeoutMs)
            else -> {
                Log.w(TAG, "performMenuStep: invalid step number $step (1..5 만 유효)")
                false
            }
        }

    // ══════════════════════════════════════════════════════════
    // Step 1 — Recents 열기 (DRAG·MENU 공통)
    // ══════════════════════════════════════════════════════════

    /**
     * 동작: GLOBAL_ACTION_RECENTS.
     * 성공 조건: 대상 카드 아이콘 노드가 나타남 (launcher 패키지 창 안에서).
     */
    private suspend fun step1OpenRecents(ctx: EntryContext, timeoutMs: Long): Boolean {
        val accepted = runCatching {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }.getOrDefault(false)
        if (!accepted) {
            // 실측상 이 경로는 accepted=true 로 확인됐지만, 조용한 실패 금지 원칙상 로그는 남긴다.
            Log.w(TAG, "step1: GLOBAL_ACTION_RECENTS 가 false 반환 — 그래도 성공 조건 폴링은 계속함")
        }
        return pollUntil(timeoutMs) { findCardIconNode(ctx) != null }
    }

    // ══════════════════════════════════════════════════════════
    // DRAG 레시피 Step 2 — 대상 앱 카드 아이콘을 화면 상단 가장자리로 홀드-드래그
    // ══════════════════════════════════════════════════════════

    /**
     * [실기기 확인, 2026-07-25] `input draganddrop 592 322 1092 150 800`
     * (카드 아이콘 중심 → 화면 상단 가장자리 중심) 이 상하(T/B) 분할-선택 상태를 만들어냄이 실증됐다.
     * `input draganddrop` = 롱프레스 후 드래그 — 접근성 제스처로는 [holdThenDrag] 로 재현한다.
     *
     * 동작: 카드 아이콘 노드를 찾되(클릭하지 않는다) bounds 중심을 시작점으로, 화면 상단 가장자리
     * (`screen.top + DROP_MARGIN_PX`) 를 도착점으로 holdThenDrag.
     * 성공 조건: [PaneGeometry.isSplitSelectTopPane] — 대상 창이 전폭·상단 도킹·가시 높이
     * 비율 15~75% 를 모두 만족하는 상하 분할-선택 상태. (팝업/프리폼 오탐 차단 — 실측 근거는
     * DEVICE_FACTS.md "step2/3 성공 조건 허점" 참조.)
     *
     * [회귀 수정, 실기기 확인 2026-07-25] 유튜브 DRAG E2E 회귀 로그 분석 결과 두 가지 판정
     * 버그가 확인됐다:
     *  1. 유령 매치 즉시 실패 — `structural-clickable-label` 셀렉터가 bounds 조회 불가 노드를
     *     매치하면 시도 전체가 수 ms 만에 false 로 끝났다. 이제는 유효 bounds 를 못 얻어도
     *     시도를 끝내지 않고 재폴링을 계속한다.
     *  2. 성공 미인지 재시도 — 드래그가 물리적으로 성공해도 전환 애니메이션 정착이 폴링 잔여
     *     예산(관찰: 정착 직전 ~370ms 남음, 정착에는 더 필요)을 넘기면 실패로 판정됐고, 다음
     *     시도는 이미 사라진 Recents 카드를 재탐색하다 영원히 실패했다. 이제는 매 폴링 주기마다
     *     먼저 목표 상태(분할-선택 상단) 도달 여부를 확인해 이전 시도의 늦은 정착을 흡수한다.
     */
    private suspend fun step2DragToTopEdge(ctx: EntryContext, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        fun remaining() = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)

        val acquired = pollForValue(remaining()) {
            if (isTargetInSplitSelectTopState(ctx)) {
                Step2Acquire.AlreadyThere
            } else {
                val iconNode = findCardIconNode(ctx)
                if (iconNode == null) {
                    null
                } else {
                    val rect = Rect()
                    val gotBounds =
                        runCatching { iconNode.getBoundsInScreen(rect) }.isSuccess && !rect.isEmpty
                    if (gotBounds) {
                        Step2Acquire.BoundsReady(rect)
                    } else {
                        // 유령 매치 — 시도를 끝내지 않고 다음 폴링 주기로 넘어간다.
                        Log.w(TAG, "step2: 카드 아이콘 bounds 조회 실패 — 유령 매치, 재폴링 계속")
                        null
                    }
                }
            }
        }
        if (acquired == null) {
            Log.w(TAG, "step2: ${timeoutMs}ms 안에 목표 상태 도달도, 유효 카드 아이콘도 확보하지 못함")
            return false
        }
        return when (acquired) {
            is Step2Acquire.AlreadyThere -> {
                Log.i(TAG, "step2: 분할-선택 상태 이미 도달 — 드래그 생략")
                true
            }
            is Step2Acquire.BoundsReady -> {
                val bounds = acquired.bounds
                val dropX = ctx.screen.centerX()
                val dropY = ctx.screen.top + EntrySelectors.DROP_MARGIN_PX
                Log.i(
                    TAG,
                    "step2: holdThenDrag icon(${bounds.centerX()},${bounds.centerY()}) -> ($dropX,$dropY)",
                )
                val gestureCompleted = suspendHoldThenDrag(
                    fromX = bounds.centerX(),
                    fromY = bounds.centerY(),
                    toX = dropX,
                    toY = dropY,
                )
                if (!gestureCompleted) {
                    // step1 과 동일한 관례: 제스처 완료 콜백이 false 여도(예: 타이밍 경합) 실제 시스템
                    // 반응은 비동기일 수 있으므로 성공 조건 폴링은 그대로 계속한다. 조용히 포기하지 않는다.
                    Log.w(TAG, "step2: holdThenDrag 완료 콜백=false — 그래도 상태 폴링 계속")
                }
                pollUntil(remaining()) { isTargetInSplitSelectTopState(ctx) }
            }
        }
    }

    /** [step2DragToTopEdge] 통합 폴링 결과: 목표 상태에 이미 도달했는지, 유효 bounds 를 확보했는지. */
    private sealed class Step2Acquire {
        object AlreadyThere : Step2Acquire()
        data class BoundsReady(val bounds: Rect) : Step2Acquire()
    }

    /** [holdThenDrag] 의 콜백을 suspend 로 잇는다. 정확히 한 번만 재개된다. */
    private suspend fun suspendHoldThenDrag(fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean =
        suspendCancellableCoroutine { cont ->
            holdThenDrag(
                service = service,
                fromX = fromX,
                fromY = fromY,
                toX = toX,
                toY = toY,
                holdMs = EntrySelectors.DRAG_HOLD_MS,
                moveMs = EntrySelectors.DRAG_MOVE_MS,
            ) { completed ->
                if (cont.isActive) cont.resume(completed)
            }
        }

    // ══════════════════════════════════════════════════════════
    // DRAG 레시피 Step 3 — 파트너(우리 패널)를 반대쪽 페인에 배치
    // ══════════════════════════════════════════════════════════

    /**
     * [#20 재작성, 2026-07-25] launcher `FromRecentActivity` 피커에서 우리 패널 라벨 노드를
     * [ClickCyclePlan.PICKER] 로 클릭-사이클 에스컬레이션한다 (docs/DESIGN_20_CLICK_CYCLE.md).
     *
     * 옛 전략2(FALLBACK, `startActivity(ctx.panelIntent)` LAUNCH_ADJACENT)는 완전히 삭제했다.
     * [측정 2026-07-25] 분할 선택 중 LAUNCH_ADJACENT 는 전체화면으로 떠서 전략1(피커)이 만든
     * 분할 선택 상태 자체를 파괴했고, 구조적으로 성공 실측 0회였다. 게다가 예산은 항상
     * true 를 반환하는 죽은 ACTION_CLICK(피커 폴백 미발동, DESIGN_20 §1 [code-certain])의
     * 단일 수렴 폴링에 전부 소진돼 전략2가 ~0ms 예산으로 발화 — 3연속 동일 실패를 만드는
     * 재시도 오염원이었다.
     *
     * 성공 조건: [isSplitPairPresent] — 패키지 2종(대상·자기 자신) 존재 **그리고**
     * 두 창의 IntRect 가 [PaneGeometry.isTopBottomSplit] 통과. (패키지 존재만으로는 팝업/
     * 전체화면 강탈을 성공으로 오판할 수 있었다 — PROGRESS.md 열린 질문 #18.)
     */
    private suspend fun step3PlacePartner(ctx: EntryContext, timeoutMs: Long): Boolean {
        // [회귀 예방, 2026-07-25] step2 와 동일한 함정: 이전 시도의 클릭/드래그가 정착 지연으로
        // 실패 판정된 뒤 성공을 몰라보고 재탐색부터 시작하면 안 된다 — 먼저 목표 상태를 확인한다.
        if (isSplitPairPresent(ctx)) {
            Log.i(TAG, "step3: 분할 쌍 이미 존재 — 피커 탭 생략")
            return true
        }
        return clickUntilCondition(timeoutMs, "step3 panel-picker", ClickCyclePlan.PICKER, ::findPanelPickerNode) {
            isSplitPairPresent(ctx)
        }
    }

    // ══════════════════════════════════════════════════════════
    // MENU 레시피 Step 2 — 카드 아이콘 탭(메뉴 노출)
    // ══════════════════════════════════════════════════════════

    /**
     * 동작: 카드 아이콘 노드를 클릭(드래그 아님).
     * 성공 조건: "분할 화면" 텍스트/desc 를 가진 메뉴 노드 출현 ([findSplitMenuNode]).
     */
    private suspend fun menuStep2TapCardIcon(ctx: EntryContext, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        fun remaining() = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)

        val clicked = service.clickWhenFound(remaining(), "menuStep2 card-icon", TAG) { findCardIconNode(ctx) }
        if (!clicked) {
            Log.w(TAG, "menuStep2: 카드 아이콘 발견/클릭 실패")
            return false
        }
        return pollUntil(remaining()) { findSplitMenuNode() != null }
    }

    // ══════════════════════════════════════════════════════════
    // MENU 레시피 Step 3 — "분할 화면으로 열기" 메뉴 노드 탭
    // ══════════════════════════════════════════════════════════

    /**
     * 동작: [findSplitMenuNode] 로 찾은 메뉴 노드를 클릭.
     * 성공 조건: [PaneGeometry.isSplitSelectSidePane] — 대상 창이 전고·좌/우 가장자리 도킹·
     * 가시 폭 비율 15~75% 를 모두 만족하는 좌우 분할-선택 상태.
     *
     * [반증 2026-07-25, 정정] 리사이저블 앱 기본 경로에서는 이 메뉴가 좌우 분할을 만들어
     * 무용했지만(가로에서도 상하 분할 필요), UNRESIZEABLE 앱 분기(MENU 레시피)에서는 이후
     * step5 회전 단계와 결합해 좌우→상하 전환이 가능함이 실측 확정됐다 — 유일한 진입 경로.
     */
    private suspend fun menuStep3TapSplitMenuNode(ctx: EntryContext, timeoutMs: Long): Boolean {
        // [회귀 예방, 2026-07-25] step2 정착-지연 함정과 동일 패턴의 선체크.
        if (isTargetInSplitSelectSideState(ctx)) {
            Log.i(TAG, "menuStep3: 좌우 분할-선택 상태 이미 도달 — 메뉴 탭 생략")
            return true
        }
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        fun remaining() = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)

        val clicked = service.clickWhenFound(remaining(), "menuStep3 split-menu-item", TAG) { findSplitMenuNode() }
        if (!clicked) {
            Log.w(TAG, "menuStep3: 분할 화면 메뉴 노드 발견/클릭 실패")
            return false
        }
        return pollUntil(remaining()) { isTargetInSplitSelectSideState(ctx) }
    }

    // ══════════════════════════════════════════════════════════
    // MENU 레시피 Step 4 — 피커에서 파트너 탭 (좌우 분할 확정)
    // ══════════════════════════════════════════════════════════

    /**
     * [#20 재작성, 2026-07-25] [findPanelPickerNode] 로 찾은 파트너(우리 패널) 노드를
     * [ClickCyclePlan.PICKER] 로 클릭-사이클 에스컬레이션한다 (DRAG 레시피 step3 와 동일 셀렉터
     * 재사용, docs/DESIGN_20_CLICK_CYCLE.md).
     * 성공 조건: 패키지 2종 존재 ∧ [PaneGeometry.isLeftRightSplit] ([isSplitPairPresentLeftRight]).
     */
    private suspend fun menuStep4TapPartnerInPicker(ctx: EntryContext, timeoutMs: Long): Boolean {
        // [회귀 예방, 2026-07-25] step2 정착-지연 함정과 동일 패턴의 선체크.
        if (isSplitPairPresentLeftRight(ctx)) {
            Log.i(TAG, "menuStep4: 좌우 분할 쌍 이미 존재 — 피커 탭 생략")
            return true
        }
        return clickUntilCondition(timeoutMs, "menuStep4 panel-picker", ClickCyclePlan.PICKER, ::findPanelPickerNode) {
            isSplitPairPresentLeftRight(ctx)
        }
    }

    // ══════════════════════════════════════════════════════════
    // MENU 레시피 Step 5 — 디바이더 핸들 탭 → "시계 방향으로 회전" (좌우 → 상하 전환)
    // ══════════════════════════════════════════════════════════

    /**
     * [측정 2026-07-25] 디바이더 핸들 탭 → 팝업 노드 3종(content-desc): "App pair 추가 위치" /
     * "창 전환" / "시계 방향으로 회전". 좌우 분할에서 "시계 방향으로 회전" 을 누르면 상하 분할로
     * 전환됨이 실측 확정됐다. 회전 후 어느 페인이 위인지는 신경 쓰지 않는다 — 서비스 레이어
     * `handleDragDividerTo` 가 PaneSwapper 로 위치 불일치를 이미 처리한다.
     *
     * 성공 조건: [isSplitPairPresent] — 두 창이 [PaneGeometry.isTopBottomSplit] 통과.
     *
     * 핸들 재조회·탭·회전 노드 폴링·클릭 로직 자체는 [DividerPopupRotator] 로 추출되어
     * 서비스 레이어의 위치 교정 폴백(PaneSwapper 실패 시 회전×2)과 공유된다.
     */
    private suspend fun menuStep5RotateDivider(ctx: EntryContext, timeoutMs: Long): Boolean {
        // [회귀 예방, 2026-07-25] step2 정착-지연 함정과 동일 패턴의 선체크 — 회전 호출 전에 확인.
        if (isSplitPairPresent(ctx)) {
            Log.i(TAG, "menuStep5: 상하 분할 쌍 이미 존재 — 회전 생략")
            return true
        }
        return rotator.rotateOnce(ctx.screen.toIntRect(), timeoutMs) { isSplitPairPresent(ctx) }
    }

    // ══════════════════════════════════════════════════════════
    // 성공 조건 판정
    // ══════════════════════════════════════════════════════════

    /** 대상 창을 IntRect 로 변환해 [predicate] 로 판정한다. 창 단위로 실패를 격리한다(다른 창에 영향 없음). */
    private fun isTargetWindowMatching(
        ctx: EntryContext,
        predicate: (pane: IntRect, screen: IntRect) -> Boolean,
    ): Boolean {
        val screen = ctx.screen.toIntRect()
        val windows = runCatching { service.windows }.getOrDefault(emptyList())
        return windows.any { w ->
            runCatching {
                val type = w.type
                val pkg = w.root?.packageName?.toString()
                if (type != AccessibilityWindowInfo.TYPE_APPLICATION || pkg != ctx.targetPackage) {
                    false
                } else {
                    val bounds = Rect()
                    w.getBoundsInScreen(bounds)
                    predicate(bounds.toIntRect(), screen)
                }
            }.getOrDefault(false)
        }
    }

    private fun isTargetInSplitSelectTopState(ctx: EntryContext): Boolean =
        isTargetWindowMatching(ctx) { pane, screen -> PaneGeometry.isSplitSelectTopPane(pane, screen) }

    private fun isTargetInSplitSelectSideState(ctx: EntryContext): Boolean =
        isTargetWindowMatching(ctx) { pane, screen -> PaneGeometry.isSplitSelectSidePane(pane, screen) }

    /** targetPackage/selfPackage APPLICATION 창 존재 여부와 그 IntRect 목록을 함께 수집한다. */
    private fun collectTargetSelfPaneState(ctx: EntryContext): Triple<Boolean, Boolean, List<IntRect>> {
        val windows = runCatching { service.windows }.getOrDefault(emptyList())
        var hasTarget = false
        var hasSelf = false
        val paneRects = mutableListOf<IntRect>()
        for (w in windows) {
            if (runCatching { w.type }.getOrNull() != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val pkg = runCatching { w.root?.packageName?.toString() }.getOrNull()
            when (pkg) {
                ctx.targetPackage -> hasTarget = true
                ctx.selfPackage -> hasSelf = true
            }
            if (pkg == ctx.targetPackage || pkg == ctx.selfPackage) {
                runCatching {
                    val bounds = Rect()
                    w.getBoundsInScreen(bounds)
                    paneRects.add(bounds.toIntRect())
                }
            }
        }
        return Triple(hasTarget, hasSelf, paneRects)
    }

    /** 상하 분할 쌍 존재 판정 (DRAG step3, MENU step5 공통) */
    private fun isSplitPairPresent(ctx: EntryContext): Boolean {
        val (hasTarget, hasSelf, paneRects) = collectTargetSelfPaneState(ctx)
        return hasTarget && hasSelf && PaneGeometry.isTopBottomSplit(paneRects, ctx.screen.toIntRect())
    }

    /** 좌우 분할 쌍 존재 판정 (MENU step4 전용) */
    private fun isSplitPairPresentLeftRight(ctx: EntryContext): Boolean {
        val (hasTarget, hasSelf, paneRects) = collectTargetSelfPaneState(ctx)
        return hasTarget && hasSelf && PaneGeometry.isLeftRightSplit(paneRects, ctx.screen.toIntRect())
    }

    // ══════════════════════════════════════════════════════════
    // 노드 탐색 (셀렉터 체인)
    // ══════════════════════════════════════════════════════════

    private fun findCardIconNode(ctx: EntryContext): AccessibilityNodeInfo? {
        val roots = launcherWindowRoots()
        if (roots.isEmpty()) return null
        val label = ctx.targetLabel

        val selectors = listOf<Pair<String, (AccessibilityNodeInfo) -> Boolean>>(
            "ko-content-desc" to { node ->
                val desc = node.contentDescription?.toString().orEmpty()
                desc.contains(EntrySelectors.CARD_ICON_DESC_KO) && (label == null || desc.contains(label))
            },
            "en-content-desc" to { node -> // [미검증] 영어 로케일
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
                label != null &&
                    EntrySelectors.CARD_ICON_DESC_EN.any { desc.contains(it) } &&
                    desc.contains(label.lowercase())
            },
            // [실측 2026-07-25 3차] "구조적" 셀렉터라 라벨만 맞으면 매치되는데, 정작 Recents 카드
            // 본체/섬네일(대형 노드, 실측 중심 1092,833)이 label 을 contentDescription 으로 물려받아
            // 유효 bounds 를 가진 채로 오매치됐다 — holdThenDrag 가 그 큰 카드를 통째로 끌어 Recents
            // 세션 자체를 파괴하고 빈-bounds 가드(2026-07-25 2차 수정)는 통과해버렸다. 진짜 아이콘은
            // 카드 헤더의 소형 아이콘(성공 런 2회 실측 중심 (593,323), 대략 90px 급)이므로, 화면 폭의
            // 1/10(2184px 기준 ≈218px, 좌표 하드코딩이 아니라 화면 비율 기준) 이하인 정사각형에 가까운
            // 노드만 매치로 인정한다 — 카드 본체 같은 수백 px 대형 노드를 차단한다.
            "structural-clickable-label" to { node ->
                if (label == null || !node.isClickable ||
                    node.contentDescription?.toString()?.contains(label) != true
                ) {
                    false
                } else {
                    val rect = Rect()
                    val gotBounds = runCatching { node.getBoundsInScreen(rect) }.isSuccess
                    val maxIconDim = ctx.screen.width() / 10
                    gotBounds && !rect.isEmpty && rect.width() <= maxIconDim && rect.height() <= maxIconDim
                }
            },
        )
        return firstMatch("step2 card-icon", roots, selectors)
    }

    /**
     * MENU 레시피 step2/3 용: 카드 메뉴가 노출한 "분할 화면(으로 열기)" 노드를 찾는다.
     *
     * [반증 2026-07-25, 정정] 리사이저블 앱 기본 경로로는 반증(가로에서도 좌우 분할 생성) —
     * 단 UNRESIZEABLE 앱 분기(MENU 레시피)에서는 이후 회전 단계와 결합해 유일한 진입 경로로
     * 실측 확정됐다 (2026-07-25). 이 함수는 그 경로에서만 호출된다.
     */
    private fun findSplitMenuNode(): AccessibilityNodeInfo? {
        val roots = launcherWindowRoots()
        if (roots.isEmpty()) return null
        val selectors = listOf<Pair<String, (AccessibilityNodeInfo) -> Boolean>>(
            "ko-split-menu" to { node ->
                val text = node.text?.toString().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()
                text.contains(EntrySelectors.SPLIT_MENU_TEXT_KO) || desc.contains(EntrySelectors.SPLIT_MENU_TEXT_KO)
            },
            "en-split-menu" to { node -> // [미검증] 영어 로케일
                val text = node.text?.toString()?.lowercase().orEmpty()
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
                text.contains(EntrySelectors.SPLIT_MENU_TEXT_EN) || desc.contains(EntrySelectors.SPLIT_MENU_TEXT_EN)
            },
        )
        return firstMatch("menuStep2/3 split-menu", roots, selectors)
    }

    /**
     * step3/menuStep4 공통: Recents 파트너 피커(`FromRecentActivity`)에서 우리 패널 라벨을 찾는다.
     * [실측 2026-07-25] 피커의 앱 카드 라벨은 클릭 불가 텍스트 노드(클릭 가능 카드 컨테이너의 자식)라
     * isClickable 을 요구하면 절대 매치되지 않는다 — 클릭 대상 해석은 clickWhenFound 가 맡는다.
     */
    private fun findPanelPickerNode(): AccessibilityNodeInfo? {
        val roots = launcherWindowRoots()
        if (roots.isEmpty()) return null
        val selectors = EntrySelectors.PANEL_LABEL_CANDIDATES.map { label ->
            "panel-label:$label" to { node: AccessibilityNodeInfo ->
                val text = node.text?.toString().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()
                text.contains(label) || desc.contains(label)
            }
        }
        return firstMatch("step3/menuStep4 panel-picker", roots, selectors)
    }

    private fun launcherWindowRoots(): List<AccessibilityNodeInfo> =
        runCatching { service.windows }.getOrDefault(emptyList())
            .filter { w ->
                runCatching { w.root?.packageName?.toString() }.getOrNull() == EntrySelectors.LAUNCHER_PACKAGE
            }
            .mapNotNull { w -> runCatching { w.root }.getOrNull() }

    /**
     * 셀렉터 체인을 **단일 트리 순회**로 평가한다. 어떤 셀렉터가 매치됐는지 Log.i 로 남겨
     * 향후 로케일 확장(어떤 후보가 실제로 쓰였는지) 근거로 삼는다.
     *
     * [P1, 2026-07-29] 기존에는 셀렉터 개수만큼 트리를 반복 순회했다(`findCardIconNode` 기준
     * 폴링 주기당 3회). 이제 순회는 1회이고, 각 노드에서 [BestMatchTracker.accepts] 를 통과한
     * 셀렉터만 평가한다. **어떤 노드가 선택되는지는 바뀌지 않는다** — 등가 논증은
     * [BestMatchTracker] KDoc 참조(엄격 부등호 + pre-order 순회 유지 + 멀티 루트 처리).
     *
     * 추가된 유일한 동작 차이는 경계다: 깊이 [MAX_TREE_DEPTH] 초과 서브트리는 잘라내고,
     * 노드 방문이 [MAX_NODES_VISITED_TREE] 를 넘으면 순회를 중단한다(둘 다 경고 로그).
     * 기존 순회에는 상한이 아예 없었다.
     */
    private fun firstMatch(
        logLabel: String,
        roots: List<AccessibilityNodeInfo>,
        selectors: List<Pair<String, (AccessibilityNodeInfo) -> Boolean>>,
    ): AccessibilityNodeInfo? {
        val tracker = BestMatchTracker<AccessibilityNodeInfo>()
        // 예산은 루트 루프 바깥 — 이 호출 전체가 공유하고, 경고도 호출당 1회만 찍힌다.
        var budget = MAX_NODES_VISITED_TREE
        for (root in roots) {
            walk(root, maxDepth = MAX_TREE_DEPTH) { node ->
                if (budget-- <= 0) {
                    Log.w(TAG, "$logLabel: 노드 예산 $MAX_NODES_VISITED_TREE 소진 — 순회 중단")
                    return@walk false
                }
                selectors.forEachIndexed { i, (_, pred) ->
                    if (tracker.accepts(i) && runCatching { pred(node) }.getOrDefault(false)) {
                        tracker.offer(i, node)
                    }
                }
                !tracker.isTopPriority() // 0순위 매치면 조기 종료
            }
            // 예산 소진(budget < 0)이든 0순위 매치든 루트 루프를 더 돌 이유가 없다.
            if (tracker.isTopPriority() || budget < 0) break
        }
        val node = tracker.best
        if (node != null) Log.i(TAG, "$logLabel matched via selector [${selectors[tracker.bestIndex].first}]")
        return node
    }

    /**
     * [#20] 클릭-사이클 에스컬레이션 (docs/DESIGN_20_CLICK_CYCLE.md §2-2). [step3PlacePartner],
     * [menuStep4TapPartnerInPicker] 가 쓴다.
     *
     * [budgetMs] 예산 안에서 최대 [MAX_CLICK_CYCLES] 사이클을 돈다. 매 사이클:
     *  1. 디스패치 전 [condition] 선체크 — 이전 사이클의 클릭이 늦게 정착했거나, 늦은 폴링
     *     주기에 이미 원하는 상태가 됐으면 새 디스패치 없이 즉시 성공 처리한다.
     *  2. [find] 로 노드 재탐색(`pollForValue`) — 못 찾으면 다음 사이클로.
     *  3. 스테일 가드([AccessibilityNodeInfo.refresh]) — phantom 노드에 탭하지 않는다.
     *  4. [plan.mechanismFor] 가 정한 메커니즘으로 디스패치.
     *  5. 검증 슬라이스([plan.verifySliceMs]) 동안 [condition] 폴링.
     * 3사이클을 다 써도 수렴하지 않으면 잔여 예산 전부를 [condition] 폴링에 마지막으로 쓴다
     * (늦은 정착 최종 흡수).
     *
     * `AccessibilityNodeInfo.performAction(ACTION_CLICK)` 의 true 반환은 "노드가 살아있고
     * clickable" 만 보장하고 실제 클릭 핸들러 실행 여부는 보장하지 않는다([ClickCyclePlan] KDoc
     * 참고) — 그래서 이 함수는 디스패치 성공(dispatched)과 [condition] 수렴(실제 효과)을
     * 분리해서 판정한다. 거부된 디스패치(dispatched=false)는 검증 슬라이스를 생략하고 바로
     * 다음 사이클로 넘어간다 — 애초에 반영될 리 없는 디스패치를 위해 800ms 를 낭비하지 않는다.
     */
    private suspend fun clickUntilCondition(
        budgetMs: Long,
        what: String,
        plan: ClickCyclePlan,
        find: () -> AccessibilityNodeInfo?,
        condition: () -> Boolean,
    ): Boolean {
        val startedAtMs = SystemClock.uptimeMillis()
        val deadline = startedAtMs + budgetMs
        fun remaining() = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)
        fun elapsedMs() = SystemClock.uptimeMillis() - startedAtMs

        for (cycle in 0 until MAX_CLICK_CYCLES) {
            if (remaining() <= 0) break

            if (condition()) {
                Log.i(TAG, "clickCycle: [$what] converged cycle=$cycle mech=none elapsedMs=${elapsedMs()}")
                return true
            }

            val node = pollForValue(minOf(plan.findSliceMs, remaining())) { find() }
            if (node == null) {
                Log.w(TAG, "clickCycle: [$what] cycle=$cycle node-not-found")
                continue
            }

            // 스테일 가드: 트리 갱신 사이 phantom 매치에 탭을 날리지 않는다.
            val fresh = runCatching { node.refresh() }.getOrDefault(false)
            if (!fresh) {
                Log.w(TAG, "clickCycle: [$what] cycle=$cycle stale-node")
                continue
            }

            val mechanism = plan.mechanismFor(cycle)
            val dispatched = when (mechanism) {
                ClickMechanism.GESTURE_TAP -> {
                    if (ownOverlayVisible()) {
                        // 제스처 탭은 히트테스트 기반이라 자기 터치 가능 오버레이가 탭을 삼킨다
                        // (ACTION_CLICK 엔 없던 실패 모드) — 조용히 넘기지 않고 명시 실패로 드러낸다.
                        Log.e(TAG, "clickCycle: [$what] own touchable overlay present — tap would be swallowed")
                        return false
                    }
                    service.tapNodeCenter(node)
                }

                ClickMechanism.A11Y_ACTION -> {
                    val clickable = clickableAncestorOrSelf(node)
                    val clicked = clickable != null &&
                        runCatching { clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                            .getOrDefault(false)
                    // clickable 조상이 없거나 ACTION_CLICK 이 false 면 같은 사이클 안에서 즉시 제스처로 폴백.
                    if (clicked) true else service.tapNodeCenter(node)
                }
            }
            val mechLabel = if (mechanism == ClickMechanism.GESTURE_TAP) "gesture" else "a11y"
            Log.i(TAG, "clickCycle: [$what] cycle=$cycle mech=$mechLabel dispatched=$dispatched")

            if (!dispatched) continue // 거부된 디스패치엔 검증 슬라이스를 낭비하지 않는다.

            if (pollUntil(minOf(plan.verifySliceMs, remaining())) { condition() }) {
                Log.i(TAG, "clickCycle: [$what] converged cycle=$cycle mech=$mechLabel elapsedMs=${elapsedMs()}")
                return true
            }
        }

        // 3사이클 소진 후 잔여 예산 전부를 늦은 정착 최종 흡수에 쓴다.
        if (pollUntil(remaining()) { condition() }) {
            Log.i(TAG, "clickCycle: [$what] converged cycle=$MAX_CLICK_CYCLES mech=none elapsedMs=${elapsedMs()}")
            return true
        }
        Log.w(TAG, "clickCycle: [$what] budget exhausted after $MAX_CLICK_CYCLES cycles")
        return false
    }

    companion object {
        private const val TAG = "FWSplitEntry"

        /** [#20] clickUntilCondition 최대 사이클 수 (docs/DESIGN_20_CLICK_CYCLE.md §2-2). */
        private const val MAX_CLICK_CYCLES = 3
    }
}

/** 진입 레시피. 대상 앱의 리사이즈 모드에 따라 서비스 레이어가 선택한다 (ResizeModeDetector 참조). */
enum class EntryRecipe(val stepCount: Int) {
    /** 리사이저블 앱 기본: Recents 열기 → 카드 상단 드래그(분할-선택) → 파트너 배치 */
    DRAG(3),

    /** UNRESIZEABLE 앱 전용: Recents 열기 → 카드 메뉴 탭 → "분할 화면으로 열기" → 피커 탭 → 회전 */
    MENU(5),
}

/** 진입에 필요한 컨텍스트. 서비스 레이어가 채워서 넘긴다 */
data class EntryContext(
    val targetPackage: String,
    /** PackageManager 로 조회한 대상 앱 표시 이름. 조회 실패 시 null */
    val targetLabel: String?,
    val selfPackage: String,
    /** 현재 화면 전체 rect (landscape 2184×1968). 서비스가 WindowMetrics 로 채운다 */
    val screen: Rect,
    /** 이번 세션에 적용할 진입 레시피. 서비스가 ResizeModeDetector 판정 결과로 결정한다 */
    val recipe: EntryRecipe,
)

/** android.graphics.Rect → domain.IntRect 변환. platform 계층의 유일한 경계 매핑 지점. */
private fun Rect.toIntRect(): IntRect = IntRect(left, top, right, bottom)

/**
 * 셀렉터 리터럴 모음. docs/DEVICE_FACTS.md "분할 진입 전략" 실측값이 근거다.
 * 로케일 추가는 이 object 에 상수/후보를 한 줄 추가하는 것으로 끝나야 한다.
 */
private object EntrySelectors {
    // 실측 (한국어, One UI 8) — docs/DEVICE_FACTS.md. step2 카드 아이콘 노드 탐색에 사용.
    const val CARD_ICON_DESC_KO = "고급 옵션"

    // [미검증] 영어 로케일 후보 — PROGRESS.md 열린 질문 #6
    val CARD_ICON_DESC_EN: List<String> = listOf("more options", "advanced options")

    // [반증 2026-07-25, 정정] Recents 카드 팝업 메뉴에서 "분할 화면으로 열기" 를 탭하면
    // 리사이저블 앱 기본 경로(가로, 시청 시나리오, 상하 분할 필요)에서는 좌우(L/R) 분할이
    // 생성되어 무용하다 — DRAG 레시피에서는 여전히 쓰지 않는다.
    // 단, UNRESIZEABLE 앱 분기(MENU 레시피)에서는 이 메뉴 → 좌우 분할 선택 → 회전 단계를
    // 거치면 상하 분할로 전환 가능함이 실측 확정됐다(2026-07-25) — MENU 레시피의 유일한 진입
    // 경로로 부활했다. 리사이저블 앱 기본 경로로 되살리지 말 것(그 경로에 한해 여전히 반증됨).
    const val SPLIT_MENU_TEXT_KO = "분할 화면"
    const val SPLIT_MENU_TEXT_EN = "split screen"

    const val LAUNCHER_PACKAGE = "com.sec.android.app.launcher"

    // [실측 2026-07-25] "FoldWindow" 후보가 P3-4 OnboardingActivity 라벨(@string/app_name = "FoldWindow")
    // 과 충돌해 피커가 온보딩을 오클릭 → 전체화면으로 떠 분할-선택을 파괴함이 실기기에서 재현됐다.
    // "FW Panel" 만 남긴다. 후보 추가 시 앱 서랍에 노출되는 다른 액티비티 라벨과 겹치지 않는지 반드시 확인할 것.
    val PANEL_LABEL_CANDIDATES: List<String> = listOf("FW Panel")

    // 회전 팝업 노드 셀렉터(ROTATE_DESC_KO/EN)는 DividerPopupRotator 로 이동했다 —
    // SplitEntry.menuStep5 와 서비스 레이어 위치 교정 폴백이 공유하는 단일 출처.

    // [측정] input draganddrop 592 322 1092 150 800 이 실기기에서 상하 분할-선택을 만들어냈다.
    // 드롭 Y = 화면 상단 + 150px. 화면 크기 하드코딩이 아니라 상단 기준 오프셋이므로 CLAUDE.md
    // 규칙(좌표 하드코딩 금지)을 지킨다 — 화면 자체의 top 기준 상대 오프셋만 쓴다.
    const val DROP_MARGIN_PX = 150

    // [측정 기반, 정확한 홀드/이동 시간 자체는 미세조정 여지 있음] input draganddrop 의
    // "롱프레스 후 드래그" 를 재현하는 holdThenDrag 파라미터.
    const val DRAG_HOLD_MS = 500L
    const val DRAG_MOVE_MS = 600L
}
