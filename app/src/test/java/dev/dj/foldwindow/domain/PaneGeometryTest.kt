package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class PaneGeometryTest {

    /** 가로(상하 분할) 시청 시나리오 화면. Fold 7 실측: 2184(W) x 1968(H) */
    private val landscapeScreen = IntRect(0, 0, 2184, 1968)

    // ── visibleRect ────────────────────────────────────────────

    @Test
    fun `visibleRect fully inside screen returns the same rect`() {
        val r = IntRect(100, 100, 2000, 1800)
        val visible = PaneGeometry.visibleRect(r, landscapeScreen)
        assertEquals(r, visible)
    }

    @Test
    fun `visibleRect clamps negative-left offscreen slide to visible intersection`() {
        // DEVICE_FACTS.md 실측 재현: 세로 좌우분할 최소 스냅 시 ProbeActivity frame
        // Rect(-1592,0-181,2184) — 창은 화면 밖으로 슬라이드되었고 가시 폭만 181px.
        val portraitScreen = IntRect(0, 0, 1968, 2184)
        val slidPane = IntRect(-1592, 0, 181, 2184)

        val visible = PaneGeometry.visibleRect(slidPane, portraitScreen)

        assertEquals(IntRect(0, 0, 181, 2184), visible)
        assertEquals(181, visible!!.width)
    }

    @Test
    fun `visibleRect with no overlap returns null`() {
        val portraitScreen = IntRect(0, 0, 1968, 2184)
        val entirelyOffscreen = IntRect(-500, 0, -100, 2184)

        assertNull(PaneGeometry.visibleRect(entirelyOffscreen, portraitScreen))
    }

    // ── findHorizontalGapCenterY ─────────────────────────────────

    @Test
    fun `findHorizontalGapCenterY happy path finds correct center for 14px gap`() {
        val upper = IntRect(0, 0, 2184, 975)
        val lower = IntRect(0, 989, 2184, 1968) // gap = 989 - 975 = 14

        val center = PaneGeometry.findHorizontalGapCenterY(listOf(upper, lower), landscapeScreen)

        assertEquals(982, center) // (975 + 989) / 2
    }

    @Test
    fun `findHorizontalGapCenterY rejects gap smaller than tolerance`() {
        val upper = IntRect(0, 0, 2184, 975)
        val lower = IntRect(0, 979, 2184, 1968) // gap = 4px, below (14 - 6) = 8 floor

        assertNull(PaneGeometry.findHorizontalGapCenterY(listOf(upper, lower), landscapeScreen))
    }

    @Test
    fun `findHorizontalGapCenterY rejects gap larger than tolerance`() {
        val upper = IntRect(0, 0, 2184, 975)
        val lower = IntRect(0, 1055, 2184, 1968) // gap = 80px, above (14 + 40) = 54 ceiling

        assertNull(PaneGeometry.findHorizontalGapCenterY(listOf(upper, lower), landscapeScreen))
    }

    @Test
    fun `findHorizontalGapCenterY returns null for side-by-side vertical split panes`() {
        // 좌우 분할: 각 페인 폭이 화면의 절반이라 60% 임계(1310.4px)를 절대 못 넘는다.
        val left = IntRect(0, 0, 1090, 1968)
        val right = IntRect(1094, 0, 2184, 1968)

        assertNull(PaneGeometry.findHorizontalGapCenterY(listOf(left, right), landscapeScreen))
    }

    @Test
    fun `findHorizontalGapCenterY excludes narrow popup pane leaving fewer than 2 candidates`() {
        val mainPane = IntRect(0, 0, 2184, 975)
        val narrowPopup = IntRect(800, 975, 1384, 1200) // width 584 < 60% of 2184

        assertNull(PaneGeometry.findHorizontalGapCenterY(listOf(mainPane, narrowPopup), landscapeScreen))
    }

    @Test
    fun `findHorizontalGapCenterY still finds gap after clamping an offscreen-slid pane`() {
        val slidUpper = IntRect(-50, 0, 2134, 975) // 왼쪽으로 살짝 슬라이드. 클램프 후 폭 2134
        val lower = IntRect(0, 989, 2184, 1968)     // gap = 989 - 975 = 14

        val center = PaneGeometry.findHorizontalGapCenterY(listOf(slidUpper, lower), landscapeScreen)

        assertEquals(982, center)
    }

    // ── isTopBottomSplit ─────────────────────────────────────────

    @Test
    fun `isTopBottomSplit is true for two stacked wide panes covering most of the screen`() {
        val upper = IntRect(0, 0, 2184, 975)
        val lower = IntRect(0, 989, 2184, 1968)

        assertTrue(PaneGeometry.isTopBottomSplit(listOf(upper, lower), landscapeScreen))
    }

    @Test
    fun `isTopBottomSplit is false with only one pane`() {
        val upper = IntRect(0, 0, 2184, 975)

        assertFalse(PaneGeometry.isTopBottomSplit(listOf(upper), landscapeScreen))
    }

    @Test
    fun `isTopBottomSplit is false for side-by-side panes`() {
        val left = IntRect(0, 0, 1090, 1968)
        val right = IntRect(1094, 0, 2184, 1968)

        assertFalse(PaneGeometry.isTopBottomSplit(listOf(left, right), landscapeScreen))
    }

    // ── isLeftRightSplit ─────────────────────────────────────────

    @Test
    fun `isLeftRightSplit is true for two side-by-side panes covering most of the screen`() {
        val left = IntRect(0, 0, 1090, 1968)
        val right = IntRect(1094, 0, 2184, 1968)

        assertTrue(PaneGeometry.isLeftRightSplit(listOf(left, right), landscapeScreen))
    }

    @Test
    fun `isLeftRightSplit rejects a floating popup leaving fewer than 2 tall candidates`() {
        val mainPane = IntRect(0, 0, 1090, 1968) // 가시 높이 1968 (전체) -> 통과
        val centerPopup = IntRect(800, 600, 1400, 1200) // 가시 높이 600 < 60%(1180.8) -> 제외

        assertFalse(PaneGeometry.isLeftRightSplit(listOf(mainPane, centerPopup), landscapeScreen))
    }

    @Test
    fun `isLeftRightSplit rejects top-bottom shaped panes (axis mismatch)`() {
        // 상하 분할 페인은 각 가시 높이가 60% 임계(1180.8px)를 넘지 못해 후보에서 전부 제외된다.
        val upper = IntRect(0, 0, 2184, 975)
        val lower = IntRect(0, 989, 2184, 1968)

        assertFalse(PaneGeometry.isLeftRightSplit(listOf(upper, lower), landscapeScreen))
    }

    @Test
    fun `isLeftRightSplit still finds split after clamping an offscreen-slid pane`() {
        val slidLeft = IntRect(-100, 0, 1190, 1968) // 왼쪽으로 슬라이드. 클램프 후 폭 1190
        val right = IntRect(1194, 0, 2184, 1968)     // 폭 990

        assertTrue(PaneGeometry.isLeftRightSplit(listOf(slidLeft, right), landscapeScreen))
    }

    // ── isSplitSelectTopPane ───────────────────────────────────────

    @Test
    fun `isSplitSelectTopPane accepts full-width top-docked pane within ratio range`() {
        val pane = IntRect(0, 0, 2184, 900) // 폭 100%, 상단 도킹, 비율 900/1968=0.457

        assertTrue(PaneGeometry.isSplitSelectTopPane(pane, landscapeScreen))
    }

    @Test
    fun `isSplitSelectTopPane rejects a narrow centered popup`() {
        val popup = IntRect(600, 400, 1600, 1200) // 폭 1000 < 90%(1965.6)

        assertFalse(PaneGeometry.isSplitSelectTopPane(popup, landscapeScreen))
    }

    @Test
    fun `isSplitSelectTopPane rejects a left-right shaped pane (axis mismatch)`() {
        // 좌우 분할 페인 모양: 전고·좁은 폭 -> 전폭(90%) 조건에서 탈락
        val sidePane = IntRect(0, 0, 600, 1968)

        assertFalse(PaneGeometry.isSplitSelectTopPane(sidePane, landscapeScreen))
    }

    @Test
    fun `isSplitSelectTopPane uses clamped visible width, not raw off-screen width`() {
        // 원본 rect 는 화면 밖으로 -1996 만큼 슬라이드되어 raw 폭(right-left)이 2184(100%)로 보이지만,
        // 실제 가시 교집합 폭은 188px(8.6%)뿐이다. 클램프를 안 하면 오탐 통과한다.
        val slidPane = IntRect(-1996, 0, 188, 900)

        assertFalse(PaneGeometry.isSplitSelectTopPane(slidPane, landscapeScreen))
    }

    // ── isSplitSelectSidePane ──────────────────────────────────────

    @Test
    fun `isSplitSelectSidePane accepts full-height left-docked pane within ratio range`() {
        val pane = IntRect(0, 0, 900, 1968) // 높이 100%, 좌측 도킹, 비율 900/2184=0.412

        assertTrue(PaneGeometry.isSplitSelectSidePane(pane, landscapeScreen))
    }

    @Test
    fun `isSplitSelectSidePane accepts full-height right-docked pane within ratio range`() {
        val pane = IntRect(1284, 0, 2184, 1968) // 우측 도킹 (visible.right=2184 >= screen.right-40=2144)

        assertTrue(PaneGeometry.isSplitSelectSidePane(pane, landscapeScreen))
    }

    @Test
    fun `isSplitSelectSidePane rejects a floating popup that is not edge-docked and too short`() {
        val popup = IntRect(700, 500, 1500, 1400) // 높이 900 < 90%(1771.2), 어느 가장자리에도 안 붙음

        assertFalse(PaneGeometry.isSplitSelectSidePane(popup, landscapeScreen))
    }

    @Test
    fun `isSplitSelectSidePane rejects a top-bottom shaped pane (axis mismatch)`() {
        // 상하 분할 페인 모양: 전폭·낮은 높이 -> 전고(90%) 조건에서 탈락
        val topPane = IntRect(0, 0, 2184, 900)

        assertFalse(PaneGeometry.isSplitSelectSidePane(topPane, landscapeScreen))
    }

    @Test
    fun `isSplitSelectSidePane uses clamped visible width, not raw off-screen width`() {
        // raw 폭(right-left)=1900, 비율 1900/2184=0.870 (0.75 상한 초과로 raw 기준이면 거부돼야 함).
        // 실제 가시 교집합 폭은 700px, 비율 0.320 으로 정상 범위 -> 클램프가 없으면 잘못된 결과가 나온다.
        val slidPane = IntRect(-1200, 0, 700, 1968)

        assertTrue(PaneGeometry.isSplitSelectSidePane(slidPane, landscapeScreen))
    }

    // ── pickPaneLike ───────────────────────────────────────────────
    // [측정 2026-07-25] 넷플릭스 "최소화된 플레이어" 팝업(같은 패키지의 좁은 부유 창)이
    // 위치 판정을 오염시키는 실측 대응.

    @Test
    fun `pickPaneLike selects the only wide pane candidate`() {
        val pane = IntRect(0, 0, 2184, 975)

        val result = PaneGeometry.pickPaneLike(listOf(pane), landscapeScreen)

        assertEquals(pane, result)
    }

    @Test
    fun `pickPaneLike returns null when only a narrow popup window exists`() {
        // 넷플릭스 "최소화된 플레이어" 팝업 재현 — 우하단 부유 창, 폭 500 < 60%(1310.4)
        val popup = IntRect(1600, 1400, 2100, 1900)

        val result = PaneGeometry.pickPaneLike(listOf(popup), landscapeScreen)

        assertNull(result)
    }

    @Test
    fun `pickPaneLike picks the split pane over a coexisting minimized-player popup`() {
        val pane = IntRect(0, 0, 2184, 975) // 넷플릭스 APPLICATION 창(분할 페인)
        val popup = IntRect(1600, 1400, 2100, 1900) // 같은 패키지의 최소화된 플레이어 팝업

        val result = PaneGeometry.pickPaneLike(listOf(pane, popup), landscapeScreen)

        assertEquals(pane, result)
    }

    @Test
    fun `pickPaneLike compares clamped visible area, not raw off-screen size`() {
        // offscreenSlid 의 raw 크기(3600x1000=3,600,000)는 fullyVisible(2184x975=2,130,300)보다 크지만,
        // 실제 가시 교집합은 (0,500)-(2000,1500) 로 클램프되어 면적 2,000,000 뿐이다.
        // 클램프 후 면적으로 비교하지 않으면 offscreenSlid 가 잘못 선택된다.
        val offscreenSlid = IntRect(-1600, 500, 2000, 1500)
        val fullyVisible = IntRect(0, 0, 2184, 975)

        val result = PaneGeometry.pickPaneLike(listOf(offscreenSlid, fullyVisible), landscapeScreen)

        assertEquals(fullyVisible, result)
    }

    @Test
    fun `pickPaneLike returns null for an empty candidate list`() {
        assertNull(PaneGeometry.pickPaneLike(emptyList(), landscapeScreen))
    }
}
