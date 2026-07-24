package dev.dj.foldwindow.domain

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙).
 *
 * ADR-1 3단 폴백: ① 프로파일 고정값 → ② 스크린샷 실측 → ③ 프리셋.
 * 어떤 앱이든 "쓸 수 있는 종횡비"가 반드시 하나는 나오도록 보장하는 것이 이 리졸버의 역할이다.
 */

/**
 * 종횡비 결정 결과.
 *
 * @param source      어느 티어에서 결정됐는가
 * @param measurement source == MEASURED 일 때만 non-null (그 외엔 항상 null)
 */
data class ResolvedAspect(
    val aspect: Float,
    val source: AspectSource,
    val measurement: AspectMeasurement?,
)

object AspectResolver {

    /**
     * 실측값을 채택하는 최소 신뢰도. ADAPTIVE 검출 경로의 신뢰도 상한이 0.6 이므로
     * 그보다 낮게 잡아야 ADAPTIVE 결과도 통상적으로 채택될 수 있다.
     * 실기기 미검증 값 — 실사용 데이터로 튜닝 대상.
     */
    const val DEFAULT_MIN_MEASUREMENT_CONFIDENCE = 0.25f

    /**
     * 3단 폴백으로 실제 사용할 종횡비를 결정한다.
     *
     * @param profile                 앱에 등록된 프로파일. null 이면 미등록 앱
     * @param measurement             이번 세션에서 얻은 스크린샷 실측 결과. null 이면 캡처 실패/미시도
     *                                 (DRM 전체 검정 화면으로 검출 자체가 실패한 경우 포함)
     * @param presetAspect            티어 ③ 최종 폴백값. 보통 defaults.aspect 또는 사용자가 고른 프리셋
     * @param minMeasurementConfidence 실측을 채택하기 위한 최소 신뢰도
     */
    fun resolve(
        profile: AppProfile?,
        measurement: AspectMeasurement?,
        presetAspect: Float,
        minMeasurementConfidence: Float = DEFAULT_MIN_MEASUREMENT_CONFIDENCE,
    ): ResolvedAspect {
        require(presetAspect > 0f) { "presetAspect must be positive, was $presetAspect" }

        // 티어 ①: 프로파일에 고정값이 있으면 그것을 최우선으로 쓴다.
        // aspectSource == MEASURED 인 프로파일은 저장된 aspect 가 항상 null 이므로
        // (Profiles.kt validate() 규칙) 이 분기를 자연히 건너뛰고 티어 ②로 넘어간다.
        if (profile != null && profile.aspectSource == AspectSource.PROFILE && profile.aspect != null) {
            return ResolvedAspect(aspect = profile.aspect, source = AspectSource.PROFILE, measurement = null)
        }

        // 티어 ②: 이번 세션의 스크린샷 실측이 충분히 신뢰할 만하면 채택한다.
        if (measurement != null && measurement.confidence >= minMeasurementConfidence) {
            return ResolvedAspect(aspect = measurement.value, source = AspectSource.MEASURED, measurement = measurement)
        }

        // 티어 ③: 위 둘 다 실패하면 프리셋(또는 defaults) 값으로 폴백한다.
        return ResolvedAspect(aspect = presetAspect, source = AspectSource.PRESET, measurement = null)
    }
}
