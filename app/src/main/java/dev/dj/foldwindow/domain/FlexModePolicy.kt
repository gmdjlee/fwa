package dev.dj.foldwindow.domain

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙).
 *
 * P3-5: 갤럭시 Z Fold 를 노트북 자세(반접힘, 힌지 수평)로 접었을 때를 감지해 자동 상단 배치를
 * 트리거하기 위한 순수 디바운스 정책. platform/FoldStateMonitor 가 androidx.window
 * FoldingFeature 를 [FoldPosture] 로 사상해 이 클래스에 먹인다 — 이 파일 자체는 androidx.window 를
 * 몰라야 한다.
 *
 * ADR-2 준수: 시간은 이 클래스 밖에서 얻어 nowMs 인자로만 받는다 (ArrangeStateMachine 과 동일
 * 패턴). [stabilityMs] 는 "고정 지연으로 성공을 가정"하는 것이 아니다 — 서비스 쪽이 예약 시각에
 * delay 로 도달한 뒤에도 [shouldTriggerNow] 로 자세 유지 여부를 실제로 재검증한다. delay 는
 * 시각을 맞추는 수단일 뿐 성공 판정은 항상 조건 재확인이 담당한다.
 *
 * 스레딩: 이 클래스의 모든 public 메서드는 서비스 메인 스레드(Dispatchers.Main.immediate)에서만
 * 호출된다고 가정한다. 내부 상태 변이에 동기화가 없는 이유다 — 다른 스레드에서 호출하면 안 된다.
 */

/** 폴딩 자세. platform/FoldStateMonitor 가 androidx.window FoldingFeature 를 이 값으로 사상한다 */
enum class FoldPosture { UNKNOWN, FLAT, HALF_OPENED_HORIZONTAL, HALF_OPENED_VERTICAL }

/**
 * 노트북 자세([FoldPosture.HALF_OPENED_HORIZONTAL]) 진입을 감지하고, [stabilityMs] 동안 그 자세가
 * 끊김 없이 유지돼야만 발화 가능([shouldTriggerNow] == true) 상태가 되는 디바운스 정책.
 *
 * 플렉스 진입 1회당 발화는 최대 1회다 — [shouldTriggerNow] 가 true 를 반환하는 순간 내부 arm 을
 * 소모하고, 다음 발화는 이 자세를 완전히 벗어났다가 다시 진입해야만(재접기) 가능하다.
 *
 * @param stabilityMs 자세 진입 후 발화 가능까지 요구하는 최소 유지 시간.
 *   [DEFAULT_STABILITY_MS](800ms)의 근거: 기기를 완전히 닫는 동작이 물리적으로 HALF_OPENED 상태를
 *   일시 통과한다 — 이 통과는 수백 ms 미만이고, 의도적으로 노트북 자세로 거치하는 경우는 그보다
 *   오래 유지된다는 가정이다. [실기기 검증 대상] — 실제 통과 시간·오발화 여부는
 *   docs/DEVICE_FACTS.md "P3-5 FoldingFeature (미검증)" 절에 기록한다.
 */
class FlexModePolicy(private val stabilityMs: Long = DEFAULT_STABILITY_MS) {

    companion object {
        const val DEFAULT_STABILITY_MS = 800L
    }

    /** 마지막으로 보고된 자세. placement 체인(FLEX 티어)이 안정화 없이 순간값을 그대로 참조한다 */
    var posture: FoldPosture = FoldPosture.UNKNOWN
        private set

    /** 현재(또는 가장 최근) HALF_OPENED_HORIZONTAL 연속 구간의 최초 진입 시각. 그 구간 밖이면 null */
    private var enteredFlexAtMs: Long? = null

    /** 이번 진입에서 아직 발화하지 않았고 게이트에 의해 거부되지도 않았는지 여부 */
    private var armed: Boolean = false

    /**
     * 새 자세 보고를 흡수한다. [posture] 는 호출 즉시 갱신된다.
     *
     * @return HALF_OPENED_HORIZONTAL 로 "새로 진입"했을 때만(직전 [posture] 가 그것이 아니었을 때)
     *   안정성 확인을 예약할 시각([nowMs] + [stabilityMs])을 반환한다. 그 외 — 동일 자세의 중복
     *   보고(androidx.window 가 같은 레이아웃을 재방출할 수 있다), 다른 자세로의 이탈, 애초에
     *   HALF_OPENED_HORIZONTAL 이 아닌 보고 — 에는 null.
     *
     *   동일 자세 중복 보고는 진입 시각을 갱신하지 않는다 — [shouldTriggerNow] 의 안정성 판정은
     *   항상 최초 진입 시각 기준이어야 디바운스가 의미를 갖는다.
     */
    fun onPosture(new: FoldPosture, nowMs: Long): Long? {
        val previous = posture
        posture = new

        if (new != FoldPosture.HALF_OPENED_HORIZONTAL) {
            // 플렉스 이탈(또는 애초에 플렉스가 아님). 재무장의 유일한 경로는 다음 "진입"이므로
            // 여기서 진입 시각/arm 을 완전히 지운다.
            enteredFlexAtMs = null
            armed = false
            return null
        }

        return if (previous != FoldPosture.HALF_OPENED_HORIZONTAL) {
            // 새 진입 — 진입 시각을 새로 찍고 재무장한다.
            enteredFlexAtMs = nowMs
            armed = true
            nowMs + stabilityMs
        } else {
            // 동일 자세 중복 보고 — 최초 진입 시각을 그대로 둔 채 스케줄하지 않는다.
            null
        }
    }

    /**
     * [onPosture] 가 예약한 시각 이후 서비스가 호출해 실제 발화 여부를 재확인한다.
     * ADR-2: 이 함수 자체가 "조건 재검증"이다 — 서비스의 delay 는 이 시각에 도달하기 위한 수단일
     * 뿐, 성공을 가정하지 않는다(자세가 그새 바뀌었을 수 있다).
     *
     * true 를 반환하면 그 순간 arm 을 소모(false)한다 — 동일 진입 구간에서 두 번 발화하지 않는다.
     */
    fun shouldTriggerNow(nowMs: Long): Boolean {
        val enteredAt = enteredFlexAtMs ?: return false
        if (posture != FoldPosture.HALF_OPENED_HORIZONTAL || !armed) return false
        if (nowMs - enteredAt < stabilityMs) return false
        armed = false
        return true
    }

    /**
     * 서비스가 자동 트리거 게이트(예: 세션 진행 중, 분할 이미 활성 등)에서 거부했을 때 호출한다.
     * 이번 플렉스 진입 구간에서는 더 이상 재발화하지 않는다 — 재무장은 자세를 벗어났다가 다시
     * HALF_OPENED_HORIZONTAL 로 진입하는 것(재접기)만이 유일한 경로다.
     */
    fun disarm() {
        armed = false
    }
}
