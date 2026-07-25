package dev.dj.foldwindow.domain

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙).
 * platform/DividerLocator 가 위임하는 기하 계산. 실기기 없이 전부 검증 가능해야 한다.
 */

/**
 * 화면-창 기하 계산 유틸리티.
 *
 * DEVICE_FACTS.md 실측 함정 대응: 디바이더가 최소 스냅에 걸리면 앱 창은 리사이즈가 아니라
 * 화면 밖으로 슬라이드된다(`getBoundsInScreen()` 이 음수 left/top 을 반환할 수 있음).
 * 따라서 이 오브젝트의 모든 함수는 "가시 교집합" 을 기준으로 페인을 해석한다.
 */
object PaneGeometry {

    /** 상하 분할로 인정하려면 각 페인이 최소 이만큼의 화면 폭 비율을 가져야 한다 (팝업/좁은 창 배제) */
    private const val MIN_PANE_WIDTH_FRACTION = 0.6f

    /** 상하 분할로 인정하려면 두 페인 가시 높이 합이 화면 높이의 이 비율 이상이어야 한다 */
    private const val MIN_COMBINED_HEIGHT_FRACTION = 0.7f

    /** 기대 간격보다 이만큼 작은 값까지 허용 (아래로 슬랙) */
    private const val GAP_LOWER_SLACK_PX = 6

    /** 기대 간격보다 이만큼 큰 값까지 허용 (위로 슬랙) */
    private const val GAP_UPPER_SLACK_PX = 40

    /** 화면과 창의 가시 교집합. 겹침 없으면 null (오프스크린 슬라이드 함정 대응) */
    fun visibleRect(r: IntRect, screen: IntRect): IntRect? {
        val left = maxOf(r.left, screen.left)
        val top = maxOf(r.top, screen.top)
        val right = minOf(r.right, screen.right)
        val bottom = minOf(r.bottom, screen.bottom)
        if (right <= left || bottom <= top) return null
        return IntRect(left, top, right, bottom)
    }

    /**
     * 가로(상하 분할)에서 두 페인 사이 수평 간격의 중심 Y를 휴리스틱으로 추정.
     * 조건: 위/아래로 쌓인 두 페인, 각 페인 가시 폭 >= screen.width * 0.6,
     * 간격 = lower.top - upper.bottom 이 [expectedGapPx - 6, expectedGapPx + 40] 범위.
     * 미충족 시 null. panes 는 내부에서 visibleRect 로 클램프하고 null 인 것은 제외한다.
     */
    fun findHorizontalGapCenterY(panes: List<IntRect>, screen: IntRect, expectedGapPx: Int = 14): Int? {
        val candidates = visibleWideCandidates(panes, screen)
        if (candidates.size < 2) return null

        val sorted = candidates.sortedBy { it.top }
        val gapLower = expectedGapPx - GAP_LOWER_SLACK_PX
        val gapUpper = expectedGapPx + GAP_UPPER_SLACK_PX

        for (i in 0 until sorted.size - 1) {
            val upper = sorted[i]
            val lower = sorted[i + 1]
            if (lower.top < upper.bottom) continue // 수직으로 겹치면 상하 분할이 아님 (좌우 분할 등)

            val gap = lower.top - upper.bottom
            if (gap in gapLower..gapUpper) {
                return (upper.bottom + lower.top) / 2
            }
        }
        return null
    }

    /**
     * 상하 분할 레이아웃인가: 겹치지 않는 두 페인이 수직으로 쌓여 있고
     * 각각 가시 폭 >= screen.width * 0.6, 두 가시 높이 합 >= screen.height * 0.7
     */
    fun isTopBottomSplit(panes: List<IntRect>, screen: IntRect): Boolean {
        val candidates = visibleWideCandidates(panes, screen)
        if (candidates.size < 2) return false

        val sorted = candidates.sortedBy { it.top }
        val minCombinedHeight = screen.height * MIN_COMBINED_HEIGHT_FRACTION

        for (i in 0 until sorted.size - 1) {
            val upper = sorted[i]
            val lower = sorted[i + 1]
            if (lower.top < upper.bottom) continue // 수직으로 겹치면 상하 분할이 아님

            val combinedHeight = upper.height + lower.height
            if (combinedHeight >= minCombinedHeight) return true
        }
        return false
    }

    /** 화면과의 가시 교집합을 구하고, 폭이 임계치 미만인 것(팝업 등)을 제외한 후보 목록 */
    private fun visibleWideCandidates(panes: List<IntRect>, screen: IntRect): List<IntRect> {
        val minWidth = screen.width * MIN_PANE_WIDTH_FRACTION
        return panes.mapNotNull { visibleRect(it, screen) }
            .filter { it.width >= minWidth }
    }
}
