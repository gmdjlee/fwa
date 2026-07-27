package dev.dj.foldwindow.domain

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙).
 *
 * P3-5: 갤럭시 Z Fold 를 노트북 자세(반접힘, 힌지 수평)로 접었을 때를 감지해 자동 상단 배치를
 * 트리거하기 위한 순수 정책. platform/FoldStateMonitor 가 androidx.window FoldingFeature 를
 * [FoldPosture] 로, platform/HingeAngleMonitor 가 Sensor.TYPE_HINGE_ANGLE 을 각도(도)로 사상해 이
 * 클래스에 먹인다 — 이 파일 자체는 androidx.window 도 SensorManager 도 몰라야 한다.
 *
 * ADR-2 준수: 시간은 이 클래스 밖에서 얻어 nowMs 인자로만 받는다 (ArrangeStateMachine 과 동일
 * 패턴). [stabilityMs] 는 "고정 지연으로 성공을 가정"하는 것이 아니다 — 서비스 쪽이 예약 시각에
 * delay 로 도달한 뒤에도 [shouldTriggerNow] 로 자세 유지 여부와 힌지 각도 정지 여부를 실제로
 * 재검증한다. delay 는 시각을 맞추는 수단일 뿐 성공 판정은 항상 조건 재확인이 담당한다.
 *
 * 스레딩: 이 클래스의 모든 public 메서드는 서비스 메인 스레드(Dispatchers.Main.immediate)에서만
 * 호출된다고 가정한다. 내부 상태 변이에 동기화가 없는 이유다 — 다른 스레드에서 호출하면 안 된다.
 * (SensorEventListener 콜백도 기본 핸들러 = 메인 루퍼에서 도착하므로 이 계약을 만족한다.)
 */

/** 폴딩 자세. platform/FoldStateMonitor 가 androidx.window FoldingFeature 를 이 값으로 사상한다 */
enum class FoldPosture { UNKNOWN, FLAT, HALF_OPENED_HORIZONTAL, HALF_OPENED_VERTICAL }

/**
 * 노트북 자세([FoldPosture.HALF_OPENED_HORIZONTAL]) 진입을 감지하고, 다음 두 조건을 **모두**
 * 만족해야만 발화 가능([shouldTriggerNow] == true) 상태가 되는 정책이다.
 *
 * 1. **시간 디바운스** — 진입 후 [stabilityMs] 동안 그 자세가 끊김 없이 유지될 것.
 * 2. **힌지 각도 안정성**([isAngleStable]) — 각도가 노트북 대역 안에서 *정지*했을 것.
 *
 * 플렉스 진입 1회당 발화는 최대 1회다 — [shouldTriggerNow] 가 true 를 반환하는 순간 내부 arm 을
 * 소모하고, 다음 발화는 이 자세를 완전히 벗어났다가 다시 진입해야만(재접기) 가능하다.
 * 각도 불안정으로 발화가 보류된 경우에는 arm 을 소모하지 **않는다** — 호출자가 조건 폴링으로
 * 재확인하면 되고, 자세 이탈([onPosture])이 폴링의 자연 종료 조건이다.
 *
 * @param stabilityMs 자세 진입 후 발화 가능까지 요구하는 최소 유지 시간.
 *   [DEFAULT_STABILITY_MS](800ms). **[실기기 반증, 2026-07-27]** 원래 근거는 "기기를 완전히 닫는
 *   동작의 HALF_OPENED 통과가 수백 ms 미만"이라는 가정이었으나, Fold 7/One UI 8 실측에서 완전
 *   닫기가 HALF_OPENED 대역을 **~2초**(2표본: 2.1s, 1.95s) 체류하며 통과함이 확인됐다 — 800ms
 *   디바운스가 닫는 도중 만료돼 자동 배치가 오발화했다. 상수 증액은 느린 닫기 꼬리에 다시 뚫리는
 *   타이밍 도박이므로 채택하지 않고, 속도와 무관한 조건 신호(각도 정지)를 게이트로 추가했다.
 *   시간 디바운스는 그 각도 게이트의 1차 필터로 남는다.
 */
class FlexModePolicy(private val stabilityMs: Long = DEFAULT_STABILITY_MS) {

    companion object {
        const val DEFAULT_STABILITY_MS = 800L

        // ── 힌지 각도 안정성 게이트 상수 ─────────────────────────────
        //
        // 근거: 완전 닫기 동작의 HALF_OPENED 대역 체류 실측 ~2s (2026-07-27, 2표본 2.1s/1.95s)
        // — 시간 디바운스 단독으론 "닫는 중"과 "노트북 자세로 거치됨"을 구분할 수 없다. 각도 정지가
        // 유일하게 속도-무관한 신호다(닫는 중에는 각도가 계속 변하고, 거치되면 변화가 멎는다).
        // 대역 경계값(45/135)은 아직 [실기기 미검증] — logcat FWHingeAngleMonitor 태그의 각도 로그로
        // 실제 노트북 자세 각도를 확인한 뒤 docs/DEVICE_FACTS.md 에 근거와 함께 조정할 것.

        /** 노트북 자세로 인정할 힌지 각도 하한(도). 관대하게 잡는다 — 거치 각도는 사용자마다 다르다 */
        const val ANGLE_MIN_DEG = 45f

        /** 노트북 자세로 인정할 힌지 각도 상한(도). 이 위는 사실상 펼친 상태로 본다 */
        const val ANGLE_MAX_DEG = 135f

        /**
         * 이 시간만큼 새 샘플이 없으면 "각도가 멎었다"고 판정한다. TYPE_HINGE_ANGLE 은 on-change
         * 센서라 값이 변하지 않으면 방출 자체가 멈춘다 — 샘플 부재 기간이 곧 정지의 증거다.
         */
        const val ANGLE_QUIET_MS = 600L

        /** 저분산(미세 떨림) 판정에 쓰는 트레일링 윈도 길이 */
        const val ANGLE_WINDOW_MS = 600L

        /** 트레일링 윈도 안에서 허용하는 최대 각도 스프레드(max-min, 도). 이보다 크면 "움직이는 중" */
        const val ANGLE_SPREAD_MAX_DEG = 8f

        /**
         * 각도 샘플 보관 상한(방어적). 정상 경로에서는 [ANGLE_WINDOW_MS] 트레일링 정리만으로 크기가
         * 묶이지만, 센서가 예상보다 촘촘히 방출해도 장기 실행 서비스에서 무한히 자라지 않게 한다.
         */
        private const val MAX_ANGLE_SAMPLES = 64
    }

    /** 힌지 각도 샘플 하나. [atMs] 는 [onPosture]/[shouldTriggerNow] 와 **동일한 시계**여야 한다 */
    private data class AngleSample(val angleDeg: Float, val atMs: Long)

    /** 마지막으로 보고된 자세. placement 체인(FLEX 티어)이 안정화 없이 순간값을 그대로 참조한다 */
    var posture: FoldPosture = FoldPosture.UNKNOWN
        private set

    /** 현재(또는 가장 최근) HALF_OPENED_HORIZONTAL 연속 구간의 최초 진입 시각. 그 구간 밖이면 null */
    private var enteredFlexAtMs: Long? = null

    /** 이번 진입에서 아직 발화하지 않았고 게이트에 의해 거부되지도 않았는지 여부 */
    private var armed: Boolean = false

    /**
     * 이번 플렉스 진입 구간이 아직 살아 있는지(발화·거부·이탈 전인지). 서비스의 각도 조건 폴링이
     * "계속 폴링할 이유가 남았는가"를 판정하는 종료 조건으로 쓴다 — false 면 폴링을 끝내야 한다.
     */
    val isArmed: Boolean get() = armed

    /** 트레일링 각도 샘플(오래된 것이 앞). [onHingeAngle] 이 윈도 밖 샘플을 정리한다 */
    private val angleSamples = ArrayDeque<AngleSample>()

    /**
     * 이 기기에서 힌지 각도 신호가 실재함이 증명됐는지. 한 번 true 가 되면 되돌리지 않는다 —
     * 센서 유무는 기기 속성이지 세션 속성이 아니다. false 인 동안 각도 게이트는 통과로 취급한다
     * (힌지 센서가 없는/못 읽는 기기에서 기능 전체가 죽지 않게 하는 폴백 — 종전 디바운스 단독
     * 의미론으로 격하된다).
     */
    private var angleSignalSeen: Boolean = false

    /**
     * 새 자세 보고를 흡수한다. [posture] 는 호출 즉시 갱신된다.
     *
     * @return HALF_OPENED_HORIZONTAL 로 "새로 진입"했을 때만(직전 [posture] 가 그것이 아니었을 때)
     *   안정성 확인을 예약할 시각([nowMs] + [stabilityMs])을 반환한다. 그 외 — 동일 자세의 중복
     *   보고(androidx.window 가 같은 레이아웃을 재방출할 수 있다), 다른 자세로의 이탈, 애초에
     *   HALF_OPENED_HORIZONTAL 이 아닌 보고 — 에는 null.
     *
     *   동일 자세 중복 보고는 진입 시각을 갱신하지 않는다 — [shouldTriggerNow] 의 안정성 판정은
     *   항상 최초 진입 시각 기준이어야 디바운스가 의미를 갖는다. 중복 보고가 각도 히스토리를
     *   지우지도 않는다(진입 구간 내내 누적돼야 정지 판정이 가능하다).
     */
    fun onPosture(new: FoldPosture, nowMs: Long): Long? {
        val previous = posture
        posture = new

        if (new != FoldPosture.HALF_OPENED_HORIZONTAL) {
            // 플렉스 이탈(또는 애초에 플렉스가 아님). 재무장의 유일한 경로는 다음 "진입"이므로
            // 여기서 진입 시각/arm 을 완전히 지운다. 각도 히스토리도 함께 버린다 — 이탈 전의
            // 스테일 샘플이 다음 진입의 정지 판정에 끼면(예: 이탈 직전 대역 안 값이 남아 있으면)
            // 재진입 즉시 "침묵 = 정지"로 오판정된다.
            enteredFlexAtMs = null
            armed = false
            angleSamples.clear()
            return null
        }

        return if (previous != FoldPosture.HALF_OPENED_HORIZONTAL) {
            // 새 진입 — 진입 시각을 새로 찍고 재무장한다. (직전 비-플렉스 보고가 이미 히스토리를
            // 비웠으므로 여기서 다시 지울 필요는 없다.)
            enteredFlexAtMs = nowMs
            armed = true
            nowMs + stabilityMs
        } else {
            // 동일 자세 중복 보고 — 최초 진입 시각을 그대로 둔 채 스케줄하지 않는다.
            null
        }
    }

    /**
     * 힌지 각도 샘플 하나를 흡수한다(platform/HingeAngleMonitor → SensorManager TYPE_HINGE_ANGLE).
     * 호출 자체가 "이 기기엔 각도 신호가 있다"는 증거라 [angleSignalSeen] 을 세운다.
     */
    fun onHingeAngle(angleDeg: Float, nowMs: Long) {
        angleSignalSeen = true
        angleSamples.addLast(AngleSample(angleDeg, nowMs))

        // 트레일링 윈도 밖 샘플 정리. 방금 넣은 샘플은 나이 0이라 항상 살아남는다 — 정지 분기가
        // "마지막 샘플"을 필요로 하므로 최소 1개는 반드시 보존해야 한다.
        while (angleSamples.size > 1 && nowMs - angleSamples.first().atMs > ANGLE_WINDOW_MS) {
            angleSamples.removeFirst()
        }
        while (angleSamples.size > MAX_ANGLE_SAMPLES) {
            angleSamples.removeFirst()
        }
    }

    /**
     * 힌지 각도가 "노트북 대역 안에서 멎었는가". 아래 둘 중 하나면 true.
     *
     * - **정지 분기**: 마지막 샘플이 대역([ANGLE_MIN_DEG]..[ANGLE_MAX_DEG]) 안이고 그 뒤로
     *   [ANGLE_QUIET_MS] 이상 새 샘플이 없다. on-change 센서에서 샘플 부재 = 각도 무변화.
     * - **저분산 분기**: 트레일링 [ANGLE_WINDOW_MS] 안에 샘플이 2개 이상 있고, 전부 대역 안이며,
     *   스프레드(max-min)가 [ANGLE_SPREAD_MAX_DEG] 이하다(손 떨림/센서 노이즈 흡수).
     *
     * 샘플이 한 번도 안 들어온 기기(=힌지 센서 없음/구독 실패)에서는 항상 true 를 반환해 게이트를
     * 무력화한다 — 기능 전체를 죽이는 대신 종전 디바운스 단독 의미론으로 격하되는 쪽을 택한다.
     * 반대로, 신호가 있었던 기기에서 히스토리가 비어 있으면(이탈 직후 등) false 다 — 증거 없이
     * 발화하지 않는다.
     *
     * [알려진 잔여] 극단적으로 느린 닫기(≈13°/s 미만, 즉 600ms 동안 8° 미만 변화)는 저분산 분기를
     * 통과할 수 있다. 실측된 닫기 동작(~45°/s, 2초에 90°)과는 3배 이상 차이가 나 현실적 위험은
     * 낮다고 보고 v1 에서는 허용한다.
     */
    fun isAngleStable(nowMs: Long): Boolean {
        if (!angleSignalSeen) return true

        val last = angleSamples.lastOrNull() ?: return false
        if (last.angleDeg.inLaptopBand() && nowMs - last.atMs >= ANGLE_QUIET_MS) return true

        var count = 0
        var min = Float.MAX_VALUE
        var max = -Float.MAX_VALUE
        for (sample in angleSamples) {
            if (nowMs - sample.atMs > ANGLE_WINDOW_MS) continue
            if (!sample.angleDeg.inLaptopBand()) return false
            count++
            if (sample.angleDeg < min) min = sample.angleDeg
            if (sample.angleDeg > max) max = sample.angleDeg
        }
        return count >= 2 && (max - min) <= ANGLE_SPREAD_MAX_DEG
    }

    /** 대역 판정. NaN 은 어떤 범위에도 속하지 않으므로 자동으로 "대역 밖"(=불안정)으로 처리된다 */
    private fun Float.inLaptopBand(): Boolean = this in ANGLE_MIN_DEG..ANGLE_MAX_DEG

    /**
     * [onPosture] 가 예약한 시각 이후 서비스가 호출해 실제 발화 여부를 재확인한다.
     * ADR-2: 이 함수 자체가 "조건 재검증"이다 — 서비스의 delay 는 이 시각에 도달하기 위한 수단일
     * 뿐, 성공을 가정하지 않는다(자세가 그새 바뀌었거나 아직 접히는 중일 수 있다).
     *
     * 최종 조건 = 진입 이력 존재 ∧ 자세 유지 ∧ armed ∧ 디바운스 경과 ∧ [isAngleStable].
     *
     * true 를 반환하면 그 순간 arm 을 소모(false)한다 — 동일 진입 구간에서 두 번 발화하지 않는다.
     * **각도 불안정으로 false 인 경우 arm 은 소모되지 않는다** — 호출자는 자세가 유지되는 동안
     * ([isArmed] 가 true 인 동안) 주기적으로 다시 물어보면 된다.
     */
    fun shouldTriggerNow(nowMs: Long): Boolean {
        val enteredAt = enteredFlexAtMs ?: return false
        if (posture != FoldPosture.HALF_OPENED_HORIZONTAL || !armed) return false
        if (nowMs - enteredAt < stabilityMs) return false
        if (!isAngleStable(nowMs)) return false
        armed = false
        return true
    }

    /**
     * 서비스가 자동 트리거 게이트(예: 세션 진행 중, 분할 이미 활성 등)에서 거부했을 때 호출한다.
     * 이번 플렉스 진입 구간에서는 더 이상 재발화하지 않는다 — 재무장은 자세를 벗어났다가 다시
     * HALF_OPENED_HORIZONTAL 로 진입하는 것(재접기)만이 유일한 경로다.
     *
     * 각도 히스토리도 함께 버린다 — 거부 이후에는 센서 구독이 끊기므로(서비스가 배터리 위생상
     * 해제한다) 남아 있는 샘플은 스테일이 될 뿐이다.
     */
    fun disarm() {
        armed = false
        angleSamples.clear()
    }
}
