package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitPlannerTest {

    /** Fold 7 내부 화면 가로 모드. 디바이더/최소창은 Phase 0 실측 전 잠정값 */
    private fun fold7(
        divider: Int = 0,
        minPane: Int = 0,
        top: Int = 0,
    ) = WindowGeometry(
        usableLeft = 0,
        usableTop = top,
        usableWidth = 2184,
        usableHeight = 1968,
        dividerThickness = divider,
        minPaneHeight = minPane,
    )

    private val ASPECT_16_9 = 16f / 9f
    private val ASPECT_21_9 = 64f / 27f

    // ── 기본 계산 ──────────────────────────────────────────────

    @Test
    fun `16 to 9 on fold7 gives 1228px video pane and zero residual`() {
        val plan = SplitPlanner.plan(fold7(), ASPECT_16_9, Placement.TOP)

        assertEquals(1229, plan.videoPaneHeight)   // 2184 / 1.7778 = 1228.5 → 반올림
        assertEquals(0, plan.residualLetterboxPx)
        assertEquals(0, plan.residualPillarboxPx)
        assertTrue(plan.exact)
        assertNull(plan.clampReason)
    }

    @Test
    fun `16 to 9 top fraction is about 62 percent`() {
        val plan = SplitPlanner.plan(fold7(), ASPECT_16_9, Placement.TOP)
        assertEquals(0.6245f, plan.topFraction, 0.002f)
    }

    @Test
    fun `21 to 9 leaves a much larger partner pane`() {
        val plan = SplitPlanner.plan(fold7(), ASPECT_21_9, Placement.TOP)
        assertEquals(921, plan.videoPaneHeight)    // 2184 / 2.3704
        assertEquals(1968 - 921, plan.panelRect.height)
        assertTrue(plan.exact)
    }

    // ── 배치 방향 ──────────────────────────────────────────────

    @Test
    fun `TOP places video at the top and panel below`() {
        val plan = SplitPlanner.plan(fold7(), ASPECT_16_9, Placement.TOP)

        assertEquals(0, plan.videoRect.top)
        assertEquals(plan.videoRect.bottom, plan.panelRect.top)
        assertEquals(1968, plan.panelRect.bottom)
    }

    @Test
    fun `BOTTOM places panel at the top and video below`() {
        val plan = SplitPlanner.plan(fold7(), ASPECT_16_9, Placement.BOTTOM)

        assertEquals(0, plan.panelRect.top)
        assertEquals(plan.panelRect.bottom, plan.videoRect.top)
        assertEquals(1968, plan.videoRect.bottom)
        assertEquals(1229, plan.videoRect.height)
    }

    @Test
    fun `video pane height is identical regardless of placement`() {
        val top = SplitPlanner.plan(fold7(), ASPECT_16_9, Placement.TOP)
        val bottom = SplitPlanner.plan(fold7(), ASPECT_16_9, Placement.BOTTOM)
        assertEquals(top.videoPaneHeight, bottom.videoPaneHeight)
    }

    // ── 디바이더 두께 ──────────────────────────────────────────

    @Test
    fun `divider thickness is subtracted from allocatable height`() {
        val plan = SplitPlanner.plan(fold7(divider = 24), ASPECT_16_9, Placement.TOP)

        assertEquals(1229, plan.videoPaneHeight)
        assertEquals(1229 + 24, plan.panelRect.top)
        assertEquals(1229 + 12, plan.dividerCenterY)
        assertEquals(1968 - 1229 - 24, plan.panelRect.height)
    }

    // ── 화면 좌표 오프셋 (시스템 바 인셋) ──────────────────────

    @Test
    fun `usableTop offsets every screen coordinate`() {
        val plan = SplitPlanner.plan(fold7(top = 96), ASPECT_16_9, Placement.TOP)

        assertEquals(96, plan.videoRect.top)
        assertEquals(96 + 1229, plan.videoRect.bottom)
        assertEquals(96 + 1229, plan.dividerCenterY)
    }

    // ── 클램프 ────────────────────────────────────────────────

    @Test
    fun `partner min height caps the video pane and creates pillarbox`() {
        // 최소 창 800px → 영상 창 상한 1168px. 이상값 1229px 까지 키울 수 없다
        val plan = SplitPlanner.plan(fold7(minPane = 800), ASPECT_16_9, Placement.TOP)

        assertEquals(1168, plan.videoPaneHeight)              // 1968 - 800
        assertEquals(ClampReason.HIT_MAX_PANE_CEILING, plan.clampReason)
        assertTrue(!plan.exact)
        assertEquals(0, plan.residualLetterboxPx)             // 세로 띠는 없고
        assertTrue(plan.residualPillarboxPx > 0)              // 좌우 여백이 생긴다
    }

    @Test
    fun `ultrawide aspect hits the min pane floor and leaves residual letterbox`() {
        // 32:9 → 이상 높이 614px. 최소 창 800px 이라 그보다 작게 못 만든다
        val plan = SplitPlanner.plan(fold7(minPane = 800), 32f / 9f, Placement.TOP)

        assertEquals(800, plan.videoPaneHeight)
        assertEquals(ClampReason.HIT_MIN_PANE_FLOOR, plan.clampReason)
        assertEquals(800 - 614, plan.residualLetterboxPx)
        assertEquals(0, plan.residualPillarboxPx)
    }

    // ── 디바이더 이동 ──────────────────────────────────────────

    @Test
    fun `dividerTravel is signed`() {
        val plan = SplitPlanner.plan(fold7(), ASPECT_16_9, Placement.TOP)

        assertTrue(SplitPlanner.dividerTravel(500, plan) > 0)     // 아래로 내려야 함
        assertTrue(SplitPlanner.dividerTravel(1800, plan) < 0)    // 위로 올려야 함
    }

    @Test
    fun `needsMove ignores sub-tolerance jitter`() {
        val plan = SplitPlanner.plan(fold7(), ASPECT_16_9, Placement.TOP)
        val target = plan.dividerCenterY

        assertTrue(!SplitPlanner.needsMove(target + 2, plan, tolerancePx = 4))
        assertTrue(SplitPlanner.needsMove(target + 40, plan, tolerancePx = 4))
    }

    // ── 역산 ──────────────────────────────────────────────────

    @Test
    fun `impliedAspect round-trips with plan`() {
        val plan = SplitPlanner.plan(fold7(), ASPECT_16_9, Placement.TOP)
        val implied = SplitPlanner.impliedAspect(plan.videoRect.width, plan.videoPaneHeight)
        assertEquals(ASPECT_16_9, implied, 0.005f)
    }

    // ── 입력 검증 ──────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `zero aspect is rejected`() {
        SplitPlanner.plan(fold7(), 0f, Placement.TOP)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative aspect is rejected`() {
        SplitPlanner.plan(fold7(), -1.5f, Placement.TOP)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero usable size is rejected`() {
        WindowGeometry(0, 0, 0, 1968, 0, 0)
    }

    @Test
    fun `rects are always well formed`() {
        for (aspect in listOf(1.33f, 1.78f, 2.0f, 2.35f, 3.0f)) {
            for (placement in Placement.entries) {
                val plan = SplitPlanner.plan(fold7(divider = 24, minPane = 200), aspect, placement)
                assertNotNull(plan.videoRect)
                assertTrue(plan.videoRect.height > 0)
                assertTrue(plan.panelRect.height > 0)
                assertTrue(plan.videoRect.width == 2184)
            }
        }
    }
}
