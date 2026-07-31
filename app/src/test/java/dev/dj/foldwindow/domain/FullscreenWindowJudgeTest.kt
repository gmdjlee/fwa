package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [FullscreenWindowJudge] 의 긍정 술어 검증 (docs/DESIGN_30_FULLSCREEN_AUTO.md §5 의 14~20번).
 *
 * 14~17 번은 **실기기 프로브 리포트의 B절 창 목록을 그대로 옮긴 앵커 테스트**다 — 설계 전체가
 * 이 3표본 위에 서 있으므로(설계서 §2.1 표) 좌표를 임의로 고치면 안 된다. 출처:
 * - `docs/probe_report_fullscreen.md` (2026-07-25 00:23:39, 가로 몰입 재생, 2184×1968)
 * - `docs/probe_report.md` (2026-07-25 00:08:57, 세로 비몰입 엣지투엣지, 1968×2184)
 * - `docs/probe_report_split.md` (2026-07-25 00:14:50, 분할 활성, 1968×2184 — D절 해상도 기준)
 *
 * `isApplication` 은 각 리포트 B절 **type 열이 APPLICATION 인가**로 환원했다.
 * 18~20 번은 상수 경계 검증이라 합성 입력을 쓴다.
 */
class FullscreenWindowJudgeTest {

    // ── 실측 앵커 ────────────────────────────────────────────────

    /** `docs/probe_report_fullscreen.md` D절: 2184 × 1968 (LANDSCAPE) */
    private val fullscreenScreen = IntRect(0, 0, 2184, 1968)

    /** `docs/probe_report.md` / `docs/probe_report_split.md` D절: 1968 × 2184 (PORTRAIT) */
    private val portraitScreen = IntRect(0, 0, 1968, 2184)

    /** `docs/probe_report_fullscreen.md` B절 창 3개 (유튜브 가로 몰입 재생) */
    private fun fullscreenDump(): List<WindowBox> = listOf(
        other(2117, 507, 2184, 1530), // UNKNOWN(-1) com.samsung.android.sidegesturepad
        other(0, 460, 67, 1530), //     UNKNOWN(-1) com.samsung.android.sidegesturepad
        app(0, 0, 2184, 1968), //       APPLICATION com.google.android.youtube
    )

    /** `docs/probe_report.md` B절 창 7개 (세로 비몰입 — 전폭 상태바 `0,0,1968,89` 존재) */
    private fun portraitNonImmersiveDump(): List<WindowBox> = listOf(
        other(1917, 1134, 1968, 1646), // UNKNOWN(-1) com.samsung.android.sidegesturepad
        other(0, 1208, 51, 1701), //      UNKNOWN(-1) com.samsung.android.sidegesturepad
        other(0, 2150, 1968, 2184), //    SYSTEM      com.sec.android.app.launcher
        other(1230, 2104, 1968, 2184), // UNKNOWN(-1) com.sec.android.app.launcher
        other(1915, 235, 1968, 564), //   SYSTEM      com.sec.android.app.launcher (폭 53px = 2.7%)
        other(0, 0, 1968, 89), //         SYSTEM      com.android.systemui (전폭 상태바)
        app(0, 0, 1968, 2184), //         APPLICATION dev.dj.foldwindow
    )

    /**
     * `docs/probe_report_split.md` B절 창 7개 (분할 활성).
     *
     * 주의: `381,89,595,145`(com.android.systemui 소형 창)는 리포트 B절 type 열이 **APPLICATION**
     * 이다 — 설계서 §2.1 표는 이 창을 산문에서 "systemui 소형 창"으로 부르며 (b) 열에서 폭 미달로
     * 무시된다고 적었지만, 실제 type 이 APPLICATION 이므로 여기서는 (a) 열에서 "전체 덮음 아님"
     * 으로 걸린다. 어느 쪽이든 최종 판정과 D10 결론은 동일하다.
     */
    private fun splitDump(): List<WindowBox> = listOf(
        other(1917, 1134, 1968, 1646), // UNKNOWN(-1)          com.samsung.android.sidegesturepad
        other(0, 1208, 51, 1701), //      UNKNOWN(-1)          com.samsung.android.sidegesturepad
        other(0, 2150, 1968, 2184), //    SYSTEM               com.sec.android.app.launcher
        other(950, 981, 1018, 1202), //   SPLIT_SCREEN_DIVIDER com.android.systemui
        app(991, 0, 1968, 2184), //       APPLICATION          com.google.android.youtube (폭 49.6%)
        app(381, 89, 595, 145), //        APPLICATION          com.android.systemui (폭 10.9%)
        app(0, 0, 977, 2184), //          APPLICATION          dev.dj.foldwindow (폭 49.6%)
    )

    @Test
    fun `empty window list yields UNKNOWN`() {
        // 판정기가 UNKNOWN 을 내는 유일한 경우. 정책은 이 값을 무시 샘플로 다룬다 (D6).
        assertEquals(FullscreenSignal.UNKNOWN, FullscreenWindowJudge.judge(emptyList(), fullscreenScreen))
    }

    @Test
    fun `real fullscreen dump yields FULLSCREEN`() {
        // (a) APPLICATION 0,0,2184,1968 = 각 축 100% 덮음 ✓
        // (b) 상단 스트립(1968×0.06 = 118.08px) 과 교차하는 비-APP 창 없음 (top 507 / 460) ✓
        assertEquals(
            FullscreenSignal.FULLSCREEN,
            FullscreenWindowJudge.judge(fullscreenDump(), fullscreenScreen),
        )
    }

    @Test
    fun `real portrait non-immersive dump yields NOT_FULLSCREEN`() {
        // (a) 는 성립하지만 (b) 에서 전폭 상태바 0,0,1968,89 (top 0 ≤ 131.04, 폭 100% ≥ 80%) 가 잡힌다.
        assertEquals(
            FullscreenSignal.NOT_FULLSCREEN,
            FullscreenWindowJudge.judge(portraitNonImmersiveDump(), portraitScreen),
        )
    }

    @Test
    fun `real split dump yields NOT_FULLSCREEN`() {
        // D8 반증 표본. 최대 페인 폭 977/1968 = 49.6% 라 (a) 자체가 성립하지 않는다.
        assertEquals(
            FullscreenSignal.NOT_FULLSCREEN,
            FullscreenWindowJudge.judge(splitDump(), portraitScreen),
        )
    }

    // ── 상수 경계 ────────────────────────────────────────────────

    @Test
    fun `narrow systemui window intersecting top strip is ignored`() {
        // D10: 토스트·볼륨패널·자기 버블이 상단 스트립과 교차해도 상단 바로 승격되면 안 된다.
        // 실측 소형 창 381,89,595,145 (폭 214px = 화면 폭의 9.8%) 를 몰입 덤프에 얹는다.
        val withNarrow = fullscreenDump() + other(381, 89, 595, 145)
        assertEquals(FullscreenSignal.FULLSCREEN, FullscreenWindowJudge.judge(withNarrow, fullscreenScreen))

        // 경계: 임계 폭 = screen.width × 0.80 = 2184 × 0.80 = 1747.2px.
        // 정수 폭이라 1747px 은 미달(무시), 1748px 부터 상단 바로 인정된다.
        val justBelow = fullscreenDump() + other(0, 89, 1747, 145)
        assertEquals(FullscreenSignal.FULLSCREEN, FullscreenWindowJudge.judge(justBelow, fullscreenScreen))

        val atThreshold = fullscreenDump() + other(0, 89, 1748, 145)
        assertEquals(
            FullscreenSignal.NOT_FULLSCREEN,
            FullscreenWindowJudge.judge(atThreshold, fullscreenScreen),
        )
    }

    @Test
    fun `top strip boundary`() {
        // 상단 스트립 하한 = screen.height × 0.06 = 1968 × 0.06 = 118.08px.
        // 전폭 비-APP 창의 top 이 118 이면 교차로 판정되고, 119 면 스트립 밖이라 무시된다.
        val intersecting = fullscreenDump() + other(0, 118, 2184, 207)
        assertEquals(
            FullscreenSignal.NOT_FULLSCREEN,
            FullscreenWindowJudge.judge(intersecting, fullscreenScreen),
        )

        val belowStrip = fullscreenDump() + other(0, 119, 2184, 208)
        assertEquals(FullscreenSignal.FULLSCREEN, FullscreenWindowJudge.judge(belowStrip, fullscreenScreen))
    }

    @Test
    fun `application window covering 98 percent is not full cover`() {
        // FULL_COVER_MIN_FRACTION = 0.99. 임계 폭 = 2184 × 0.99 = 2162.16px, 임계 높이 = 1968 × 0.99
        // = 1948.32px. 분할 페인(49.6%)과 전체화면 창(100%) 사이를 가르는 눈금이다.
        val narrowByTwoPercent = listOf(app(0, 0, 2140, 1968)) // 폭 97.98%
        assertEquals(
            FullscreenSignal.NOT_FULLSCREEN,
            FullscreenWindowJudge.judge(narrowByTwoPercent, fullscreenScreen),
        )

        val shortByTwoPercent = listOf(app(0, 0, 2184, 1929)) // 높이 98.02%
        assertEquals(
            FullscreenSignal.NOT_FULLSCREEN,
            FullscreenWindowJudge.judge(shortByTwoPercent, fullscreenScreen),
        )

        // 경계: 2162px 은 미달, 2163px 부터 전체 덮음.
        assertEquals(
            FullscreenSignal.NOT_FULLSCREEN,
            FullscreenWindowJudge.judge(listOf(app(0, 0, 2162, 1968)), fullscreenScreen),
        )
        assertEquals(
            FullscreenSignal.FULLSCREEN,
            FullscreenWindowJudge.judge(listOf(app(0, 0, 2163, 1968)), fullscreenScreen),
        )
    }

    // ── 헬퍼 ────────────────────────────────────────────────────

    /** 리포트 B절 type 열이 APPLICATION 인 창 */
    private fun app(left: Int, top: Int, right: Int, bottom: Int) =
        WindowBox(IntRect(left, top, right, bottom), isApplication = true)

    /** 그 외 전부(SYSTEM · SPLIT_SCREEN_DIVIDER · UNKNOWN(-1)) */
    private fun other(left: Int, top: Int, right: Int, bottom: Int) =
        WindowBox(IntRect(left, top, right, bottom), isApplication = false)
}
