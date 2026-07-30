package dev.dj.foldwindow.platform

import android.graphics.Bitmap
import android.graphics.Color
import dev.dj.foldwindow.domain.LetterboxDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T1 (docs/IMPROVEMENT_PLAN_2026-07-29.md §T1): `ScreenshotSampler`(Bitmap → 도메인 모델 변환)의
 * 회귀 안전망. `domain/LetterboxDetectorTest` 는 이미 만들어진 [dev.dj.foldwindow.domain.LetterboxScan]
 * 을 입력으로 순수 로직만 검증하지만, 여기서는 **실제 `android.graphics.Bitmap` 픽셀 연산**
 * (`getPixels`/`setPixels`, stride, margin coerceIn) 을 Robolectric 으로 구동해 그 경계까지 검증한다.
 *
 * P4(getPixels stride 최적화, W7)가 이 파일을 건드리기 **전에** 깔아두는 안전망이다(P-2 원칙) —
 * 이 웨이브(W4)에서는 `ScreenshotSampler.kt`/`LetterboxDetector.kt` 프로덕션 로직을 일절 바꾸지 않는다.
 *
 * compileSdk=36 은 Robolectric 4.14.1 이 아직 android-all jar 를 제공하지 않아 `@Config(sdk=[34])`
 * 로 고정한다(CLAUDE.md 함정 #7 대상 아님 — 테스트 실행 환경 설정일 뿐, 실기기 측정값이 아니다).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScreenshotSamplerTest {

    // ── 합성 Bitmap 헬퍼 ──────────────────────────────────────────
    // 두 헬퍼 모두 "밴드가 교차축 전체에 걸쳐 균일"하게 만든다 — sideMarginPct/edgeMarginPct 가
    // 함수마다 다른 값이어도(letterbox 는 열 마진 5%, pillarbox 는 행 마진 12%+열 마진 0.5%)
    // 결과에 영향을 주지 않게 하기 위함이다(테스트 3의 전제 조건).

    /** 위/아래에 순흑 띠, 가운데에 밝은 콘텐츠가 있고 각 행이 폭 전체에 걸쳐 균일한 Bitmap. */
    private fun horizontalBandsBitmap(width: Int, height: Int, topBarPx: Int, bottomBarPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val barRow = IntArray(width) { Color.BLACK }
        val contentRow = IntArray(width) { Color.WHITE }
        for (y in 0 until height) {
            val row = if (y < topBarPx || y >= height - bottomBarPx) barRow else contentRow
            bmp.setPixels(row, 0, width, 0, y, width, 1)
        }
        return bmp
    }

    /** 좌/우에 순흑 띠, 가운데에 밝은 콘텐츠가 있고 각 열이 높이 전체에 걸쳐 균일한 Bitmap. */
    private fun verticalBandsBitmap(width: Int, height: Int, leftBarPx: Int, rightBarPx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            val color = if (x < leftBarPx || x >= width - rightBarPx) Color.BLACK else Color.WHITE
            val col = IntArray(height) { color }
            bmp.setPixels(col, 0, 1, x, 0, 1, height)
        }
        return bmp
    }

    // ── W7-B 추가 헬퍼 ────────────────────────────────────────────

    /** 단색 Bitmap. 초소형/퇴화 크기(1x1, 1xN, Nx1) 경계 테스트 전용. */
    private fun solidBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(color)
        return bmp
    }

    /**
     * [toPillarboxScan] 의 **세로 마진 격리** 검증 전용 Bitmap (테스트 8).
     *
     * - 마진 밴드(`y < interiorTop` 또는 `y >= interiorBottom`) = 순흑(luma 0) — 결과에 절대
     *   섞이면 안 되는 픽셀
     * - 내부 구간 = 회색 램프 `RGB(y, y, y)` — 샘플러의 정수 근사 휘도가 **정확히 y** 가 된다
     *   (`77 + 150 + 29 == 256` 이므로 `(77y + 150y + 29y) shr 8 == (256y) shr 8 == y`)
     *
     * 모든 열이 동일한 패턴이므로 산출 배열의 모든 entry 가 같은 값이어야 한다.
     */
    private fun verticalRampBitmap(width: Int, height: Int, interiorTop: Int, interiorBottom: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (y in 0 until height) {
            val level = if (y < interiorTop || y >= interiorBottom) 0 else y
            val row = IntArray(width) { Color.rgb(level, level, level) }
            bmp.setPixels(row, 0, width, 0, y, width, 1)
        }
        return bmp
    }

    // ── 1. 행축(letterbox) 파이프라인 ────────────────────────────

    @Test
    fun `pure-black top-bottom bands resolve to the embedded 16-9 aspect via toLetterboxScan`() {
        // width=192, height=188, 상하 40px 순흑 띠, 중앙 108px(=content) 밝은 콘텐츠.
        // rowStride=2(기본값 — 실사용 ArrangerAccessibilityService.ROW_STRIDE 와 동일)로 샘플링하면
        // scaledWidth=96, 콘텐츠 샘플행=54 → raw = 96/54 = 16/9 정확히
        // (192, 108 모두 rowStride=2 로 나누어떨어져 샘플링 경계에 반올림 오차가 없다).
        val bitmap = horizontalBandsBitmap(width = 192, height = 188, topBarPx = 40, bottomBarPx = 40)

        val scan = bitmap.toLetterboxScan()
        val measurement = LetterboxDetector.resolveAspect(scan)

        assertNotNull(measurement)
        assertEquals(96, scan.width)
        assertEquals(54, measurement!!.band.height)
        assertEquals(16f / 9f, measurement.raw, 0.0001f)
        assertTrue(measurement.isSnapped)
        assertEquals(16f / 9f, measurement.value, 0.0001f)
    }

    // ── 2. 열축(pillarbox) 파이프라인 ────────────────────────────

    @Test
    fun `pure-black left-right pillarbox bands resolve to the embedded 4-3 aspect via toPillarboxScan`() {
        // width=100, height=60, 좌우 10px 순흑 띠, 중앙 80px(=content) 밝은 콘텐츠.
        // colStride=2(기본값 — 실사용 COL_STRIDE 와 동일)로 샘플링하면 scaledHeight=30,
        // 콘텐츠 샘플열=40 → raw = 40/30 = 4/3 정확히.
        val bitmap = verticalBandsBitmap(width = 100, height = 60, leftBarPx = 10, rightBarPx = 10)

        val scan = bitmap.toPillarboxScan()
        val measurement = LetterboxDetector.resolveAspectPillarbox(scan)

        assertNotNull(measurement)
        assertEquals(30, scan.width)
        assertEquals(40, measurement!!.band.height)
        assertEquals(4f / 3f, measurement.raw, 0.0001f)
        assertTrue(measurement.isSnapped)
        assertEquals(4f / 3f, measurement.value, 0.0001f)
    }

    // ── 3. 전치 쌍 — scaledWidth=w/rowStride ↔ scaledHeight=h/colStride 대응 ──
    //
    // 주의: 이 테스트는 Bitmap B 를 Bitmap A 의 **픽셀 전치**(행↔열 그대로 뒤집기)로 만들지
    // *않는다*. 그렇게 하면 수학적으로 반드시 raw 가 **역수**가 된다 — letterbox 의 raw 는
    // (frame 폭)/(content 높이) 인데, pillarbox 의 raw 는 (content 폭)/(frame 높이) 라서, 화면을
    // 그대로 90도 돌리면 "frame 폭"과 "content 높이"가 서로의 자리를 바꿔치기 때문이다
    // (직접 계산으로 3중 검증: 픽셀 전치 시 raw_pillarbox == 1/raw_letterbox 였다).
    // 대신 **프레임 전체 치수만 전치**(A: 144×270 → B: 270×144, 브리프가 말하는 "(H×W)")하고,
    // 콘텐츠 밴드는 B 안에서 **독립적으로** 배치해 같은 16:9 를 재현한다. 이래야 "같은 AR" 이라는
    // 요구와 "치수는 전치" 라는 요구를 모순 없이 동시에 만족한다.
    //
    // 스트라이드도 브리프 권고대로 **명시적으로 교차**시킨다: entries 축 stride(A 의 rowStride,
    // B 의 colStride)는 둘 다 1, 교차축 stride(A 의 colStride, B 의 rowStride)는 둘 다 8 —
    // 이것이 바로 `scaledWidth=w/rowStride` ↔ `scaledHeight=h/colStride` 대응이 실제로 지켜지는지
    // 검증하는 지점이다. 대응이 깨지면(예: scaledHeight 가 rowStride 를 잘못 쓰게 바뀌면)
    // scanB.width 가 144 에서 벗어나고 raw 도 16/9 에서 벗어난다.
    @Test
    fun `transpose pair — letterboxed W x H and pillarboxed H x W resolve to the same aspect ratio`() {
        // A: 144×270, 상단 95px·하단 94px 순흑 띠, 콘텐츠 81px → rowStride=1 → raw = 144/81 = 16/9
        val a = horizontalBandsBitmap(width = 144, height = 270, topBarPx = 95, bottomBarPx = 94)
        // B: 270×144 (= A 의 전치 치수), 좌우 7px 순흑 띠, 콘텐츠 256px → colStride=1
        // → scaledHeight = 144/1 = 144 (= A 의 scaledWidth) → raw = 256/144 = 16/9
        val b = verticalBandsBitmap(width = 270, height = 144, leftBarPx = 7, rightBarPx = 7)

        val scanA = a.toLetterboxScan(rowStride = 1, colStride = 8)
        val scanB = b.toPillarboxScan(colStride = 1, rowStride = 8)

        // 대응의 핵심 물증: scaledWidth(A) 와 scaledHeight(B) 는 같은 숫자(144)여야 한다.
        assertEquals(144, scanA.width)
        assertEquals(144, scanB.width)

        val measurementA = LetterboxDetector.resolveAspect(scanA)
        val measurementB = LetterboxDetector.resolveAspectPillarbox(scanB)

        assertNotNull(measurementA)
        assertNotNull(measurementB)
        assertEquals(81, measurementA!!.band.height)
        assertEquals(256, measurementB!!.band.height)
        assertEquals(16f / 9f, measurementA.raw, 0.0001f)
        assertEquals(16f / 9f, measurementB.raw, 0.0001f)
        assertEquals("전치 쌍은 같은 AR 을 내야 한다", measurementA.raw, measurementB.raw, 0.0001f)
    }

    // ── 4. rowStride 불변성 — P4/W7 의 전제조건 ──────────────────

    @Test
    fun `raw aspect stays invariant across rowStride 1, 2, and 4`() {
        // width=192, 상하 각 64px 순흑 띠, 콘텐츠 64px — 전부 4 의 배수라 rowStride∈{1,2,4}
        // 모두 나머지 없이 나뉜다. 정수 경계라 이론상 raw 는 완전히 동일해야 하지만(실측으로도
        // 3개 stride 전부 정확히 3.0), 향후 P4(getPixels stride 최적화, W7)가 부동소수 계산
        // 경로를 바꿀 가능성에 대비해 **상대오차 3% 이내**로 검증한다 — stride 를 바꿔도 raw 가
        // 이 허용치를 벗어나면 P4 최적화가 실측값을 왜곡한다는 뜻이므로 여기서 잡아야 한다.
        val bitmap = horizontalBandsBitmap(width = 192, height = 192, topBarPx = 64, bottomBarPx = 64)
        val relativeTolerance = 0.03f

        val raws = intArrayOf(1, 2, 4).map { stride ->
            LetterboxDetector.resolveAspect(bitmap.toLetterboxScan(rowStride = stride))!!.raw
        }

        val reference = raws.first()
        assertEquals(3.0f, reference, 3.0f * relativeTolerance)
        raws.forEach { raw ->
            assertEquals("stride 간 raw 편차가 허용치를 벗어남: $raws", reference, raw, reference * relativeTolerance)
        }
    }

    // ── 5. 초소형 Bitmap 경계 ─────────────────────────────────────

    @Test
    fun `tiny bitmaps do not throw when computing side margins`() {
        // 4x4, 2x2 — toLetterboxScan/toPillarboxScan 의 coerceIn(0, w/2-1) 류 경계 연산이 예외
        // 없이 동작해야 한다. w=1(또는 h=1) 의 coerceIn(0,-1) 예외(trap #8)는 W7 에서
        // coerceAtMost((w/2-1).coerceAtLeast(0)) 로 수정됐고, 아래 1×1·1×N/N×1 테스트(6·7)가
        // 그 회귀 가드다.
        for (size in intArrayOf(4, 2)) {
            val bitmap = horizontalBandsBitmap(width = size, height = size, topBarPx = 1, bottomBarPx = 1)

            val rowScan = bitmap.toLetterboxScan()
            val colScan = bitmap.toPillarboxScan()

            assertTrue("size=$size 행 스캔이 비어 있음", rowScan.height > 0)
            assertTrue("size=$size 열 스캔이 비어 있음", colScan.height > 0)
        }
    }

    // ── 6. 1x1 Bitmap — W7-B/1-B 회귀 가드 ────────────────────────

    @Test
    fun `1x1 bitmap does not throw in either sampler`() {
        // W7-B 이전에는 `coerceIn(0, w/2 - 1)` 의 상한이 w == 1 에서 -1 이 되어 빈 범위
        // → IllegalArgumentException 이었다(테스트 5 주석의 trap #8, PROGRESS §C 가 W7 에 배정).
        // 수정 후: x0 = 0, x1 = 1 로 떨어져 한 픽셀을 정상 샘플링한다.
        //
        // 손계산(1x1, 기본 인자):
        //  - toLetterboxScan : x0=0, x1=max(1-0,1)=1 → rowBuf 1칸, sampledRows=(1+2-1)/2=1
        //                      → entries 1개, scaledWidth=(1/2).toInt()=0 → coerceAtLeast(1)=1
        //  - toPillarboxScan : y0=0, y1=max(1-0,1)=1, x0=0, x1=1
        //                      → sampledCols=(1-0+2-1)/2=1 → entries 1개, scaledHeight=1
        val bitmap = solidBitmap(width = 1, height = 1, color = Color.BLACK)

        val rowScan = bitmap.toLetterboxScan()
        val colScan = bitmap.toPillarboxScan()

        assertEquals("행 스캔 entries", 1, rowScan.height)
        assertEquals("열 스캔 entries", 1, colScan.height)
        assertEquals("행 스캔 scaledWidth", 1, rowScan.width)
        assertEquals("열 스캔 scaledHeight", 1, colScan.width)
        // 순흑 1픽셀 → 두 스캔 모두 dark ratio 1.0
        assertEquals(1f, rowScan.rowDarkRatio[0], 0.0001f)
        assertEquals(1f, colScan.rowDarkRatio[0], 0.0001f)
    }

    // ── 7. 1xN / Nx1 퇴화 Bitmap — W7-B/1-B 회귀 가드 ─────────────

    @Test
    fun `degenerate 1xN and Nx1 bitmaps do not throw in either sampler`() {
        // 1xN(폭 1): toLetterboxScan/toPillarboxScan 둘 다 `w/2 - 1 == -1` 경로를 탄다.
        // Nx1(높이 1): toPillarboxScan 의 `h/2 - 1 == -1` 경로(edgeMarginPct)를 탄다.
        // 어느 쪽도 예외 없이 최소 1개 entry 를 내야 한다.
        //
        // 손계산(기본 인자):
        //  1x8  toLetterboxScan : x0=0,x1=1, sampledRows=(8+2-1)/2=4 → entries 4,
        //                         scaledWidth=(1/2).toInt()=0 → 1
        //       toPillarboxScan : y0=(8*0.12f).toInt()=0, y1=8, x0=0, x1=1,
        //                         sampledCols=(1-0+2-1)/2=1 → entries 1, scaledHeight=(8/2)=4
        //  8x1  toLetterboxScan : x0=(8*0.05f).toInt()=0, x1=8, sampledRows=(1+2-1)/2=1 → entries 1,
        //                         scaledWidth=(8/2)=4
        //       toPillarboxScan : y0=0, y1=1, x0=0, x1=8, sampledCols=(8-0+2-1)/2=4 → entries 4,
        //                         scaledHeight=(1/2).toInt()=0 → 1
        val tall = solidBitmap(width = 1, height = 8, color = Color.BLACK)
        val wide = solidBitmap(width = 8, height = 1, color = Color.BLACK)

        val tallRow = tall.toLetterboxScan()
        val tallCol = tall.toPillarboxScan()
        val wideRow = wide.toLetterboxScan()
        val wideCol = wide.toPillarboxScan()

        assertEquals("1x8 행 스캔 entries", 4, tallRow.height)
        assertEquals("1x8 행 스캔 scaledWidth", 1, tallRow.width)
        assertEquals("1x8 열 스캔 entries", 1, tallCol.height)
        assertEquals("1x8 열 스캔 scaledHeight", 4, tallCol.width)
        assertEquals("8x1 행 스캔 entries", 1, wideRow.height)
        assertEquals("8x1 행 스캔 scaledWidth", 4, wideRow.width)
        assertEquals("8x1 열 스캔 entries", 4, wideCol.height)
        assertEquals("8x1 열 스캔 scaledHeight", 1, wideCol.width)
    }

    // ── 8. toPillarboxScan 세로 마진 격리 — W7-B/1-A 인덱스 매핑 직격 ──

    @Test
    fun `toPillarboxScan excludes pixels outside the vertical margin band`() {
        // W7-B/1-A 는 `getPixels` 로 읽는 세로 범위를 열 전체(0..h)에서 실제 소비 구간
        // `y0 until y1` 로 줄이고 버퍼 인덱스에 `-y0` 오프셋을 붙였다. 그 매핑이 어긋나면
        // (오프셋 누락 → AIOOBE, 시작 y 누락 → 마진 픽셀 혼입, off-by-one → 이웃 행 샘플링)
        // 반드시 여기서 잡힌다.
        //
        // ── 입력 ───────────────────────────────────────────────
        // 20x100, 마진 밴드(y<12, y>=88)=순흑(luma 0), 내부(12..87)=회색 램프 RGB(y,y,y)→luma==y.
        //
        // ── 기본 인자로부터의 경계 손계산 ──────────────────────
        //  edgeMarginPct=0.12f → y0 = (100 * 0.12f).toInt()
        //    0.12f 의 정확값 = 16106127/2^27 = 11.99999973177909851…
        //    100 배의 정확값 = 11.9999997317790985…, 12.0 과의 거리 2.68e-7 <
        //    12 근방 ulp(2^-20 ≈ 9.54e-7)의 절반(4.77e-7) → float 반올림 결과는 **정확히 12.0f**
        //    → y0 = 12
        //  y1 = (100 - 12).coerceAtLeast(13) = 88
        //  sideMarginPct=0.005f → x0 = (20 * 0.005f).toInt() = (0.099999994f).toInt() = 0
        //  x1 = (20 - 0).coerceAtLeast(1) = 20
        //  sampledCols = (20 - 0 + 2 - 1) / 2 = 21 / 2 = 10  (colStride=2 → x=0,2,…,18)
        //  scaledHeight = (100 / 2).toInt() = 50
        //
        // ── 한 열의 샘플(모든 열이 동일 패턴) ──────────────────
        //  rowStride=8, y = 12,20,28,36,44,52,60,68,76,84  (y<88) → counted = 10
        //  luma = y 이므로 표본 = {12,20,28,36,44,52,60,68,76,84}
        //  dark(luma <= 24) = {12, 20} → 2개 → rowDarkRatio = 2/10 = 0.2
        //  Σluma = (12+84)*10/2 = 480 → rowMeanLuma = 480/10 = 48.0
        //  Σluma² = 144+400+784+1296+1936+2704+3600+4624+5776+7056 = 28320
        //  E[X²] = 2832.0 → 분산 = 2832.0 - 48.0² = 2832.0 - 2304.0 = 528.0
        //
        // ── 실패 시 나올 값(대조군) ────────────────────────────
        //  · 마진 혼입(getPixels 를 y=0 부터 읽는 회귀): 표본 = {0,0,16,24,32,40,48,56,64,72}
        //    → dark 4개 → ratio 0.4, mean = 352/10 = 35.2  (셋 다 어긋난다)
        //  · off-by-one(`colBuf[y - y0 + 1]`): 표본 = {13,21,…,85} → mean 49.0 (ratio·분산은 동일)
        //  · 오프셋 누락(`colBuf[y]`): 버퍼 크기 76 < y=84 → ArrayIndexOutOfBoundsException
        val bitmap = verticalRampBitmap(width = 20, height = 100, interiorTop = 12, interiorBottom = 88)

        val scan = bitmap.toPillarboxScan()

        assertEquals("entries(= 샘플링된 열 개수)", 10, scan.height)
        assertEquals("scaledHeight", 50, scan.width)
        assertNotNull("rowMeanLuma 가 채워져야 한다", scan.rowMeanLuma)
        assertNotNull("rowLumaVariance 가 채워져야 한다", scan.rowLumaVariance)
        for (i in 0 until scan.height) {
            assertEquals("col[$i] darkRatio", 0.2f, scan.rowDarkRatio[i], 0.0001f)
            assertEquals("col[$i] meanLuma", 48.0f, scan.rowMeanLuma!![i], 0.0001f)
            assertEquals("col[$i] lumaVariance", 528.0f, scan.rowLumaVariance!![i], 0.01f)
        }
    }

    // ── 9. 음수 마진 비율 거부 — W7-C/수정 2 회귀 가드 ─────────────

    @Test
    fun `negative margin percentages are rejected instead of silently clamped`() {
        // W7-B 가 `coerceIn(0, n/2-1)` 를 `coerceAtMost(...)` 로 바꾸면서 **하한 0 방어가
        // 사라졌다**. 음수 pct 가 들어오면 x0/y0 가 음수가 되어 `getPixels` 가 진단하기 어려운
        // 지점에서 IllegalArgumentException 을 던진다. W7-C 는 이를 클램프가 아니라 진입부
        // `require` 로 막는다 — 음수 마진은 정상 입력이 아니라 호출자의 계산 오류이므로 조용히
        // 0 으로 뭉개면 안 된다(CLAUDE.md "조용한 실패 금지").
        //
        // 여기서 잡히는 회귀: require 를 지우거나 `>= 0f` 를 `<= 0f` 등으로 뒤집는 변경.
        val bitmap = solidBitmap(width = 16, height = 16, color = Color.BLACK)

        assertThrows(IllegalArgumentException::class.java) {
            bitmap.toLetterboxScan(sideMarginPct = -0.01f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            bitmap.toPillarboxScan(edgeMarginPct = -0.01f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            bitmap.toPillarboxScan(sideMarginPct = -0.01f)
        }

        // 대조군: 0f 는 유효한 입력이므로 통과해야 한다(경계가 `> 0` 로 잘못 좁혀지면 여기서 실패).
        assertEquals(8, bitmap.toLetterboxScan(sideMarginPct = 0f).height)
        assertEquals(8, bitmap.toPillarboxScan(edgeMarginPct = 0f, sideMarginPct = 0f).height)
    }
}
