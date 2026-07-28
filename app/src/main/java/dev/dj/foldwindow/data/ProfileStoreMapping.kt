package dev.dj.foldwindow.data

import dev.dj.foldwindow.domain.MAX_ASPECT
import dev.dj.foldwindow.domain.MIN_ASPECT
import dev.dj.foldwindow.domain.Placement

/*
 * 순수 Kotlin. android.* import 금지(CLAUDE.md 아키텍처 규칙) — domain 은 아니지만 이 파일은
 * ProfileStore(DataStore 접근 계층)가 쓰는 키/직렬화 상수를 android 의존 없이 테스트하기 위해
 * 의도적으로 분리했다. domain.Placement/MIN_ASPECT/MAX_ASPECT import 는 허용된다(data -> domain
 * 방향은 합법, 역방향만 금지).
 *
 * P3-3: bubble_prefs SharedPreferences -> Preferences DataStore 이관.
 * [ProfileStore] 는 SharedPreferencesMigration(레거시 파일명 "bubble_prefs")으로 1회 자동 이관한다.
 * 이 마이그레이션은 레거시 키 이름을 그대로 Preferences 키 이름으로 복사하므로, 아래
 * KEY_BUBBLE_* 상수 문자열은 곧 마이그레이션 계약이다 — 이름을 바꾸면 기존 사용자의 저장된
 * 버블 위치/활성 상태가 조용히 유실된다. 절대 임의 변경 금지.
 */
object ProfileStoreMapping {

    /** SharedPreferencesMigration 대상 레거시 파일명. FloatingLauncherService 가 쓰던 이름과 동일해야 한다 */
    const val LEGACY_PREFS_NAME = "bubble_prefs"

    /** 마이그레이션 계약: 레거시 SharedPreferences 키 이름을 그대로 재사용한다. 이름 변경 금지 */
    const val KEY_BUBBLE_ENABLED = "bubble_enabled"
    const val KEY_BUBBLE_X = "bubble_x"
    const val KEY_BUBBLE_Y = "bubble_y"

    /** [P4-2] 파트너 창 위젯 모드/메모 저장 키. 신규 키라 마이그레이션 대상은 아니지만, 이미 배포된
     * 빌드가 있을 수 있으므로 향후에도 이름 변경 금지(기존 bubble_* 와 동일한 이유) */
    const val KEY_PANEL_WIDGET_MODE = "panel_widget_mode"
    const val KEY_PANEL_MEMO = "panel_memo"

    /** 앱별 "마지막 성공 placement" 저장 키를 만든다. 패키지명이 비어 있으면 저장 자체가 무의미하므로 거부한다 */
    fun placementKeyFor(packageName: String): String {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        return "last_placement.$packageName"
    }

    /** [Placement] -> 저장용 문자열. enum name 을 그대로 쓴다(TOP/BOTTOM) */
    fun placementToStorage(placement: Placement): String = placement.name

    /**
     * 저장된 문자열 -> [Placement]. null/공백/알 수 없는 값은 전부 null 을 반환한다 — 디스크
     * 오염(예: 과거 스키마 잔재, 수동 편집)이 크래시로 이어지면 안 된다(조용한 실패 금지 원칙과는
     * 별개로, 여기서는 "복원 실패 시 상위 폴백 체인에 위임"이 올바른 처리라 예외를 던지지 않는다).
     */
    fun placementFromStorage(raw: String?): Placement? {
        if (raw.isNullOrBlank()) return null
        return when (raw) {
            "TOP" -> Placement.TOP
            "BOTTOM" -> Placement.BOTTOM
            else -> null
        }
    }

    /**
     * 앱별 캐시된 실측 종횡비 저장 키를 만든다(DESIGN #12 §6). [placementKeyFor] 와 동일하게
     * 패키지명이 비어 있으면 저장 자체가 무의미하므로 거부한다.
     */
    fun measuredAspectKeyFor(packageName: String): String {
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        return "measured_aspect.$packageName"
    }

    /**
     * 저장된 Float -> 유효 종횡비. null/NaN/무한대/범위 밖([MIN_ASPECT]..[MAX_ASPECT], domain
     * Profiles.kt 의 validate() 와 동일 허용 범위)은 전부 null 을 반환한다 — [placementFromStorage]
     * 와 동일한 이유로, 디스크 오염(예: 손상된 값, 수동 편집, 과거 버전의 다른 스키마)이 크래시로
     * 이어지면 안 되고 "복원 실패는 상위 폴백 체인(AspectResolver 티어 ③)에 위임"이 올바른 처리다.
     */
    fun aspectFromStorage(raw: Float?): Float? {
        if (raw == null || raw.isNaN() || raw.isInfinite()) return null
        return if (raw in MIN_ASPECT..MAX_ASPECT) raw else null
    }

    // ── 파트너 창 위젯 모드 / 메모 (P4-2) ──────────────────────────

    private val PANEL_WIDGET_MODES = setOf("CLOCK", "MEMO", "BLACK")

    /**
     * 저장된 문자열 -> 검증된 위젯 모드 문자열. [placementFromStorage] 와 동형 패턴 — null/공백/
     * 허용집합 밖 값은 전부 null 을 반환한다(디스크 오염이 크래시로 이어지면 안 되고, "복원 실패는
     * 상위 폴백에 위임"이 올바른 처리다 — 여기서는 ui.PanelWidgetMode.fromStorage 의 CLOCK 기본값이
     * 그 폴백이다). ui 계층 enum 을 여기서 직접 반환하지 않는 이유: data/ 는 domain/ 만 참조할 수
     * 있고 ui/ 를 참조하면 계층 역전이 된다 — 그래서 검증을 통과한 원본 문자열만 돌려주고, enum
     * 변환 책임은 호출부(ui)에 남겨둔다.
     */
    fun panelWidgetModeFromStorage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return if (raw in PANEL_WIDGET_MODES) raw else null
    }

    /** 파트너 창 메모 저장 상한(문자 수). TextField 무한 성장으로 인한 DataStore 파일 비대화 방지 */
    const val PANEL_MEMO_MAX_CHARS = 4000

    /** 상한을 넘는 메모는 앞부분 [PANEL_MEMO_MAX_CHARS]자만 남기고 절단한다. 상한 이하 값은 그대로 반환한다 */
    fun sanitizePanelMemo(raw: String): String =
        if (raw.length > PANEL_MEMO_MAX_CHARS) raw.substring(0, PANEL_MEMO_MAX_CHARS) else raw
}
