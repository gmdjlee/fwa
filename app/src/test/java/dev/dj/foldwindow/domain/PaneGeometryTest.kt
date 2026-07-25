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
}
