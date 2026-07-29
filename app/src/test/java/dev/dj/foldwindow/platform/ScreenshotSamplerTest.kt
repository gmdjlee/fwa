package dev.dj.foldwindow.platform

import android.graphics.Bitmap
import android.graphics.Color
import dev.dj.foldwindow.domain.LetterboxDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        // 없이 동작해야 한다. w=1(또는 h=1) 은 coerceIn(0,-1) 로 예외가 나는 별개의 잠재 결함이며
        // W4 범위 밖이다 — 여기서 고치지 않고 최종 보고에만 남긴다(trap #8).
        for (size in intArrayOf(4, 2)) {
            val bitmap = horizontalBandsBitmap(width = size, height = size, topBarPx = 1, bottomBarPx = 1)

            val rowScan = bitmap.toLetterboxScan()
            val colScan = bitmap.toPillarboxScan()

            assertTrue("size=$size 행 스캔이 비어 있음", rowScan.height > 0)
            assertTrue("size=$size 열 스캔이 비어 있음", colScan.height > 0)
        }
    }
}
