package dev.dj.foldwindow.platform

import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import dev.dj.foldwindow.domain.IntRect
import dev.dj.foldwindow.domain.PaneGeometry

/**
 * 분할 화면 디바이더 핸들 위치.
 *
 * [측정] DEVICE_FACTS.md 2026-07-25: `TYPE_SPLIT_SCREEN_DIVIDER` 창은 분할 활성 중에만
 * 노출되고, bounds 는 디바이더 전체가 아니라 드래그 핸들(세로 68×221, 가로 221×68 대칭 가정)뿐이다.
 * 핸들 중심이 드래그 기준점이다.
 *
 * @param fromAccessibilityWindow true = TYPE_SPLIT_SCREEN_DIVIDER 창에서 직접 획득,
 *                                 false = 페인 간격 휴리스틱 폴백
 */
data class DividerHandle(
    val centerX: Int,
    val centerY: Int,
    val fromAccessibilityWindow: Boolean,
)

/**
 * 접근성 창 목록에서 디바이더 핸들 위치를 조회한다.
 *
 * 1차 경로: `TYPE_SPLIT_SCREEN_DIVIDER` 창의 bounds 중심.
 * 폴백(창 미조회 시): `TYPE_APPLICATION` 페인들의 시각 간격(PaneGeometry)으로 추정.
 * 창 필드 접근은 probe/ProbeAccessibilityService 의 관례대로 runCatching 으로 방어한다.
 */
class DividerLocator {

    /** 분할 활성 중 디바이더 핸들 중심. 미발견 시 null */
    fun locate(windows: List<AccessibilityWindowInfo>, screen: IntRect): DividerHandle? {
        findDividerHandleRect(windows)?.let { rect ->
            return DividerHandle(
                centerX = (rect.left + rect.right) / 2,
                centerY = (rect.top + rect.bottom) / 2,
                fromAccessibilityWindow = true,
            )
        }

        val gapCenterY = PaneGeometry.findHorizontalGapCenterY(applicationPaneRects(windows), screen)
            ?: return null
        return DividerHandle(
            centerX = (screen.left + screen.right) / 2,
            centerY = gapCenterY,
            fromAccessibilityWindow = false,
        )
    }

    /** 분할 활성 판정: 디바이더 창 존재 OR 앱 페인 상하 분할 레이아웃 */
    fun isSplitActive(windows: List<AccessibilityWindowInfo>, screen: IntRect): Boolean {
        if (findDividerHandleRect(windows) != null) return true
        return PaneGeometry.isTopBottomSplit(applicationPaneRects(windows), screen)
    }

    private fun findDividerHandleRect(windows: List<AccessibilityWindowInfo>): IntRect? {
        val dividerWindow = runCatching {
            windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER }
        }.getOrNull() ?: return null

        return runCatching {
            val bounds = Rect().also { dividerWindow.getBoundsInScreen(it) }
            IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }.getOrNull()
    }

    /**
     * 접근성 창 목록에 보이는 `TYPE_APPLICATION` 창 수.
     *
     * 판독 유효성 판정용이다 — 풀스크린 오버레이(확장 메뉴 스크림)가 떠 있는 동안 시스템은
     * 가려진 하위 창을 목록에서 제외하므로 이 값이 0 이 된다. 전면 앱이 하나라도 있는 한
     * 0 은 물리적으로 불가능한 값이므로, **0 은 "분할 없음"이 아니라 "판독 불가"를 뜻한다.**
     */
    fun applicationWindowCount(windows: List<AccessibilityWindowInfo>): Int =
        applicationPaneRects(windows).size

    private fun applicationPaneRects(windows: List<AccessibilityWindowInfo>): List<IntRect> =
        runCatching {
            windows
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { w ->
                    runCatching {
                        val bounds = Rect().also { w.getBoundsInScreen(it) }
                        IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom)
                    }.getOrNull()
                }
        }.getOrDefault(emptyList())
}
