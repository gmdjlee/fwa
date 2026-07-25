package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [미검증→일부 확정] 페인(영상/파트너) 상하 전환 시도.
 *
 * "전환"/"switch" 키워드 셀렉터는 2026-07-25 실기기 로그에서 유튜브 세션 스왑 성공으로 확정됨.
 * 다만 같은 로그에서 MENU 레시피의 회전(step5) 직후 첫 핸들 탭이 팝업을 전혀 띄우지 못하는
 * 현상이 관측됐다 — 회전 애니메이션이 정착되기 전 탭이 시스템에 의해 무시된 것으로 추정.
 * 이에 따라 핸들 탭을 최대 [MAX_TAP_ATTEMPTS]회까지 전체 [timeoutMs] 예산 안에서 재시도한다.
 * 모두 실패하면 기존 더블탭 폴백을 1회 시도한다.
 *
 * ADR-2 준수: 팝업 노드 탐색과 전환 확인은 전부 조건 폴링(delay 루프 + withTimeoutOrNull)이며,
 * 재시도 루프의 예산도 SystemClock.uptimeMillis 기반 데드라인으로 계산한다 (고정 지연 없음).
 * 제스처 duration(50ms 탭) 자체는 입력 재생 파라미터일 뿐 완료 판정 근거가 아니다.
 */
class PaneSwapper(private val service: AccessibilityService) {

    /**
     * 페인 상하 전환 시도. isSwapped 콜백이 true 를 반환할 때까지 폴링으로 확인.
     * 핸들 탭 → 스위치 팝업 폴링을 [MAX_TAP_ATTEMPTS]회까지 재시도한다 (전체 [timeoutMs] 예산 공유).
     * @return 전환 확인 성공 여부
     */
    suspend fun swap(handle: DividerHandle, timeoutMs: Long, isSwapped: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
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
            val clicked = runCatching {
                switchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }.getOrDefault(false)
            Log.i(
                TAG,
                "strategy 1: clicked switch node text=\"${switchNode.text}\" " +
                    "desc=\"${switchNode.contentDescription}\" result=$clicked",
            )
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

        private const val TAP_DURATION_MS = 50L
        private const val DOUBLE_TAP_GAP_MS = 120L

        /** 회전 직후 첫 탭이 무시되는 현상(2026-07-25 실측) 대응 — 재시도 여지 확보를 위해 1200ms→700ms로 축소. */
        private const val POPUP_POLL_TIMEOUT_MS = 700L
        private const val POPUP_POLL_INTERVAL_MS = 150L
        private const val SWAP_POLL_INTERVAL_MS = 150L

        /** 핸들 탭 → 팝업 대기를 재시도할 최대 횟수 (2026-07-25 실측: 회전 직후 첫 탭 무시 현상 대응). */
        private const val MAX_TAP_ATTEMPTS = 3

        private const val MAX_NODES_VISITED = 500
    }
}
