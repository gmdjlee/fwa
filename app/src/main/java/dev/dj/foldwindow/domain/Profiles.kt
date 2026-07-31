package dev.dj.foldwindow.domain

/*
 * 순수 Kotlin. android.* import 및 kotlinx.serialization 등 외부 의존성 금지
 * (CLAUDE.md 아키텍처 규칙). JSON 파싱은 data/WindowProfilesParser.kt 가 담당하고
 * 이 파일은 그 결과로 만들어지는 도메인 모델과 검증 로직만 갖는다.
 *
 * SSOT: config/window_profiles.json (schema "fold-window-profiles/1")
 */

/**
 * 종횡비를 어느 경로로 결정했는가. PRESET·CACHED 는 AspectResolver 의 출력 전용이며 JSON 에서는
 * 금지된다. CACHED = 과거 세션의 합치∧verified 측정값(DataStore 저장) — DESIGN #12 §6.
 */
enum class AspectSource { PROFILE, MEASURED, CACHED, PRESET }

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
    /**
     * DESIGN #12 §6 롤백 레버. true(기본) 면 합치∧verified 로 채택된 측정 종횡비를 앱별로 캐싱해
     * 다음 세션 불합치/미측정 시 PRESET 보다 우선하는 폴백으로 쓴다. false 면 캐시 조회·폴백·저장을
     * 전부 끄고 종전 동작(3단 폴백만)으로 되돌린다.
     */
    val cacheMeasuredAspect: Boolean = true,
    /**
     * P3-5 롤백 레버. true(기본) 면 플렉스(노트북 자세, HALF_OPENED_HORIZONTAL) 감지 시 자동
     * 상단 배치 트리거와 placement 체인의 FLEX 티어(명시 override 다음 우선순위)가 전부 켜진다.
     * false 면 자동 트리거 자체가 발화하지 않고(게이트 최우선 검사) FLEX 티어도 평가되지 않아
     * 종전 동작(수동 트리거 + LAST_SUCCESS/PROFILE/DEFAULTS/FALLBACK 체인)으로 되돌아간다.
     */
    val flexAutoTopPlacement: Boolean = true,
    /**
     * P4-3 롤백 레버. true(기본) 면 폴드를 완전히 닫아 커버 화면으로 전환될 때(FoldingFeature
     * 소멸 → FoldPosture.UNKNOWN) 우리 앱이 만든 분할을 자동으로 해제하는 트리거가 켜진다.
     * false 면 이 자동 해제 트리거 자체가 발화하지 않고(게이트 최우선 검사) 종전 동작(닫아도
     * 분할이 그대로 유지되며 수동 해제만 가능)으로 되돌아간다.
     */
    val coverAutoDismiss: Boolean = true,
    /**
     * DESIGN #30 §2.3 개발자 킬스위치. 기존 4레버(requireMeasurementAgreement/cacheMeasuredAspect/
     * flexAutoTopPlacement/coverAutoDismiss)와 동일하게 **부재=true** 다 — 시드 JSON 에 키를 넣지
     * 않으며, 원격 회수가 불가능한 사이드로드 배포에서 다음 릴리스로 이 기능을 즉시 무력화하기 위한
     * 수단으로만 존재한다(DESIGN #30 R9).
     *
     * 주의: 이 레버가 true 라고 해서 전체화면 자동 배치가 켜지는 것이 아니다. **사용자 옵트인은
     * `data.ProfileStore.isFullscreenAutoEnabled()` 토글(기본값 false)이 담당**하며 게이트 체인에서
     * 이 레버 바로 다음(게이트 2)에 검사된다. 두 값이 모두 참이어야 발화한다.
     */
    val fullscreenAutoArrange: Boolean = true,
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
    /**
     * DESIGN #30 §2.3: 이 앱이 **전체화면 재생 자동 배치의 대상인가**. **부재=false(옵트인)** 다 —
     * "프로파일을 갖고 있다"와 "자동 트리거 대상이다"는 서로 다른 개념이기 때문이다. 프로파일은
     * 종횡비/배치 위치를 아는 앱 전부에 존재하지만, 사용자가 명시적으로 조작하지 않았는데 화면
     * 배치를 바꿔도 되는 앱은 그중 일부뿐이다(넷플릭스는 자사 온보딩이 "재생 중 배치 금지"를
     * 명시하고 실측이 재생 세션 파괴를 재현했다 — DESIGN #30 D11).
     *
     * 시드에서 true 인 것은 `com.google.android.youtube` 하나이며, 게이트 9(not-auto-target)가
     * 이 값을 검사한다. 새 앱을 자동 대상으로 올리려면 실기기 실측 근거를 먼저 남길 것.
     */
    val autoArrange: Boolean = false,
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

/**
 * 프로파일에서 허용하는 종횡비 범위. 4:3(1.33)보다 좁거나 4:1보다 넓은 값은 오타로 간주한다.
 * public 승격 이유(DESIGN #12 §6): data/ProfileStoreMapping 의 캐시값 오염 검증(aspectFromStorage)이
 * 이 범위를 그대로 재사용한다 — 여기서 바꾸면 validate() 뿐 아니라 캐시 채택 게이트도 함께 바뀐다.
 */
const val MIN_ASPECT = 1.0f
const val MAX_ASPECT = 4.0f
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
                // DESIGN #12 §6 실측 캐싱은 DataStore 의 별도 키 공간(measured_aspect.<pkg>)에서
                // 처리하며 이 JSON aspect 필드와는 무관하다 — JSON 의 MEASURED 프로파일은 계속
                // aspect=null 이어야 하고, 캐시값은 aspectSource=CACHED(리졸버 출력 전용)로만 나타난다.
                if (profile.aspect != null) {
                    errors += "profiles[$i].aspect: MEASURED 소스는 aspect가 null이어야 함 (값: ${profile.aspect})"
                }
            }
            AspectSource.CACHED -> {
                errors += "profiles[$i].aspectSource: CACHED 는 리졸버 출력 전용이며 JSON 에서는 금지됨"
            }
            AspectSource.PRESET -> {
                errors += "profiles[$i].aspectSource: PRESET 은 리졸버 출력 전용이며 JSON 에서는 금지됨"
            }
        }
    }

    return errors
}
