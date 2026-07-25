package dev.dj.foldwindow.domain

import kotlin.math.abs

/*
 * DESIGN #12 근거 요약 (docs/DESIGN_12_MEASUREMENT_CONSENSUS.md).
 *
 * 오염 프레임(플레이어 컨트롤 오버레이 / 인트로 / 추천 화면)이 실기기에서 conf 0.60~0.97 의
 * 고신뢰 오측을 냈다 — `confidence` 는 "띠가 얼마나 순수하게 검은가"만 재고 "이 프레임이 실제
 * 영상 콘텐츠인가"는 원리적으로 측정하지 못한다. 임계값을 어떻게 조정해도 오염 conf 가 정상
 * ADAPTIVE conf 상한(0.6)보다 높으므로 분리 불가능하다.
 *
 * 그래서 신뢰도의 정의를 "한 프레임이 얼마나 깨끗한가"에서 "시각·스캔 축·화면 컨텍스트가 서로
 * 다른 두 측정이 합치하는가"로 교체한다:
 * - pre  : 트리거 시각, 전체화면, 행축 (기존 `resolveAspect`)
 * - confirm : 진입 완료 후, 분할 페인 크롭, 행축+열축 양쪽 (`resolveAspect` / [LetterboxDetector.resolveAspectPillarbox])
 *
 * 오염원은 전부 일시적·컨텍스트 종속이라 두 측정을 같은 값으로 오염시킬 확률이 구조적으로
 * 낮다. 합치하지 않으면 무조건 PRESET(기본 16:9) 로 폴백한다 — 오탐 비용은 `closedLoopCorrection`
 * 이 흡수하는 보정 드래그 1회, 게이트 부재 시 미탐 비용은 오배치 고착(실측 사고 2회)이라
 * 비대칭이 게이트 도입에 유리하다(§3.5). 상세 합치 표는 §3.3 참조 — 이 파일은 그 표의 코드화다.
 */

/** confirm 측정에서 한 축이 어떻게 읽혔는가 */
enum class AxisReading { BARS_MEASURED, NO_BARS, UNJUDGEABLE }

/** 진입 후 확인 측정의 양축 집계 결과 */
sealed interface ConfirmOutcome {
    data class Measured(val measurement: AspectMeasurement) : ConfirmOutcome
    object NoBars : ConfirmOutcome          // 양축 무띠 — 영상 AR ≈ 페인 AR 증거
    object BothAxesBars : ConfirmOutcome    // 양축 띠 — aspect-fit 영상 불가능, 추천 화면류 물증
    object Unavailable : ConfirmOutcome     // 판정 불가 (한 축이라도 UNJUDGEABLE 인 잔여 조합 포함)
}

/** 로그/테스트용 판정 사유 */
enum class ConsensusVerdict {
    SNAP_AGREE, RAW_AGREE, NO_BARS_CONSISTENT,          // 합치 3종
    RAW_DISAGREE, NO_BARS_INCONSISTENT, BOTH_AXES_BARS, // 불합치
    CONFIRM_UNAVAILABLE, PRE_MISSING, LOW_CONFIDENCE,   // 불합치
}

/**
 * @param adopted 합치 시 채택할 측정값. 불합치면 null — 호출자는 이 경우 PRESET 티어로 폴백한다.
 * @param verdict 로깅/테스트용 판정 사유. [agreed] 와 별개로 항상 채워진다.
 */
data class ConsensusResult(val adopted: AspectMeasurement?, val verdict: ConsensusVerdict) {
    val agreed: Boolean get() = adopted != null
}

object MeasurementConsensus {

    /** 상대 오차 허용치. pre/confirm 의 raw 값 비교, 그리고 무띠 케이스의 paneAspect/pre 비교에 공용으로 쓰인다 */
    const val DEFAULT_REL_TOLERANCE = 0.03f

    /**
     * 한 축의 측정 결과를 세 가지로 분류한다.
     *
     * - [measurement] 가 있고 신뢰도가 [minConfidence] 이상이면 그 자체로 띠가 검출된 것이다 →
     *   [AxisReading.BARS_MEASURED]. 신뢰도 미달 측정은 없는 것과 동일하게 취급하고 아래 두
     *   분기로 내려간다 — 실측(2026-07-25, Fold 7): 분할 진입 직후 유튜브가 플레이어 컨트롤을
     *   상시 소환해 타이틀 그라디언트가 conf 0.08 안팎의 유사 밴드를 만들어냈고, 이 저신뢰 밴드가
     *   그대로 BARS_MEASURED 로 승격돼 반대편 축의 정상 측정과 결합해 [ConfirmOutcome.BothAxesBars]
     *   오판정을 유발했다. [agree] 가 이미 쓰는 신뢰도 하한과 동일 기준을 축 분류 단계에도 적용해
     *   더 일찍 걸러낸다.
     * - [measurement] 가 없거나(또는 신뢰도 미달) [residual] 이 엄격히 0(잔여 픽셀 없음)이면
     *   "띠 없음"이 확정된 것이다 → [AxisReading.NO_BARS]
     * - 그 외(잔여가 0보다 크지만 [LetterboxDetector.detectHybrid] 가 밴드를 거부한 경우 포함)는
     *   판정할 수 없다 → [AxisReading.UNJUDGEABLE]. 잔여 >0 인데 밴드가 거부된 상태는 오염 의심
     *   신호이므로 "띠 없음"으로 낙관하지 않고 엄격히 0인 경우만 [AxisReading.NO_BARS] 로 인정한다.
     *
     * @param minConfidence [measurement] 를 BARS_MEASURED 후보로 인정하는 최소 신뢰도. [agree] 가
     *                       raw/snap 합치를 판정하기 전에 적용하는 게이트와 동일 기본값을 쓴다.
     */
    fun classifyAxis(
        measurement: AspectMeasurement?,
        residual: ResidualBars?,
        minConfidence: Float = AspectResolver.DEFAULT_MIN_MEASUREMENT_CONFIDENCE,
    ): AxisReading =
        when {
            measurement != null && measurement.confidence >= minConfidence -> AxisReading.BARS_MEASURED
            residual != null && residual.totalPx == 0 -> AxisReading.NO_BARS
            else -> AxisReading.UNJUDGEABLE
        }

    /**
     * confirm 단계의 행축·열축 측정을 하나의 [ConfirmOutcome] 으로 집계한다.
     *
     * aspect-fit 영상은 페인보다 좁은 쪽 축에만 띠가 생긴다 — 정확히 한 축만 BARS_MEASURED 이고
     * 다른 축이 NO_BARS 인 경우만 그 측정을 채택한다. 양축 모두 띠가 있으면 aspect-fit 영상으로는
     * 설명이 안 되는 물리적 모순이므로(추천 화면류 오염의 물증) [ConfirmOutcome.BothAxesBars]. 양축
     * 모두 없으면 [ConfirmOutcome.NoBars] (영상 AR ≈ 페인 AR 이라는 별도 증거로 [agree] 에서 취급).
     * 그 외 조합(어느 한 축이라도 UNJUDGEABLE) 은 전부 [ConfirmOutcome.Unavailable] 로 묶는다 —
     * 확인 불가는 무죄가 아니다(§3.3).
     *
     * @param minConfidence 각 축을 [classifyAxis] 로 분류할 때 쓰는 최소 신뢰도. 그대로 전달된다.
     */
    fun classifyConfirm(
        rowMeasurement: AspectMeasurement?,
        rowResidual: ResidualBars?,
        colMeasurement: AspectMeasurement?,
        colResidual: ResidualBars?,
        minConfidence: Float = AspectResolver.DEFAULT_MIN_MEASUREMENT_CONFIDENCE,
    ): ConfirmOutcome {
        val row = classifyAxis(rowMeasurement, rowResidual, minConfidence)
        val col = classifyAxis(colMeasurement, colResidual, minConfidence)

        return when {
            row == AxisReading.BARS_MEASURED && col == AxisReading.NO_BARS ->
                ConfirmOutcome.Measured(requireNotNull(rowMeasurement))
            col == AxisReading.BARS_MEASURED && row == AxisReading.NO_BARS ->
                ConfirmOutcome.Measured(requireNotNull(colMeasurement))
            row == AxisReading.BARS_MEASURED && col == AxisReading.BARS_MEASURED ->
                ConfirmOutcome.BothAxesBars
            row == AxisReading.NO_BARS && col == AxisReading.NO_BARS ->
                ConfirmOutcome.NoBars
            else -> ConfirmOutcome.Unavailable
        }
    }

    /**
     * pre(진입 전 행축) 와 confirm(진입 후 양축 집계) 이 합치하는지 판정한다. §3.3 합치 표의
     * 코드화 — 합치해야만 [ConsensusResult.adopted] 가 채워지고, 그 외 모든 경로는 null 이다
     * (호출자는 null 을 PRESET 티어 폴백 신호로 쓴다).
     *
     * per-측정 신뢰도 게이트([minConfidence])는 "후보 자격"만 부여한다 — 최종 채택은 합치 여부가
     * 결정한다. pre 쪽 게이트가 confirm 판정보다 먼저 적용되므로, pre 가 없거나 신뢰도 미달이면
     * confirm 이 무엇이든 그 사유([ConsensusVerdict.PRE_MISSING]/[ConsensusVerdict.LOW_CONFIDENCE])
     * 로 즉시 불합치 처리된다.
     *
     * raw 상대오차 비교의 분모는 항상 `pre.raw` 다 (confirm.raw 가 아니다) — pre 가 기준(먼저 얻은
     * 값)이고 confirm 은 그것을 검증하는 두 번째 관측이라는 비대칭을 반영한다. 무띠 일치 비교
     * (`paneAspect` vs `pre.value`) 의 분모도 동일한 이유로 `pre.value` 다.
     *
     * @param pre           진입 전 행축 측정. null 이면 pre 자체가 실패/미시도한 것
     * @param confirm       진입 후 양축 집계 결과
     * @param paneAspect    분할 페인의 종횡비. confirm 이 [ConfirmOutcome.NoBars] 일 때만 쓰인다
     * @param relTolerance  raw 값·무띠 비교에 공용으로 쓰는 상대 오차 허용치
     * @param minConfidence 개별 측정을 "후보"로 인정하는 최소 신뢰도
     */
    fun agree(
        pre: AspectMeasurement?,
        confirm: ConfirmOutcome,
        paneAspect: Float,
        relTolerance: Float = DEFAULT_REL_TOLERANCE,
        minConfidence: Float = AspectResolver.DEFAULT_MIN_MEASUREMENT_CONFIDENCE,
    ): ConsensusResult {
        if (pre == null) {
            return ConsensusResult(null, ConsensusVerdict.PRE_MISSING)
        }
        if (pre.confidence < minConfidence) {
            return ConsensusResult(null, ConsensusVerdict.LOW_CONFIDENCE)
        }

        return when (confirm) {
            is ConfirmOutcome.Measured -> {
                val m = confirm.measurement
                when {
                    m.confidence < minConfidence ->
                        ConsensusResult(null, ConsensusVerdict.LOW_CONFIDENCE)

                    pre.snapped != null && m.snapped != null && pre.snapped == m.snapped ->
                        ConsensusResult(m, ConsensusVerdict.SNAP_AGREE)

                    abs(pre.raw - m.raw) / pre.raw <= relTolerance ->
                        ConsensusResult(m, ConsensusVerdict.RAW_AGREE)

                    else -> ConsensusResult(null, ConsensusVerdict.RAW_DISAGREE)
                }
            }

            ConfirmOutcome.NoBars -> {
                // 무띠 자체가 "영상 AR ≈ 페인 AR" 이라는 증거다 — pre 값을 그대로 채택한다.
                if (abs(paneAspect - pre.value) / pre.value <= relTolerance) {
                    ConsensusResult(pre, ConsensusVerdict.NO_BARS_CONSISTENT)
                } else {
                    ConsensusResult(null, ConsensusVerdict.NO_BARS_INCONSISTENT)
                }
            }

            ConfirmOutcome.BothAxesBars -> ConsensusResult(null, ConsensusVerdict.BOTH_AXES_BARS)

            ConfirmOutcome.Unavailable -> ConsensusResult(null, ConsensusVerdict.CONFIRM_UNAVAILABLE)
        }
    }
}
