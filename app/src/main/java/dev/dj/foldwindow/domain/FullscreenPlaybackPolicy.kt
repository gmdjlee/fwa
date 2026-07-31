package dev.dj.foldwindow.domain

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙).
 *
 * #30 (docs/DESIGN_30_FULLSCREEN_AUTO.md §2.1): 앱이 전체화면 재생(몰입 모드)으로 진입한 것을
 * 감지해 자동 배치를 트리거하기 위한 순수 정책. platform/FullscreenSignalSampler 가 접근성 창
 * 목록을 domain/FullscreenWindowJudge 로 넘겨 [FullscreenSignal] 3값으로 압축한 뒤 이 클래스에
 * 먹인다 — 이 파일은 창 목록도 WindowInsets 도 몰라야 한다(신호원 중립). 설계서 §7 의 비목표대로
 * 2차 신호원(인셋 기반)으로 교체하더라도 이 클래스는 재작성 대상이 아니다.
 *
 * ADR-2 준수: 시간은 이 클래스 밖에서 얻어 nowMs 인자로만 받는다 (FlexModePolicy·
 * CoverDismissPolicy 와 동일 패턴). [onSignal] 이 돌려주는 예약 시각은 "고정 지연으로 성공을
 * 가정"하는 것이 아니다 — 서비스가 delay 로 그 시각에 도달한 뒤에도 라이브 샘플을 다시 [onSignal]
 * 로 흡수하고 [isTriggerReady]/[shouldTriggerNow] 로 조건을 재검증한다. delay 는 시각을 맞추는
 * 수단일 뿐 성공 판정은 항상 조건 재확인이 담당한다.
 *
 * 스레딩: FlexModePolicy 와 동일하게 이 클래스의 모든 public 멤버는 서비스 메인 스레드
 * (Dispatchers.Main.immediate)에서만 접근된다고 가정한다 — 내부 상태 변이에 동기화가 없다.
 */

/** 전체화면 형상 신호. 신호원(창 목록/인셋)에 중립적이다 — 판정기가 이 값으로 압축해 넘긴다. */
enum class FullscreenSignal { FULLSCREEN, NOT_FULLSCREEN, UNKNOWN }

/**
 * 전체화면 신호의 **진입 엣지**를 검출하고, 진입 후 [entryDebounceMs] 가 지나야만 발화 가능
 * ([shouldTriggerNow] == true) 상태가 되는 정책이다. 진입 1회당 발화는 최대 1회이며, 재무장은
 * **이탈 확정 후 재진입**만이 유일한 경로다.
 *
 * 의미론 3가지 (설계서 §2.1 "의미론(핵심 3가지)"):
 *
 * 1. **콜드스타트 보호(D5)** — 생성/[reset] 직후 `stable` 이 null 인 동안 첫 non-UNKNOWN 샘플은
 *    베이스라인으로만 기록하고 **절대 arm 하지 않는다**. 전체화면 재생 중에 접근성 서비스가
 *    리바인드되면(앱 업데이트·수동 재시작) 첫 샘플이 곧바로 진입 엣지로 취급돼 즉시 화면을
 *    바꿔버리는 사고를 막는다. [CoverDismissPolicy] 의 `armed` 가드와 동일 형태다.
 * 2. **비대칭 히스테리시스(D9)** — [FullscreenSignal.NOT_FULLSCREEN] 은 [exitHoldMs] **이상 연속
 *    유지**되어야 이탈로 확정된다. 그 미만의 깜빡임(플레이어 컨트롤 표시, transient bar, 창 목록
 *    비원자적 재구축 — DEVICE_FACTS.md "풀스크린 터치 가능 오버레이 = a11y 창 목록 가림-제외")은
 *    흡수되며 **진입 디바운스 타이머를 리셋하지 않는다**. 진입은 즉시, 이탈은 지연 — 이 비대칭이
 *    가짜 진입 엣지의 생성 자체를 막는다.
 * 3. **UNKNOWN 무시(D6)** — [FullscreenSignal.UNKNOWN] 은 상태를 전혀 바꾸지 않는 무시 샘플이다
 *    (arm 도 disarm 도 하지 않고 [lastSignal] 도 갱신하지 않는다). 판정기가 UNKNOWN 을 내는
 *    경우를 "창 목록이 빈 경우" 하나로 좁혔기 때문에(FullscreenWindowJudge) 이 무시가 이탈 유실로
 *    이어지지 않는다 — 그 외의 판정 불확실은 전부 보수적으로 NOT_FULLSCREEN 으로 접힌다.
 *
 * @param entryDebounceMs 진입 확정 후 발화 가능까지 요구하는 최소 유지 시간.
 *   [DEFAULT_ENTRY_DEBOUNCE_MS](3000ms). 근거: 유튜브 플레이어 컨트롤 오염 실측
 *   (DEVICE_FACTS.md G2 — 탭 직후 트리거 시 pre band 370/160 비대칭으로 snap 1.5 오측). 컨트롤
 *   자동 숨김 이후로 pre-measure 를 밀어 MEASURED 채택률을 지킨다. 대조군은 같은 표의 G3
 *   (전체화면 재생 중 트리거 3/3 SNAP_AGREE). **컨트롤 자동 숨김 실제 시간은 [미검증]** — 설계서
 *   §6 W0-2 로 확정할 것.
 * @param exitHoldMs 이탈 확정에 요구하는 NOT_FULLSCREEN 연속 유지 시간.
 *   [DEFAULT_EXIT_HOLD_MS](1200ms). **[미검증]** — 창 목록 비원자적 재구축 흡수용으로 기존
 *   `WINDOWS_SETTLE_TIMEOUT_MS`(1200ms) 와 같은 눈금을 쓴다. 설계서 §6 W0-3/W0-6 (컨트롤 표시
 *   지속시간 · transient bar 자동 숨김 시간) 측정 후 조정 대상.
 */
class FullscreenPlaybackPolicy(
    private val entryDebounceMs: Long = DEFAULT_ENTRY_DEBOUNCE_MS,
    private val exitHoldMs: Long = DEFAULT_EXIT_HOLD_MS,
) {

    companion object {
        const val DEFAULT_ENTRY_DEBOUNCE_MS = 3_000L
        const val DEFAULT_EXIT_HOLD_MS = 1_200L
    }

    /**
     * 히스테리시스를 통과해 **확정된** 신호. null 은 콜드스타트(아직 베이스라인 없음)를 뜻하며,
     * 이 상태에서 들어온 첫 non-UNKNOWN 샘플은 베이스라인이 될 뿐 arm 하지 않는다.
     */
    private var stable: FullscreenSignal? = null

    /**
     * 이탈 후보(연속 NOT_FULLSCREEN)가 시작된 시각. [exitHoldMs] 이상 유지되면 이탈이 확정되고,
     * 그 전에 FULLSCREEN 이 하나라도 들어오면 깜빡임으로 간주해 이 값만 지운다(진입 시각 불변).
     */
    private var pendingNotFullscreenSinceMs: Long? = null

    /** 현재 확정된 전체화면 구간의 진입 시각. 디바운스 판정 기준점이며 구간 밖이면 null */
    private var enteredFullscreenAtMs: Long? = null

    /** 이번 진입 구간에서 아직 발화하지 않았고 게이트에 의해 거부되지도 않았는지 여부 */
    private var armed: Boolean = false

    /**
     * 이번 전체화면 진입 구간이 아직 살아 있는지(발화·거부·이탈 전인지). 서비스의 조건 폴링이
     * "계속 폴링할 이유가 남았는가"를 판정하는 종료 조건으로 쓴다 — false 면 폴링을 끝내야 한다.
     */
    val isArmed: Boolean get() = armed

    /**
     * 마지막으로 흡수한 non-UNKNOWN 신호(히스테리시스 이전의 **날 것**). 설계서 §9 의 전이 로깅이
     * 이 값의 변화를 감시해 W0 창 목록 판정을 logcat 만으로 재구성하므로, 확정값([stable])이 아니라
     * 원 샘플이어야 한다 — 흡수된 깜빡임도 로그에 드러나야 진단이 된다.
     * 초기값 [FullscreenSignal.UNKNOWN] = "아직 아무 신호도 흡수하지 않음".
     */
    var lastSignal: FullscreenSignal = FullscreenSignal.UNKNOWN
        private set

    /**
     * 새 신호 샘플을 흡수한다.
     *
     * @return 전체화면 **진입 엣지가 확정**됐을 때만 재검증 예약 시각([nowMs] + [entryDebounceMs])을
     *   반환한다. 그 외 — 콜드스타트 베이스라인, 동일 상태 중복 샘플, 이탈 후보/확정,
     *   [FullscreenSignal.UNKNOWN] — 에는 null.
     *
     *   같은 진입 구간 안의 중복 FULLSCREEN 보고는 진입 시각을 갱신하지 않는다 — 디바운스 판정은
     *   항상 최초 진입 시각 기준이어야 의미를 갖는다([FlexModePolicy.onPosture] 와 동일 관례).
     */
    fun onSignal(signal: FullscreenSignal, nowMs: Long): Long? {
        if (signal == FullscreenSignal.UNKNOWN) {
            // 무시 샘플 — arm 도 disarm 도 하지 않고 lastSignal 도 건드리지 않는다 (의미론 3).
            return null
        }

        lastSignal = signal

        if (stable == null) {
            // 콜드스타트 보호 (의미론 1): 첫 샘플은 베이스라인으로만 기록한다. 여기서 FULLSCREEN 을
            // 받아도 arm 하지 않으므로, 전체화면 재생 중 서비스가 리바인드돼도 즉시 발화하지 않는다.
            // 이 상태를 벗어나는 유일한 경로는 아래의 정상 전이(이탈 확정 → 재진입)다.
            stable = signal
            return null
        }

        if (signal == FullscreenSignal.FULLSCREEN) {
            // 진행 중이던 이탈 후보는 깜빡임으로 판정해 취소한다 (의미론 2). 진입 시각은 건드리지
            // 않는다 — 이것이 "깜빡임이 진입 디바운스를 리셋하지 않는다"의 구현 지점이다.
            pendingNotFullscreenSinceMs = null
            if (stable == FullscreenSignal.FULLSCREEN) return null // 동일 상태 중복 보고

            stable = FullscreenSignal.FULLSCREEN
            enteredFullscreenAtMs = nowMs
            armed = true
            return nowMs + entryDebounceMs
        }

        // signal == NOT_FULLSCREEN
        if (stable == FullscreenSignal.NOT_FULLSCREEN) return null // 이미 이탈 확정 상태

        val since = pendingNotFullscreenSinceMs ?: nowMs.also { pendingNotFullscreenSinceMs = it }
        if (nowMs - since >= exitHoldMs) {
            // 이탈 확정 — 이번 구간을 완전히 닫는다. 재무장은 다음 진입 엣지만이 유일한 경로다.
            stable = FullscreenSignal.NOT_FULLSCREEN
            pendingNotFullscreenSinceMs = null
            enteredFullscreenAtMs = null
            armed = false
        }
        return null
    }

    /**
     * arm 을 **소모하지 않는** 발화 가능 조회. 미디어 재생 확증(platform/MediaPlaybackProbe)처럼
     * 요동치는 게이트를 게이트 체인 밖(폴링 루프)에서 재시도해야 하기 때문에 필요하다 — 미디어가
     * 잠시 끊겼다고 arm 을 태워버리면 정당한 발화를 잃는다(설계서 §3.1).
     *
     * 조건 = armed ∧ 확정 신호가 FULLSCREEN ∧ 진입 이력 존재 ∧ [entryDebounceMs] 경과.
     */
    fun isTriggerReady(nowMs: Long): Boolean {
        if (!armed || stable != FullscreenSignal.FULLSCREEN) return false
        val enteredAt = enteredFullscreenAtMs ?: return false
        return nowMs - enteredAt >= entryDebounceMs
    }

    /**
     * 실제 발화 여부를 확정한다. ADR-2: 이 함수 자체가 "조건 재검증"이다 — 서비스의 delay 는
     * [onSignal] 이 돌려준 시각에 도달하기 위한 수단일 뿐, 그 사이 전체화면을 벗어났을 수 있다.
     *
     * true 를 반환하면 그 순간 arm 을 소모한다 — 동일 진입 구간에서 두 번 발화하지 않는다.
     * false 인 경우(아직 디바운스 미경과 등)에는 arm 을 소모하지 않으므로, 호출자는 [isArmed] 가
     * true 인 동안 조건 폴링으로 다시 물어보면 된다.
     */
    fun shouldTriggerNow(nowMs: Long): Boolean {
        if (!isTriggerReady(nowMs)) return false
        armed = false
        return true
    }

    /**
     * 서비스가 자동 트리거 게이트(레버 OFF, 세션 진행 중, 래치됨 등)에서 거부했을 때 호출한다.
     * 이번 진입 구간에서는 더 이상 재발화하지 않는다 — 재무장은 이탈이 확정된 뒤 다시 전체화면으로
     * 진입하는 것만이 유일한 경로다.
     *
     * 확정 신호([stable])와 이탈 후보 추적은 건드리지 않는다 — 거부는 "신호가 틀렸다"가 아니라
     * "지금은 발화할 때가 아니다"라는 뜻이고, 이후의 이탈/재진입 판정은 그대로 유효해야 한다.
     */
    fun disarm() {
        armed = false
    }

    /**
     * 상태를 완전히 초기화한다(접근성 서비스 재연결 등). 콜드스타트 보호(`stable == null`)가 다시
     * 걸리므로, reset 직후 첫 FULLSCREEN 은 베이스라인이 될 뿐 arm 하지 않는다.
     */
    fun reset() {
        stable = null
        pendingNotFullscreenSinceMs = null
        enteredFullscreenAtMs = null
        armed = false
        lastSignal = FullscreenSignal.UNKNOWN
    }
}
