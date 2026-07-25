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

    // ── 하이브리드 검출 (v2: 순흑 실패 시 적응형 폴백) ──────────────

    /**
     * 휘도 통계가 채워진 스캔 생성. darkRatio 는 barLuma <= 24 일 때만 1.0 으로 합성해
     * "순흑 띠" 와 "글로우 띠" 를 구분해 시뮬레이션한다.
     */
    private fun statsScan(
        height: Int,
        topBar: Int,
        bottomBar: Int,
        barLuma: Float,
        barVariance: Float,
        width: Int = 2184,
        contentLuma: Float = 128f,
        contentVariance: Float = 3000f,
    ): LetterboxScan {
        val darkRatio = FloatArray(height) { i ->
            val isBar = i < topBar || i >= height - bottomBar
            if (isBar && barLuma <= 24f) 1.0f else 0f
        }
        val meanLuma = FloatArray(height) { i ->
            if (i < topBar || i >= height - bottomBar) barLuma else contentLuma
        }
        val variance = FloatArray(height) { i ->
            if (i < topBar || i >= height - bottomBar) barVariance else contentVariance
        }
        return LetterboxScan(darkRatio, width, meanLuma, variance)
    }

    @Test
    fun `hybrid detection prefers pure black path when bars are truly black`() {
        val s = statsScan(height = 1968, topBar = 370, bottomBar = 370, barLuma = 5f, barVariance = 5f)

        val hybrid = LetterboxDetector.detectHybrid(s)!!
        val pure = LetterboxDetector.detect(s)!!

        assertEquals(DetectionMethod.PURE_BLACK, hybrid.method)
        assertEquals(pure.top, hybrid.top)
        assertEquals(pure.bottom, hybrid.bottom)
        assertEquals(pure.confidence, hybrid.confidence, 0.0001f)
    }

    @Test
    fun `ambient glow bars fail pure black and fall back to adaptive detection`() {
        // 앰비언트 모드 실측: 상단 darkRatio 0.000, 하단 최대 0.66 → 순흑 임계 0.97 도달 불가
        val s = statsScan(height = 1968, topBar = 370, bottomBar = 370, barLuma = 40f, barVariance = 100f)

        assertNull("순흑 경로는 실패해야 한다", LetterboxDetector.detect(s))

        val band = LetterboxDetector.detectHybrid(s)
        assertNotNull(band)
        assertEquals(DetectionMethod.ADAPTIVE, band!!.method)
        assertEquals(370, band.topBarPx)
        assertEquals(370, band.bottomBarPx)
        assertEquals(1228, band.height)
    }

    @Test
    fun `resolveAspect snaps ambient glow measurement to 16 to 9 via adaptive fallback`() {
        val s = statsScan(height = 1968, topBar = 370, bottomBar = 370, barLuma = 40f, barVariance = 100f)

        val m = LetterboxDetector.resolveAspect(s)

        assertNotNull(m)
        assertEquals(DetectionMethod.ADAPTIVE, m!!.method)
        assertTrue(m.isSnapped)
        assertEquals(16f / 9f, m.value, 0.01f)
    }

    @Test
    fun `a uniformly dark low-detail frame is rejected instead of stripping all content`() {
        // 전 화면이 어둡고 저디테일한 장면(예: 페이드아웃 순간) 이 통째로 "띠" 로 오검출되면 안 된다
        val s = statsScan(
            height = 1968, topBar = 0, bottomBar = 0,
            barLuma = 40f, barVariance = 100f,
            contentLuma = 40f, contentVariance = 100f,
        )

        assertNull(LetterboxDetector.detectHybrid(s))
    }

    @Test
    fun `a small content band below the minimum fraction is rejected`() {
        // 위아래 모두 존재하지만 남는 콘텐츠가 전체의 25% 미만이면 오탐으로 거부한다
        val s = statsScan(height = 1968, topBar = 900, bottomBar = 900, barLuma = 40f, barVariance = 100f)

        assertNull(LetterboxDetector.detectHybrid(s))
    }

    @Test
    fun `bars on only one side are rejected even when luma-like`() {
        // 위쪽만 어두운 저디테일 UI 요소 (상단 상태바 등) 는 레터박스가 아니다
        val s = statsScan(height = 1968, topBar = 370, bottomBar = 0, barLuma = 40f, barVariance = 100f)

        assertNull(LetterboxDetector.detectHybrid(s))
    }

    @Test
    fun `gradient background stops at ref delta before digging into content`() {
        val height = 200
        val topGradientRows = 20
        val bottomBar = 40
        val meanLuma = FloatArray(height)
        val variance = FloatArray(height)
        val darkRatio = FloatArray(height) { 0f }

        for (i in 0 until height) {
            meanLuma[i] = when {
                i < topGradientRows -> 40f + 5f * i          // 40, 45, ..., 135 로 서서히 밝아짐
                i >= height - bottomBar -> 40f                 // 아래는 평범한 저채도 띠
                else -> 130f                                    // 콘텐츠
            }
            variance[i] = when {
                i < topGradientRows -> 50f
                i >= height - bottomBar -> 50f
                else -> 3000f
            }
        }

        val s = LetterboxScan(darkRatio, 2184, meanLuma, variance)
        val band = LetterboxDetector.detectHybrid(s)

        assertNotNull(band)
        assertEquals(DetectionMethod.ADAPTIVE, band!!.method)
        // refTop = avg(40,45,50) = 45. REF_DELTA=28 → luma<=73 까지만 허용 → i=0..6 통과, i=7(luma=75) 에서 정지
        assertEquals(7, band.top)
        // 진짜 그라디언트 경계(20)보다 훨씬 안쪽에서 멈춰야 한다 = 콘텐츠를 잠식하지 않는다
        assertTrue(band.top < topGradientRows)
    }

    @Test
    fun `detectHybrid does not fall back without luma stats even when bars are not pure black`() {
        // 기존 형태(휘도 통계 없음) 의 스캔은 폴백을 시도하지 않고 조용히 null 을 반환한다 (크래시 없음)
        val band = LetterboxDetector.detectHybrid(
            scan(height = 1968, topBar = 370, bottomBar = 370, barDark = 0.5f)
        )
        assertNull(band)
    }

    @Test
    fun `bright low-detail background is rejected by the max bar luma guard`() {
        val s = statsScan(height = 1968, topBar = 370, bottomBar = 370, barLuma = 200f, barVariance = 10f)

        assertNull(LetterboxDetector.detectHybrid(s))
    }

    @Test
    fun `adaptive confidence is lower than pure black confidence for equivalent geometry`() {
        val pureBlack = LetterboxDetector.detectHybrid(
            statsScan(height = 1968, topBar = 370, bottomBar = 370, barLuma = 5f, barVariance = 5f)
        )!!
        val ambient = LetterboxDetector.detectHybrid(
            statsScan(height = 1968, topBar = 370, bottomBar = 370, barLuma = 40f, barVariance = 100f)
        )!!

        assertEquals(DetectionMethod.PURE_BLACK, pureBlack.method)
        assertEquals(DetectionMethod.ADAPTIVE, ambient.method)
        assertTrue(
            "adaptive=${ambient.confidence} pureBlack=${pureBlack.confidence}",
            ambient.confidence < pureBlack.confidence
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched luma stats array size is rejected`() {
        LetterboxScan(FloatArray(10) { 0f }, 2184, FloatArray(5), FloatArray(10))
    }

    // ── residualBars (검증 전용: "띠 없음(성공)" 과 "판정 불가" 를 구분) ────────

    @Test
    fun `residualBars returns zero for a full-bleed scan (perfect placement)`() {
        val bars = LetterboxDetector.residualBars(scan(height = 1968, topBar = 0, bottomBar = 0))

        assertNotNull(bars)
        assertEquals(0, bars!!.topPx)
        assertEquals(0, bars.bottomPx)
        assertEquals(0, bars.totalPx)
    }

    @Test
    fun `residualBars measures small symmetric residual bars`() {
        val bars = LetterboxDetector.residualBars(scan(height = 200, topBar = 4, bottomBar = 4))

        assertNotNull(bars)
        assertEquals(4, bars!!.topPx)
        assertEquals(4, bars.bottomPx)
        assertEquals(8, bars.totalPx)
    }

    @Test
    fun `residualBars returns null when the whole frame is black`() {
        val rows = FloatArray(1968) { 1.0f }
        assertNull(LetterboxDetector.residualBars(LetterboxScan(rows, 2184)))
    }

    @Test
    fun `residualBars returns null when content fraction is below the minimum`() {
        // 콘텐츠가 전체의 10% 미만 → detect() 와 동일하게 판정 불가로 거부한다
        assertNull(LetterboxDetector.residualBars(scan(height = 1968, topBar = 900, bottomBar = 872)))
    }

    @Test
    fun `residualBars measures one-sided bars`() {
        val bars = LetterboxDetector.residualBars(scan(height = 1968, topBar = 370, bottomBar = 0))

        assertNotNull(bars)
        assertEquals(370, bars!!.topPx)
        assertEquals(0, bars.bottomPx)
        assertEquals(370, bars.totalPx)
    }

    @Test
    fun `residualBars does not reject near-full content unlike detect (the semantics gap this fixes)`() {
        // detect() 는 NO_LETTERBOX_FRACTION(0.99) 이상이면 null 을 반환한다 — 이것이 실기기에서
        // 확인된 버그: 완벽에 가까운 배치(잔여 4px)를 "측정 불가"로 오판해 Done(verified=false) 를
        // 보고했다. residualBars() 는 상한 거부가 없어 이 값을 정확히 측정한다.
        val target = scan(height = 1968, topBar = 2, bottomBar = 2)

        val bars = LetterboxDetector.residualBars(target)
        val detected = LetterboxDetector.detect(target)

        assertNotNull(bars)
        assertEquals(4, bars!!.totalPx)
        assertNull("detect() 는 상한 거부로 null 이어야 한다(대조군, 이 격차가 버그의 근원)", detected)
    }

    // ── resolveAspectPillarbox (열축 필러박스 역산, DESIGN #12 §3.4) ────────────
    // scan() 의 height 인자 = entries(열, 좌→우) 개수, width 인자 = 페인 높이(같은 stride 단위).

    @Test
    fun `resolveAspectPillarbox derives 16 to 9 from a strided pane crop (worked example)`() {
        // 페인 2184x977, colStride=2 → entries 1092, width(페인 높이 stride 환산) 488.
        // 16:9 콘텐츠 폭 1725px → 863 entries → 863/488 ≈ 1.768 → snap 1.7778 (DESIGN #12 §3.4 검산 예).
        val m = LetterboxDetector.resolveAspectPillarbox(
            scan(height = 1092, topBar = 114, bottomBar = 115, width = 488)
        )

        assertNotNull(m)
        assertEquals(863, m!!.band.height)
        assertTrue(m.isSnapped)
        assertEquals(16f / 9f, m.value, 0.0001f)
        assertTrue(m.raw != m.value)
    }

    @Test
    fun `resolveAspectPillarbox returns null when there is no pillarbox`() {
        assertNull(
            LetterboxDetector.resolveAspectPillarbox(scan(height = 1092, topBar = 0, bottomBar = 0, width = 488))
        )
    }

    @Test
    fun `resolveAspectPillarbox returns null when content fraction is below minimum`() {
        // 콘텐츠가 entries 의 10%대 → MIN_CONTENT_FRACTION(0.25) 미달로 오탐 거부
        assertNull(
            LetterboxDetector.resolveAspectPillarbox(scan(height = 1092, topBar = 490, bottomBar = 490, width = 488))
        )
    }

    @Test
    fun `resolveAspectPillarbox falls back to adaptive detection for ambient-glow pillarbox bars`() {
        val s = statsScan(height = 1092, topBar = 114, bottomBar = 115, barLuma = 40f, barVariance = 100f, width = 488)

        val m = LetterboxDetector.resolveAspectPillarbox(s)

        assertNotNull(m)
        assertEquals(DetectionMethod.ADAPTIVE, m!!.method)
        assertTrue("conf=${m.confidence} must stay <= 0.6 (adaptive ceiling)", m.confidence <= 0.6f)
    }

    @Test
    fun `resolveAspectPillarbox keeps the raw value when nothing is close enough`() {
        // 690 entries / 408 ≈ 1.691 — 16:10(1.600)과 16:9(1.778) 사이의 빈 구간이라 스냅되지 않는다
        val m = LetterboxDetector.resolveAspectPillarbox(
            scan(height = 1000, topBar = 155, bottomBar = 155, width = 408)
        )

        assertNotNull(m)
        assertNull(m!!.snapped)
        assertEquals(m.raw, m.value, 0.0001f)
        assertEquals(690f / 408f, m.raw, 0.0001f)
    }
}
