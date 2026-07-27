package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FlexModePolicy] 의 시간 디바운스 + 힌지 각도 안정성 게이트 의미론 검증. 기본 stabilityMs(800ms)를
 * 그대로 쓰는 케이스가 대부분이며, 커스텀 값 케이스는 별도로 둔다. [ArrangeStateMachineTest] 와
 * 동일하게 nowMs(그리고 각도 샘플 시각)를 명시적으로 넘겨 100% 결정적으로 검증한다 — 실제 센서도
 * 슬립도 없다.
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

    // ── 힌지 각도 안정성 게이트 ──────────────────────────────────
    //
    // 실기기 오발화 물증(2026-07-27, Fold 7/One UI 8): 완전히 닫는 동작이 HALF_OPENED 대역을
    // ~2초(2표본 2.1s/1.95s) 체류하며 통과해 800ms 디바운스가 닫는 도중 만료 → 자동 배치 오발화.
    // 아래 케이스들은 "닫는 중(각도가 계속 변함)"과 "노트북 자세로 거치됨(각도가 멎음)"을
    // 시간이 아니라 조건으로 구분하는지 검증한다.

    @Test
    fun `closing sweep never triggers even after the debounce elapses`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)

        // 100ms 간격으로 170도 -> 10도 선형 하강(≈1.6s 짜리 닫기 스윕, 실측 ~2s 와 같은 부류).
        var t = 0L
        var angle = 170f
        while (angle >= 10f) {
            policy.onHingeAngle(angle, nowMs = t)
            // 샘플 직후와 샘플 사이(+50ms) 두 시점 모두에서 발화 불가여야 한다 —
            // 디바운스(800ms)가 만료된 뒤 구간도 전부 포함된다.
            assertFalse("t=$t angle=$angle", policy.shouldTriggerNow(nowMs = t))
            assertFalse("t=${t + 50} angle=$angle", policy.shouldTriggerNow(nowMs = t + 50))
            t += 100
            angle -= 10f
        }

        // 각도 게이트가 막았을 뿐 arm 은 살아 있다(자세를 벗어나야만 무장 해제).
        assertTrue(policy.isArmed)
    }

    @Test
    fun `settling inside the band triggers only after the quiet period`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)

        // 170 -> 100 스윕 후 90도에서 정지(마지막 샘플 t=800, 이후 방출 없음).
        var t = 0L
        var angle = 170f
        while (angle > 90f) {
            policy.onHingeAngle(angle, nowMs = t)
            t += 100
            angle -= 10f
        }
        policy.onHingeAngle(90f, nowMs = t)
        val lastSampleAtMs = t

        assertFalse(policy.shouldTriggerNow(nowMs = lastSampleAtMs + FlexModePolicy.ANGLE_QUIET_MS - 1))
        assertTrue(policy.shouldTriggerNow(nowMs = lastSampleAtMs + FlexModePolicy.ANGLE_QUIET_MS))
        // 발화가 arm 을 소모했다.
        assertFalse(policy.isArmed)
    }

    @Test
    fun `micro jitter inside the band triggers via the low-variance branch`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)

        // 89/91 도를 오가는 미세 떨림이 100ms 간격으로 계속 유입(t=0..1100) — 침묵 분기는
        // 성립하지 않지만 스프레드 2도라 저분산 분기가 통과해야 한다.
        var t = 0L
        repeat(12) { i ->
            policy.onHingeAngle(if (i % 2 == 0) 89f else 91f, nowMs = t)
            t += 100
        }

        assertFalse("디바운스 전에는 각도와 무관하게 불가", policy.shouldTriggerNow(nowMs = 700))
        assertTrue(policy.isAngleStable(nowMs = 1200))
        assertTrue(policy.shouldTriggerNow(nowMs = 1200))
    }

    @Test
    fun `quiet angle below the band never triggers`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)

        // 거의 닫힌 각도에서 방출이 멎은 상황 — 침묵은 정지의 증거지만 대역 밖이라 노트북 자세가 아니다.
        policy.onHingeAngle(20f, nowMs = 100)

        assertFalse(policy.isAngleStable(nowMs = 800))
        assertFalse(policy.shouldTriggerNow(nowMs = 800))
        assertFalse(policy.shouldTriggerNow(nowMs = 5_000))
        assertTrue(policy.isArmed)
    }

    @Test
    fun `quiet angle above the band never triggers`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)

        // 사실상 펼친 각도(160도)에서 침묵 — 대역 상한 밖이라 발화하지 않는다.
        policy.onHingeAngle(160f, nowMs = 100)

        assertFalse(policy.isAngleStable(nowMs = 800))
        assertFalse(policy.shouldTriggerNow(nowMs = 5_000))
    }

    @Test
    fun `no hinge samples at all keeps the legacy debounce-only semantics`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)

        // 힌지 센서가 없거나 구독에 실패한 기기: 각도 게이트를 통과로 취급해 기능 전체가 죽지 않게 한다.
        assertTrue(policy.isAngleStable(nowMs = 800))
        assertFalse(policy.shouldTriggerNow(nowMs = 799))
        assertTrue(policy.shouldTriggerNow(nowMs = 800))
    }

    @Test
    fun `exiting the posture clears angle history so stale samples cannot vouch for the next entry`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)
        policy.onHingeAngle(90f, nowMs = 100) // 대역 안 + 이후 침묵 = (이 구간에서는) 정지 증거
        policy.onPosture(FoldPosture.FLAT, nowMs = 200)

        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 300)

        // 재진입 디바운스 만료 시각(1100). 스테일 90도 샘플이 남아 있었다면 정지 분기로 통과했겠지만
        // 이탈이 히스토리를 비웠으므로 "증거 없음" -> 발화 불가.
        assertFalse(policy.isAngleStable(nowMs = 1100))
        assertFalse(policy.shouldTriggerNow(nowMs = 1100))

        // 새 샘플이 들어오고 그 뒤로 침묵이 확인돼야 비로소 발화한다.
        policy.onHingeAngle(90f, nowMs = 1200)
        assertFalse(policy.shouldTriggerNow(nowMs = 1799))
        assertTrue(policy.shouldTriggerNow(nowMs = 1800))
    }

    @Test
    fun `disarm clears angle history as well`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)
        policy.onHingeAngle(90f, nowMs = 100)

        policy.disarm()

        // 게이트 거부 후에는 서비스가 센서 구독을 끊는다 — 남은 샘플은 스테일이므로 버려져야 한다.
        assertFalse(policy.isAngleStable(nowMs = 800))
    }

    @Test
    fun `angle instability defers the trigger without consuming the arm`() {
        val policy = FlexModePolicy()
        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)
        policy.onHingeAngle(130f, nowMs = 600)
        policy.onHingeAngle(100f, nowMs = 700)
        policy.onHingeAngle(70f, nowMs = 800) // 대역 안이지만 스프레드 60도 — 아직 접히는 중

        assertFalse(policy.shouldTriggerNow(nowMs = 800))
        assertTrue("각도 불안정은 arm 을 소모하지 않는다", policy.isArmed)

        // 같은 진입 구간에서 각도가 멎으면(마지막 샘플 t=800 이후 600ms 침묵) 그때 발화한다 —
        // 서비스는 이 재확인을 조건 폴링으로 수행한다(ADR-2).
        assertTrue(policy.shouldTriggerNow(nowMs = 1400))
        assertFalse(policy.isArmed)
    }

    @Test
    fun `isArmed reflects entry, trigger consumption and exit`() {
        val policy = FlexModePolicy()
        assertFalse(policy.isArmed)

        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 0)
        assertTrue(policy.isArmed)

        assertTrue(policy.shouldTriggerNow(nowMs = 800))
        assertFalse(policy.isArmed)

        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 900) // 중복 보고 — 재무장 아님
        assertFalse(policy.isArmed)

        policy.onPosture(FoldPosture.FLAT, nowMs = 1000)
        assertFalse(policy.isArmed)

        policy.onPosture(FoldPosture.HALF_OPENED_HORIZONTAL, nowMs = 1100) // 재접기 = 재무장
        assertTrue(policy.isArmed)
    }
}
