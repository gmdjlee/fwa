package dev.dj.foldwindow.data

import dev.dj.foldwindow.domain.AspectSource
import dev.dj.foldwindow.domain.Placement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class WindowProfilesParserTest {

    // ── 실제 SSOT 파일 ────────────────────────────────────────────

    @Test
    fun `parses real SSOT file successfully`() {
        val text = readSsotJson()
        val result = WindowProfilesParser.parse(text)

        val success = result as? ProfilesParseResult.Success
            ?: fail("expected Success but was $result").let { return }

        val config = success.config
        assertEquals("fold-window-profiles/1", config.schema)
        assertEquals(1.7778f, config.defaults.aspect, 0.0001f)
        assertEquals(Placement.TOP, config.defaults.placement)
        assertEquals(true, config.defaults.closedLoopCorrection)
        assertEquals(8, config.defaults.residualTolerancePx)
        // SSOT 시드에는 requireMeasurementAgreement 키가 없다 — 부재 시 기본 true 로 동작해야 한다.
        assertEquals(true, config.defaults.requireMeasurementAgreement)
        // SSOT 시드에는 cacheMeasuredAspect 키도 없다 — 부재 시 기본 true 로 동작해야 한다(DESIGN #12 §6).
        assertEquals(true, config.defaults.cacheMeasuredAspect)
        assertEquals(6, config.presets.size)
        assertEquals(5, config.profiles.size)

        val youtube = config.profiles.first { it.packageName == "com.google.android.youtube" }
        assertEquals(AspectSource.MEASURED, youtube.aspectSource)
        assertEquals(null, youtube.aspect)

        val tving = config.profiles.first { it.packageName == "net.cj.cjhv.gs.tving" }
        assertEquals(AspectSource.PROFILE, tving.aspectSource)
        assertEquals(1.7778f, tving.aspect!!, 0.0001f)
    }

    private fun readSsotJson(): String {
        val candidates = listOf(
            File("../config/window_profiles.json"),
            File("config/window_profiles.json"),
        )
        val file = candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException(
                "SSOT window_profiles.json not found. tried: ${candidates.map { it.absolutePath }}",
            )
        return file.readText()
    }

    // ── 스키마/문법 ───────────────────────────────────────────────

    @Test
    fun `wrong schema version fails with message mentioning schema`() {
        val result = WindowProfilesParser.parse(validJson(schema = "fold-window-profiles/999"))
        val failure = assertFailure(result)
        assertTrue(failure.errors.any { it.contains("schema") })
    }

    @Test
    fun `malformed json fails without throwing`() {
        val result = WindowProfilesParser.parse("{ not valid json ][")
        assertFailure(result)
    }

    // ── aspectSource 의미론 ──────────────────────────────────────

    @Test
    fun `PROFILE source with null aspect fails and mentions location`() {
        val profileJson = """
            { "package": "com.example.a", "label": "A", "aspect": null, "aspectSource": "PROFILE", "placement": "TOP" }
        """.trimIndent()
        val result = WindowProfilesParser.parse(validJson(profilesJson = "[$profileJson]"))
        val failure = assertFailure(result)
        assertTrue(failure.errors.any { it.contains("profiles[0]") && it.contains("aspect") })
    }

    @Test
    fun `MEASURED source with non-null aspect fails`() {
        val profileJson = """
            { "package": "com.example.a", "label": "A", "aspect": 1.7778, "aspectSource": "MEASURED", "placement": "TOP" }
        """.trimIndent()
        val result = WindowProfilesParser.parse(validJson(profilesJson = "[$profileJson]"))
        val failure = assertFailure(result)
        assertTrue(failure.errors.any { it.contains("profiles[0]") })
    }

    @Test
    fun `aspectSource PRESET in profile is rejected`() {
        val profileJson = """
            { "package": "com.example.a", "label": "A", "aspect": 1.7778, "aspectSource": "PRESET", "placement": "TOP" }
        """.trimIndent()
        val result = WindowProfilesParser.parse(validJson(profilesJson = "[$profileJson]"))
        val failure = assertFailure(result)
        assertTrue(failure.errors.any { it.contains("profiles[0]") })
    }

    // ── 중복 ─────────────────────────────────────────────────────

    @Test
    fun `duplicate packageName fails`() {
        val a = """
            { "package": "com.example.a", "label": "A", "aspect": 1.7778, "aspectSource": "PROFILE", "placement": "TOP" }
        """.trimIndent()
        val b = """
            { "package": "com.example.a", "label": "B", "aspect": 2.0, "aspectSource": "PROFILE", "placement": "TOP" }
        """.trimIndent()
        val result = WindowProfilesParser.parse(validJson(profilesJson = "[$a,$b]"))
        val failure = assertFailure(result)
        assertTrue(failure.errors.any { it.contains("packageName") })
    }

    @Test
    fun `duplicate preset id fails`() {
        val presets = """
            [
              { "id": "16:9", "aspect": 1.7778, "label": "A" },
              { "id": "16:9", "aspect": 2.0, "label": "B" }
            ]
        """.trimIndent()
        val result = WindowProfilesParser.parse(validJson(presetsJson = presets))
        val failure = assertFailure(result)
        assertTrue(failure.errors.any { it.contains("presets") && it.contains("id") })
    }

    // ── 알 수 없는 enum 값 ────────────────────────────────────────

    @Test
    fun `unknown placement string fails`() {
        val profileJson = """
            { "package": "com.example.a", "label": "A", "aspect": 1.7778, "aspectSource": "PROFILE", "placement": "LEFT" }
        """.trimIndent()
        val result = WindowProfilesParser.parse(validJson(profilesJson = "[$profileJson]"))
        val failure = assertFailure(result)
        assertTrue(failure.errors.any { it.contains("placement") })
    }

    @Test
    fun `unknown partner string fails`() {
        val result = WindowProfilesParser.parse(validJson(defaultsPartner = "WHITE"))
        val failure = assertFailure(result)
        assertTrue(failure.errors.any { it.contains("partner") })
    }

    // ── 범위 ─────────────────────────────────────────────────────

    @Test
    fun `aspect out of range too low fails`() {
        val profileJson = """
            { "package": "com.example.a", "label": "A", "aspect": 0.5, "aspectSource": "PROFILE", "placement": "TOP" }
        """.trimIndent()
        val result = WindowProfilesParser.parse(validJson(profilesJson = "[$profileJson]"))
        assertFailure(result)
    }

    @Test
    fun `aspect out of range too high fails`() {
        val profileJson = """
            { "package": "com.example.a", "label": "A", "aspect": 5.0, "aspectSource": "PROFILE", "placement": "TOP" }
        """.trimIndent()
        val result = WindowProfilesParser.parse(validJson(profilesJson = "[$profileJson]"))
        assertFailure(result)
    }

    @Test
    fun `negative residualTolerancePx fails`() {
        val result = WindowProfilesParser.parse(validJson(defaultsResidualTolerancePx = -1))
        assertFailure(result)
    }

    // ── 병합 ─────────────────────────────────────────────────────

    @Test
    fun `omitted profile placement merges from defaults`() {
        val profileJson = """
            { "package": "com.example.a", "label": "A", "aspect": 1.7778, "aspectSource": "PROFILE" }
        """.trimIndent()
        val result = WindowProfilesParser.parse(validJson(defaultsPlacement = "BOTTOM", profilesJson = "[$profileJson]"))
        val success = result as? ProfilesParseResult.Success
            ?: fail("expected Success but was $result").let { return }
        assertEquals(Placement.BOTTOM, success.config.profiles.single().placement)
    }

    // ── requireMeasurementAgreement 토글 (DESIGN #12 §3.6) ────────

    @Test
    fun `requireMeasurementAgreement defaults to true when the key is omitted`() {
        val result = WindowProfilesParser.parse(validJson())
        val success = result as? ProfilesParseResult.Success
            ?: fail("expected Success but was $result").let { return }
        assertEquals(true, success.config.defaults.requireMeasurementAgreement)
    }

    @Test
    fun `requireMeasurementAgreement explicit false is honored`() {
        val result = WindowProfilesParser.parse(validJson(requireMeasurementAgreement = false))
        val success = result as? ProfilesParseResult.Success
            ?: fail("expected Success but was $result").let { return }
        assertEquals(false, success.config.defaults.requireMeasurementAgreement)
    }

    @Test
    fun `requireMeasurementAgreement explicit true is honored`() {
        val result = WindowProfilesParser.parse(validJson(requireMeasurementAgreement = true))
        val success = result as? ProfilesParseResult.Success
            ?: fail("expected Success but was $result").let { return }
        assertEquals(true, success.config.defaults.requireMeasurementAgreement)
    }

    // ── cacheMeasuredAspect 토글 (DESIGN #12 §6) ──────────────────

    @Test
    fun `cacheMeasuredAspect defaults to true when the key is omitted`() {
        val result = WindowProfilesParser.parse(validJson())
        val success = result as? ProfilesParseResult.Success
            ?: fail("expected Success but was $result").let { return }
        assertEquals(true, success.config.defaults.cacheMeasuredAspect)
    }

    @Test
    fun `cacheMeasuredAspect explicit false is honored`() {
        val result = WindowProfilesParser.parse(validJson(cacheMeasuredAspect = false))
        val success = result as? ProfilesParseResult.Success
            ?: fail("expected Success but was $result").let { return }
        assertEquals(false, success.config.defaults.cacheMeasuredAspect)
    }

    @Test
    fun `cacheMeasuredAspect explicit true is honored`() {
        val result = WindowProfilesParser.parse(validJson(cacheMeasuredAspect = true))
        val success = result as? ProfilesParseResult.Success
            ?: fail("expected Success but was $result").let { return }
        assertEquals(true, success.config.defaults.cacheMeasuredAspect)
    }

    // ── 헬퍼 ─────────────────────────────────────────────────────

    private fun assertFailure(result: ProfilesParseResult): ProfilesParseResult.Failure {
        val failure = result as? ProfilesParseResult.Failure
            ?: fail("expected Failure but was $result").let {
                throw IllegalStateException("unreachable")
            }
        assertTrue("errors must not be empty", failure.errors.isNotEmpty())
        return failure
    }

    /**
     * 유효한 최소 JSON을 만들고 필요한 부분만 오버라이드한다.
     * @param requireMeasurementAgreement null 이면 키 자체를 생략한다(부재 시 기본값 검증용).
     * @param cacheMeasuredAspect null 이면 키 자체를 생략한다(부재 시 기본값 검증용, DESIGN #12 §6).
     */
    private fun validJson(
        schema: String = "fold-window-profiles/1",
        defaultsPlacement: String = "TOP",
        defaultsPartner: String = "BLACK",
        defaultsResidualTolerancePx: Int = 8,
        requireMeasurementAgreement: Boolean? = null,
        cacheMeasuredAspect: Boolean? = null,
        presetsJson: String = """[ { "id": "16:9", "aspect": 1.7778, "label": "16:9" } ]""",
        profilesJson: String = "[]",
    ): String {
        val requireMeasurementAgreementField = requireMeasurementAgreement
            ?.let { ""","requireMeasurementAgreement": $it""" }
            ?: ""
        val cacheMeasuredAspectField = cacheMeasuredAspect
            ?.let { ""","cacheMeasuredAspect": $it""" }
            ?: ""
        return """
        {
          "schema": "$schema",
          "defaults": {
            "aspect": 1.7778,
            "placement": "$defaultsPlacement",
            "partner": "$defaultsPartner",
            "closedLoopCorrection": true,
            "residualTolerancePx": $defaultsResidualTolerancePx$requireMeasurementAgreementField$cacheMeasuredAspectField
          },
          "presets": $presetsJson,
          "profiles": $profilesJson
        }
        """.trimIndent()
    }
}
