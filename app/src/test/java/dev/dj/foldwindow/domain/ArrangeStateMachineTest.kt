package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangeStateMachineTest {

    private fun reduce(
        state: ArrangeState,
        event: ArrangeEvent,
        config: ArrangeConfig = ArrangeConfig(),
    ) = ArrangeStateMachine.reduce(state, event, config)

    // ── 해피 패스 ──────────────────────────────────────────────

    @Test
    fun `happy path full flow emits expected effects at every step`() {
        val config = ArrangeConfig()

        var t = reduce(ArrangeState.Idle, ArrangeEvent.Start(nowMs = 0, targetDividerCenterY = 1000), config)
        assertEquals(ArrangeState.CheckingSplit(since = 0, targetY = 1000), t.state)
        assertEquals(listOf(ArrangeEffect.QuerySplitState), t.effects)

        t = reduce(t.state, ArrangeEvent.SplitStateResult(nowMs = 10, active = false), config)
        assertEquals(ArrangeState.EnteringSplit(step = 1, attempt = 1, stepSince = 10, targetY = 1000), t.state)
        assertEquals(listOf(ArrangeEffect.PerformEntryStep(1)), t.effects)

        for (step in 1..4) {
            val stepNow = 10L + step * 10
            t = reduce(t.state, ArrangeEvent.EntryStepResult(nowMs = stepNow, success = true), config)
            if (step < 4) {
                assertEquals(
                    ArrangeState.EnteringSplit(step = step + 1, attempt = 1, stepSince = stepNow, targetY = 1000),
                    t.state,
                )
                assertEquals(listOf(ArrangeEffect.PerformEntryStep(step + 1)), t.effects)
            } else {
                assertEquals(ArrangeState.WaitingDivider(since = stepNow, targetY = 1000), t.state)
                assertEquals(listOf(ArrangeEffect.QueryDivider), t.effects)
            }
        }

        t = reduce(t.state, ArrangeEvent.DividerResult(nowMs = 200, centerY = 500), config)
        assertEquals(ArrangeState.Dragging(since = 200, targetY = 1000, adjustedOnce = false, lastShotAt = null), t.state)
        assertEquals(listOf(ArrangeEffect.DragDividerTo(1000)), t.effects)

        t = reduce(t.state, ArrangeEvent.DragResult(nowMs = 300, completed = true), config)
        assertEquals(ArrangeState.Verifying(since = 300, targetY = 1000, adjustedOnce = false, lastShotAt = 300), t.state)
        assertEquals(listOf(ArrangeEffect.MeasureLetterbox(notBeforeMs = 300)), t.effects)

        t = reduce(t.state, ArrangeEvent.MeasureResult(nowMs = 1500, residualPx = 3, correctedTargetY = null), config)
        assertEquals(ArrangeState.Done(verified = true, finalResidualPx = 3, adjusted = false), t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun `already active split skips entry steps`() {
        val start = reduce(ArrangeState.Idle, ArrangeEvent.Start(nowMs = 0, targetDividerCenterY = 1000))
        val t = reduce(start.state, ArrangeEvent.SplitStateResult(nowMs = 5, active = true))
        assertEquals(ArrangeState.WaitingDivider(since = 5, targetY = 1000), t.state)
        assertEquals(listOf(ArrangeEffect.QueryDivider), t.effects)
    }

    // ── 스플릿 체크 ────────────────────────────────────────────

    @Test
    fun `split check times out`() {
        val config = ArrangeConfig(splitCheckTimeoutMs = 2000)
        val state = ArrangeState.CheckingSplit(since = 0, targetY = 1000)
        val t = reduce(state, ArrangeEvent.Tick(nowMs = 2001), config)
        assertEquals(ArrangeState.Failed(FailureReason.SPLIT_CHECK_TIMEOUT), t.state)
    }

    // ── 진입 단계 ──────────────────────────────────────────────

    @Test
    fun `entry step failure retries before succeeding`() {
        val entering = ArrangeState.EnteringSplit(step = 2, attempt = 1, stepSince = 100, targetY = 1000)

        val failT = reduce(entering, ArrangeEvent.EntryStepResult(nowMs = 150, success = false))
        assertEquals(ArrangeState.EnteringSplit(step = 2, attempt = 2, stepSince = 150, targetY = 1000), failT.state)
        assertEquals(listOf(ArrangeEffect.PerformEntryStep(2)), failT.effects)

        val successT = reduce(failT.state, ArrangeEvent.EntryStepResult(nowMs = 200, success = true))
        assertEquals(ArrangeState.EnteringSplit(step = 3, attempt = 1, stepSince = 200, targetY = 1000), successT.state)
        assertEquals(listOf(ArrangeEffect.PerformEntryStep(3)), successT.effects)
    }

    @Test
    fun `entry step exhausts attempts and fails`() {
        val config = ArrangeConfig(entryStepMaxAttempts = 3)
        var t = reduce(
            ArrangeState.EnteringSplit(step = 1, attempt = 1, stepSince = 0, targetY = 1000),
            ArrangeEvent.EntryStepResult(nowMs = 10, success = false),
            config,
        )
        assertEquals(2, (t.state as ArrangeState.EnteringSplit).attempt)

        t = reduce(t.state, ArrangeEvent.EntryStepResult(nowMs = 20, success = false), config)
        assertEquals(3, (t.state as ArrangeState.EnteringSplit).attempt)

        t = reduce(t.state, ArrangeEvent.EntryStepResult(nowMs = 30, success = false), config)
        assertEquals(ArrangeState.Failed(FailureReason.ENTRY_STEP_FAILED), t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun `entry step timeout via tick fails`() {
        val config = ArrangeConfig(entryStepTimeoutMs = 3000)
        val state = ArrangeState.EnteringSplit(step = 2, attempt = 1, stepSince = 1000, targetY = 1000)
        val t = reduce(state, ArrangeEvent.Tick(nowMs = 4001), config)
        assertEquals(ArrangeState.Failed(FailureReason.ENTRY_TIMEOUT), t.state)
    }

    @Test
    fun `entry step tick exactly at timeout boundary is not a timeout`() {
        val config = ArrangeConfig(entryStepTimeoutMs = 3000)
        val state = ArrangeState.EnteringSplit(step = 2, attempt = 1, stepSince = 1000, targetY = 1000)
        val t = reduce(state, ArrangeEvent.Tick(nowMs = 4000), config)
        assertEquals(state, t.state)
        assertTrue(t.effects.isEmpty())
    }

    // ── 디바이더 탐색 ──────────────────────────────────────────

    @Test
    fun `divider not found yet keeps polling and preserves since`() {
        val state = ArrangeState.WaitingDivider(since = 500, targetY = 1000)
        val t = reduce(state, ArrangeEvent.DividerResult(nowMs = 600, centerY = null))
        assertEquals(ArrangeState.WaitingDivider(since = 500, targetY = 1000), t.state)
        assertEquals(listOf(ArrangeEffect.QueryDivider), t.effects)
    }

    @Test
    fun `divider search times out`() {
        val config = ArrangeConfig(dividerTimeoutMs = 4000)
        val state = ArrangeState.WaitingDivider(since = 1000, targetY = 1000)
        val t = reduce(state, ArrangeEvent.Tick(nowMs = 5001), config)
        assertEquals(ArrangeState.Failed(FailureReason.DIVIDER_NOT_FOUND), t.state)
    }

    @Test
    fun `divider within tolerance skips drag and goes straight to verifying`() {
        val config = ArrangeConfig(dividerTolerancePx = 4)
        val state = ArrangeState.WaitingDivider(since = 500, targetY = 1000)
        val t = reduce(state, ArrangeEvent.DividerResult(nowMs = 600, centerY = 1003), config)
        assertEquals(
            ArrangeState.Verifying(since = 600, targetY = 1000, adjustedOnce = false, lastShotAt = 600),
            t.state,
        )
        assertEquals(listOf(ArrangeEffect.MeasureLetterbox(notBeforeMs = 600)), t.effects)
    }

    // ── 드래그 ────────────────────────────────────────────────

    @Test
    fun `drag cancelled fails immediately`() {
        val state = ArrangeState.Dragging(since = 100, targetY = 1000, adjustedOnce = false, lastShotAt = null)
        val t = reduce(state, ArrangeEvent.DragResult(nowMs = 200, completed = false))
        assertEquals(ArrangeState.Failed(FailureReason.DRAG_FAILED), t.state)
    }

    @Test
    fun `drag times out`() {
        val config = ArrangeConfig(dragTimeoutMs = 3000)
        val state = ArrangeState.Dragging(since = 1000, targetY = 1000, adjustedOnce = false, lastShotAt = null)
        val t = reduce(state, ArrangeEvent.Tick(nowMs = 4001), config)
        assertEquals(ArrangeState.Failed(FailureReason.DRAG_TIMEOUT), t.state)
    }

    // ── 검증 & ADR-5 1회 보정 ───────────────────────────────────

    @Test
    fun `verify within tolerance completes successfully`() {
        val config = ArrangeConfig(residualTolerancePx = 8)
        val state = ArrangeState.Verifying(since = 300, targetY = 1000, adjustedOnce = false, lastShotAt = 300)
        val t = reduce(state, ArrangeEvent.MeasureResult(nowMs = 1500, residualPx = 8, correctedTargetY = null), config)
        assertEquals(ArrangeState.Done(verified = true, finalResidualPx = 8, adjusted = false), t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun `residual exceeds tolerance triggers exactly one correction then succeeds`() {
        val config = ArrangeConfig(residualTolerancePx = 8, screenshotMinIntervalMs = 1100)
        val verifying = ArrangeState.Verifying(since = 300, targetY = 1000, adjustedOnce = false, lastShotAt = 300)

        val correctT = reduce(
            verifying,
            ArrangeEvent.MeasureResult(nowMs = 1500, residualPx = 20, correctedTargetY = 1010),
            config,
        )
        assertEquals(
            ArrangeState.Dragging(since = 1500, targetY = 1010, adjustedOnce = true, lastShotAt = 300),
            correctT.state,
        )
        assertEquals(listOf(ArrangeEffect.DragDividerTo(1010)), correctT.effects)

        val dragDoneT = reduce(correctT.state, ArrangeEvent.DragResult(nowMs = 1600, completed = true), config)
        val verifying2 = dragDoneT.state as ArrangeState.Verifying
        assertTrue(verifying2.adjustedOnce)
        assertEquals(1010, verifying2.targetY)

        val finalT = reduce(
            dragDoneT.state,
            ArrangeEvent.MeasureResult(nowMs = 3000, residualPx = 4, correctedTargetY = null),
            config,
        )
        assertEquals(ArrangeState.Done(verified = true, finalResidualPx = 4, adjusted = true), finalT.state)
    }

    @Test
    fun `residual still exceeds after single correction reports residual without further drag`() {
        val config = ArrangeConfig(residualTolerancePx = 8)
        val verifying = ArrangeState.Verifying(since = 1600, targetY = 1010, adjustedOnce = true, lastShotAt = 1600)

        val t = reduce(
            verifying,
            ArrangeEvent.MeasureResult(nowMs = 3000, residualPx = 15, correctedTargetY = 1020),
            config,
        )
        assertEquals(ArrangeState.Done(verified = true, finalResidualPx = 15, adjusted = true), t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun `closedLoopCorrection disabled reports residual honestly without correction drag`() {
        val config = ArrangeConfig(residualTolerancePx = 8, closedLoopCorrection = false)
        val verifying = ArrangeState.Verifying(since = 300, targetY = 1000, adjustedOnce = false, lastShotAt = 300)

        val t = reduce(
            verifying,
            ArrangeEvent.MeasureResult(nowMs = 1500, residualPx = 224, correctedTargetY = 1235),
            config,
        )
        assertEquals(ArrangeState.Done(verified = true, finalResidualPx = 224, adjusted = false), t.state)
        assertTrue(t.effects.isEmpty())
    }

    @Test
    fun `closedLoopCorrection default true preserves existing single-correction behavior`() {
        val config = ArrangeConfig(residualTolerancePx = 8)
        assertTrue(config.closedLoopCorrection)
        val verifying = ArrangeState.Verifying(since = 300, targetY = 1000, adjustedOnce = false, lastShotAt = 300)

        val correctT = reduce(
            verifying,
            ArrangeEvent.MeasureResult(nowMs = 1500, residualPx = 20, correctedTargetY = 1010),
            config,
        )
        assertEquals(
            ArrangeState.Dragging(since = 1500, targetY = 1010, adjustedOnce = true, lastShotAt = 300),
            correctT.state,
        )
        assertEquals(listOf(ArrangeEffect.DragDividerTo(1010)), correctT.effects)
    }

    @Test
    fun `measure failure yields unverified done`() {
        val verifying = ArrangeState.Verifying(since = 300, targetY = 1000, adjustedOnce = false, lastShotAt = 300)
        val t = reduce(verifying, ArrangeEvent.MeasureResult(nowMs = 1500, residualPx = null, correctedTargetY = null))
        assertEquals(ArrangeState.Done(verified = false, finalResidualPx = null, adjusted = false), t.state)
    }

    @Test
    fun `verify timeout yields unverified done`() {
        val config = ArrangeConfig(verifyTimeoutMs = 5000)
        val verifying = ArrangeState.Verifying(since = 1000, targetY = 1000, adjustedOnce = false, lastShotAt = 1000)
        val t = reduce(verifying, ArrangeEvent.Tick(nowMs = 6001), config)
        assertEquals(ArrangeState.Done(verified = false, finalResidualPx = null, adjusted = false), t.state)
    }

    // ── 스크린샷 레이트 리밋 (함정 #3) ─────────────────────────

    @Test
    fun `rate limit enforces minimum interval between measurements`() {
        val config = ArrangeConfig(screenshotMinIntervalMs = 1100, residualTolerancePx = 8)
        val verifying = ArrangeState.Verifying(since = 300, targetY = 1000, adjustedOnce = false, lastShotAt = 300)

        val correctT = reduce(
            verifying,
            ArrangeEvent.MeasureResult(nowMs = 350, residualPx = 20, correctedTargetY = 1010),
            config,
        )
        val dragging = correctT.state as ArrangeState.Dragging
        assertEquals(300L, dragging.lastShotAt)

        // 드래그가 빠르게 끝나도(레이트 리밋 윈도우 이전) notBeforeMs 는 그 윈도우를 지켜야 한다.
        val dragDoneT = reduce(dragging, ArrangeEvent.DragResult(nowMs = 400, completed = true), config)
        val measureEffect = dragDoneT.effects.single() as ArrangeEffect.MeasureLetterbox
        assertTrue(measureEffect.notBeforeMs >= 300 + 1100)
    }

    // ── 취소 ──────────────────────────────────────────────────

    @Test
    fun `cancel during entry or drag fails with CANCELLED`() {
        val entering = ArrangeState.EnteringSplit(step = 2, attempt = 1, stepSince = 100, targetY = 1000)
        val t1 = reduce(entering, ArrangeEvent.Cancel(nowMs = 150))
        assertEquals(ArrangeState.Failed(FailureReason.CANCELLED), t1.state)

        val dragging = ArrangeState.Dragging(since = 100, targetY = 1000, adjustedOnce = false, lastShotAt = null)
        val t2 = reduce(dragging, ArrangeEvent.Cancel(nowMs = 200))
        assertEquals(ArrangeState.Failed(FailureReason.CANCELLED), t2.state)
    }

    // ── 5단계 진입 레시피 (MENU, UNRESIZEABLE 전용) ─────────────

    @Test
    fun `five step entry recipe succeeds through all steps then waits for divider`() {
        val config = ArrangeConfig(entryStepCount = 5)

        var t = reduce(ArrangeState.Idle, ArrangeEvent.Start(nowMs = 0, targetDividerCenterY = 1000), config)
        t = reduce(t.state, ArrangeEvent.SplitStateResult(nowMs = 10, active = false), config)
        assertEquals(ArrangeState.EnteringSplit(step = 1, attempt = 1, stepSince = 10, targetY = 1000), t.state)
        assertEquals(listOf(ArrangeEffect.PerformEntryStep(1)), t.effects)

        for (step in 1..5) {
            val stepNow = 10L + step * 10
            t = reduce(t.state, ArrangeEvent.EntryStepResult(nowMs = stepNow, success = true), config)
            if (step < 5) {
                assertEquals(
                    ArrangeState.EnteringSplit(step = step + 1, attempt = 1, stepSince = stepNow, targetY = 1000),
                    t.state,
                )
                assertEquals(listOf(ArrangeEffect.PerformEntryStep(step + 1)), t.effects)
            } else {
                assertEquals(ArrangeState.WaitingDivider(since = stepNow, targetY = 1000), t.state)
                assertEquals(listOf(ArrangeEffect.QueryDivider), t.effects)
            }
        }
    }

    @Test
    fun `five step entry recipe fails when step5 exhausts retry attempts`() {
        val config = ArrangeConfig(entryStepCount = 5, entryStepMaxAttempts = 3)

        var t = reduce(
            ArrangeState.EnteringSplit(step = 5, attempt = 1, stepSince = 0, targetY = 1000),
            ArrangeEvent.EntryStepResult(nowMs = 10, success = false),
            config,
        )
        assertEquals(2, (t.state as ArrangeState.EnteringSplit).attempt)

        t = reduce(t.state, ArrangeEvent.EntryStepResult(nowMs = 20, success = false), config)
        assertEquals(3, (t.state as ArrangeState.EnteringSplit).attempt)

        t = reduce(t.state, ArrangeEvent.EntryStepResult(nowMs = 30, success = false), config)
        assertEquals(ArrangeState.Failed(FailureReason.ENTRY_STEP_FAILED), t.state)
        assertTrue(t.effects.isEmpty())
    }

    // ── 스테일 이벤트 무시 ────────────────────────────────────

    @Test
    fun `stale or mismatched events are ignored`() {
        val idleT = reduce(ArrangeState.Idle, ArrangeEvent.DividerResult(nowMs = 10, centerY = 500))
        assertEquals(ArrangeState.Idle, idleT.state)
        assertTrue(idleT.effects.isEmpty())

        val done = ArrangeState.Done(verified = true, finalResidualPx = 0, adjusted = false)
        val doneT = reduce(done, ArrangeEvent.Tick(nowMs = 999))
        assertEquals(done, doneT.state)
        assertTrue(doneT.effects.isEmpty())

        val dragging = ArrangeState.Dragging(since = 100, targetY = 1000, adjustedOnce = false, lastShotAt = null)
        val draggingT = reduce(dragging, ArrangeEvent.SplitStateResult(nowMs = 150, active = true))
        assertEquals(dragging, draggingT.state)
        assertTrue(draggingT.effects.isEmpty())

        val failed = ArrangeState.Failed(FailureReason.DRAG_FAILED)
        val failedT = reduce(failed, ArrangeEvent.Cancel(nowMs = 300))
        assertEquals(failed, failedT.state)
        assertFalse(failedT.state == ArrangeState.Failed(FailureReason.CANCELLED))
    }
}
