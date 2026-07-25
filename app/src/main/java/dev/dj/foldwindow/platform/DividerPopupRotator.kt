package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import dev.dj.foldwindow.domain.IntRect
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 디바이더 핸들 탭 → 팝업의 "시계 방향으로 회전" 노드 클릭 공용 헬퍼.
 *
 * [실측 2026-07-25] `SplitEntry.menuStep5`(UNRESIZEABLE 앱 진입 경로의 좌우→상하 1회 회전)와
 * `ArrangerAccessibilityService` 의 위치 교정 폴백(`PaneSwapper.swap` 실패 시 회전×2 로
 * 상하 페인을 맞교환)이 동일한 "핸들 재조회 → 핸들 중심 탭 → 회전 팝업 노드 폴링·클릭" 로직을
 * 필요로 해 이 클래스로 추출했다. 셀렉터 상수(`ROTATE_DESC_*`)도 이 파일 한 곳에만 있다.
 *
 * ADR-2 준수: 모든 대기는 조건 폴링(`withTimeoutOrNull` + `delay` 루프) + 데드라인이며
 * 고정 지연은 쓰지 않는다. 탭/클릭 제스처의 duration 은 재생 파라미터일 뿐 완료 판정 근거가 아니다.
 */
class DividerPopupRotator(private val service: AccessibilityService) {

    private val dividerLocator = DividerLocator()

    /**
     * 회전 1회 시도: 디바이더 핸들 재조회 → 핸들 중심 탭 → "시계 방향으로 회전" 팝업 노드
     * 폴링·클릭 → [settled] 조건 폴링. 핸들을 못 찾거나 회전 노드를 못 찾거나 클릭 후
     * [settled] 이 [timeoutMs] 안에 true 가 되지 않으면 false.
     *
     * @param screen 현재 화면 전체 rect. 호출자가 WindowMetrics/displayMetrics 로 채운다
     * @param timeoutMs 이 시도 전체(핸들 조회 ~ settled 확인)에 허용된 예산
     * @param settled 회전 클릭 후 "정착"으로 볼 조건 (예: 좌우/상하 분할 판정)
     */
    suspend fun rotateOnce(screen: IntRect, timeoutMs: Long, settled: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        fun remaining() = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)

        val windowList = runCatching { service.windows }.getOrDefault(emptyList())
        val handle = dividerLocator.locate(windowList, screen)
        if (handle == null) {
            Log.w(TAG, "rotateOnce: 디바이더 핸들을 찾지 못함")
            return false
        }

        val tapped = tapPoint(handle.centerX, handle.centerY)
        if (!tapped) {
            Log.w(TAG, "rotateOnce: 핸들 탭 제스처 디스패치 실패 — 그래도 회전 노드 폴링 계속")
        }

        val rotateClicked = clickWhenFound(remaining(), "rotateOnce rotate-node") { findRotateNode() }
        if (!rotateClicked) {
            Log.w(TAG, "rotateOnce: 회전 노드 발견/클릭 실패")
            return false
        }

        return pollUntil(remaining(), settled)
    }

    /** 팝업은 launcher 가 아니라 시스템 오버레이 창일 수 있어 전체 windows 의 root 를 뒤진다. */
    private fun findRotateNode(): AccessibilityNodeInfo? {
        val roots = runCatching { service.windows.mapNotNull { it.root } }.getOrDefault(emptyList())
        for (root in roots) {
            val found = searchNode(root) { node ->
                val desc = runCatching { node.contentDescription?.toString() }.getOrNull()
                (desc != null && desc.contains(ROTATE_DESC_KO)) ||
                    (desc != null && desc.lowercase().contains(ROTATE_DESC_EN))
            }
            if (found != null) return found
        }
        return null
    }

    private fun searchNode(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (runCatching { predicate(node) }.getOrDefault(false)) return node
        val childCount = runCatching { node.childCount }.getOrDefault(0)
        for (i in 0 until childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            searchNode(child, predicate)?.let { return it }
        }
        return null
    }

    /** [budgetMs] 안에서 [find] 로 노드를 폴링 탐색해 발견 즉시 클릭한다 (SplitEntry.clickWhenFound 와 동일 관례). */
    private suspend fun clickWhenFound(
        budgetMs: Long,
        what: String,
        find: () -> AccessibilityNodeInfo?,
    ): Boolean {
        val attempt: () -> Boolean = attempt@{
            val node = find() ?: return@attempt false
            val clickable = clickableAncestorOrSelf(node)
            if (clickable != null &&
                runCatching { clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK) }.getOrDefault(false)
            ) {
                Log.i(TAG, "clickWhenFound: [$what] clicked")
                return@attempt true
            }
            val tapped = tapNodeCenter(node)
            if (tapped) Log.i(TAG, "clickWhenFound: [$what] gesture-tap-fallback")
            tapped
        }
        val ok = if (budgetMs <= 0) {
            attempt()
        } else {
            withTimeoutOrNull(budgetMs) {
                while (!attempt()) delay(POLL_INTERVAL_MS)
                true
            } ?: false
        }
        if (!ok) Log.w(TAG, "clickWhenFound: [$what] ${budgetMs}ms 예산 안에 노드 발견/클릭 실패")
        return ok
    }

    private fun clickableAncestorOrSelf(node: AccessibilityNodeInfo, maxDepth: Int = 10): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = node
        repeat(maxDepth + 1) {
            val n = cur ?: return null
            if (runCatching { n.isClickable }.getOrDefault(false)) return n
            cur = runCatching { n.parent }.getOrNull()
        }
        return null
    }

    private fun tapNodeCenter(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        runCatching { node.getBoundsInScreen(bounds) }
        if (bounds.isEmpty) return false
        return tapPoint(bounds.centerX(), bounds.centerY())
    }

    private fun tapPoint(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS))
            .build()
        return runCatching { service.dispatchGesture(gesture, null, null) }.getOrDefault(false)
    }

    private suspend fun pollUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        if (timeoutMs <= 0) return condition()
        return withTimeoutOrNull(timeoutMs) {
            while (!condition()) delay(POLL_INTERVAL_MS)
            true
        } ?: false
    }

    companion object {
        private const val TAG = "FWDividerRotator"
        private const val POLL_INTERVAL_MS = 150L
        private const val TAP_DURATION_MS = 50L

        // [측정] 디바이더 핸들 탭 팝업 노드 3종(content-desc) 중 회전 버튼.
        // docs/DEVICE_FACTS.md "분할 진입 전략" 실측값이 근거다.
        const val ROTATE_DESC_KO = "시계 방향으로 회전"

        // [미검증] 영어 로케일 후보
        const val ROTATE_DESC_EN = "rotate"
    }
}
