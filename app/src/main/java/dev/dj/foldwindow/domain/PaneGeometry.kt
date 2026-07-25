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

    /** 좌우 분할로 인정하려면 각 페인이 최소 이만큼의 화면 높이 비율을 가져야 한다 (팝업/좁은 창 배제) */
    private const val MIN_PANE_HEIGHT_FRACTION_LR = 0.6f

    /** 좌우 분할로 인정하려면 두 페인 가시 폭 합이 화면 폭의 이 비율 이상이어야 한다 */
    private const val MIN_COMBINED_WIDTH_FRACTION_LR = 0.7f

    /**
     * 분할-선택 상태 판정(SplitEntry 드래그·메뉴 레시피 공용)의 가시 비율 허용 범위.
     * 실측(DEVICE_FACTS.md): 상하 분할에서 대상 창 가시 높이가 15~75% 이면 분할-선택 상태.
     * 좌우 분할(메뉴 레시피)에서도 동일 비율 의미로 재사용한다(축만 폭↔높이로 바뀜).
     */
    const val SPLIT_SELECT_MIN_RATIO = 0.15f
    const val SPLIT_SELECT_MAX_RATIO = 0.75f

    /**
     * 분할-선택 판정에서 요구하는 "전체 축 커버리지" 비율(전폭 또는 전고).
     * [실측 근거] 팝업(프리폼) 창이 기존 가시 비율(15~75%) 조건만으로는 오탐 통과함
     * (DEVICE_FACTS.md "step2/3 성공 조건 허점"). 전폭/전고 요구로 팝업을 차단한다.
     */
    private const val FULL_AXIS_FRACTION = 0.9f

    /** 화면 가장자리 도킹 판정 허용 오차(px). [미검증] 실기기 튜닝 대상 */
    const val EDGE_DOCK_TOLERANCE_PX = 40

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

    /**
     * 좌우 분할 레이아웃인가: [isTopBottomSplit] 의 좌우 미러.
     * 겹치지 않는 두 페인이 수평으로 나란히 있고(수평 겹침 없음: `right.left >= left.right`),
     * 각 페인 가시 높이 >= screen.height * 0.6, 두 가시 폭 합 >= screen.width * 0.7.
     */
    fun isLeftRightSplit(panes: List<IntRect>, screen: IntRect): Boolean {
        val candidates = visibleTallCandidates(panes, screen)
        if (candidates.size < 2) return false

        val sorted = candidates.sortedBy { it.left }
        val minCombinedWidth = screen.width * MIN_COMBINED_WIDTH_FRACTION_LR

        for (i in 0 until sorted.size - 1) {
            val left = sorted[i]
            val right = sorted[i + 1]
            if (right.left < left.right) continue // 수평으로 겹치면 좌우 분할이 아님 (상하 분할 등)

            val combinedWidth = left.width + right.width
            if (combinedWidth >= minCombinedWidth) return true
        }
        return false
    }

    /**
     * 드래그 레시피 step2 판정: 대상 앱이 상하 분할-선택 상태로 들어갔는가.
     * 팝업/프리폼 오탐 차단을 위해 세 조건을 모두 요구한다: 전폭(>= screen.width * 0.9),
     * 상단 도킹(가시 top <= screen.top + [EDGE_DOCK_TOLERANCE_PX]), 가시 높이 비율
     * [SPLIT_SELECT_MIN_RATIO]..[SPLIT_SELECT_MAX_RATIO].
     *
     * [실측 근거] 팝업 창이 높이 비율 조건만으로는 오탐 통과 — DEVICE_FACTS.md 참조.
     */
    fun isSplitSelectTopPane(pane: IntRect, screen: IntRect): Boolean {
        val visible = visibleRect(pane, screen) ?: return false
        val fullWidthOk = visible.width >= screen.width * FULL_AXIS_FRACTION
        val topDockedOk = visible.top <= screen.top + EDGE_DOCK_TOLERANCE_PX
        val ratio = visible.height.toFloat() / screen.height.toFloat()
        val ratioOk = ratio in SPLIT_SELECT_MIN_RATIO..SPLIT_SELECT_MAX_RATIO
        return fullWidthOk && topDockedOk && ratioOk
    }

    /**
     * 메뉴 레시피(좌우 분할 선택) step 판정: [isSplitSelectTopPane] 의 좌우 미러.
     * 전고(>= screen.height * 0.9), 좌 또는 우 가장자리 도킹, 가시 폭 비율
     * [SPLIT_SELECT_MIN_RATIO]..[SPLIT_SELECT_MAX_RATIO] 를 모두 요구한다.
     */
    fun isSplitSelectSidePane(pane: IntRect, screen: IntRect): Boolean {
        val visible = visibleRect(pane, screen) ?: return false
        val fullHeightOk = visible.height >= screen.height * FULL_AXIS_FRACTION
        val edgeDockedOk = visible.left <= screen.left + EDGE_DOCK_TOLERANCE_PX ||
            visible.right >= screen.right - EDGE_DOCK_TOLERANCE_PX
        val ratio = visible.width.toFloat() / screen.width.toFloat()
        val ratioOk = ratio in SPLIT_SELECT_MIN_RATIO..SPLIT_SELECT_MAX_RATIO
        return fullHeightOk && edgeDockedOk && ratioOk
    }

    /**
     * 후보 rect 중 "분할 페인"으로 볼 수 있는 것(가시 폭 >= screen.width * [MIN_PANE_WIDTH_FRACTION])만
     * 남기고, 그 중 가시 면적(클램프된 폭×높이)이 최대인 것을 반환한다. 남는 후보가 없으면 null.
     *
     * [측정 2026-07-25] 넷플릭스가 분할 진입 도중 "최소화된 플레이어" 팝업(같은 패키지의 좁은
     * 부유 창)을 함께 띄우는 현상이 실측됐다. 기존에는 targetPackage 의 APPLICATION 창을
     * `firstOrNull` 로 하나만 집었는데, 이 팝업이 먼저 채택되면 위치 판정(TOP/BOTTOM)과
     * letterbox 측정 crop 이 영구적으로 오염된다. 폭이 좁은 팝업은 [visibleWideCandidates] 의
     * 폭 필터에서 배제되고, 남은 후보 중 가시 면적이 가장 넓은 것이 진짜 분할 페인으로 채택된다.
     */
    fun pickPaneLike(candidates: List<IntRect>, screen: IntRect): IntRect? {
        val paneLike = visibleWideCandidates(candidates, screen)
        return paneLike.maxByOrNull { it.width.toLong() * it.height.toLong() }
    }

    /** 화면과의 가시 교집합을 구하고, 폭이 임계치 미만인 것(팝업 등)을 제외한 후보 목록 */
    private fun visibleWideCandidates(panes: List<IntRect>, screen: IntRect): List<IntRect> {
        val minWidth = screen.width * MIN_PANE_WIDTH_FRACTION
        return panes.mapNotNull { visibleRect(it, screen) }
            .filter { it.width >= minWidth }
    }

    /** 화면과의 가시 교집합을 구하고, 높이가 임계치 미만인 것(팝업 등)을 제외한 후보 목록 */
    private fun visibleTallCandidates(panes: List<IntRect>, screen: IntRect): List<IntRect> {
        val minHeight = screen.height * MIN_PANE_HEIGHT_FRACTION_LR
        return panes.mapNotNull { visibleRect(it, screen) }
            .filter { it.height >= minHeight }
    }
}
