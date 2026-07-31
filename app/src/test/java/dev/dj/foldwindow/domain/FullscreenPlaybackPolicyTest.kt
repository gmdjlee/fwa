package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [FullscreenPlaybackPolicy] 의 콜드스타트 보호 · 비대칭 히스테리시스 · UNKNOWN 무시 의미론 검증
 * (docs/DESIGN_30_FULLSCREEN_AUTO.md §5 의 1~13번).
 *
 * [FlexModePolicyTest] 와 동일하게 nowMs 를 전부 명시적으로 넘겨 100% 결정적으로 검증한다 —
 * 실제 창 이벤트도 슬립도 없다. 기본 상수(진입 디바운스 3000ms / 이탈 유지 1200ms)를 그대로 쓰며
 * 경계값은 설계서가 명시한 숫자를 그대로 옮겼다.
 */
class FullscreenPlaybackPolicyTest {

    // ── 콜드스타트 보호 (D5) ─────────────────────────────────────

    @Test
    fun `first signal after construction records baseline and does not arm`() {
        val policy = FullscreenPlaybackPolicy()

        val scheduled = policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 0)

        // 전체화면 재생 중에 접근성 서비스가 리바인드된 상황 — 첫 샘플은 베이스라인일 뿐이다.
        assertNull(scheduled)
        assertFalse(policy.isArmed)
        assertFalse(policy.shouldTriggerNow(nowMs = 100_000))
    }

    @Test
    fun `reset restores cold-start guard`() {
        val policy = FullscreenPlaybackPolicy()
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 0)
        assertEquals(3100L, policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 100))

        policy.reset()

        assertNull(policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 200))
        assertFalse(policy.isArmed)
        assertEquals(FullscreenSignal.FULLSCREEN, policy.lastSignal)
    }

    // ── 진입 엣지와 디바운스 ─────────────────────────────────────

    @Test
    fun `not-fullscreen baseline then fullscreen arms and schedules entry debounce`() {
        val policy = FullscreenPlaybackPolicy()
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 0)

        val scheduled = policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 100)

        assertEquals(100L + FullscreenPlaybackPolicy.DEFAULT_ENTRY_DEBOUNCE_MS, scheduled)
        assertTrue(policy.isArmed)
        assertEquals(FullscreenSignal.FULLSCREEN, policy.lastSignal)
    }

    @Test
    fun `duplicate fullscreen samples inside candidate window do not reschedule`() {
        val policy = FullscreenPlaybackPolicy()
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 0)
        assertEquals(3100L, policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 100))

        val duplicate = policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 200)

        assertNull(duplicate)
        // 재보고 시각(200)이 아니라 최초 진입 시각(100) 기준으로 디바운스가 판정돼야 한다.
        assertFalse(policy.shouldTriggerNow(nowMs = 3099))
        assertTrue(policy.shouldTriggerNow(nowMs = 3100))
    }

    @Test
    fun `shouldTriggerNow is false before debounce elapses`() {
        val policy = FullscreenPlaybackPolicy()
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 0)
        policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 100)

        assertFalse(policy.shouldTriggerNow(nowMs = 3099))
        assertTrue(policy.shouldTriggerNow(nowMs = 3100))
    }

    @Test
    fun `shouldTriggerNow consumes arm exactly once`() {
        val policy = FullscreenPlaybackPolicy()
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 0)
        policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 100)

        assertTrue(policy.shouldTriggerNow(nowMs = 3100))
        assertFalse(policy.shouldTriggerNow(nowMs = 3100))
        assertFalse(policy.shouldTriggerNow(nowMs = 5_000))
        assertFalse(policy.isArmed)
    }

    // ── 비대칭 히스테리시스 (D9) ─────────────────────────────────

    @Test
    fun `brief not-fullscreen blip shorter than exitHold does not reset entry debounce`() {
        val policy = FullscreenPlaybackPolicy()
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 0)
        policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 100)

        // 플레이어 컨트롤 표시/창 목록 비원자 재구축이 만드는 400ms 짜리 깜빡임(< 1200ms).
        assertNull(policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 500))
        assertTrue("이탈 유지 시간 미달 — 아직 무장 상태여야 한다", policy.isArmed)
        assertNull(policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 900))

        // 깜빡임은 흡수될 뿐 진입 시각(100)을 900 으로 밀지 않는다.
        assertFalse(policy.shouldTriggerNow(nowMs = 3099))
        assertTrue(policy.shouldTriggerNow(nowMs = 3100))
    }

    @Test
    fun `not-fullscreen held for exitHold disarms and requires new entry edge`() {
        val policy = FullscreenPlaybackPolicy()
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 0)
        policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 100)

        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 500) // 이탈 후보 시작
        assertNull(policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 1699))
        assertTrue("1700 미만은 아직 이탈 확정이 아니다", policy.isArmed)

        assertNull(policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 1701))
        assertFalse(policy.isArmed)
        assertFalse(policy.shouldTriggerNow(nowMs = 3100))

        // 재무장은 새 진입 엣지만이 유일한 경로다.
        val reentry = policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 2000)
        assertEquals(5000L, reentry)
        assertFalse(policy.shouldTriggerNow(nowMs = 4999))
        assertTrue(policy.shouldTriggerNow(nowMs = 5000))
    }

    @Test
    fun `fullscreen held stable does not produce a second entry edge`() {
        val policy = FullscreenPlaybackPolicy()
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 0)
        policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 100)
        assertTrue(policy.shouldTriggerNow(nowMs = 3100))

        // 발화 후에도 창 이벤트는 계속 들어온다 — 전부 동일 상태 중복 보고여야 한다.
        assertNull(policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 3200))
        assertNull(policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 10_000))
        assertFalse(policy.isArmed)
        assertFalse(policy.shouldTriggerNow(nowMs = 20_000))
    }

    // ── UNKNOWN 무시의 안전화 (D6) ───────────────────────────────

    @Test
    fun `unknown between fullscreen samples does not swallow the next entry edge`() {
        val policy = FullscreenPlaybackPolicy()
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 0)
        policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 100)

        assertNull(policy.onSignal(FullscreenSignal.UNKNOWN, nowMs = 500))
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 600) // 이탈 후보 시작
        assertNull(policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 1801)) // 1201ms 유지 → 확정
        assertFalse(policy.isArmed)

        // UNKNOWN 이 이탈을 삼켜 영구 래치로 만들지 않았다 — 다음 진입이 정상적으로 무장한다.
        val reentry = policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 2000)
        assertEquals(5000L, reentry)
        assertTrue(policy.isArmed)
    }

    @Test
    fun `unknown never arms and never disarms`() {
        // 콜드스타트: UNKNOWN 은 베이스라인조차 되지 못한다 — 다음 FULLSCREEN 도 여전히 베이스라인.
        val cold = FullscreenPlaybackPolicy()
        assertNull(cold.onSignal(FullscreenSignal.UNKNOWN, nowMs = 0))
        assertEquals(FullscreenSignal.UNKNOWN, cold.lastSignal)
        assertNull(cold.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 100))
        assertFalse(cold.isArmed)

        // 무장 구간: UNKNOWN 이 arm 을 태우지도, 진입 시각을 옮기지도 않는다.
        val armed = FullscreenPlaybackPolicy()
        armed.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 0)
        armed.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 100)
        assertNull(armed.onSignal(FullscreenSignal.UNKNOWN, nowMs = 500))
        assertNull(armed.onSignal(FullscreenSignal.UNKNOWN, nowMs = 2_000))
        assertTrue(armed.isArmed)
        assertEquals(FullscreenSignal.FULLSCREEN, armed.lastSignal)
        assertTrue(armed.shouldTriggerNow(nowMs = 3100))
    }

    // ── arm 소모 경계 · 게이트 거부 ──────────────────────────────

    @Test
    fun `isTriggerReady does not consume arm`() {
        val policy = FullscreenPlaybackPolicy()
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 0)
        policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 100)

        assertFalse(policy.isTriggerReady(nowMs = 3099))
        // 미디어 게이트가 루프에서 재시도하는 동안 여러 번 조회돼도 arm 이 살아 있어야 한다.
        assertTrue(policy.isTriggerReady(nowMs = 3100))
        assertTrue(policy.isTriggerReady(nowMs = 3350))
        assertTrue(policy.isArmed)

        assertTrue(policy.shouldTriggerNow(nowMs = 3350))
        assertFalse(policy.isTriggerReady(nowMs = 3350))
    }

    @Test
    fun `disarm blocks trigger until exit and re-entry`() {
        val policy = FullscreenPlaybackPolicy()
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 0)
        policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 100)

        policy.disarm() // 게이트 거부 (예: 래치됨 / 세션 진행 중)
        assertFalse(policy.isArmed)
        assertNull(
            "동일 구간의 FULLSCREEN 재보고는 재무장하지 않는다",
            policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 3_000),
        )
        assertFalse(policy.shouldTriggerNow(nowMs = 3100))

        // 이탈이 확정돼야(1200ms 연속 유지) 다음 진입이 새 엣지가 된다.
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 4_000)
        policy.onSignal(FullscreenSignal.NOT_FULLSCREEN, nowMs = 5_200)
        val reentry = policy.onSignal(FullscreenSignal.FULLSCREEN, nowMs = 6_000)

        assertEquals(9_000L, reentry)
        assertTrue(policy.isArmed)
        assertTrue(policy.shouldTriggerNow(nowMs = 9_000))
    }
}
