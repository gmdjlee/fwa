package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import dev.dj.foldwindow.domain.ClickCyclePlan
import dev.dj.foldwindow.domain.ClickMechanism
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [일부 확정, #20 재작성 2026-07-25] 페인(영상/파트너) 상하 전환 시도.
 *
 * "전환"/"switch" 키워드 셀렉터는 2026-07-25 실기기 로그에서 유튜브 세션 스왑 성공으로 확정됨.
 * 다만 같은 로그에서 MENU 레시피의 회전(step5) 직후 첫 핸들 탭이 팝업을 전혀 띄우지 못하는
 * 현상과, 팝업을 찾아 클릭(ACTION_CLICK=true)했는데도 실제 전환이 일어나지 않는 현상이 각각
 * 관측됐다 — 둘 다 전이(회전/클릭) 정착 전 시스템측 억제로 추정된다(docs/DESIGN_20_CLICK_CYCLE.md
 * §1 [inferred]). 시간에 걸친 재시도로 대응한다:
 *
 *  1. **정착 게이트** — 핸들 탭 루프 진입 전 [dividerSettled] 로 디바이더 bounds 안정을
 *     조건 폴링한다(best-effort, 실패해도 속행 — 사이클 루프 자체가 안전망).
 *  2. **핸들 탭 → 팝업 탐색 루프** — 핸들 탭을 최대 [MAX_TAP_ATTEMPTS]회까지 전체 [timeoutMs]
 *     예산 안에서 재시도해 전환 팝업 노드를 찾는다. 모두 실패하면(팝업 자체를 못 찾음) 기존
 *     더블탭 폴백을 1회 시도한다.
 *  3. **클릭-사이클 에스컬레이션** — 팝업을 찾으면 [ClickCyclePlan.POPUP_SWITCH] 순서로
 *     최대 [MAX_SWITCH_CLICK_CYCLES]회 클릭을 재시도한다. `ACTION_CLICK`=true 는 노드가
 *     살아있고 클릭 가능함만 보장할 뿐 하류 전환 로직 실행을 보장하지 않으므로([ClickCyclePlan]
 *     KDoc 근거), 디스패치 성공과 실제 전환([isSwapped])을 분리해서 판정한다.
 *
 * ADR-2 준수: 팝업 노드 탐색과 전환 확인은 전부 조건 폴링(delay 루프 + withTimeoutOrNull)이며,
 * 재시도 루프의 예산도 SystemClock.uptimeMillis 기반 데드라인으로 계산한다 (고정 지연 없음).
 * 제스처 duration(50ms 탭) 자체는 입력 재생 파라미터일 뿐 완료 판정 근거가 아니다.
 */
class PaneSwapper(private val service: AccessibilityService) {

    /**
     * 페인 상하 전환 시도. isSwapped 콜백이 true 를 반환할 때까지 폴링으로 확인.
     * 핸들 탭 → 스위치 팝업 폴링을 [MAX_TAP_ATTEMPTS]회까지 재시도한다 (전체 [timeoutMs] 예산 공유).
     *
     * [#20, 2026-07-25] 팝업을 찾은 뒤의 전환 클릭은 [ClickCyclePlan.POPUP_SWITCH] 기반
     * 클릭-사이클 에스컬레이션으로 재구현됐다 (클래스 KDoc, docs/DESIGN_20_CLICK_CYCLE.md §2-3).
     *
     * @param dividerSettled 핸들 탭 루프 진입 전 "정착 게이트" — 주어진 예산 안에서 디바이더
     *   bounds 안정 여부를 조건 폴링으로 판정해 돌려주는 호출자 콜백(서비스 레이어 구현).
     *   best-effort — 타임아웃이어도 로그만 남기고 진행은 계속한다.
     * @return 전환 확인 성공 여부
     */
    suspend fun swap(
        handle: DividerHandle,
        timeoutMs: Long,
        dividerSettled: suspend (Long) -> Boolean,
        isSwapped: () -> Boolean,
    ): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs

        // [#20] 정착 게이트: 회전/드래그 직후 애니메이션이 안 끝난 상태에서의 첫 탭 무효화(클래스
        // KDoc [inferred] 근거)에 대응한다. 실패해도 로그만 남기고 속행 — 사이클 루프가 안전망이다.
        val settleBudget = minOf(SETTLE_GATE_BUDGET_MS, deadline - SystemClock.uptimeMillis())
        val settleStart = SystemClock.uptimeMillis()
        if (dividerSettled(settleBudget)) {
            Log.i(TAG, "swap: settleGate ok in ${SystemClock.uptimeMillis() - settleStart}ms")
        } else {
            Log.w(TAG, "swap: settle gate timeout — proceeding")
        }

        var switchNode: AccessibilityNodeInfo? = null

        var attempt = 1
        while (attempt <= MAX_TAP_ATTEMPTS) {
            val remaining = deadline - SystemClock.uptimeMillis()
            if (remaining <= 0) break

            val tapDispatched = dispatchTap(handle.centerX, handle.centerY)
            if (!tapDispatched) Log.w(TAG, "strategy 1 attempt $attempt: single tap dispatch failed")

            val pollBudget = minOf(POPUP_POLL_TIMEOUT_MS, remaining)
            switchNode = withTimeoutOrNull(pollBudget) {
                var found: AccessibilityNodeInfo? = null
                while (found == null) {
                    found = findSwitchNode(logAllNodes = attempt == 1)
                    if (found == null) delay(POPUP_POLL_INTERVAL_MS)
                }
                found
            }

            if (switchNode != null) {
                Log.i(TAG, "strategy 1 attempt $attempt: switch node found")
                break
            }
            Log.i(TAG, "strategy 1 attempt $attempt: no switch node found within ${pollBudget}ms")
            attempt++
        }

        if (switchNode != null) {
            // [#20] 단일 ACTION_CLICK 을 클릭-사이클 에스컬레이션으로 대체. ACTION_CLICK=true 는
            // "클릭 가능한 살아있는 노드" 만 보장할 뿐 하류 전환 로직 실행을 보장하지 않는다
            // ([ClickCyclePlan] KDoc 근거) — 디스패치 성공과 isSwapped() 수렴을 분리 판정한다.
            cycleLoop@ for (cycle in 0 until MAX_SWITCH_CLICK_CYCLES) {
                val cycleRemaining = deadline - SystemClock.uptimeMillis()
                if (cycleRemaining <= 0) break@cycleLoop

                if (isSwapped()) {
                    // involution 가드: 스왑 2회 = 원위치. 이미 착지한 스왑에 재클릭하면 도로 되돌아간다.
                    Log.i(TAG, "swap: converged cycle=$cycle mech=none")
                    return true
                }

                var node = findSwitchNode(logAllNodes = false)
                if (node == null) {
                    // 팝업은 다른 조작(예: 이전 사이클의 클릭)으로 활성화되며 소멸한다 — 재탭 전에
                    // 소멸이 곧 성공이었는지부터 재확인한다(소멸+미스왑이면 클릭이 실제로 죽은 것).
                    if (isSwapped()) {
                        Log.i(TAG, "swap: converged cycle=$cycle mech=none")
                        return true
                    }
                    dispatchTap(handle.centerX, handle.centerY)
                    val repollBudget = minOf(POPUP_POLL_TIMEOUT_MS, deadline - SystemClock.uptimeMillis())
                    node = pollForSwitchNode(repollBudget)
                }
                val switchTarget = node ?: continue@cycleLoop

                val mechanism = ClickCyclePlan.POPUP_SWITCH.mechanismFor(cycle)
                val dispatched = when (mechanism) {
                    ClickMechanism.A11Y_ACTION ->
                        runCatching { switchTarget.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                            .getOrDefault(false)
                    ClickMechanism.GESTURE_TAP -> tapNodeBounds(switchTarget)
                }
                val mechLabel = if (mechanism == ClickMechanism.A11Y_ACTION) "a11y" else "gesture"
                Log.i(TAG, "swap: switch-click cycle=$cycle mech=$mechLabel result=$dispatched")

                if (!dispatched) continue@cycleLoop // 거부된 디스패치엔 검증 슬라이스를 낭비하지 않는다.

                val verifyBudget = minOf(SWAP_VERIFY_SLICE_MS, deadline - SystemClock.uptimeMillis())
                val verified = withTimeoutOrNull(verifyBudget) {
                    while (!isSwapped()) delay(SWAP_POLL_INTERVAL_MS)
                    true
                } ?: false
                if (verified) {
                    Log.i(TAG, "swap: converged cycle=$cycle mech=$mechLabel")
                    return true
                }
            }
        } else {
            Log.i(TAG, "strategy 1: no switch node found after $MAX_TAP_ATTEMPTS attempts, trying strategy 2 (double tap)")
            val doubleTapDispatched = dispatchDoubleTap(handle.centerX, handle.centerY)
            if (!doubleTapDispatched) Log.w(TAG, "strategy 2: double tap dispatch failed")
        }

        val remainingForSwapPoll = deadline - SystemClock.uptimeMillis()
        return withTimeoutOrNull(remainingForSwapPoll) {
            while (!isSwapped()) delay(SWAP_POLL_INTERVAL_MS)
            true
        } ?: false
    }

    /**
     * [#20] [budgetMs] 안에서 [POPUP_POLL_INTERVAL_MS] 간격으로 전환 팝업 노드를 폴링한다.
     * 핸들 탭/팝업 탐색 루프([swap] 상단) 내부의 폴링 블록과 동일한 로직이지만, 클릭-사이클
     * 루프의 재탭 분기에서 재사용하기 위해 별도 함수로 뽑았다(기존 루프 자체는 손대지 않는다).
     */
    private suspend fun pollForSwitchNode(budgetMs: Long): AccessibilityNodeInfo? {
        if (budgetMs <= 0) return findSwitchNode(logAllNodes = false)
        return withTimeoutOrNull(budgetMs) {
            var found: AccessibilityNodeInfo? = null
            while (found == null) {
                found = findSwitchNode(logAllNodes = false)
                if (found == null) delay(POPUP_POLL_INTERVAL_MS)
            }
            found
        }
    }

    /**
     * 현재 창들의 루트에서 클릭 가능한 노드를 순회하며 "전환" 계열 텍스트/설명을 가진
     * 첫 노드를 반환한다. [logAllNodes] 가 true 일 때만 순회한 모든 클릭 가능 노드를 로그로
     * 남긴다 (재시도마다 남기면 로그가 폭주하므로 첫 시도에서만 남긴다).
     */
    private fun findSwitchNode(logAllNodes: Boolean): AccessibilityNodeInfo? {
        val roots = runCatching { service.windows.mapNotNull { it.root } }.getOrDefault(emptyList())
        for (root in roots) {
            val match = searchClickableNodes(root, logAllNodes)
            if (match != null) return match
        }
        return null
    }

    private fun searchClickableNodes(root: AccessibilityNodeInfo, logAllNodes: Boolean): AccessibilityNodeInfo? {
        var match: AccessibilityNodeInfo? = null
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.addLast(root)
        var visited = 0

        while (stack.isNotEmpty() && visited < MAX_NODES_VISITED) {
            val node = stack.removeLast()
            visited++

            val isClickable = runCatching { node.isClickable }.getOrDefault(false)
            val text = runCatching { node.text?.toString() }.getOrNull()
            val desc = runCatching { node.contentDescription?.toString() }.getOrNull()

            if (isClickable && (text != null || desc != null)) {
                if (logAllNodes) {
                    // 실기기 셀렉터 확정용 — 이 로그가 P2 다음 단계의 근거 자료가 된다.
                    Log.i(TAG, "clickable node: text=\"$text\" desc=\"$desc\"")
                }
                if (match == null && containsSwitchKeyword(text, desc)) match = node
            }

            val childCount = runCatching { node.childCount }.getOrDefault(0)
            for (i in 0 until childCount) {
                runCatching { node.getChild(i) }.getOrNull()?.let { stack.addLast(it) }
            }
        }
        return match
    }

    private fun containsSwitchKeyword(text: String?, desc: String?): Boolean =
        SWITCH_KEYWORDS.any { kw ->
            (text != null && text.contains(kw, ignoreCase = true)) ||
                (desc != null && desc.contains(kw, ignoreCase = true))
        }

    private fun dispatchTap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS))
            .build()
        return dispatchNoWait(gesture, "single tap")
    }

    /**
     * [#20] `SplitEntry.tapNodeCenter` 미러 — refresh() 로 스테일 노드를 가드하고 bounds 중심에
     * [dispatchTap] 을 디스패치한다. [ClickMechanism.GESTURE_TAP] 사이클에서 쓴다.
     */
    private fun tapNodeBounds(node: AccessibilityNodeInfo): Boolean {
        val fresh = runCatching { node.refresh() }.getOrDefault(false)
        if (!fresh) return false
        val bounds = Rect()
        runCatching { node.getBoundsInScreen(bounds) }
        if (bounds.isEmpty) return false
        return dispatchTap(bounds.centerX(), bounds.centerY())
    }

    /** 동일 좌표 탭 2회를 하나의 제스처 안에서 시간차(120ms)로 배치 (더블탭). */
    private fun dispatchDoubleTap(x: Int, y: Int): Boolean {
        val firstPath = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val firstTap = GestureDescription.StrokeDescription(firstPath, 0L, TAP_DURATION_MS)

        val secondPath = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val secondTap = GestureDescription.StrokeDescription(secondPath, DOUBLE_TAP_GAP_MS, TAP_DURATION_MS)

        val gesture = GestureDescription.Builder()
            .addStroke(firstTap)
            .addStroke(secondTap)
            .build()
        return dispatchNoWait(gesture, "double tap")
    }

    private fun dispatchNoWait(gesture: GestureDescription, label: String): Boolean =
        runCatching {
            service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.i(TAG, "$label completed")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(TAG, "$label cancelled")
                    }
                },
                null,
            )
        }.getOrElse {
            Log.e(TAG, "$label dispatch threw", it)
            false
        }

    companion object {
        private const val TAG = "FWPaneSwapper"

        /** [미검증] 실기기에서 팝업 문구를 확인해 갱신할 것 */
        private val SWITCH_KEYWORDS = listOf("전환", "switch", "Switch")

        // [M3] 탭 duration 은 platform/NodeActions.kt 의 공용 TAP_DURATION_MS(=50L) 를 쓴다.
        // 나머지 디스패치 헬퍼(dispatchTap/tapNodeBounds/dispatchDoubleTap/dispatchNoWait)는
        // 의도적으로 통합하지 않았다 — GestureResultCallback 로그 콜백과 refresh() 스테일 가드가
        // 공용 tapPoint/tapNodeCenter 와 동작이 다르다.
        private const val DOUBLE_TAP_GAP_MS = 120L

        /** 회전 직후 첫 탭이 무시되는 현상(2026-07-25 실측) 대응 — 재시도 여지 확보를 위해 1200ms→700ms로 축소. */
        private const val POPUP_POLL_TIMEOUT_MS = 700L
        private const val POPUP_POLL_INTERVAL_MS = 150L
        private const val SWAP_POLL_INTERVAL_MS = 150L

        /** 핸들 탭 → 팝업 대기를 재시도할 최대 횟수 (2026-07-25 실측: 회전 직후 첫 탭 무시 현상 대응). */
        private const val MAX_TAP_ATTEMPTS = 3

        /** [#20] 정착 게이트("swap: settleGate ...") 예산 — 회전/드래그 직후 전이 정착 대기. */
        private const val SETTLE_GATE_BUDGET_MS = 800L

        /** [#20] 클릭-사이클 검증 슬라이스 — 실측 수렴(~160ms)의 5배 + doubleTapTimeout 초과 보장. */
        private const val SWAP_VERIFY_SLICE_MS = 800L

        /** [#20] 전환 팝업 클릭-사이클 최대 횟수 (docs/DESIGN_20_CLICK_CYCLE.md §2-3). */
        private const val MAX_SWITCH_CLICK_CYCLES = 3

        private const val MAX_NODES_VISITED = 500
    }
}
