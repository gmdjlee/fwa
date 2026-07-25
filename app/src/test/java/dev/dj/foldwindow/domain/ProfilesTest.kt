package dev.dj.foldwindow.domain

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WindowProfilesConfig.validate] 규칙 중 JSON 파서를 거치지 않고는 만들 수 없는 케이스를 도메인
 * 모델을 직접 구성해 검증한다 — aspectSource=CACHED 는 리졸버 출력 전용이라
 * [dev.dj.foldwindow.data.WindowProfilesParser] 가 애초에 이 값을 만들지 않는다(JSON 문자열
 * "CACHED" 는 알 수 없는 값으로 거부될 뿐, 이 도메인 규칙과는 다른 오류 경로다). 스키마/문법 오류나
 * 알 수 없는 enum 문자열 등 파서 경유 검증은 [dev.dj.foldwindow.data.WindowProfilesParserTest] 가 담당한다.
 */
class ProfilesTest {

    private fun minimalConfig(profile: AppProfile) = WindowProfilesConfig(
        schema = SUPPORTED_PROFILES_SCHEMA,
        defaults = ProfileDefaults(
            aspect = 1.7778f,
            placement = Placement.TOP,
            partner = PartnerMode.BLACK,
            closedLoopCorrection = true,
            residualTolerancePx = 8,
        ),
        presets = emptyList(),
        profiles = listOf(profile),
    )

    @Test
    fun `CACHED aspectSource in a profile is rejected with a location-specific error`() {
        val profile = AppProfile(
            packageName = "com.example.app",
            label = "Example",
            aspect = 1.7778f,
            aspectSource = AspectSource.CACHED,
            placement = Placement.TOP,
        )
        val errors = minimalConfig(profile).validate()

        assertTrue(
            "expected an error mentioning profiles[0].aspectSource and CACHED, got: $errors",
            errors.any { it.contains("profiles[0].aspectSource") && it.contains("CACHED") },
        )
    }
}
