package dev.dj.foldwindow.domain

/*
 * 순수 Kotlin. android.* import 및 kotlinx.serialization 등 외부 의존성 금지
 * (CLAUDE.md 아키텍처 규칙). JSON 파싱은 data/WindowProfilesParser.kt 가 담당하고
 * 이 파일은 그 결과로 만들어지는 도메인 모델과 검증 로직만 갖는다.
 *
 * SSOT: config/window_profiles.json (schema "fold-window-profiles/1")
 */

/** 종횡비를 어느 경로로 결정했는가. PRESET 은 AspectResolver 의 출력 전용이며 JSON 에서는 금지된다 */
enum class AspectSource { PROFILE, MEASURED, PRESET }

/** 파트너(비영상) 창을 어떻게 채울지. v1 은 검정 배경(BLACK)만 지원한다. Phase 3 에서 확장 예정 */
enum class PartnerMode { BLACK }

/**
 * JSON `defaults` 블록. 프로파일에 값이 없을 때의 병합 기준점.
 */
data class ProfileDefaults(
    val aspect: Float,
    val placement: Placement,
    val partner: PartnerMode,
    val closedLoopCorrection: Boolean,
    val residualTolerancePx: Int,
    /**
     * DESIGN #12 롤백 레버. true(기본) 면 MEASURED 채택에 [MeasurementConsensus] 합치(pre×confirm)를
     * 요구한다. false 면 기존 동작(pre 단독 + 신뢰도 게이트만)으로 되돌린다. 배선은 후속 작업.
     */
    val requireMeasurementAgreement: Boolean = true,
)

/** 사용자가 수동으로 고를 수 있는 종횡비 프리셋. aspect == null 이면 "자동 감지" 항목이다 */
data class AspectPreset(
    val id: String,
    val aspect: Float?,
    val label: String,
)

/**
 * 앱 하나에 대한 프로파일. [placement] 는 파싱 단계에서 JSON 값이 없으면 defaults.placement 로
 * 병합되므로 이 모델에서는 항상 non-null 이다.
 */
data class AppProfile(
    val packageName: String,
    val label: String,
    val aspect: Float?,
    val aspectSource: AspectSource,
    val placement: Placement,
    val note: String? = null,
)

/** window_profiles.json 전체를 반영한 도메인 모델 */
data class WindowProfilesConfig(
    val schema: String,
    val defaults: ProfileDefaults,
    val presets: List<AspectPreset>,
    val profiles: List<AppProfile>,
)

/** 이 스키마만 지원한다. 새 스키마 버전이 필요하면 파서와 함께 여기도 올릴 것 */
const val SUPPORTED_PROFILES_SCHEMA = "fold-window-profiles/1"

/** 프로파일에서 허용하는 종횡비 범위. 4:3(1.33)보다 좁거나 4:1보다 넓은 값은 오타로 간주한다 */
private const val MIN_ASPECT = 1.0f
private const val MAX_ASPECT = 4.0f
private const val MAX_RESIDUAL_TOLERANCE_PX = 100

/**
 * [WindowProfilesConfig] 의 의미론적 무결성을 검사한다. 문법 파싱은 이미 끝났다고 가정한다.
 *
 * @return 빈 리스트면 유효. 그렇지 않으면 각 메시지는 위치를 특정한다 (예: "profiles[2].aspect: ...").
 */
fun WindowProfilesConfig.validate(): List<String> {
    val errors = mutableListOf<String>()

    if (schema != SUPPORTED_PROFILES_SCHEMA) {
        errors += "schema: 지원하지 않는 값 '$schema' (기대값 '$SUPPORTED_PROFILES_SCHEMA')"
    }

    if (defaults.aspect !in MIN_ASPECT..MAX_ASPECT) {
        errors += "defaults.aspect: 범위 밖 값 ${defaults.aspect} (허용 범위 $MIN_ASPECT..$MAX_ASPECT)"
    }
    if (defaults.residualTolerancePx !in 0..MAX_RESIDUAL_TOLERANCE_PX) {
        errors += "defaults.residualTolerancePx: 범위 밖 값 ${defaults.residualTolerancePx} " +
            "(허용 범위 0..$MAX_RESIDUAL_TOLERANCE_PX)"
    }

    val seenPresetIds = mutableSetOf<String>()
    presets.forEachIndexed { i, preset ->
        if (preset.id.isBlank()) {
            errors += "presets[$i].id: 비어 있음"
        } else if (!seenPresetIds.add(preset.id)) {
            errors += "presets[$i].id: 중복된 id '${preset.id}'"
        }
        if (preset.label.isBlank()) {
            errors += "presets[$i].label: 비어 있음"
        }
        val aspect = preset.aspect
        if (aspect != null && aspect !in MIN_ASPECT..MAX_ASPECT) {
            errors += "presets[$i].aspect: 범위 밖 값 $aspect (허용 범위 $MIN_ASPECT..$MAX_ASPECT)"
        }
    }

    val seenPackages = mutableSetOf<String>()
    profiles.forEachIndexed { i, profile ->
        if (profile.packageName.isBlank()) {
            errors += "profiles[$i].packageName: 비어 있음"
        } else if (!seenPackages.add(profile.packageName)) {
            errors += "profiles[$i].packageName: 중복된 패키지명 '${profile.packageName}'"
        }
        if (profile.label.isBlank()) {
            errors += "profiles[$i].label: 비어 있음"
        }

        when (profile.aspectSource) {
            AspectSource.PROFILE -> {
                val aspect = profile.aspect
                if (aspect == null) {
                    errors += "profiles[$i].aspect: PROFILE 소스는 aspect가 필수"
                } else if (aspect !in MIN_ASPECT..MAX_ASPECT) {
                    errors += "profiles[$i].aspect: 범위 밖 값 $aspect (허용 범위 $MIN_ASPECT..$MAX_ASPECT)"
                }
            }
            AspectSource.MEASURED -> {
                // 시드 JSON 의미론: MEASURED 는 저장된 값이 없다는 뜻이므로 aspect 는 반드시 null 이어야 한다.
                // Phase 3 에서 실측값을 캐싱하게 되면 이 제약을 재검토할 것.
                if (profile.aspect != null) {
                    errors += "profiles[$i].aspect: MEASURED 소스는 aspect가 null이어야 함 (값: ${profile.aspect})"
                }
            }
            AspectSource.PRESET -> {
                errors += "profiles[$i].aspectSource: PRESET 은 리졸버 출력 전용이며 JSON 에서는 금지됨"
            }
        }
    }

    return errors
}
