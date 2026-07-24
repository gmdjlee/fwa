package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterboxDetectorTest {

    /**
     * 테스트용 스캔 생성.
     * @param topBar/bottomBar 완전히 검은 행 개수
     * @param contentDark      콘텐츠 영역 행의 어두움 비율 (밝은 장면 0.1, 어두운 장면 0.8 등)
     */
    private fun scan(
        height: Int,
        topBar: Int,
        bottomBar: Int,
        width: Int = 2184,
        contentDark: Float = 0.30f,
        barDark: Float = 1.0f,
    ): LetterboxScan {
        val rows = FloatArray(height) { i ->
            when {
                i < topBar -> barDark
                i >= height - bottomBar -> barDark
                else -> contentDark
            }
        }
        return LetterboxScan(rows, width)
    }

    // ── 정상 감지 ──────────────────────────────────────────────

    @Test
    fun `detects symmetric letterbox bars`() {
        val band = LetterboxDetector.detect(scan(height = 1968, topBar = 370, bottomBar = 370))

        assertNotNull(band)
        assertEquals(370, band!!.topBarPx)
        assertEquals(370, band.bottomBarPx)
        assertEquals(1228, band.height)
    }

    @Test
    fun `detects asymmetric bars`() {
        val band = LetterboxDetector.detect(scan(height = 1968, topBar = 100, bottomBar = 640))!!

        assertEquals(100, band.topBarPx)
        assertEquals(640, band.bottomBarPx)
        assertEquals(1228, band.height)
    }

    @Test
    fun `detects video already pushed to the top`() {
        val band = LetterboxDetector.detect(scan(height = 1968, topBar = 0, bottomBar = 740))!!

        assertEquals(0, band.topBarPx)
        assertEquals(740, band.bottomBarPx)
        assertEquals(0, band.top)
    }

    // ── 역산과 스냅 ────────────────────────────────────────────

    @Test
    fun `implied aspect of a 1228px band on a 2184px frame is 16 to 9`() {
        val band = LetterboxDetector.detect(scan(height = 1968, topBar = 370, bottomBar = 370))!!
        val aspect = LetterboxDetector.impliedAspect(band, 2184)

        assertEquals(16f / 9f, aspect, 0.01f)
    }

    @Test
    fun `resolveAspect snaps a noisy measurement to the 16 to 9 preset`() {
        // 1220px 밴드 → 1.790, 16:9(1.7778)에서 0.7% 오차 → 스냅되어야 한다
        val m = LetterboxDetector.resolveAspect(scan(height = 1968, topBar = 374, bottomBar = 374))!!

        assertTrue(m.isSnapped)
        assertEquals(16f / 9f, m.value, 0.0001f)
        assertTrue(m.raw != m.value)
    }

    @Test
    fun `resolveAspect keeps the raw value when nothing is close enough`() {
        // 1292px 밴드 → 1.690. 16:10(1.600)과 16:9(1.778) 사이의 빈 구간이라 스냅되지 않는다
        val m = LetterboxDetector.resolveAspect(scan(height = 1968, topBar = 338, bottomBar = 338))!!

        assertNull(m.snapped)
        assertEquals(m.raw, m.value, 0.0001f)
    }

    @Test
    fun `snapToKnownAspect returns null outside tolerance`() {
        assertNull(LetterboxDetector.snapToKnownAspect(1.45f))
        assertEquals(16f / 9f, LetterboxDetector.snapToKnownAspect(1.79f)!!, 0.0001f)
        assertEquals(2f, LetterboxDetector.snapToKnownAspect(2.02f)!!, 0.0001f)
    }

    // ── 거부 케이스 ────────────────────────────────────────────

    @Test
    fun `returns null when there is no letterbox`() {
        assertNull(LetterboxDetector.detect(scan(height = 1968, topBar = 0, bottomBar = 0)))
    }

    @Test
    fun `returns null when the whole frame is dark`() {
        val rows = FloatArray(1968) { 1.0f }
        assertNull(LetterboxDetector.detect(LetterboxScan(rows, 2184)))
    }

    @Test
    fun `returns null when the content band is implausibly small`() {
        // 콘텐츠가 전체의 10% → 오탐으로 간주
        assertNull(LetterboxDetector.detect(scan(height = 1968, topBar = 900, bottomBar = 872)))
    }

    // ── 잡음 내성 ──────────────────────────────────────────────

    @Test
    fun `a dark scene inside the content band is not mistaken for a bar`() {
        // 콘텐츠가 어둡지만(0.90) 임계(0.97) 미만이면 띠가 아니다
        val band = LetterboxDetector.detect(
            scan(height = 1968, topBar = 370, bottomBar = 370, contentDark = 0.90f)
        )!!
        assertEquals(1228, band.height)
    }

    @Test
    fun `a nearly black bar with a few stray pixels is still a bar`() {
        val band = LetterboxDetector.detect(
            scan(height = 1968, topBar = 370, bottomBar = 370, barDark = 0.98f)
        )!!
        assertEquals(370, band.topBarPx)
    }

    @Test
    fun `confidence is higher for clean bars than for murky ones`() {
        val clean = LetterboxDetector.detect(
            scan(height = 1968, topBar = 370, bottomBar = 370, barDark = 1.0f, contentDark = 0.05f)
        )!!
        val murky = LetterboxDetector.detect(
            scan(height = 1968, topBar = 370, bottomBar = 370, barDark = 0.975f, contentDark = 0.95f)
        )!!

        assertTrue("clean=${clean.confidence} murky=${murky.confidence}", clean.confidence > murky.confidence)
    }

    // ── 입력 검증 ──────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `empty scan is rejected`() {
        LetterboxScan(FloatArray(0), 2184)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero width is rejected`() {
        LetterboxScan(FloatArray(10) { 0f }, 0)
    }
}
