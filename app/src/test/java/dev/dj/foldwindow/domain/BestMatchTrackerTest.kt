package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BestMatchTracker] 의 우선순위 규칙 동결 테스트.
 *
 * 이 규칙이 곧 `SplitEntry.firstMatch` 의 "단일 순회 = 구 다중 순회" 등가 주장의 근거이므로,
 * 여기서 깨지면 실기기에서 어떤 노드가 선택되는지가 바뀐다.
 */
class BestMatchTrackerTest {

    // ── 1. 초기 상태 ─────────────────────────────────────────────

    @Test
    fun `initial state has no match and accepts any selector`() {
        val tracker = BestMatchTracker<String>()

        assertNull(tracker.best)
        assertEquals(Int.MAX_VALUE, tracker.bestIndex)
        assertFalse(tracker.isTopPriority())
        assertTrue(tracker.accepts(0))
    }

    // ── 2. 같은 인덱스 중복 매치 → 먼저 만난 값 유지 (등가 근거 ①) ──

    @Test
    fun `offering the same index twice keeps the first value`() {
        val tracker = BestMatchTracker<String>()

        tracker.offer(1, "first")
        tracker.offer(1, "second")

        assertEquals("first", tracker.best)
        assertEquals(1, tracker.bestIndex)
    }

    // ── 3. 나중에 더 낮은 인덱스가 오면 교체 (등가 근거 ②·④) ────────

    @Test
    fun `a later lower index replaces the current best`() {
        val tracker = BestMatchTracker<String>()

        tracker.offer(2, "low-priority")
        assertEquals("low-priority", tracker.best)
        assertEquals(2, tracker.bestIndex)

        tracker.offer(0, "top-priority")

        assertEquals("top-priority", tracker.best)
        assertEquals(0, tracker.bestIndex)
    }

    // ── 4. 이미 낮은 인덱스가 있으면 높은 인덱스는 무시 (등가 근거 ③) ─

    @Test
    fun `a higher index is ignored once a lower index matched`() {
        val tracker = BestMatchTracker<String>()

        tracker.offer(0, "top-priority")
        tracker.offer(1, "worse")
        tracker.offer(5, "much worse")

        assertEquals("top-priority", tracker.best)
        assertEquals(0, tracker.bestIndex)
    }

    // ── 5. accepts 는 selectorIndex >= bestIndex 에서 false ───────

    @Test
    fun `accepts is false for indices at or above the current best index`() {
        val tracker = BestMatchTracker<String>()
        tracker.offer(2, "match")

        assertTrue(tracker.accepts(0))
        assertTrue(tracker.accepts(1))
        assertFalse(tracker.accepts(2)) // 동률 — 먼저 만난 값이 이미 이겼다
        assertFalse(tracker.accepts(3))
    }

    // ── 6. isTopPriority 는 인덱스 0 에서만 true ──────────────────

    @Test
    fun `isTopPriority is true only when index zero matched`() {
        val tracker = BestMatchTracker<String>()
        assertFalse(tracker.isTopPriority())

        tracker.offer(1, "not-top")
        assertFalse(tracker.isTopPriority())

        tracker.offer(0, "top")
        assertTrue(tracker.isTopPriority())
    }
}
