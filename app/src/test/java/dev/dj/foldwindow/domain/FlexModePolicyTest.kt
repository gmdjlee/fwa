package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FlexModePolicy] 디바운스 의미론 검증. 기본 stabilityMs(800ms)를 그대로 쓰는 케이스가 대부분이며,
 * 커스텀 값 케이스는 별도로 둔다. [ArrangeStateMachineTest] 와 동일하게 nowMs 를 명시적으로 넘겨
 * 100% 결정적으로 검증한다.
 */
class FlexModePolicyTest {

    @Test
    fun `flat to half-h entry schedules stability check at now plus stabilityMs`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.FLAT, nowMs = 0)

        val scheduled = policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 1000)

        assertEquals(1000L + FlexModePolicy.DEFAULT_STABILITY_MS, scheduled)
        assertEquals(FoldPosture.HALF_OPENED_HORIZONTAL, policy.posture)
    }

    @Test
    fun `duplicate half-h report returns null but keeps original entry time for stability`() {
        val policy = FlexModePolicy()
        val first = policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)
        assertEquals(800L, first)

        val duplicate = policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 500)
        assertNull(duplicate)

        // 재보고 시각(500)이 아니라 최초 진입 시각(0) 기준으로 판정돼야 한다.
        assertFalse(policy.shouldTriggerNow(nowMs = 500))
        assertTrue(policy.shouldTriggerNow(nowMs = 800))
    }

    @Test
    fun `shouldTriggerNow is false before stability elapses`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)

        assertFalse(policy.shouldTriggerNow(nowMs = 799))
    }

    @Test
    fun `shouldTriggerNow becomes true after stability and consumes arm`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)

        assertTrue(policy.shouldTriggerNow(nowMs = 800))
        // arm 소모됨 — 동일 진입 구간에서 재호출은 항상 false.
        assertFalse(policy.shouldTriggerNow(nowMs = 800))
        assertFalse(policy.shouldTriggerNow(nowMs = 900))
    }

    @Test
    fun `exiting to flat before scheduled check disarms the trigger`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)

        val exitScheduled = policy.onPosture(FoldPosture.FLAT, nowMs = 300)

        assertNull(exitScheduled)
        assertFalse(policy.shouldTriggerNow(nowMs = 800))
    }

    @Test
    fun `re-entering half-h after exit re-arms the trigger`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)
        policy.onPosture(FoldPosture.FLAT, nowMs = 300)

        val reentry = policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 400)

        assertEquals(1200L, reentry)
        assertFalse(policy.shouldTriggerNow(nowMs = 1199))
        assertTrue(policy.shouldTriggerNow(nowMs = 1200))
    }

    @Test
    fun `half-opened vertical never schedules but posture property reflects it`() {
        val policy = FlexModePolicy()

        val scheduled = policy.onPosture(FoldPosture.HALF_OPENED_VERTICAL, nowMs = 0)

        assertNull(scheduled)
        assertEquals(FoldPosture.HALF_OPENED_VERTICAL, policy.posture)
        assertFalse(policy.shouldTriggerNow(nowMs = 100_000))
    }

    @Test
    fun `unknown posture never schedules`() {
        val policy = FlexModePolicy()

        val scheduled = policy.onPosture(FoldPosture.UNKNOWN, nowMs = 0)

        assertNull(scheduled)
        assertEquals(FoldPosture.UNKNOWN, policy.posture)
    }

    @Test
    fun `disarm suppresses trigger even after stability elapses`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)

        policy.disarm()

        assertFalse(policy.shouldTriggerNow(nowMs = 800))
        assertFalse(policy.shouldTriggerNow(nowMs = 5_000))
    }

    @Test
    fun `disarm then re-entering half-h re-arms`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)
        policy.disarm()
        policy.onPosture(FoldPosture.FLAT, nowMs = 100)

        val reentry = policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 200)

        assertEquals(1000L, reentry)
        assertTrue(policy.shouldTriggerNow(nowMs = 1000))
    }

    @Test
    fun `half-h to vertical to half-h reschedules with new entry time and re-arms`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)
        policy.onPosture(FoldPosture.HALF_OPENED_VERTICAL, nowMs = 100)

        val reentry = policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 200)

        assertEquals(1000L, reentry)
        assertFalse(policy.shouldTriggerNow(nowMs = 999))
        assertTrue(policy.shouldTriggerNow(nowMs = 1000))
    }

    @Test
    fun `shouldTriggerNow is false with no entry history`() {
        val policy = FlexModePolicy()

        assertFalse(policy.shouldTriggerNow(nowMs = 100_000))
    }

    // ── 부가 케이스 ────────────────────────────────────────────────

    @Test
    fun `custom stabilityMs is honored instead of the default`() {
        val policy = FlexModePolicy(stabilityMs = 200L)

        val scheduled = policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 1000)

        assertEquals(1200L, scheduled)
        assertFalse(policy.shouldTriggerNow(nowMs = 1199))
        assertTrue(policy.shouldTriggerNow(nowMs = 1200))
    }

    @Test
    fun `disarm without any entry is a safe no-op`() {
        val policy = FlexModePolicy()

        policy.disarm()

        assertFalse(policy.shouldTriggerNow(nowMs = 100_000))
        // disarm 이 이후의 정상 진입까지 오염시키지 않는다.
        val scheduled = policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)
        assertEquals(800L, scheduled)
        assertTrue(policy.shouldTriggerNow(nowMs = 800))
    }
}
