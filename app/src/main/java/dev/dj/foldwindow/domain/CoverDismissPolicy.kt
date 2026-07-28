package dev.dj.foldwindow.domain

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙).
 *
 * P4-3: 폴드를 완전히 닫아 커버 화면으로 전환되면(= FoldingFeature 소멸) 우리 앱이 만든 분할을
 * 자동으로 해제하기 위한 순수 정책. platform/FoldStateMonitor 가 androidx.window FoldingFeature
 * 부재를 [FoldPosture.UNKNOWN] 으로 사상해 이 클래스에 먹인다 — 이 파일은 androidx.window 를
 * 몰라야 한다.
 *
 * [실기기 확정, docs/DEVICE_FACTS.md 15차] 폴드를 닫는 도중에는 FoldStateMonitor 방출이 끊기지
 * 않고 좌표계만 커버 디스플레이 기하(1080×2520)로 전환되며, 이때도 여전히 HALF_OPENED_* 자세가
 * 나온다. 완전히 닫혀 FoldingFeature 자체가 사라져야만 UNKNOWN 이 된다 — 즉 "닫는 중"과
 * "완전히 닫힘"은 이미 FoldStateMonitor 사상 단계에서 구분되어 있으므로, FlexModePolicy 와 달리
 * 이 정책에는 별도의 힌지 각도 안정성 게이트가 필요 없다(시간 디바운스만으로 충분).
 *
 * ADR-2 준수: 시간은 이 클래스 밖에서 얻어 nowMs 인자로만 받는다. [debounceMs] 는
 * "고정 지연으로 성공을 가정"하는 것이 아니다 — 서비스가 delay 로 예약 시각에 도달한 뒤에도
 * [shouldDismissNow] 로 자세가 여전히 UNKNOWN 인지 실제로 재검증한다. delay 는 시각을 맞추는
 * 수단일 뿐 성공 판정은 항상 조건 재확인이 담당한다.
 *
 * 스레딩: FlexModePolicy 와 동일하게 이 클래스의 모든 public 메서드는 서비스 메인 스레드
 * (Dispatchers.Main.immediate)에서만 호출된다고 가정한다 — 내부 상태 변이에 동기화가 없다.
 */

/**
 * [FoldPosture.UNKNOWN] 진입(= 폴드 완전히 닫힘)을 감지하고 [debounceMs] 뒤 [shouldDismissNow] 로
 * 재검증해 커버 화면 전환 시 분할 자동 해제를 트리거하는 정책이다.
 *
 * **콜드 스타트 보호**: 생성/[reset] 이후 UNKNOWN 이 아닌 자세를 한 번도 관측하지 못한 상태
 * ([armed] == false)에서 들어오는 UNKNOWN 은 완전히 무시한다(에피소드를 시작하지 않는다).
 * 기기가 닫힌 채로 서비스가 기동되는 경우(재부팅, 접근성 서비스 재시작 등)에는 애초에 우리가 만든
 * 분할이 있을 수 없는데도 발화하는 것을 막기 위함이다 — armed 되기 전의 UNKNOWN 은 무시된다.
 *
 * **에피소드당 발화 1회**: armed 상태에서 UNKNOWN 에 새로 진입하면 그 에피소드에 대해 딱 한 번만
 * `entryAt + [debounceMs]` 를 반환한다. 같은 에피소드 안에서의 반복 UNKNOWN 방출(동일 자세 중복
 * 보고)은 재스케줄하지 않는다(null 반환). UNKNOWN 이 아닌 자세가 관측되면(다시 열림) 진행 중이던
 * 에피소드는 즉시 취소되고, 다음에 다시 닫히면 새 에피소드로 재발화할 수 있다.
 *
 * @param debounceMs UNKNOWN 진입 후 실제 dismiss 재검증까지의 유예. 기본값
 *   [DEFAULT_DEBOUNCE_MS](600ms).
 */
class CoverDismissPolicy(private val debounceMs: Long = DEFAULT_DEBOUNCE_MS) {

    companion object {
        const val DEFAULT_DEBOUNCE_MS = 600L
    }

    /** 생성/[reset] 이후 UNKNOWN 아닌 자세를 한 번이라도 관측했는지. 콜드 스타트 UNKNOWN 무시 게이트 */
    private var armed: Boolean = false

    /** 현재(또는 가장 최근) UNKNOWN 연속 구간의 최초 진입 시각. 그 구간 밖(비-UNKNOWN 관측 후)이면 null */
    private var enteredUnknownAtMs: Long? = null

    /** 이번 UNKNOWN 에피소드가 이미 발화했는지(래치). true 면 같은 에피소드에서 다시 발화하지 않는다 */
    private var fired: Boolean = false

    /**
     * 새 자세 보고를 흡수한다.
     *
     * @return armed 상태에서 UNKNOWN 으로 "새로 진입"했을 때만(직전 보고가 UNKNOWN 이 아니었을
     *   때) dismiss 재검증을 예약할 시각([nowMs] + [debounceMs])을 반환한다. 그 외 — 아직 armed
     *   되지 않은 콜드 스타트 UNKNOWN, 같은 에피소드 안의 중복 UNKNOWN 보고, UNKNOWN 이 아닌
     *   자세로의 이탈/유지 — 에는 null.
     *
     *   UNKNOWN 이 아닌 자세를 관측하면 armed 를 세우고(이미 true 여도 무해) 진행 중이던 에피소드를
     *   완전히 지운다(진입 시각과 발화 래치를 모두 리셋) — 다음 UNKNOWN 진입이 새 에피소드가 된다.
     */
    fun onPosture(posture: FoldPosture, nowMs: Long): Long? {
        if (posture != FoldPosture.UNKNOWN) {
            // 열림(또는 애초에 UNKNOWN 이 아님) — armed 를 세우고 진행 중이던 에피소드를 취소한다.
            armed = true
            enteredUnknownAtMs = null
            fired = false
            return null
        }

        if (!armed) {
            // 콜드 스타트: 서비스 기동 이래 비-UNKNOWN 자세를 한 번도 못 봤다 — 이 UNKNOWN 은
            // 신뢰할 수 없는 초기값일 수 있으므로(닫힌 채 기동 등) 에피소드를 시작하지 않는다.
            return null
        }

        return if (enteredUnknownAtMs == null) {
            // 새 에피소드 진입.
            enteredUnknownAtMs = nowMs
            fired = false
            nowMs + debounceMs
        } else {
            // 같은 에피소드 안의 중복 UNKNOWN 보고 — 재스케줄하지 않는다.
            null
        }
    }

    /**
     * [onPosture] 가 예약한 시각 이후 서비스가 호출해 실제 발화 여부를 재확인한다. [posture] 는
     * 호출 시점의 최신 자세를 그대로 넘겨야 한다 — 서비스는 이미 존재하는 "최신 자세" 필드(예:
     * FlexModePolicy.posture)를 재사용하면 되고, 이 함수 자체가 "여전히 UNKNOWN 인가"의
     * 재검증이다(ADR-2: delay 는 시각 도달 수단일 뿐 성공을 가정하지 않는다).
     *
     * 최종 조건 = 진행 중인 에피소드 존재 ∧ [posture] 가 여전히 UNKNOWN ∧ 디바운스 경과 ∧ 이
     * 에피소드 미발화. true 를 반환하면 그 순간 발화를 래치한다 — 같은 에피소드에서 다시 호출하면
     * 항상 false. UNKNOWN 이 아닌 자세가 오면([onPosture] 를 통해) 래치 여부와 무관하게 에피소드
     * 자체가 사라지고, 다시 닫히면 새 에피소드로 재발화할 수 있다.
     */
    fun shouldDismissNow(posture: FoldPosture, nowMs: Long): Boolean {
        val enteredAt = enteredUnknownAtMs ?: return false
        if (posture != FoldPosture.UNKNOWN) return false
        if (fired) return false
        if (nowMs < enteredAt + debounceMs) return false
        fired = true
        return true
    }

    /**
     * 상태를 완전히 초기화한다(서비스 재연결 등). 콜드 스타트 보호([armed] == false)가 다시
     * 걸리므로, reset 직후 첫 UNKNOWN 은 비-UNKNOWN 자세를 한 번 관측하기 전까지 무시된다.
     */
    fun reset() {
        armed = false
        enteredUnknownAtMs = null
        fired = false
    }
}
