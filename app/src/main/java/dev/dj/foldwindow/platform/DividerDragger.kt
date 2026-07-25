package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import dev.dj.foldwindow.domain.IntRect
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * 디바이더 드래그 제스처 전략.
 *
 * [실기기 확인, 2026-07-25] `input swipe 1092 984 1092 1235 500` (단일 스트로크) 만으로
 * 디바이더가 정확히 목표 위치까지 이동해 검은 띠가 완전히 제거됐다 — SINGLE_STROKE 는 홀드가
 * 전혀 필요 없음이 실증됐다. 그래서 기본 전략을 SINGLE_STROKE 로 승격한다.
 * HOLD_THEN_MOVE 는 PROGRESS.md 미해결 항목 #2(실기기 비교)의 잔재 경로로 유지하되,
 * 이제는 [holdThenDrag] 를 통해 두 번의 `dispatchGesture` 로 올바르게 구현된다(함정 #4).
 */
enum class DragStrategy { SINGLE_STROKE, HOLD_THEN_MOVE }

/**
 * 분할 화면 디바이더를 `dispatchGesture` 로 드래그한다.
 *
 * [측정] DEVICE_FACTS.md 함정: 디바이더가 최소 스냅에 걸리면 앱 창은 리사이즈가 아니라
 * 화면 밖으로 슬라이드된다. 목표 Y 자체는 `SplitPlanner`/`minPaneHeight` 가 이미 클램프하므로
 * 여기서는 핸들 크기만큼의 화면 경계 여유만 추가로 보정한다.
 *
 * ADR-2 준수: 여기서는 고정 지연을 쓰지 않는다 — 제스처 지속시간(duration)은 애니메이션을
 * "재생"하기 위한 파라미터일 뿐, 완료 판정은 항상 `GestureResultCallback` 을 통해서만 이뤄진다.
 */
class DividerDragger(private val service: AccessibilityService) {

    /**
     * 핸들 중심에서 (같은 X, targetY) 까지 드래그. 결과는 콜백으로.
     * completed=false 는 제스처 취소/디스패치 실패.
     */
    fun drag(
        handle: DividerHandle,
        targetY: Int,
        screen: IntRect,
        strategy: DragStrategy = DragStrategy.SINGLE_STROKE,
        onResult: (completed: Boolean) -> Unit,
    ) {
        val clampedTargetY = targetY.coerceIn(screen.top + EDGE_MARGIN_PX, screen.bottom - EDGE_MARGIN_PX)
        val distancePx = abs(clampedTargetY - handle.centerY)

        when (strategy) {
            DragStrategy.SINGLE_STROKE -> {
                val gesture = buildSingleStrokeGesture(handle, clampedTargetY, distancePx)
                dispatchSingle(gesture, strategy, clampedTargetY, onResult)
            }

            DragStrategy.HOLD_THEN_MOVE -> {
                val moveDuration = scaledDuration(distancePx, baseMs = 0L)
                holdThenDrag(
                    service = service,
                    fromX = handle.centerX,
                    fromY = handle.centerY,
                    toX = handle.centerX,
                    toY = clampedTargetY,
                    holdMs = HOLD_DURATION_MS,
                    moveMs = moveDuration,
                ) { completed ->
                    Log.i(TAG, "drag(HOLD_THEN_MOVE) result=$completed targetY=$clampedTargetY")
                    onResult(completed)
                }
            }
        }
    }

    private fun dispatchSingle(
        gesture: GestureDescription,
        strategy: DragStrategy,
        targetY: Int,
        onResult: (completed: Boolean) -> Unit,
    ) {
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.i(TAG, "drag completed strategy=$strategy targetY=$targetY")
                onResult(true)
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "drag cancelled strategy=$strategy targetY=$targetY")
                onResult(false)
            }
        }

        val dispatched = runCatching { service.dispatchGesture(gesture, callback, null) }
            .getOrElse {
                Log.e(TAG, "dispatchGesture threw", it)
                false
            }

        if (!dispatched) {
            Log.w(TAG, "dispatchGesture returned false immediately (strategy=$strategy)")
            onResult(false)
        }
    }

    private fun buildSingleStrokeGesture(handle: DividerHandle, targetY: Int, distancePx: Int): GestureDescription {
        val duration = scaledDuration(distancePx, baseMs = SINGLE_STROKE_BASE_MS)
        val path = Path().apply {
            moveTo(handle.centerX.toFloat(), handle.centerY.toFloat())
            lineTo(handle.centerX.toFloat(), targetY.toFloat())
        }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
    }

    /** 이동 거리에 비례해 지속시간을 늘리되 [MIN_MOVE_DURATION_MS, MAX_MOVE_DURATION_MS] 로 클램프 */
    private fun scaledDuration(distancePx: Int, baseMs: Long): Long =
        (baseMs + distancePx * DURATION_PER_PX_MS)
            .roundToLong()
            .coerceIn(MIN_MOVE_DURATION_MS, MAX_MOVE_DURATION_MS)

    companion object {
        private const val TAG = "FWDividerDragger"

        /** 핸들 반높이(34px, DEVICE_FACTS 68x221 핸들 기준) + 여유 6px */
        private const val EDGE_MARGIN_PX = 40

        private const val HOLD_DURATION_MS = 180L
        private const val SINGLE_STROKE_BASE_MS = 400L
        private const val DURATION_PER_PX_MS = 0.3
        private const val MIN_MOVE_DURATION_MS = 300L
        private const val MAX_MOVE_DURATION_MS = 800L
    }
}
