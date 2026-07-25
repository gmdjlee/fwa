package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [미검증] 페인(영상/파트너) 상하 전환 시도.
 *
 * 여기 있는 모든 셀렉터(텍스트/contentDescription 키워드)는 실기기에서 아직 검증되지 않았다.
 * 두 전략을 순차 시도하고, 팝업이 뜨면 그 안의 모든 클릭 가능 노드를 로그로 남긴다 —
 * 이것이 실기기에서 진짜 셀렉터를 찾아내는 방법이다 (DEVICE_FACTS.md 갱신 근거로 사용할 것).
 *
 * ADR-2 준수: 팝업 노드 탐색과 전환 확인은 전부 조건 폴링(delay 루프 + withTimeoutOrNull)이다.
 * 제스처 duration(50ms 탭) 자체는 입력 재생 파라미터일 뿐 완료 판정 근거가 아니다.
 */
class PaneSwapper(private val service: AccessibilityService) {

    /**
     * 페인 상하 전환 시도. isSwapped 콜백이 true 를 반환할 때까지 폴링으로 확인.
     * @return 전환 확인 성공 여부
     */
    suspend fun swap(handle: DividerHandle, timeoutMs: Long, isSwapped: () -> Boolean): Boolean {
        val singleTapDispatched = dispatchTap(handle.centerX, handle.centerY)
        if (!singleTapDispatched) Log.w(TAG, "strategy 1: single tap dispatch failed")

        val switchNode = withTimeoutOrNull(POPUP_POLL_TIMEOUT_MS) {
            var found: AccessibilityNodeInfo? = null
            while (found == null) {
                found = findSwitchNode()
                if (found == null) delay(POPUP_POLL_INTERVAL_MS)
            }
            found
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
            Log.i(TAG, "strategy 1: no switch node found within ${POPUP_POLL_TIMEOUT_MS}ms, trying strategy 2 (double tap)")
            val doubleTapDispatched = dispatchDoubleTap(handle.centerX, handle.centerY)
            if (!doubleTapDispatched) Log.w(TAG, "strategy 2: double tap dispatch failed")
        }

        return withTimeoutOrNull(timeoutMs) {
            while (!isSwapped()) delay(SWAP_POLL_INTERVAL_MS)
            true
        } ?: false
    }

    /**
     * 현재 창들의 루트에서 클릭 가능한 노드를 전부 순회하며 로그를 남기고,
     * "전환" 계열 텍스트/설명을 가진 첫 노드를 반환한다.
     */
    private fun findSwitchNode(): AccessibilityNodeInfo? {
        val roots = runCatching { service.windows.mapNotNull { it.root } }.getOrDefault(emptyList())
        for (root in roots) {
            val match = searchClickableNodes(root)
            if (match != null) return match
        }
        return null
    }

    private fun searchClickableNodes(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
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
                // 실기기 셀렉터 확정용 — 이 로그가 P2 다음 단계의 근거 자료가 된다.
                Log.i(TAG, "clickable node: text=\"$text\" desc=\"$desc\"")
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

        private const val POPUP_POLL_TIMEOUT_MS = 1_200L
        private const val POPUP_POLL_INTERVAL_MS = 150L
        private const val SWAP_POLL_INTERVAL_MS = 150L

        private const val MAX_NODES_VISITED = 500
    }
}
