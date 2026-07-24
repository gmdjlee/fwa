package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AspectResolverTest {

    private fun profile(
        aspect: Float?,
        aspectSource: AspectSource,
        packageName: String = "com.example.app",
    ) = AppProfile(
        packageName = packageName,
        label = "Example",
        aspect = aspect,
        aspectSource = aspectSource,
        placement = Placement.TOP,
    )

    private fun measurement(confidence: Float, value: Float = 2.0f) = AspectMeasurement(
        raw = value,
        snapped = value,
        value = value,
        band = ContentBand(
            top = 100,
            bottom = 900,
            topBarPx = 100,
            bottomBarPx = 100,
            confidence = confidence,
            method = DetectionMethod.PURE_BLACK,
        ),
    )

    // ── 티어 ① PROFILE ───────────────────────────────────────────

    @Test
    fun `PROFILE profile adopts tier 1`() {
        val p = profile(aspect = 1.7778f, aspectSource = AspectSource.PROFILE)
        val result = AspectResolver.resolve(profile = p, measurement = null, presetAspect = 2.0f)

        assertEquals(AspectSource.PROFILE, result.source)
        assertEquals(1.7778f, result.aspect, 0.0001f)
        assertNull(result.measurement)
    }

    @Test
    fun `PROFILE profile wins over high-confidence measurement`() {
        val p = profile(aspect = 1.7778f, aspectSource = AspectSource.PROFILE)
        val m = measurement(confidence = 0.99f, value = 2.35f)
        val result = AspectResolver.resolve(profile = p, measurement = m, presetAspect = 2.0f)

        assertEquals(AspectSource.PROFILE, result.source)
        assertEquals(1.7778f, result.aspect, 0.0001f)
        assertNull(result.measurement)
    }

    // ── 티어 ② MEASURED ──────────────────────────────────────────

    @Test
    fun `MEASURED profile with high-confidence measurement adopts tier 2`() {
        val p = profile(aspect = null, aspectSource = AspectSource.MEASURED)
        val m = measurement(confidence = 0.8f, value = 2.35f)
        val result = AspectResolver.resolve(profile = p, measurement = m, presetAspect = 1.7778f)

        assertEquals(AspectSource.MEASURED, result.source)
        assertEquals(2.35f, result.aspect, 0.0001f)
        assertSame(m, result.measurement)
    }

    @Test
    fun `MEASURED profile with null measurement falls back to tier 3`() {
        val p = profile(aspect = null, aspectSource = AspectSource.MEASURED)
        val result = AspectResolver.resolve(profile = p, measurement = null, presetAspect = 1.7778f)

        assertEquals(AspectSource.PRESET, result.source)
        assertEquals(1.7778f, result.aspect, 0.0001f)
        assertNull(result.measurement)
    }

    @Test
    fun `MEASURED profile with low-confidence measurement falls back to tier 3`() {
        val p = profile(aspect = null, aspectSource = AspectSource.MEASURED)
        val m = measurement(confidence = 0.1f, value = 2.35f)
        val result = AspectResolver.resolve(
            profile = p,
            measurement = m,
            presetAspect = 1.7778f,
            minMeasurementConfidence = 0.25f,
        )

        assertEquals(AspectSource.PRESET, result.source)
        assertEquals(1.7778f, result.aspect, 0.0001f)
        assertNull(result.measurement)
    }

    @Test
    fun `confidence exactly at threshold is adopted`() {
        val p = profile(aspect = null, aspectSource = AspectSource.MEASURED)
        val m = measurement(confidence = 0.25f, value = 2.35f)
        val result = AspectResolver.resolve(
            profile = p,
            measurement = m,
            presetAspect = 1.7778f,
            minMeasurementConfidence = 0.25f,
        )

        assertEquals(AspectSource.MEASURED, result.source)
        assertEquals(2.35f, result.aspect, 0.0001f)
    }

    // ── 미등록 앱 (profile == null) ────────────────────────────────

    @Test
    fun `unregistered app with measurement adopts tier 2`() {
        val m = measurement(confidence = 0.9f, value = 2.35f)
        val result = AspectResolver.resolve(profile = null, measurement = m, presetAspect = 1.7778f)

        assertEquals(AspectSource.MEASURED, result.source)
        assertEquals(2.35f, result.aspect, 0.0001f)
    }

    @Test
    fun `unregistered app with no measurement falls back to tier 3`() {
        val result = AspectResolver.resolve(profile = null, measurement = null, presetAspect = 1.7778f)

        assertEquals(AspectSource.PRESET, result.source)
        assertEquals(1.7778f, result.aspect, 0.0001f)
        assertNull(result.measurement)
    }

    // ── 방어 ─────────────────────────────────────────────────────

    @Test
    fun `non-positive presetAspect throws`() {
        assertThrows(IllegalArgumentException::class.java) {
            AspectResolver.resolve(profile = null, measurement = null, presetAspect = 0f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AspectResolver.resolve(profile = null, measurement = null, presetAspect = -1.5f)
        }
    }
}
