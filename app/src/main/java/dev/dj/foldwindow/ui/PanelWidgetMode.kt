package dev.dj.foldwindow.ui

/**
 * [P4-2] 파트너 창(PanelActivity)이 표시할 위젯 종류 — 사용자가 파트너 화면에서 고르는 UI 표시
 * 선호일 뿐이다. 도메인 `PartnerMode`(domain/Profiles.kt, JSON `defaults.partner` 스키마 소속)와는
 * 완전히 별개의 enum이므로 혼동 금지 — 이 enum 을 JSON 파싱/AspectResolver 등 도메인 로직에
 * 끌어들이지 않는다.
 */
enum class PanelWidgetMode {
    CLOCK,
    MEMO,
    BLACK,
    ;

    companion object {
        /**
         * 저장된 문자열 -> 위젯 모드. null/공백/알 수 없는 값은 전부 CLOCK 으로 폴백한다(크래시
         * 금지). data/ProfileStoreMapping.panelWidgetModeFromStorage 가 DataStore 값 자체는 이미
         * 허용집합으로 걸러주지만, 이 함수도 임의 문자열에 대해 독립적으로 안전해야 한다(방어적 이중화 —
         * 조용한 실패 금지 원칙상 어느 한쪽 방어가 빠지거나 우회되어도 CLOCK 이라는 안전한 기본값으로
         * 떨어진다).
         */
        fun fromStorage(raw: String?): PanelWidgetMode = when (raw) {
            "CLOCK" -> CLOCK
            "MEMO" -> MEMO
            "BLACK" -> BLACK
            else -> CLOCK
        }
    }
}
