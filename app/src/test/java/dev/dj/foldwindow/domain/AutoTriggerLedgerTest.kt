package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AutoTriggerLedger] 의 에피소드 래치 · 서킷브레이커 계약 검증
 * (docs/DESIGN_30_FULLSCREEN_AUTO.md §5 의 21~25번).
 *
 * 이 장부가 닫는 것은 P-1 자가유발 재진입 루프(D1·D2)와 실패 자동 세션의 자기 재무장(D3)이다.
 * **해제 조건에 시간이 없다**는 것이 설계의 핵심이라, 이 테스트에도 nowMs 가 등장하지 않는다.
 */
class AutoTriggerLedgerTest {

    private val youtube = "com.google.android.youtube"
    private val launcher = "com.sec.android.app.launcher"

    /** 홈이 아닌 일반 앱. 21차 분리 실험의 대조군(이 경로는 재발화 1회가 관측됐다). */
    private val chrome = "com.android.chrome"

    @Test
    fun `latched package blocks re-fire until foreground leaves`() {
        val ledger = AutoTriggerLedger()

        ledger.onAutoFired(youtube)

        assertTrue(ledger.isLatched(youtube))
        assertFalse("래치는 패키지 단위다", ledger.isLatched(launcher))

        // 같은 앱에 머무는 동안의 포그라운드 재보고(셰이드 개폐·잠금해제 후 복귀 등)는 해제가 아니다.
        ledger.onForeground(youtube, isHome = false)
        assertTrue(ledger.isLatched(youtube))

        // 시간이 아니라 "포그라운드가 래치 패키지를 떠남"만이 해제 조건이다.
        ledger.onForeground(launcher, isHome = true)
        assertFalse(ledger.isLatched(youtube))
    }

    @Test
    fun `split dismissal re-latches last auto package`() {
        val ledger = AutoTriggerLedger()
        ledger.onAutoFired(youtube)
        ledger.onForeground(launcher, isHome = true)
        assertFalse(ledger.isLatched(youtube))

        // 사용자가 유튜브로 돌아왔다 — 이것만으로는 재래치되지 않는다(정상 재발화 경로 W1-3).
        ledger.onForeground(youtube, isHome = false)
        assertFalse(ledger.isLatched(youtube))

        // 우리 분할이 해제되면 마지막 자동 대상을 재래치한다 — 해제 직후의 전체화면 자동 복귀가
        // 만드는 진입 엣지를 게이트 10 에서 죽이는 지점이다(D1·D2).
        ledger.onSplitDismissed()
        assertTrue(ledger.isLatched(youtube))
    }

    @Test
    fun `manual trigger clears latch and fail streak`() {
        val ledger = AutoTriggerLedger()
        ledger.onAutoFired(youtube)
        ledger.onAutoResult(youtube, success = false)
        assertTrue(ledger.isLatched(youtube))
        assertEquals(1, ledger.failStreak(youtube))

        // 사용자가 버블을 눌러 수동 배치했다 = 자동화 재신뢰 신호.
        ledger.onManualTrigger(youtube)

        assertFalse(ledger.isLatched(youtube))
        assertEquals(0, ledger.failStreak(youtube))
        assertFalse(ledger.isDisabled(youtube))
    }

    @Test
    fun `two consecutive auto failures disable the package`() {
        val ledger = AutoTriggerLedger()

        ledger.onAutoResult(youtube, success = false)
        assertFalse("1회 실패로는 비활성화하지 않는다", ledger.isDisabled(youtube))

        ledger.onAutoResult(youtube, success = false)
        assertTrue(ledger.isDisabled(youtube))
        assertEquals(AutoTriggerLedger.DEFAULT_MAX_FAIL_STREAK, ledger.failStreak(youtube))
        assertFalse("서킷브레이커도 패키지 단위다", ledger.isDisabled(launcher))

        // 성공 1회가 스트릭을 0으로 리셋한다.
        ledger.onAutoResult(youtube, success = true)
        assertEquals(0, ledger.failStreak(youtube))
        assertFalse(ledger.isDisabled(youtube))

        // "연속" 실패만 누적된다 — 사이에 성공이 끼면 다시 처음부터다.
        val interleaved = AutoTriggerLedger()
        interleaved.onAutoResult(youtube, success = false)
        interleaved.onAutoResult(youtube, success = true)
        interleaved.onAutoResult(youtube, success = false)
        assertFalse(interleaved.isDisabled(youtube))
    }

    @Test
    fun `foreground null does not clear latch`() {
        val ledger = AutoTriggerLedger()
        ledger.onAutoFired(youtube)

        // 창 전환 도중의 "포그라운드 미상" 블립. 모르는 것은 해제 근거가 되지 못한다.
        ledger.onForeground(null, isHome = false)
        assertTrue(ledger.isLatched(youtube))
        ledger.onForeground(null, isHome = false)
        assertTrue(ledger.isLatched(youtube))

        // 실제로 다른 패키지가 관측돼야 비로소 풀린다.
        ledger.onForeground(launcher, isHome = true)
        assertFalse(ledger.isLatched(youtube))
    }

    /**
     * [#31] [AutoTriggerLedger.onForeground] 의 반환값 계약. 상태 변화는 종전과 동일하고 반환값만
     * 추가됐다 — 서비스가 이 값으로 `auto latch released: foreground=...` 로그를 남긴다
     * (CLAUDE.md "조용한 실패 금지": 21차에서 래치 해제가 logcat 에 전혀 안 남아 #31 원인 규명에
     * 실기기 분리 실험이 필요했다). "실제로 풀었을 때만 true" 여야 로그가 소음이 되지 않는다.
     */
    @Test
    fun `onForeground reports true only when it actually released the latch`() {
        val ledger = AutoTriggerLedger()

        // 래치가 아예 없으면 어떤 패키지가 와도 푼 것이 없다.
        assertFalse(ledger.onForeground(youtube, isHome = false))
        assertFalse(ledger.onForeground(launcher, isHome = true))
        assertFalse(ledger.onForeground(null, isHome = false))

        ledger.onAutoFired(youtube)

        // 같은 패키지 재보고 = 아직 떠나지 않았다.
        assertFalse(ledger.onForeground(youtube, isHome = false))
        // 미상 블립도 해제가 아니다.
        assertFalse(ledger.onForeground(null, isHome = false))
        assertTrue(ledger.isLatched(youtube))

        // 실제 이탈 1회만 true.
        assertTrue(ledger.onForeground(launcher, isHome = true))
        assertFalse(ledger.isLatched(youtube))
        // 이미 풀린 뒤의 후속 이벤트는 false — 로그가 이벤트마다 반복되지 않는다.
        assertFalse(ledger.onForeground(launcher, isHome = true))
        assertFalse(ledger.onForeground(youtube, isHome = false))
    }

    // ══════════════════════════════════════════════════════════
    // sticky / non-sticky 래치 (#31 2차 — 22차 실측 P-1 회귀 대응)
    //
    // 클래스 KDoc 의 동작 표 4행을 그대로 테스트로 옮긴다. 1행 = #31 회귀 가드,
    // 2행 = P-1 회귀 가드, 3·4행 = sticky 래치의 해제/유지 비대칭.
    // ══════════════════════════════════════════════════════════

    /**
     * **동작 표 1행 / #31 회귀 가드.** [AutoTriggerLedger.onAutoFired] 가 건 래치는 non-sticky 라
     * 홈 이동으로 풀린다 — 이 경로에는 "이 배치를 원하지 않는다"는 사용자의 명시적 거부 신호가
     * 없기 때문이다. 21차 실측에서 이 경로가 `reason=latched` 로 막혀 설계서 §6 W1-3 명세
     * (「홈 → 복귀 → 재발화 1회」)가 불성립했다.
     */
    @Test
    fun `home release clears a non-sticky latch set by auto fire`() {
        val ledger = AutoTriggerLedger()
        ledger.onAutoFired(youtube)

        assertTrue("자동 발화 래치는 홈으로 풀린다 (#31)", ledger.onForeground(launcher, isHome = true))
        assertFalse(ledger.isLatched(youtube))
    }

    /**
     * **동작 표 2행 / P-1 루프 회귀 가드.** [AutoTriggerLedger.onSplitDismissed] 가 건 래치는
     * sticky 라 홈 런처로 풀리지 않는다.
     *
     * 근거 — 22차 실기기 실측. 분할 해제 전환 중 One UI 가 홈 런처를 잠깐 노출하는데, 그 이벤트가
     * 재래치를 0.3초 만에 무효화해 P-1 루프가 그대로 되살아났다:
     * ```
     * 07:02:56.698 fullscreen signal: FULLSCREEN -> NOT_FULLSCREEN appFull=1 topBars=1
     * 07:02:57.004 auto latch released: foreground=com.sec.android.app.launcher  ← 재래치가 즉시 풀림
     * 07:02:59.662 fullscreen signal: NOT_FULLSCREEN -> FULLSCREEN appFull=1 topBars=0
     * 07:03:02.675 fullscreen auto-arrange trigger: target=com.google.android.youtube ← 재발화
     * ```
     * 시간창(grace period)으로 덮는 것은 D2 가 이미 기각한 접근이자 ADR-2 위반이므로, 래치의
     * **기원**으로 가른다.
     */
    @Test
    fun `home does not clear a sticky latch set by split dismissal`() {
        val ledger = AutoTriggerLedger()
        ledger.onAutoFired(youtube)
        ledger.onSplitDismissed()
        assertTrue(ledger.isLatched(youtube))

        assertFalse(
            "분할 해제 전환 중 런처 블립은 해제 근거가 아니다 (22차 07:02:57 실측)",
            ledger.onForeground(launcher, isHome = true),
        )
        assertTrue("sticky 래치는 홈 이벤트 뒤에도 유지된다", ledger.isLatched(youtube))
    }

    /** **동작 표 2행 보강.** 홈 이벤트가 여러 번 도착해도 sticky 래치는 계속 무시한다. */
    @Test
    fun `repeated home events never clear a sticky latch`() {
        val ledger = AutoTriggerLedger()
        ledger.onAutoFired(youtube)
        ledger.onSplitDismissed()

        repeat(5) {
            assertFalse(ledger.onForeground(launcher, isHome = true))
            assertTrue(ledger.isLatched(youtube))
        }
    }

    /**
     * **동작 표 3행.** sticky 래치라도 **홈이 아닌** 다른 앱이 전면에 오면 정상적으로 풀린다 —
     * sticky 는 "영구 래치"가 아니라 "홈 런처 블립 면역"일 뿐이다. 21차 분리 실험의 Chrome
     * 대조군(재발화 1회 관측)이 이 경로다.
     */
    @Test
    fun `a different app still clears a sticky latch`() {
        val ledger = AutoTriggerLedger()
        ledger.onAutoFired(youtube)
        ledger.onSplitDismissed()

        assertTrue(ledger.onForeground(chrome, isHome = false))
        assertFalse(ledger.isLatched(youtube))
    }

    /**
     * **동작 표 4행.** sticky 상태에서 홈을 거쳐 **같은 앱**으로 돌아오는 것만으로는 풀리지
     * 않는다 — 의도된 비대칭이다(1행과 대비). 분할 해제는 사용자의 명시적 거부 신호이므로,
     * 자동화 재신뢰에는 더 강한 신호(버블 탭 = [AutoTriggerLedger.onManualTrigger])가 필요하다.
     */
    @Test
    fun `returning to the same app through home keeps the sticky latch until a manual trigger`() {
        val ledger = AutoTriggerLedger()
        ledger.onAutoFired(youtube)
        ledger.onSplitDismissed()

        assertFalse(ledger.onForeground(launcher, isHome = true))
        assertFalse("같은 앱 복귀는 이탈이 아니다", ledger.onForeground(youtube, isHome = false))
        assertTrue(ledger.isLatched(youtube))

        // 유일한 탈출구(R7): 사용자가 버블을 눌러 수동 배치 = 자동화 재신뢰 신호.
        ledger.onManualTrigger(youtube)
        assertFalse(ledger.isLatched(youtube))
    }

    /**
     * sticky 는 래치와 **함께** 소멸해야 한다. 해제 후 새로 자동 발화한 래치가 이전 sticky 를
     * 물려받으면 #31 이 조용히 되살아난다(홈으로 안 풀리는 상태로 고착).
     */
    @Test
    fun `stickiness does not leak into the next auto fired latch`() {
        val ledger = AutoTriggerLedger()
        ledger.onAutoFired(youtube)
        ledger.onSplitDismissed()

        // 다른 앱이 sticky 래치를 풀었다.
        assertTrue(ledger.onForeground(chrome, isHome = false))

        // 새 에피소드 — 여기서 걸리는 래치는 다시 non-sticky 여야 한다.
        ledger.onAutoFired(youtube)
        assertTrue(ledger.onForeground(launcher, isHome = true))
        assertFalse(ledger.isLatched(youtube))
    }

    /**
     * [AutoTriggerLedger.onManualTrigger] 는 sticky 플래그까지 지운다. 래치만 지우고 sticky 를
     * 남기면, 이후 [AutoTriggerLedger.onAutoFired] 없이 [AutoTriggerLedger.onSplitDismissed] 만
     * 다시 와도 상태가 어긋난다 — 상태 쌍을 항상 함께 되돌린다는 계약을 동결한다.
     */
    @Test
    fun `manual trigger clears stickiness together with the latch`() {
        val ledger = AutoTriggerLedger()
        ledger.onAutoFired(youtube)
        ledger.onSplitDismissed()

        ledger.onManualTrigger(youtube)
        assertFalse(ledger.isLatched(youtube))

        // 수동 트리거 후 다시 자동 발화하면 non-sticky — 홈으로 풀려야 한다.
        ledger.onAutoFired(youtube)
        assertTrue(ledger.onForeground(launcher, isHome = true))
    }

    /** [AutoTriggerLedger.reset] 은 sticky 도 함께 지운다(접근성 서비스 재연결 등). */
    @Test
    fun `reset clears stickiness`() {
        val ledger = AutoTriggerLedger()
        ledger.onAutoFired(youtube)
        ledger.onSplitDismissed()

        ledger.reset()
        assertFalse(ledger.isLatched(youtube))

        // reset 이 lastAutoPackage 도 지우므로 재래치 대상이 없다 — sticky 가 남았다면 아래
        // 새 에피소드의 홈 해제가 막혔을 것이다.
        ledger.onAutoFired(youtube)
        assertTrue(ledger.onForeground(launcher, isHome = true))
        assertFalse(ledger.isLatched(youtube))
    }

    /**
     * [TriggerSource] 는 이 파일에 동거하는 domain 타입이라 CLAUDE.md 의 "domain 변경 시 대응 테스트"
     * 규칙에 따라 함께 동결한다. 자동 기원 2종만 [TriggerSource.isAuto] 이며, 서비스는 이 값으로
     * 토스트 억제(D19)와 posture-exit 취소 조건(D4)을 가른다.
     */
    @Test
    fun `isAuto is true only for the two automatic trigger sources`() {
        assertFalse(TriggerSource.MANUAL.isAuto)
        assertFalse(TriggerSource.SHORTCUT.isAuto)
        assertTrue(TriggerSource.FLEX_AUTO.isAuto)
        assertTrue(TriggerSource.FULLSCREEN_AUTO.isAuto)
    }
}
