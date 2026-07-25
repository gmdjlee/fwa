package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DESIGN #12 §3.3 합치 표의 코드화 검증. 픽스처는 [AspectResolverTest] 와 동일하게 실제
 * [ContentBand]/[AspectMeasurement] 를 직접 조립한다(순수 데이터 클래스라 mock 불필요).
 */
class MeasurementConsensusTest {

    private fun measurement(
        raw: Float,
        snapped: Float?,
        confidence: Float,
        value: Float = snapped ?: raw,
    ) = AspectMeasurement(
        raw = raw,
        snapped = snapped,
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

    // ── classifyAxis ─────────────────────────────────────────────

    @Test
    fun `classifyAxis returns BARS_MEASURED when a measurement is present`() {
        val m = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.8f)
        assertEquals(AxisReading.BARS_MEASURED, MeasurementConsensus.classifyAxis(m, null))
    }

    @Test
    fun `classifyAxis returns NO_BARS when residual is exactly zero`() {
        assertEquals(AxisReading.NO_BARS, MeasurementConsensus.classifyAxis(null, ResidualBars(0, 0)))
    }

    @Test
    fun `classifyAxis returns UNJUDGEABLE when residual is unavailable`() {
        assertEquals(AxisReading.UNJUDGEABLE, MeasurementConsensus.classifyAxis(null, null))
    }

    @Test
    fun `classifyAxis treats nonzero residual as UNJUDGEABLE not NO_BARS (strict zero boundary)`() {
        // 잔여 >0 인데 밴드가 거부된 상태는 오염 의심 — NO_BARS 로 낙관하지 않는다 (엄격히 0만 인정)
        assertEquals(AxisReading.UNJUDGEABLE, MeasurementConsensus.classifyAxis(null, ResidualBars(2, 0)))
    }

    // ── classifyConfirm ──────────────────────────────────────────

    @Test
    fun `classifyConfirm adopts the row measurement when only the row axis has bars`() {
        val rowM = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.8f)
        val outcome = MeasurementConsensus.classifyConfirm(
            rowMeasurement = rowM, rowResidual = null,
            colMeasurement = null, colResidual = ResidualBars(0, 0),
        )
        assertEquals(ConfirmOutcome.Measured(rowM), outcome)
    }

    @Test
    fun `classifyConfirm adopts the column measurement when only the column axis has bars`() {
        val colM = measurement(raw = 2.35f, snapped = 2.35f, confidence = 0.8f)
        val outcome = MeasurementConsensus.classifyConfirm(
            rowMeasurement = null, rowResidual = ResidualBars(0, 0),
            colMeasurement = colM, colResidual = null,
        )
        assertEquals(ConfirmOutcome.Measured(colM), outcome)
    }

    @Test
    fun `classifyConfirm reports BothAxesBars when both axes measure bars`() {
        val rowM = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.8f)
        val colM = measurement(raw = 2.35f, snapped = 2.35f, confidence = 0.8f)
        val outcome = MeasurementConsensus.classifyConfirm(
            rowMeasurement = rowM, rowResidual = null,
            colMeasurement = colM, colResidual = null,
        )
        assertEquals(ConfirmOutcome.BothAxesBars, outcome)
    }

    @Test
    fun `classifyConfirm reports NoBars when both axes measure zero residual`() {
        val outcome = MeasurementConsensus.classifyConfirm(
            rowMeasurement = null, rowResidual = ResidualBars(0, 0),
            colMeasurement = null, colResidual = ResidualBars(0, 0),
        )
        assertEquals(ConfirmOutcome.NoBars, outcome)
    }

    @Test
    fun `classifyConfirm reports Unavailable when one axis is BARS_MEASURED and the other is UNJUDGEABLE`() {
        val rowM = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.8f)
        val outcome = MeasurementConsensus.classifyConfirm(
            rowMeasurement = rowM, rowResidual = null,
            colMeasurement = null, colResidual = null, // 측정도 잔여도 없음 = UNJUDGEABLE
        )
        assertEquals(ConfirmOutcome.Unavailable, outcome)
    }

    @Test
    fun `classifyConfirm reports Unavailable when both axes are UNJUDGEABLE`() {
        val outcome = MeasurementConsensus.classifyConfirm(
            rowMeasurement = null, rowResidual = null,
            colMeasurement = null, colResidual = null,
        )
        assertEquals(ConfirmOutcome.Unavailable, outcome)
    }

    // ── agree — §3.3 합치 표 전 행 ───────────────────────────────

    @Test
    fun `agree returns SNAP_AGREE when snapped values match despite differing raw`() {
        val pre = measurement(raw = 1.75f, snapped = 16f / 9f, confidence = 0.8f)
        val confirmM = measurement(raw = 1.80f, snapped = 16f / 9f, confidence = 0.8f)

        val result = MeasurementConsensus.agree(pre, ConfirmOutcome.Measured(confirmM), paneAspect = 2.2f)

        assertEquals(ConsensusVerdict.SNAP_AGREE, result.verdict)
        assertTrue(result.agreed)
        assertSame(confirmM, result.adopted)
    }

    @Test
    fun `agree returns RAW_AGREE at exactly the 3 percent boundary (inclusive)`() {
        // relΔ = |1000-1030| / 1000 = 0.03 정확히 — 경계값 포함(<=)
        val pre = measurement(raw = 1000f, snapped = null, confidence = 0.8f, value = 1000f)
        val confirmM = measurement(raw = 1030f, snapped = null, confidence = 0.8f, value = 1030f)

        val result = MeasurementConsensus.agree(pre, ConfirmOutcome.Measured(confirmM), paneAspect = 2.2f)

        assertEquals(ConsensusVerdict.RAW_AGREE, result.verdict)
        assertSame(confirmM, result.adopted)
    }

    @Test
    fun `agree returns RAW_DISAGREE just past the 3 percent boundary (exclusive)`() {
        // relΔ = |1000-1030.5| / 1000 = 0.0305 — 3% 초과는 배제
        val pre = measurement(raw = 1000f, snapped = null, confidence = 0.8f, value = 1000f)
        val confirmM = measurement(raw = 1030.5f, snapped = null, confidence = 0.8f, value = 1030.5f)

        val result = MeasurementConsensus.agree(pre, ConfirmOutcome.Measured(confirmM), paneAspect = 2.2f)

        assertEquals(ConsensusVerdict.RAW_DISAGREE, result.verdict)
        assertNull(result.adopted)
        assertFalse(result.agreed)
    }

    @Test
    fun `agree returns NO_BARS_CONSISTENT when pane aspect matches pre within tolerance`() {
        val pre = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.8f)

        val result = MeasurementConsensus.agree(pre, ConfirmOutcome.NoBars, paneAspect = 1.8f)

        // relΔ = |1.8 - 1.7778| / 1.7778 ≈ 1.25% <= 3%
        assertEquals(ConsensusVerdict.NO_BARS_CONSISTENT, result.verdict)
        assertSame(pre, result.adopted)
    }

    @Test
    fun `agree returns NO_BARS_INCONSISTENT when pane aspect diverges from pre`() {
        val pre = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.8f)

        val result = MeasurementConsensus.agree(pre, ConfirmOutcome.NoBars, paneAspect = 2.2f)

        assertEquals(ConsensusVerdict.NO_BARS_INCONSISTENT, result.verdict)
        assertNull(result.adopted)
    }

    @Test
    fun `agree returns BOTH_AXES_BARS unconditionally when confirm sees bars on both axes`() {
        val pre = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.8f)

        val result = MeasurementConsensus.agree(pre, ConfirmOutcome.BothAxesBars, paneAspect = 2.2f)

        assertEquals(ConsensusVerdict.BOTH_AXES_BARS, result.verdict)
        assertNull(result.adopted)
    }

    @Test
    fun `agree returns CONFIRM_UNAVAILABLE when confirm could not be judged`() {
        val pre = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.8f)

        val result = MeasurementConsensus.agree(pre, ConfirmOutcome.Unavailable, paneAspect = 2.2f)

        assertEquals(ConsensusVerdict.CONFIRM_UNAVAILABLE, result.verdict)
        assertNull(result.adopted)
    }

    @Test
    fun `agree returns PRE_MISSING when pre is null regardless of a favorable confirm`() {
        // confirm 이 완벽히 합치했을 값이어도 pre 부재가 우선한다 — confirm 단독 채택 금지
        val confirmM = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.9f)

        val result = MeasurementConsensus.agree(null, ConfirmOutcome.Measured(confirmM), paneAspect = 2.2f)

        assertEquals(ConsensusVerdict.PRE_MISSING, result.verdict)
        assertNull(result.adopted)
        assertFalse(result.agreed)
    }

    @Test
    fun `agree returns LOW_CONFIDENCE when pre confidence is below the gate`() {
        val pre = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.1f)
        val confirmM = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.9f)

        val result = MeasurementConsensus.agree(
            pre, ConfirmOutcome.Measured(confirmM), paneAspect = 2.2f, minConfidence = 0.25f,
        )

        // snap 은 일치하지만 pre 게이트가 먼저 걸려 SNAP_AGREE 로 새지 않는다
        assertEquals(ConsensusVerdict.LOW_CONFIDENCE, result.verdict)
        assertNull(result.adopted)
    }

    @Test
    fun `agree returns LOW_CONFIDENCE when confirm measurement confidence is below the gate`() {
        val pre = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.9f)
        val confirmM = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.1f)

        val result = MeasurementConsensus.agree(
            pre, ConfirmOutcome.Measured(confirmM), paneAspect = 2.2f, minConfidence = 0.25f,
        )

        assertEquals(ConsensusVerdict.LOW_CONFIDENCE, result.verdict)
        assertNull(result.adopted)
    }

    // ── ConsensusResult.agreed ───────────────────────────────────

    @Test
    fun `agreed is true exactly when adopted is non-null`() {
        val pre = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.8f)
        val confirmM = measurement(raw = 1.7778f, snapped = 16f / 9f, confidence = 0.8f)

        val agreedResult = MeasurementConsensus.agree(pre, ConfirmOutcome.Measured(confirmM), paneAspect = 2.2f)
        val disagreedResult = MeasurementConsensus.agree(pre, ConfirmOutcome.BothAxesBars, paneAspect = 2.2f)

        assertNotNull(agreedResult.adopted)
        assertTrue(agreedResult.agreed)
        assertNull(disagreedResult.adopted)
        assertFalse(disagreedResult.agreed)
    }
}
