package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClickCyclePlanTest {

    // ── 동결 회귀: 프로파일별 메커니즘 순서 (순서 자체가 계약이다) ─────

    @Test
    fun `PICKER is gesture-first with a11y as the last-resort cycle`() {
        assertEquals(
            listOf(ClickMechanism.GESTURE_TAP, ClickMechanism.GESTURE_TAP, ClickMechanism.A11Y_ACTION),
            ClickCyclePlan.PICKER.mechanisms,
        )
    }

    @Test
    fun `POPUP_SWITCH is a11y-first with gesture as fallback`() {
        assertEquals(
            listOf(ClickMechanism.A11Y_ACTION, ClickMechanism.GESTURE_TAP, ClickMechanism.GESTURE_TAP),
            ClickCyclePlan.POPUP_SWITCH.mechanisms,
        )
    }

    // ── mechanismFor 클램프 ────────────────────────────────────

    @Test
    fun `mechanismFor returns the mechanism at index for in-range cycles`() {
        val plan = ClickCyclePlan.PICKER
        assertEquals(ClickMechanism.GESTURE_TAP, plan.mechanismFor(0))
        assertEquals(ClickMechanism.GESTURE_TAP, plan.mechanismFor(1))
        assertEquals(ClickMechanism.A11Y_ACTION, plan.mechanismFor(2))
    }

    @Test
    fun `mechanismFor clamps to the last mechanism beyond list bounds`() {
        val plan = ClickCyclePlan.PICKER
        assertEquals(ClickMechanism.A11Y_ACTION, plan.mechanismFor(3))
        assertEquals(ClickMechanism.A11Y_ACTION, plan.mechanismFor(100))
    }

    @Test
    fun `mechanismFor clamps to the last mechanism for negative cycle indices too`() {
        // List.getOrElse(index) { default } 는 index>=0 && index<=lastIndex 가 아니면(음수 포함)
        // 항상 defaultValue 람다를 쓴다 — mechanismFor 는 그 람다로 last() 를 넘기므로 음수도 클램프된다.
        val plan = ClickCyclePlan.PICKER
        assertEquals(ClickMechanism.A11Y_ACTION, plan.mechanismFor(-1))
    }

    // ── require 위반 3종 ───────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `zero findSliceMs is rejected`() {
        ClickCyclePlan(findSliceMs = 0, verifySliceMs = 800, mechanisms = listOf(ClickMechanism.A11Y_ACTION))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative findSliceMs is rejected`() {
        ClickCyclePlan(findSliceMs = -1, verifySliceMs = 800, mechanisms = listOf(ClickMechanism.A11Y_ACTION))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `verifySliceMs below MIN_VERIFY_SLICE_MS is rejected`() {
        ClickCyclePlan(findSliceMs = 600, verifySliceMs = 399, mechanisms = listOf(ClickMechanism.A11Y_ACTION))
    }

    @Test
    fun `verifySliceMs exactly at MIN_VERIFY_SLICE_MS boundary is accepted`() {
        val plan = ClickCyclePlan(
            findSliceMs = 600,
            verifySliceMs = ClickCyclePlan.MIN_VERIFY_SLICE_MS,
            mechanisms = listOf(ClickMechanism.A11Y_ACTION),
        )
        assertEquals(400L, plan.verifySliceMs)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty mechanisms list is rejected`() {
        ClickCyclePlan(findSliceMs = 600, verifySliceMs = 800, mechanisms = emptyList())
    }

    // ── 불변식: 두 프로파일 모두 doubleTapTimeout 초과 보장 ───────────

    @Test
    fun `both profiles satisfy the verifySliceMs greater than or equal to 400 invariant`() {
        assertTrue(ClickCyclePlan.PICKER.verifySliceMs >= ClickCyclePlan.MIN_VERIFY_SLICE_MS)
        assertTrue(ClickCyclePlan.POPUP_SWITCH.verifySliceMs >= ClickCyclePlan.MIN_VERIFY_SLICE_MS)
    }

    @Test
    fun `MIN_VERIFY_SLICE_MS constant is exactly 400`() {
        assertEquals(400L, ClickCyclePlan.MIN_VERIFY_SLICE_MS)
    }
}
