package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log

private const val TAG = "FWGestureDrags"

/**
 * 홀드 후 드래그. [측정] `input draganddrop` 상당 — 롱프레스로 집은 뒤 이동.
 *
 * [실기기 확인, 2026-07-25] `StrokeDescription.continueStroke` 는 반드시 **별도의**
 * `dispatchGesture` 호출로 나눠야 한다. 최초 스트로크(willContinue=true)와 그 연속 스트로크를
 * 하나의 `GestureDescription` 에 함께 넣어 한 번에 디스패치하면, 시스템이 8ms 만에 가짜
 * `onCompleted` 를 보고하고 실제로는 아무것도 움직이지 않는다 (`DividerDragger` 의 구 구현이
 * 이 함정에 걸렸었다).
 *
 * [측정, 2026-07-25] 길이 0 경로(`moveTo(p); lineTo(p)`)의 continueStroke 는 시스템이
 * 거부한다 — 3단계 구현에서 P2(hold-in-place) 가 디스패치 +6ms 만에 cancelled 되었다.
 * 그래서 홀드 스트로크는 1px 드리프트(`lineTo(fromX+1, fromY)`)를 준다. 터치 슬롭보다
 * 한참 작아 롱프레스 인식에는 영향이 없다.
 *
 * [측정, 2026-07-25] willContinue=true 스트로크의 `onCompleted` 는 "수락/큐 적재" 를
 * 뜻할 뿐 "주입 완료" 가 아니다 — 디스패치 후 ~4ms 만에 도착하고, 실제 주입은 duration
 * 전체 동안 재생된다 (600ms move 단계가 벽시계 605ms 로 측정됨). 큐에 들어간 연속
 * 스트로크는 앞 스트로크의 전체 duration 이후 순차 재생된다. 그래도 S2 디스패치 전에
 * S1 디스패치 시점 기준 경과 시간이 holdMs 에 못 미치면 나머지를 대기한다(값싼 보험).
 *
 * ADR-2 준수: duration(holdMs/moveMs)과 위 타이밍 보정 대기는 제스처 "재생" 파라미터일 뿐이다.
 * 완료 판정은 각 단계의 `GestureResultCallback` 으로만 이뤄진다.
 *
 * @param onResult 정확히 한 번만 호출된다. true = 2단계(S2 move)까지 정상 완료.
 *                 false = 어느 단계에서든 취소(onCancelled) 되었거나 dispatchGesture() 가
 *                 즉시 false 를 반환(디스패치 자체 거부)한 경우.
 */
fun holdThenDrag(
    service: AccessibilityService,
    fromX: Int,
    fromY: Int,
    toX: Int,
    toY: Int,
    holdMs: Long,
    moveMs: Long,
    onResult: (completed: Boolean) -> Unit,
) {
    var resultDelivered = false
    fun deliver(completed: Boolean) {
        if (resultDelivered) return
        resultDelivered = true
        onResult(completed)
    }

    val fx = fromX.toFloat()
    val fy = fromY.toFloat()

    // S1 디스패치 시점. 각 단계 로그와 S2 타이밍 보정의 기준점이다.
    var s1DispatchUptime = 0L
    fun elapsed(): Long = SystemClock.uptimeMillis() - s1DispatchUptime

    fun dispatchPhase(
        phase: String,
        gesture: GestureDescription,
        onPhaseCompleted: () -> Unit,
        isLast: Boolean,
    ) {
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.i(TAG, "holdThenDrag: $phase completed (+${elapsed()}ms since S1 dispatch)")
                if (isLast) deliver(true) else onPhaseCompleted()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "holdThenDrag: $phase cancelled (+${elapsed()}ms since S1 dispatch)")
                deliver(false)
            }
        }
        val dispatched = runCatching { service.dispatchGesture(gesture, callback, null) }
            .getOrElse {
                Log.e(TAG, "holdThenDrag: $phase dispatchGesture threw", it)
                false
            }
        if (!dispatched) {
            Log.w(TAG, "holdThenDrag: $phase dispatchGesture returned false immediately")
            deliver(false)
        }
    }

    // ── S1: hold (1px 드리프트 — 길이 0 경로는 시스템이 거부, willContinue=true) ──
    val holdPath = Path().apply {
        moveTo(fx, fy)
        lineTo(fx + 1f, fy)
    }
    val holdStroke = GestureDescription.StrokeDescription(
        holdPath,
        /* startTime = */ 0L,
        holdMs,
        /* willContinue = */ true,
    )

    // ── S2: move (S1 종점(fromX+1, fromY)→to 드래그, willContinue=false) ──
    fun dispatchS2() {
        val movePath = Path().apply {
            moveTo(fx + 1f, fy)
            lineTo(toX.toFloat(), toY.toFloat())
        }
        val moveStroke = holdStroke.continueStroke(
            movePath,
            /* startTime = */ 0L,
            moveMs,
            /* willContinue = */ false,
        )
        val moveGesture = GestureDescription.Builder().addStroke(moveStroke).build()
        Log.i(TAG, "holdThenDrag: dispatching S2(move) -> ($toX,$toY) (+${elapsed()}ms since S1 dispatch)")
        dispatchPhase("S2(move)", moveGesture, onPhaseCompleted = {}, isLast = true)
    }

    fun onS1Completed() {
        // 제스처 재생 타이밍 보정이다 — willContinue 스트로크의 onCompleted 는 "큐 적재" 의미라
        // duration 을 기다리지 않고 ~4ms 만에 오는 것을 실기기에서 확인(2026-07-25). 큐에 들어간
        // 연속 스트로크는 어차피 앞 스트로크 duration 이후 순차 재생되지만, 값싼 보험으로 대기를
        // 유지한다. ADR-2 가 금지하는 것은 상태 전이를 고정 지연으로 대신하는 것이고, 이 대기는
        // input draganddrop 의 롱프레스 인식 시간(뷰 시스템 요구사항)을 재현하는 제스처
        // 파라미터다. 완료 판정은 여전히 콜백으로만 한다.
        val elapsedMs = elapsed()
        if (elapsedMs < holdMs) {
            val remaining = holdMs - elapsedMs
            Log.i(TAG, "holdThenDrag: timing guard — waiting ${remaining}ms to honor holdMs=$holdMs (+${elapsedMs}ms since S1 dispatch)")
            Handler(Looper.getMainLooper()).postDelayed({ dispatchS2() }, remaining)
        } else {
            dispatchS2()
        }
    }

    val holdGesture = GestureDescription.Builder().addStroke(holdStroke).build()
    s1DispatchUptime = SystemClock.uptimeMillis()
    Log.i(TAG, "holdThenDrag: dispatching S1(hold) at ($fromX,$fromY) holdMs=$holdMs moveMs=$moveMs")
    dispatchPhase("S1(hold)", holdGesture, onPhaseCompleted = ::onS1Completed, isLast = false)
}
