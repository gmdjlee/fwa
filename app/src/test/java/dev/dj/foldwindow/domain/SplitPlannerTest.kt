package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    // ── Fold 7 실측 기하 (P1-1) ──────────────────────────────────

    /**
     * Fold 7 실측값 팩토리(dividerThickness=14, minPaneHeight=563 — 24차 가로 실측)
     */
    private fun foldSevenLandscapeGeom() = WindowGeometry.foldSevenLandscape()

    @Test
    fun `foldSevenLandscape with 16 to 9 gives exact fit`() {
        val geom = foldSevenLandscapeGeom()
        val plan = SplitPlanner.plan(geom, ASPECT_16_9, Placement.TOP)

        // allocatable = 1968 - 14 = 1954
        // idealVideoH = round(2184 / 1.7778) = 1229
        // videoH = 1229.coerceIn(563, 1391) = 1229
        assertEquals(1229, plan.videoPaneHeight)
        assertEquals(0, plan.residualLetterboxPx)
        assertEquals(0, plan.residualPillarboxPx)
        assertTrue(plan.exact)
        assertNull(plan.clampReason)
    }

    @Test
    fun `foldSevenLandscape 16 to 9 TOP placement has correct divider positioning`() {
        val geom = foldSevenLandscapeGeom()
        val plan = SplitPlanner.plan(geom, ASPECT_16_9, Placement.TOP)

        assertEquals(1229 + 14, plan.panelRect.top)
        assertEquals(1229 + 7, plan.dividerCenterY)
        assertEquals(1968 - 1229 - 14, plan.panelRect.height)
    }

    @Test
    fun `foldSevenLandscape with 21 to 9 exact fit with minPane not triggered`() {
        val geom = foldSevenLandscapeGeom()
        val plan = SplitPlanner.plan(geom, ASPECT_21_9, Placement.TOP)

        // allocatable = 1954, idealVideoH = round(2184 / 2.3704) = 921
        // videoH = 921.coerceIn(563, 1391) = 921
        assertEquals(921, plan.videoPaneHeight)
        assertTrue(plan.exact)
        assertNull(plan.clampReason)
    }

    @Test
    fun `foldSevenLandscape with extreme 20 to 1 hits minPaneHeight floor`() {
        val geom = foldSevenLandscapeGeom()
        val plan = SplitPlanner.plan(geom, 20f, Placement.TOP)

        // idealVideoH = round(2184 / 20) = 109
        // videoH = 109.coerceIn(563, 1391) = 563 (hit floor)
        assertEquals(563, plan.videoPaneHeight)
        assertEquals(ClampReason.HIT_MIN_PANE_FLOOR, plan.clampReason)
        assertEquals(563 - 109, plan.residualLetterboxPx)  // 454px residual letterbox
    }

    @Test
    fun `foldSevenLandscape with 4 to 3 hits max pane ceiling`() {
        // [24차 결함 ②] 이 기기 가로에서 4:3 은 성립 불가능하다 — 필요한 영상 창 1638px 이
        // 상한 1391px(= 1954 - 563)을 넘는다. 종전 minPaneHeight=181 에서는 상한이 1773 이라
        // 클램프가 걸리지 않아, 도달 불가능한 목표를 clamp=null 로 내보내고 성공으로 보고했다.
        val geom = foldSevenLandscapeGeom()
        val plan = SplitPlanner.plan(geom, 1.3333f, Placement.TOP)

        assertEquals(1391, plan.videoPaneHeight)
        assertEquals(ClampReason.HIT_MAX_PANE_CEILING, plan.clampReason)
        assertFalse(plan.exact)
        assertEquals(0, plan.residualLetterboxPx)
        // 영상이 창 높이에 맞춰지며 좌우로 남는 폭: 2184 - round(1391 * 1.3333) = 2184 - 1855
        assertEquals(329, plan.residualPillarboxPx)
    }

    @Test
    fun `foldSevenLandscape constants match measured values`() {
        val geom = foldSevenLandscapeGeom()

        // [측정] DEVICE_FACTS.md 2026-07-25 (dividerThickness), 24차 2026-08-01 (minPaneHeight)
        assertEquals(14, geom.dividerThickness)
        assertEquals(563, geom.minPaneHeight)
    }

    // ── 기하 불변식 (F1, W3) ───────────────────────────────────

    @Test
    fun `allocatableHeight exactly equal to 2 times minPaneHeight constructs fine`() {
        // usableHeight=1000, divider=0, minPane=500 → allocatable=1000, 2*500=1000 (경계 정확히 통과)
        val zeroDivider = WindowGeometry(
            usableLeft = 0, usableTop = 0,
            usableWidth = 2184, usableHeight = 1000,
            dividerThickness = 0, minPaneHeight = 500,
        )
        assertEquals(1000, zeroDivider.allocatableHeight)

        // usableHeight=1000, divider=14, minPane=493 → allocatable=986, 2*493=986 (디바이더 있는 경계)
        val withDivider = WindowGeometry(
            usableLeft = 0, usableTop = 0,
            usableWidth = 2184, usableHeight = 1000,
            dividerThickness = 14, minPaneHeight = 493,
        )
        assertEquals(986, withDivider.allocatableHeight)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `allocatableHeight one pixel short of 2 times minPaneHeight is rejected`() {
        // usableHeight=1000, divider=0, minPane=501 → allocatable=1000, 2*501=1002 → 1000 < 1002
        WindowGeometry(
            usableLeft = 0, usableTop = 0,
            usableWidth = 2184, usableHeight = 1000,
            dividerThickness = 0, minPaneHeight = 501,
        )
    }

    @Test
    fun `foldSevenLandscape satisfies the allocatable height invariant`() {
        val geom = WindowGeometry.foldSevenLandscape()
        // allocatable = 1968 - 14 = 1954, 2*minPaneHeight = 2*563 = 1126, 1954 >= 1126 → 통과
        assertEquals(1954, geom.allocatableHeight)
        assertTrue(geom.allocatableHeight >= 2 * geom.minPaneHeight)
    }

    // ── 화면 기하 정합성 (F2, W3) ─────────────────────────────

    @Test
    fun `matchesScreen returns true for an exact match`() {
        val geom = WindowGeometry.foldSevenLandscape()
        assertTrue(geom.matchesScreen(IntRect(0, 0, 2184, 1968)))
    }

    @Test
    fun `matchesScreen returns true within tolerance`() {
        val geom = WindowGeometry.foldSevenLandscape()
        // 임계값: width 2184*0.01=21.84, height 1968*0.01=19.68
        assertTrue(geom.matchesScreen(IntRect(0, 0, 2205, 1987)))  // diff 21 / 19, 둘 다 임계값 이내
    }

    @Test
    fun `matchesScreen returns false when either dimension exceeds tolerance`() {
        val geom = WindowGeometry.foldSevenLandscape()
        // width diff 22 > 21.84 → false. height diff 는 0 이라 이 케이스가 폭 단독 위반을 잡아낸다
        assertTrue(!geom.matchesScreen(IntRect(0, 0, 2206, 1968)))
        // height diff 20 > 19.68 → false. width diff 는 0 이라 이 케이스가 높이 단독 위반을 잡아낸다
        assertTrue(!geom.matchesScreen(IntRect(0, 0, 2184, 1988)))
    }

    @Test
    fun `matchesScreen returns false for a portrait-transposed screen`() {
        // F2 가 막는 실제 결함: 세로 방향에서 가로 기하를 그대로 쓰면 조용히 틀린 곳에 디바이더가 놓인다
        val geom = WindowGeometry.foldSevenLandscape()
        assertTrue(!geom.matchesScreen(IntRect(0, 0, 1968, 2184)))
    }
}
