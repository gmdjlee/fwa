package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log

/**
 * P3-5 보강: `Sensor.TYPE_HINGE_ANGLE`(API 30+) 구독 래퍼. 각도 샘플을 도(deg) 단위 실수와
 * 타임스탬프로 사상해 [dev.dj.foldwindow.domain.FlexModePolicy.onHingeAngle] 에 먹인다 —
 * domain 은 SensorManager 를 몰라야 하므로(CLAUDE.md 아키텍처 규칙) 센서 접근은 여기에만 있다.
 *
 * **왜 필요한가 [실기기 물증, 2026-07-27 / Fold 7 · One UI 8]**: 기기를 완전히 닫는 동작이
 * HALF_OPENED 대역을 ~2초(3표본 2.1s/1.95s/1.2s) 체류하며 통과해 `FlexModePolicy` 의 800ms 시간
 * 디바운스가 닫는 도중 만료됐고, 그 결과 닫힌 기기에서 자동 배치가 오발화했다(→ Recents 진입
 * 실패 `ENTRY_STEP_FAILED`). 디바운스 상수 증액은 느린 닫기 꼬리에 다시 뚫리는 타이밍 도박이라
 * 기각하고(ADR-2 정신), 속도와 무관한 조건 신호 — "각도가 노트북 대역 안에서 **멎었는가**" — 를
 * 게이트로 추가했다. TYPE_HINGE_ANGLE 은 on-change 센서라 각도가 변하지 않으면 방출이 멈춘다:
 * **샘플 부재 자체가 정지의 증거**다(판정은 domain 쪽 `FlexModePolicy.isAngleStable`).
 *
 * **시계 계약**: 콜백에 넘기는 시각은 [SystemClock.uptimeMillis] 다. `ArrangerAccessibilityService`
 * 가 `FlexModePolicy.onPosture`/`shouldTriggerNow` 에 넘기는 시계와 반드시 같아야 정지 판정
 * (`now - 마지막샘플시각`)이 성립한다 — 서비스 전역이 uptimeMillis 를 쓰므로 여기서도 uptimeMillis
 * 다(`SensorEvent.timestamp` 는 elapsedRealtime 나노초 좌표계라 그대로 쓰면 좌표계가 어긋난다).
 *
 * **스레딩**: 핸들러를 넘기지 않는 `registerListener` 는 콜백을 메인 루퍼로 배달한다 —
 * `FlexModePolicy` 의 "메인 스레드 전용" 계약을 그대로 만족한다.
 *
 * 힌지 센서가 없는 기기에서는 [start] 가 로그만 남기고 무동작한다(크래시 금지). 이 경우
 * `FlexModePolicy` 는 각도 샘플을 한 번도 못 받아 각도 게이트를 통과로 취급 — 종전 디바운스 단독
 * 의미론으로 격하된다.
 *
 * [실기기 확인 2026-07-27] Fold 7 이 TYPE_HINGE_ANGLE 을 노출함을 확정 — 도 단위, 노트북 자세 ≈90.0,
 * 완전 닫힘 0.0, on-change 방출(정지 시 침묵). `FlexModePolicy.ANGLE_MIN_DEG..ANGLE_MAX_DEG`
 * (45~135도) 대역은 90.0 실측 기준 여유 충분(경계값 자체는 미실측).
 */
class HingeAngleMonitor(private val service: AccessibilityService) {

    /** 등록된 리스너. null 이면 미구독 상태 — [stop] 의 멱등성과 [start] 재진입 안전성의 기준이다 */
    private var listener: SensorEventListener? = null

    /** "센서 없음" 경고를 매 진입마다 반복하지 않기 위한 1회 플래그 (접었다 펼 때마다 start 가 불린다) */
    private var missingSensorLogged = false

    /**
     * 힌지 각도 구독을 시작한다. 재진입 안전 — 이미 구독 중이면 기존 리스너를 먼저 해제한다.
     *
     * @param onAngle (각도 deg, [SystemClock.uptimeMillis] 시각) 콜백. 메인 스레드에서 호출된다.
     */
    fun start(onAngle: (Float, Long) -> Unit) {
        stop()

        val sensorManager = runCatching { service.getSystemService(SensorManager::class.java) }
            .onFailure { Log.w(TAG, "SensorManager 조회 실패 — 각도 게이트 비활성(기능만 격하)", it) }
            .getOrNull()
        val sensor = sensorManager?.let {
            runCatching { it.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) }
                .onFailure { e -> Log.w(TAG, "TYPE_HINGE_ANGLE 조회 실패", e) }
                .getOrNull()
        }
        if (sensor == null) {
            if (!missingSensorLogged) {
                missingSensorLogged = true
                Log.w(
                    TAG,
                    "TYPE_HINGE_ANGLE 센서 없음 — 각도 안정성 게이트를 켤 수 없다. " +
                        "FlexModePolicy 는 시간 디바운스 단독 의미론으로 격하된다(크래시 아님).",
                )
            }
            return
        }

        val newListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                val angleDeg = event?.values?.firstOrNull() ?: return
                // 실기기 대역 검증(45~135도 가정)을 위한 물증 로그. on-change 센서라 접는 동작
                // 1회당 수~수십 줄이며, 정지 상태에서는 방출 자체가 없어 스팸이 되지 않는다.
                Log.d(TAG, "hinge angle=$angleDeg")
                onAngle(angleDeg, SystemClock.uptimeMillis())
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        val registered = runCatching {
            sensorManager.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }.onFailure { Log.w(TAG, "registerListener 예외", it) }.getOrDefault(false)

        if (registered) {
            listener = newListener
            Log.i(TAG, "hinge angle monitor started")
        } else {
            // 조용한 실패 금지: 구독 실패를 명시적으로 드러낸다. 이 경우 샘플이 오지 않으므로
            // FlexModePolicy 의 각도 게이트는 (샘플을 한 번도 못 받았다면) 통과로 격하된다.
            Log.w(TAG, "registerListener 실패 — 각도 게이트 비활성(기능만 격하)")
        }
    }

    /** 구독을 해제한다. 멱등 — 미구독 상태에서 불러도 안전하다 */
    fun stop() {
        val current = listener ?: return
        listener = null
        runCatching {
            service.getSystemService(SensorManager::class.java)?.unregisterListener(current)
        }.onFailure { Log.w(TAG, "unregisterListener 예외", it) }
        Log.i(TAG, "hinge angle monitor stopped")
    }

    companion object {
        private const val TAG = "FWHingeAngleMonitor"
    }
}
