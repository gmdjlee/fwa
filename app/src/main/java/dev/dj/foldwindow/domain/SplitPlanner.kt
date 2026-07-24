package dev.dj.foldwindow.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙).
 * 이 파일이 이 프로젝트의 유일한 회귀 방어선이다. 실기기 없이 전부 검증 가능해야 한다.
 */

/** 영상 앱을 화면의 어느 쪽에 붙일지 */
enum class Placement { TOP, BOTTOM }

data class IntRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    init {
        require(right >= left) { "right($right) < left($left)" }
        require(bottom >= top) { "bottom($bottom) < top($top)" }
    }
}

/**
 * 분할 배치 계산에 필요한 기하 정보. 모든 값은 px.
 *
 * @param usableLeft/Top 화면 좌표계에서 앱 창이 실제로 쓸 수 있는 영역의 시작점
 *                       (상태바/네비게이션바 인셋을 뺀 값)
 * @param dividerThickness One UI 분할 디바이더 두께. Phase 0 프로브 실측값으로 채운다
 * @param minPaneHeight    시스템이 허용하는 최소 창 높이. 이보다 작게는 못 만든다
 */
data class WindowGeometry(
    val usableLeft: Int,
    val usableTop: Int,
    val usableWidth: Int,
    val usableHeight: Int,
    val dividerThickness: Int,
    val minPaneHeight: Int,
) {
    init {
        require(usableWidth > 0 && usableHeight > 0) { "usable size must be positive" }
        require(dividerThickness >= 0) { "dividerThickness must be >= 0" }
        require(minPaneHeight >= 0) { "minPaneHeight must be >= 0" }
    }

    /** 두 창에 실제로 배분 가능한 총 높이 (디바이더 제외) */
    val allocatableHeight: Int get() = usableHeight - dividerThickness

    companion object {
        /**
         * Galaxy Z Fold 7 내부 화면 가로(상하 분할). DEVICE_FACTS.md 2026-07-25 실측.
         * ⚠ 이 수치는 실기기 측정값이다. 바꾸려면 새 측정 근거를 DEVICE_FACTS.md에 기록할 것.
         */
        fun foldSevenLandscape() = WindowGeometry(
            usableLeft = 0,
            usableTop = 0,          // 상태바 인셋 실측 전 0 유지 (미확정)
            usableWidth = 2184,
            usableHeight = 1968,
            dividerThickness = 14,  // [측정] 세로 좌우분할 시각 간격. 가로 대칭 가정 [미검증]
            minPaneHeight = 181,    // [측정] 세로 좌우분할. 가로 상하분할 [미검증]
        )
    }
}

/**
 * 계산 결과.
 *
 * @param residualLetterboxPx 배치 후에도 영상 창 안에 남게 될 위아래 검은 띠 총합.
 *                            0이면 목표 달성. 클램프에 걸렸을 때만 0보다 커진다.
 * @param residualPillarboxPx 창이 이상값보다 낮아 좌우 여백이 생기는 경우의 총합.
 * @param exact               클램프 없이 요청 종횡비를 정확히 맞췄는가
 */
data class SplitPlan(
    val videoRect: IntRect,
    val panelRect: IntRect,
    val dividerCenterY: Int,
    val videoPaneHeight: Int,
    val topFraction: Float,
    val exact: Boolean,
    val residualLetterboxPx: Int,
    val residualPillarboxPx: Int,
    val clampReason: ClampReason?,
)

enum class ClampReason {
    /**
     * 영상 창을 이상값까지 "줄이지" 못했다. 최소 창 높이 하한에 걸림.
     * 결과: 영상 창이 필요보다 커서 위아래 검은 띠가 남는다.
     */
    HIT_MIN_PANE_FLOOR,

    /**
     * 영상 창을 이상값까지 "키우지" 못했다. 파트너 창의 최소 높이 때문에 상한에 걸림.
     * 결과: 영상이 창 높이에 맞춰지고 좌우 여백(필러박스)이 생긴다.
     */
    HIT_MAX_PANE_CEILING,
}

object SplitPlanner {

    /**
     * 영상 종횡비에 맞춰 분할 배치를 계산한다.
     *
     * 핵심 아이디어: 영상 창의 높이를 `usableWidth / videoAspect` 로 잡으면
     * 그 창 안에서 letterbox 가 0이 되고, 남는 공간 전체가 반대쪽으로 몰린다.
     *
     * @param videoAspect 가로/세로. 16:9 = 1.7778
     */
    fun plan(
        geom: WindowGeometry,
        videoAspect: Float,
        placement: Placement,
    ): SplitPlan {
        require(videoAspect > 0f) { "videoAspect must be positive, was $videoAspect" }

        val idealVideoH = (geom.usableWidth / videoAspect).roundToInt()

        val lowerBound = geom.minPaneHeight
        val upperBound = (geom.allocatableHeight - geom.minPaneHeight).coerceAtLeast(lowerBound)

        val videoH = idealVideoH.coerceIn(lowerBound, upperBound)
        val panelH = geom.allocatableHeight - videoH

        val clampReason = when {
            videoH > idealVideoH -> ClampReason.HIT_MIN_PANE_FLOOR
            videoH < idealVideoH -> ClampReason.HIT_MAX_PANE_CEILING
            else -> null
        }

        // 영상 창이 이상값보다 크면 그 차이만큼 위아래 검은 띠가 남는다.
        val residualLetterbox = (videoH - idealVideoH).coerceAtLeast(0)
        // 영상 창이 이상값보다 작으면 영상은 높이에 맞춰지고 좌우 여백이 생긴다.
        val residualPillarbox = if (videoH < idealVideoH) {
            (geom.usableWidth - (videoH * videoAspect).roundToInt()).coerceAtLeast(0)
        } else 0

        val l = geom.usableLeft
        val r = geom.usableLeft + geom.usableWidth
        val top = geom.usableTop

        return when (placement) {
            Placement.TOP -> SplitPlan(
                videoRect = IntRect(l, top, r, top + videoH),
                panelRect = IntRect(l, top + videoH + geom.dividerThickness, r, top + geom.usableHeight),
                dividerCenterY = top + videoH + geom.dividerThickness / 2,
                videoPaneHeight = videoH,
                topFraction = videoH.toFloat() / geom.usableHeight,
                exact = clampReason == null,
                residualLetterboxPx = residualLetterbox,
                residualPillarboxPx = residualPillarbox,
                clampReason = clampReason,
            )

            Placement.BOTTOM -> SplitPlan(
                videoRect = IntRect(l, top + panelH + geom.dividerThickness, r, top + geom.usableHeight),
                panelRect = IntRect(l, top, r, top + panelH),
                dividerCenterY = top + panelH + geom.dividerThickness / 2,
                videoPaneHeight = videoH,
                topFraction = panelH.toFloat() / geom.usableHeight,
                exact = clampReason == null,
                residualLetterboxPx = residualLetterbox,
                residualPillarboxPx = residualPillarbox,
                clampReason = clampReason,
            )
        }
    }

    /**
     * 현재 디바이더 위치에서 목표까지 이동해야 할 거리(px). 부호 포함.
     * 양수면 아래로, 음수면 위로.
     */
    fun dividerTravel(currentDividerCenterY: Int, plan: SplitPlan): Int =
        plan.dividerCenterY - currentDividerCenterY

    /** 이동이 의미 있는 수준인지. 1~2px 흔들림에 제스처를 낭비하지 않는다. */
    fun needsMove(currentDividerCenterY: Int, plan: SplitPlan, tolerancePx: Int = 4): Boolean =
        abs(dividerTravel(currentDividerCenterY, plan)) > tolerancePx

    /** 창 크기로부터 그 창이 완벽히 채워질 종횡비를 역산 */
    fun impliedAspect(paneWidth: Int, paneHeight: Int): Float {
        require(paneHeight > 0) { "paneHeight must be positive" }
        return paneWidth.toFloat() / paneHeight
    }
}
