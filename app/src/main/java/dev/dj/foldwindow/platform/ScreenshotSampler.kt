package dev.dj.foldwindow.platform

import android.graphics.Bitmap
import android.graphics.Color
import dev.dj.foldwindow.domain.LetterboxScan

/**
 * Bitmap → 도메인 모델 변환. 여기가 Android SDK 와 순수 도메인의 경계다.
 *
 * @param rowStride    몇 행마다 한 번 샘플링할지. 1이면 전체. 성능/정밀도 트레이드오프
 * @param colStride    행 안에서 몇 픽셀마다 볼지
 * @param darkLuma     이 값 이하의 휘도를 "어두운 픽셀" 로 본다 (0..255)
 * @param sideMarginPct 좌우 가장자리를 이 비율만큼 무시한다. 스크롤바/제스처 힌트 오염 방지
 */
fun Bitmap.toLetterboxScan(
    rowStride: Int = 2,
    colStride: Int = 8,
    darkLuma: Int = 24,
    sideMarginPct: Float = 0.05f,
): LetterboxScan {
    require(rowStride >= 1 && colStride >= 1) { "stride must be >= 1" }

    val w = width
    val h = height
    val x0 = (w * sideMarginPct).toInt().coerceIn(0, w / 2 - 1)
    val x1 = (w - x0).coerceAtLeast(x0 + 1)

    val sampledRows = (h + rowStride - 1) / rowStride
    val ratios = FloatArray(sampledRows)
    val meanLuma = FloatArray(sampledRows)
    val lumaVariance = FloatArray(sampledRows)

    val rowBuf = IntArray(x1 - x0)
    var idx = 0
    var y = 0
    while (y < h) {
        getPixels(rowBuf, 0, rowBuf.size, x0, y, rowBuf.size, 1)
        var dark = 0
        var counted = 0
        var sumLuma = 0L
        var sumLumaSq = 0L
        var x = 0
        while (x < rowBuf.size) {
            val c = rowBuf[x]
            // 정수 근사 휘도. 부동소수 없이 빠르게
            val luma = (77 * Color.red(c) + 150 * Color.green(c) + 29 * Color.blue(c)) shr 8
            if (luma <= darkLuma) dark++
            sumLuma += luma
            sumLumaSq += luma.toLong() * luma // 오버플로 방지를 위해 Long 누적
            counted++
            x += colStride
        }
        if (counted == 0) {
            ratios[idx] = 0f
            meanLuma[idx] = 0f
            lumaVariance[idx] = 0f
        } else {
            ratios[idx] = dark.toFloat() / counted
            val mean = sumLuma.toFloat() / counted
            meanLuma[idx] = mean
            // E[X^2] - E[X]^2. 부동소수 오차로 음수가 나올 수 있어 0 이상으로 clamp
            lumaVariance[idx] = ((sumLumaSq.toFloat() / counted) - mean * mean).coerceAtLeast(0f)
        }
        idx++
        y += rowStride
    }

    // rowStride 로 축소된 스캔이므로 width 도 같은 좌표계로 맞춘다.
    // 종횡비 역산이 정확하려면 width 를 "행 개수와 같은 스케일" 로 환산해야 한다.
    val scaledWidth = (w.toFloat() / rowStride).toInt().coerceAtLeast(1)
    return LetterboxScan(
        rowDarkRatio = ratios.copyOf(idx),
        width = scaledWidth,
        rowMeanLuma = meanLuma.copyOf(idx),
        rowLumaVariance = lumaVariance.copyOf(idx),
    )
}
