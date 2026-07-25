package dev.dj.foldwindow.data

import dev.dj.foldwindow.domain.Placement

/*
 * 순수 Kotlin. android.* import 금지(CLAUDE.md 아키텍처 규칙) — domain 은 아니지만 이 파일은
 * ProfileStore(DataStore 접근 계층)가 쓰는 키/직렬화 상수를 android 의존 없이 테스트하기 위해
 * 의도적으로 분리했다. domain.Placement import 는 허용된다(data -> domain 방향은 합법, 역방향만 금지).
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
}
