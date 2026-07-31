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

    @Test
    fun `latched package blocks re-fire until foreground leaves`() {
        val ledger = AutoTriggerLedger()

        ledger.onAutoFired(youtube)

        assertTrue(ledger.isLatched(youtube))
        assertFalse("래치는 패키지 단위다", ledger.isLatched(launcher))

        // 같은 앱에 머무는 동안의 포그라운드 재보고(셰이드 개폐·잠금해제 후 복귀 등)는 해제가 아니다.
        ledger.onForeground(youtube)
        assertTrue(ledger.isLatched(youtube))

        // 시간이 아니라 "포그라운드가 래치 패키지를 떠남"만이 해제 조건이다.
        ledger.onForeground(launcher)
        assertFalse(ledger.isLatched(youtube))
    }

    @Test
    fun `split dismissal re-latches last auto package`() {
        val ledger = AutoTriggerLedger()
        ledger.onAutoFired(youtube)
        ledger.onForeground(launcher)
        assertFalse(ledger.isLatched(youtube))

        // 사용자가 유튜브로 돌아왔다 — 이것만으로는 재래치되지 않는다(정상 재발화 경로 W1-3).
        ledger.onForeground(youtube)
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
        ledger.onForeground(null)
        assertTrue(ledger.isLatched(youtube))
        ledger.onForeground(null)
        assertTrue(ledger.isLatched(youtube))

        // 실제로 다른 패키지가 관측돼야 비로소 풀린다.
        ledger.onForeground(launcher)
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
