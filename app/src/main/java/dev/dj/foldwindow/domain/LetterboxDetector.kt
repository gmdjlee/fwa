package dev.dj.foldwindow.domain

import kotlin.math.abs

/*
 * ADR-1 ②: 다른 앱의 영상 종횡비는 직접 읽을 수 없다.
 * 대신 스크린샷에서 위아래 검은 띠를 실측해 실제 콘텐츠 종횡비를 역산한다.
 *
 * 이 파일은 Bitmap 을 모른다. "행별 어두움 비율" 배열만 입력으로 받는 순수 함수다.
 * Bitmap → LetterboxScan 변환은 platform/ScreenshotSampler.kt 가 담당한다.
 */

/**
 * @param rowDarkRatio index = 행 번호(위→아래), value = 그 행에서 어두운 픽셀이 차지하는 비율 0..1
 * @param width        원본 프레임의 가로 픽셀 수 (종횡비 역산에 필요)
 */
class LetterboxScan(
    val rowDarkRatio: FloatArray,
    val width: Int,
) {
    val height: Int get() = rowDarkRatio.size

    init {
        require(width > 0) { "width must be positive" }
        require(rowDarkRatio.isNotEmpty()) { "rowDarkRatio must not be empty" }
    }
}

/**
 * @param top/bottom    콘텐츠 밴드의 시작/끝 행 (bottom exclusive)
 * @param confidence    0..1. 띠가 얼마나 깨끗하게 검고 경계가 얼마나 뚜렷한가
 */
data class ContentBand(
    val top: Int,
    val bottom: Int,
    val topBarPx: Int,
    val bottomBarPx: Int,
    val confidence: Float,
) {
    val height: Int get() = bottom - top
}

object LetterboxDetector {

    /** 이 비율 이상 어두우면 "검은 띠 행" 으로 본다 */
    const val DEFAULT_DARK_ROW_THRESHOLD = 0.97f

    /** 콘텐츠 밴드가 전체 높이의 이 비율보다 작으면 오탐으로 간주하고 버린다 */
    const val MIN_CONTENT_FRACTION = 0.25f

    /** 이 비율 이상 콘텐츠면 애초에 띠가 없는 것으로 본다 */
    const val NO_LETTERBOX_FRACTION = 0.99f

    /** 흔한 영상 종횡비 프리셋. 역산값을 여기로 스냅해 잡음을 제거한다 */
    val KNOWN_ASPECTS = floatArrayOf(
        4f / 3f,       // 1.3333
        1.5f,          // 3:2
        16f / 10f,     // 1.6000
        16f / 9f,      // 1.7778
        1.85f,         // 미국 극장 표준
        2f,            // 2:1 (Univisium)
        64f / 27f,     // 2.3704 (21:9 UW)
        2.35f,         // 시네마스코프
        2.39f,         // 현대 애너모픽
    )

    /**
     * 위/아래에서 안쪽으로 스캔해 검은 띠를 벗겨낸다.
     *
     * @return 콘텐츠 밴드. 띠가 없거나 신뢰할 수 없으면 null
     */
    fun detect(
        scan: LetterboxScan,
        darkRowThreshold: Float = DEFAULT_DARK_ROW_THRESHOLD,
    ): ContentBand? {
        val h = scan.height
        val rows = scan.rowDarkRatio

        var top = 0
        while (top < h && rows[top] >= darkRowThreshold) top++

        var bottomExclusive = h
        while (bottomExclusive > top && rows[bottomExclusive - 1] >= darkRowThreshold) bottomExclusive--

        val contentH = bottomExclusive - top
        if (contentH <= 0) return null                                  // 전 화면이 검음

        val fraction = contentH.toFloat() / h
        if (fraction < MIN_CONTENT_FRACTION) return null                // 콘텐츠가 너무 작음 = 오탐
        if (fraction >= NO_LETTERBOX_FRACTION) return null              // 띠가 사실상 없음

        return ContentBand(
            top = top,
            bottom = bottomExclusive,
            topBarPx = top,
            bottomBarPx = h - bottomExclusive,
            confidence = confidenceOf(rows, top, bottomExclusive, darkRowThreshold),
        )
    }

    /**
     * 신뢰도 = 띠의 순수함(어두운 정도의 평균) × 경계의 뚜렷함(첫 콘텐츠 행의 대비)
     */
    private fun confidenceOf(
        rows: FloatArray,
        top: Int,
        bottomExclusive: Int,
        threshold: Float,
    ): Float {
        val barRows = top + (rows.size - bottomExclusive)
        if (barRows == 0) return 0f

        var barSum = 0f
        for (i in 0 until top) barSum += rows[i]
        for (i in bottomExclusive until rows.size) barSum += rows[i]
        val purity = barSum / barRows

        // 경계 대비: 띠의 마지막 행과 콘텐츠 첫 행의 차이가 클수록 확실하다
        val edgeContrast = when {
            top in rows.indices && top > 0 -> (rows[top - 1] - rows[top]).coerceIn(0f, 1f)
            bottomExclusive < rows.size -> (rows[bottomExclusive] - rows[bottomExclusive - 1]).coerceIn(0f, 1f)
            else -> 0f
        }

        return (purity * (0.5f + 0.5f * (edgeContrast / (1f - threshold + 1e-6f)).coerceAtMost(1f)))
            .coerceIn(0f, 1f)
    }

    /** 콘텐츠 밴드로부터 실제 영상 종횡비를 역산 */
    fun impliedAspect(band: ContentBand, frameWidth: Int): Float {
        require(band.height > 0) { "content band height must be positive" }
        return frameWidth.toFloat() / band.height
    }

    /**
     * 역산값을 흔한 프리셋으로 스냅한다.
     * @param relativeTolerance 상대 오차 허용치. 0.03 = 3%
     * @return 스냅된 값. 어느 프리셋에도 가깝지 않으면 null
     */
    fun snapToKnownAspect(aspect: Float, relativeTolerance: Float = 0.03f): Float? {
        var best: Float? = null
        var bestErr = Float.MAX_VALUE
        for (known in KNOWN_ASPECTS) {
            val err = abs(aspect - known) / known
            if (err < bestErr) {
                bestErr = err
                best = known
            }
        }
        return if (bestErr <= relativeTolerance) best else null
    }

    /**
     * 감지 → 역산 → 스냅을 한 번에.
     * @return 스냅 성공 시 프리셋 값, 실패 시 원본 역산값, 감지 실패 시 null
     */
    fun resolveAspect(scan: LetterboxScan): AspectMeasurement? {
        val band = detect(scan) ?: return null
        val raw = impliedAspect(band, scan.width)
        val snapped = snapToKnownAspect(raw)
        return AspectMeasurement(
            raw = raw,
            snapped = snapped,
            value = snapped ?: raw,
            band = band,
        )
    }
}

data class AspectMeasurement(
    val raw: Float,
    val snapped: Float?,
    val value: Float,
    val band: ContentBand,
) {
    val confidence: Float get() = band.confidence
    val isSnapped: Boolean get() = snapped != null
}
