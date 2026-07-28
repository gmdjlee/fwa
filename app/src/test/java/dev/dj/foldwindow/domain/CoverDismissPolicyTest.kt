package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CoverDismissPolicy] 의 콜드 스타트 보호 + 시간 디바운스 + 에피소드당 1회 발화 래치 의미론
 * 검증. [FlexModePolicyTest] 와 동일하게 nowMs 를 명시적으로 넘겨 100% 결정적으로 검증한다 —
 * 실제 센서도 슬립도 없다.
 */
class CoverDismissPolicyTest {

    // ── 콜드 스타트 보호 ──────────────────────────────────────────

    @Test
    fun `cold start unknown posture never schedules or fires`() {
        val policy = CoverDismissPolicy()

        val scheduled = policy.onPosture(FoldPosture.UNKNOWN, nowMs = 0)

        assertNull(scheduled)
        assertFalse(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 100_000))
    }

    @Test
    fun `repeated cold start unknown reports still never arm the episode`() {
        val policy = CoverDismissPolicy()

        policy.onPosture(FoldPosture.UNKNOWN, nowMs = 0)
        val second = policy.onPosture(FoldPosture.UNKNOWN, nowMs = 1_000)

        assertNull(second)
        assertFalse(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 100_000))
    }

    // ── 정상 경로: open → close → 발화 ────────────────────────────

    @Test
    fun `open then close schedules and fires once after debounce`() {
        val policy = CoverDismissPolicy()
        policy.onPosture(FoldPosture.FLAT, nowMs = 0) // armed

        val scheduled = policy.onPosture(FoldPosture.UNKNOWN, nowMs = 100)

        assertEquals(100L + CoverDismissPolicy.DEFAULT_DEBOUNCE_MS, scheduled)
        assertFalse(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 699))
        assertTrue(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 700))
    }

    @Test
    fun `shouldDismissNow boundary is inclusive at exactly debounceMs`() {
        val policy = CoverDismissPolicy()
        policy.onPosture(FoldPosture.FLAT, nowMs = 0)
        policy.onPosture(FoldPosture.UNKNOWN, nowMs = 0)

        assertFalse(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = CoverDismissPolicy.DEFAULT_DEBOUNCE_MS - 1))
        assertTrue(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = CoverDismissPolicy.DEFAULT_DEBOUNCE_MS))
    }

    @Test
    fun `half-opened vertical counts as a non-unknown posture for arming and cancellation`() {
        val policy = CoverDismissPolicy()

        val armedSchedule = policy.onPosture(FoldPosture.HALF_OPENED_VERTICAL, nowMs = 0)
        assertNull(armedSchedule)

        val scheduled = policy.onPosture(FoldPosture.UNKNOWN, nowMs = 100)
        assertEquals(700L, scheduled)
    }

    // ── 발화 전 자세 복귀(바운스백) ────────────────────────────────

    @Test
    fun `posture bouncing back to non-unknown before the check cancels the episode`() {
        val policy = CoverDismissPolicy()
        policy.onPosture(FoldPosture.FLAT, nowMs = 0)
        policy.onPosture(FoldPosture.UNKNOWN, nowMs = 100) // 예약 시각 700

        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 300) // 발화 전 바운스백

        // 예약 시각(700)에 이르러도 실제 최신 posture 는 이미 non-UNKNOWN 이므로 false.
        assertFalse(policy.shouldDismissNow(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 700))
        // 설령 stale 한 UNKNOWN 값을 넘겨도(방어적 이중 체크) 내부 에피소드가 이미 지워졌으므로 false.
        assertFalse(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 700))
    }

    // ── 같은 에피소드 반복 UNKNOWN ────────────────────────────────

    @Test
    fun `duplicate unknown reports within the same episode do not reschedule`() {
        val policy = CoverDismissPolicy()
        policy.onPosture(FoldPosture.FLAT, nowMs = 0)
        val first = policy.onPosture(FoldPosture.UNKNOWN, nowMs = 0)
        assertEquals(600L, first)

        val duplicate = policy.onPosture(FoldPosture.UNKNOWN, nowMs = 300)

        assertNull(duplicate)
        // 재보고 시각(300)이 아니라 최초 진입 시각(0) 기준으로 디바운스가 판정돼야 한다.
        assertFalse(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 300))
        assertTrue(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 600))
    }

    // ── 재열림 후 재닫힘 재발화 ────────────────────────────────────

    @Test
    fun `reopen then reclose re-arms and fires a new episode`() {
        val policy = CoverDismissPolicy()
        policy.onPosture(FoldPosture.FLAT, nowMs = 0)
        policy.onPosture(FoldPosture.UNKNOWN, nowMs = 100) // 700
        assertTrue(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 700)) // 발화 + 래치

        policy.onPosture(FoldPosture.FLAT, nowMs = 800) // 재열림

        val reentry = policy.onPosture(FoldPosture.UNKNOWN, nowMs = 900) // 재닫힘

        assertEquals(1500L, reentry)
        assertFalse(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 1499))
        assertTrue(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 1500))
    }

    // ── 래치: 같은 에피소드 중복 발화 없음 ─────────────────────────

    @Test
    fun `firing latches so the same episode never fires twice`() {
        val policy = CoverDismissPolicy()
        policy.onPosture(FoldPosture.FLAT, nowMs = 0)
        policy.onPosture(FoldPosture.UNKNOWN, nowMs = 0)

        assertTrue(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 600))
        assertFalse(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 600))
        assertFalse(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 10_000))
    }

    @Test
    fun `shouldDismissNow rejects a non-unknown posture argument even mid-episode`() {
        val policy = CoverDismissPolicy()
        policy.onPosture(FoldPosture.FLAT, nowMs = 0)
        policy.onPosture(FoldPosture.UNKNOWN, nowMs = 0) // 에피소드 진행 중, 예약 600

        // 호출자가 실수로(또는 레이스로) 다른 posture 값을 넘기면 에피소드가 내부적으로 살아있어도 거부.
        assertFalse(policy.shouldDismissNow(FoldPosture.FLAT, nowMs = 600))
        // 에피소드 자체는 아직 살아 있으므로 올바른 posture 를 다시 넘기면 그때는 발화한다.
        assertTrue(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 600))
    }

    // ── reset ─────────────────────────────────────────────────────

    @Test
    fun `reset clears armed state and re-establishes cold start protection`() {
        val policy = CoverDismissPolicy()
        policy.onPosture(FoldPosture.FLAT, nowMs = 0)
        policy.onPosture(FoldPosture.UNKNOWN, nowMs = 0)

        policy.reset()

        // reset 직후엔 다시 콜드 스타트 상태 — armed 되기 전 UNKNOWN 은 무시돼야 한다.
        val scheduled = policy.onPosture(FoldPosture.UNKNOWN, nowMs = 100)
        assertNull(scheduled)
        assertFalse(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 100_000))
    }

    @Test
    fun `after reset a fresh open then close still fires normally`() {
        val policy = CoverDismissPolicy()
        policy.onPosture(FoldPosture.FLAT, nowMs = 0)
        policy.onPosture(FoldPosture.UNKNOWN, nowMs = 0)
        policy.reset()

        policy.onPosture(FoldPosture.FLAT, nowMs = 100) // 다시 armed
        val scheduled = policy.onPosture(FoldPosture.UNKNOWN, nowMs = 200)

        assertEquals(800L, scheduled)
        assertTrue(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 800))
    }

    // ── 부가 케이스 ────────────────────────────────────────────────

    @Test
    fun `custom debounceMs is honored instead of the default`() {
        val policy = CoverDismissPolicy(debounceMs = 200L)
        policy.onPosture(FoldPosture.FLAT, nowMs = 0)

        val scheduled = policy.onPosture(FoldPosture.UNKNOWN, nowMs = 1000)

        assertEquals(1200L, scheduled)
        assertFalse(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 1199))
        assertTrue(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 1200))
    }

    @Test
    fun `shouldDismissNow is false with no entry history`() {
        val policy = CoverDismissPolicy()
        policy.onPosture(FoldPosture.FLAT, nowMs = 0)

        assertFalse(policy.shouldDismissNow(FoldPosture.UNKNOWN, nowMs = 100_000))
    }
}
