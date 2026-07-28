package dev.dj.foldwindow.domain

import kotlin.math.roundToInt

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙).
 */

/**
 * P4-1 팝업(freeform) 모드 배치 계산. `SplitPlanner` 와 자매 관계 — 분할 대신 자유 위치의
 * 단일 창(freeform) bounds 를 낸다. `IntRect` 는 SplitPlanner.kt 에 정의된 것을 그대로 쓴다.
 *
 * v1 정책(상단 중앙 고정, DESIGN_P41_FREEFORM.md §4): 폭을 "화면폭 − 좌우 여백"으로 우선
 * 산출하고, 그 높이가 세로 여백(상단·하단)을 침범하면 높이를 그 상한으로 줄이고 폭을 다시
 * 그 높이 기준으로 재계산한다 — SplitPlanner 의 min/max 클램프와 동형 아이디어다.
 */
object PopupPlanner {
    /** 좌우 여백(px). v1 정책값 — DEVICE_FACTS 실측 아님 */
    const val MARGIN_H = 64

    /** 상단 여백(px). One UI 팝업 초기 배치 top y=150 실측 (DEVICE_FACTS 「P4-1 프로브 F1~F6」 부수 실측 — F2 초기 bounds Rect(354,150-1803,2123)) */
    const val TOP_MARGIN = 150

    /** 하단 여백(px). 상단과 대칭으로 잡은 v1 정책값 — 실측 아님 */
    const val BOTTOM_MARGIN = 150

    /**
     * @param aspect 가로/세로 (w/h). 0 이하 불가
     */
    fun plan(screenWidth: Int, screenHeight: Int, aspect: Float): IntRect {
        require(aspect > 0f) { "aspect must be positive, was $aspect" }
        require(screenWidth > 2 * MARGIN_H) { "screenWidth($screenWidth) too small for margins" }
        require(screenHeight > TOP_MARGIN + BOTTOM_MARGIN) { "screenHeight($screenHeight) too small for margins" }

        var w = screenWidth - 2 * MARGIN_H
        var h = (w / aspect).roundToInt()

        val maxH = screenHeight - TOP_MARGIN - BOTTOM_MARGIN
        if (h > maxH) {
            h = maxH
            w = (h * aspect).roundToInt()
        }

        val left = (screenWidth - w) / 2
        val top = TOP_MARGIN
        return IntRect(left, top, left + w, top + h)
    }
}
