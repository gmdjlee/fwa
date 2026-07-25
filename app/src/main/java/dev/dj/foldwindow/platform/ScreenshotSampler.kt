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

/**
 * [DESIGN #12 §3.4] `toLetterboxScan` 의 전치(transpose)판. 분할 페인은 보통 화면이 영상보다
 * 넓어(AR≈2.2) 위아래가 아니라 **좌우**에 검은 띠(필러박스)가 생긴다 — 열 단위로 스캔해
 * [LetterboxDetector.resolveAspectPillarbox] 가 그대로 재사용하는 [LetterboxScan] 을 만든다.
 * `scan.rowDarkRatio` 의 각 entry = 열(왼쪽→오른쪽), `scan.width` 자리 = 페인 높이(entries 축과
 * 동일한 stride 좌표계로 환산됨)다.
 *
 * 기존 [toLetterboxScan] 은 시그니처/동작 그대로 둔다 — 이 함수는 완전히 독립된 신규 함수다.
 *
 * @param colStride     몇 열마다 한 번 샘플링할지(entries 축, 좌→우). 성능/정밀도 트레이드오프
 * @param rowStride     한 열 안에서 몇 행마다 볼지
 * @param darkLuma      이 값 이하의 휘도를 "어두운 픽셀" 로 본다 (0..255)
 * @param edgeMarginPct 상하 가장자리를 이 비율만큼 무시한다. [toLetterboxScan] 의 sideMarginPct
 *                      전치 — 디바이더 그림자·플레이어 상단 그라디언트 오염 방지. 실측
 *                      (2026-07-25, Fold 7 confirm 단계): 분할 진입 직후 유튜브가 플레이어
 *                      컨트롤을 상시 소환 — 타이틀 그라디언트 ~상단 100px, 축소 아이콘 y
 *                      890~945(977px 페인 기준) 오염. 기존 5%(49px) 로는 두 대역을 못 걸러
 *                      12%(117px) 로 상향
 * @param sideMarginPct 좌우 최외곽 열(entries 축)을 이 비율만큼 무시한다(기본 0.5% ≈ 11px).
 *                      실측(2026-07-25, Fold 7): 최외곽 열 분산 404~501 실측 — 라운드 코너 누출 +
 *                      엣지 렌더링. 5% 세로 마진(49px) < 코너 반경(~80px) 이라 세로 마진만으로
 *                      불충분해 별도의 가로축 마진이 필요하다.
 */
fun Bitmap.toPillarboxScan(
    colStride: Int = 2,
    rowStride: Int = 8,
    darkLuma: Int = 24,
    edgeMarginPct: Float = 0.12f,
    sideMarginPct: Float = 0.005f,
): LetterboxScan {
    require(colStride >= 1 && rowStride >= 1) { "stride must be >= 1" }

    val w = width
    val h = height
    val y0 = (h * edgeMarginPct).toInt().coerceIn(0, h / 2 - 1)
    val y1 = (h - y0).coerceAtLeast(y0 + 1)
    val x0 = (w * sideMarginPct).toInt().coerceIn(0, w / 2 - 1)
    val x1 = (w - x0).coerceAtLeast(x0 + 1)

    val sampledCols = (x1 - x0 + colStride - 1) / colStride
    val ratios = FloatArray(sampledCols)
    val meanLuma = FloatArray(sampledCols)
    val lumaVariance = FloatArray(sampledCols)

    // 열 전체(0..h)를 한 번에 읽고 margin·rowStride 는 배열 순회에서 적용한다.
    // 열마다 getPixels 호출 1회 — 총 호출 수 ≈ (x1-x0)/colStride (행 스캔과 동일 오더).
    val colBuf = IntArray(h)
    var idx = 0
    var x = x0
    while (x < x1) {
        getPixels(colBuf, 0, 1, x, 0, 1, h)
        var dark = 0
        var counted = 0
        var sumLuma = 0L
        var sumLumaSq = 0L
        var y = y0
        while (y < y1) {
            val c = colBuf[y]
            val luma = (77 * Color.red(c) + 150 * Color.green(c) + 29 * Color.blue(c)) shr 8
            if (luma <= darkLuma) dark++
            sumLuma += luma
            sumLumaSq += luma.toLong() * luma // 오버플로 방지를 위해 Long 누적
            counted++
            y += rowStride
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
        x += colStride
    }

    // colStride 로 축소된 스캔(entries=열)이므로 height 도 같은 좌표계로 맞춘다 — margin 제외 전
    // 전체 높이 기준. sideMarginPct 는 entries 개수(x0..x1 범위)만 줄일 뿐 이 값과는 무관하다 —
    // 종횡비 역산의 교차축 길이는 항상 페인 전체 높이여야 한다. toLetterboxScan 의
    // scaledWidth=w/rowStride 에 정확 대응(전치판이므로 entries 축 stride 인 colStride 를 쓴다) —
    // 어긋나면 resolveAspectPillarbox 역산이 깨진다.
    val scaledHeight = (h.toFloat() / colStride).toInt().coerceAtLeast(1)
    return LetterboxScan(
        rowDarkRatio = ratios.copyOf(idx),
        width = scaledHeight,
        rowMeanLuma = meanLuma.copyOf(idx),
        rowLumaVariance = lumaVariance.copyOf(idx),
    )
}
