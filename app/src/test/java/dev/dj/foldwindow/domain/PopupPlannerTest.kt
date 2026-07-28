package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PopupPlannerTest {

    /** Fold 7 내부 화면 세로(자연 방향). DEVICE_FACTS.md 실측값 */
    private val SCREEN_W = 1968
    private val SCREEN_H = 2184

    @Test
    fun `16 to 9 gives width-first layout centered at top`() {
        val rect = PopupPlanner.plan(SCREEN_W, SCREEN_H, 16f / 9f)

        assertEquals(64, rect.left)
        assertEquals(150, rect.top)
        assertEquals(1840, rect.width)
        assertEquals(1035, rect.height)
    }

    @Test
    fun `portrait 9 to 16 clamps height and recomputes width centered`() {
        val rect = PopupPlanner.plan(SCREEN_W, SCREEN_H, 9f / 16f)

        assertEquals(1884, rect.height)      // maxH = 2184 - 150 - 150
        assertEquals(1060, rect.width)       // round(1884 * 0.5625)
        assertEquals(150, rect.top)
        assertEquals((SCREEN_W - rect.width) / 2, rect.left)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero aspect throws`() {
        PopupPlanner.plan(SCREEN_W, SCREEN_H, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative aspect throws`() {
        PopupPlanner.plan(SCREEN_W, SCREEN_H, -1.5f)
    }

    @Test
    fun `aspect exactly at clamp boundary keeps width-first result`() {
        val maxH = SCREEN_H - PopupPlanner.TOP_MARGIN - PopupPlanner.BOTTOM_MARGIN // 1884
        val w = SCREEN_W - 2 * PopupPlanner.MARGIN_H // 1840
        val boundaryAspect = w.toFloat() / maxH.toFloat()

        val rect = PopupPlanner.plan(SCREEN_W, SCREEN_H, boundaryAspect)

        assertEquals(w, rect.width)
        assertEquals(maxH, rect.height)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `screen too narrow for margins throws`() {
        PopupPlanner.plan(2 * PopupPlanner.MARGIN_H, SCREEN_H, 16f / 9f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `screen too short for margins throws`() {
        PopupPlanner.plan(SCREEN_W, PopupPlanner.TOP_MARGIN + PopupPlanner.BOTTOM_MARGIN, 16f / 9f)
    }
}
