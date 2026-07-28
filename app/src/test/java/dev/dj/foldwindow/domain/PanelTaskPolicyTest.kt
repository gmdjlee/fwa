package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PanelTaskPolicy] 의 「패널 카드 소환/정리」 의미론 검증 (설계: docs/DESIGN_27_PANEL_CARD.md §3.3).
 *
 * 배경: step3(분할 파트너 피커 탭)는 「FW Panel」 최근 태스크 카드가 recents 에 존재한다는
 * 전제 위에 선다. 기존 `purgeStalePanelTasks` 가 세션 시작마다 그 카드를 전부 지워서
 * 배치 기능 전체가 불능이 됐다(17차 실기기 4회 재현, 18차 purge 자충 물증화).
 * **MRU 패널 태스크 1개 보존은 step3 의 소환원을 지키는 불변식**이며, 이 파일의 핵심은
 * "보존 개수가 정확히 1" 을 못 박는 테스트다.
 */
class PanelTaskPolicyTest {

    private companion object {
        const val PANEL_CLASS = "dev.dj.foldwindow.ui.PanelActivity"
        const val OTHER_CLASS = "com.android.settings.Settings"
    }

    private fun panel(id: Int, last: Long = 0L) = PanelTaskSnapshot(id, PANEL_CLASS, last)
    private fun other(id: Int, last: Long = 0L) = PanelTaskSnapshot(id, OTHER_CLASS, last)
    private fun unknownComponent(id: Int, last: Long = 0L) = PanelTaskSnapshot(id, null, last)

    // ── hasPanelTask / pruneTargets: 해피 패스 ───────────────────────

    @Test
    fun `빈 목록이면 패널 태스크가 없고 정리 대상도 없다`() {
        val tasks = emptyList<PanelTaskSnapshot>()

        assertFalse(PanelTaskPolicy.hasPanelTask(tasks, PANEL_CLASS))
        assertEquals(emptyList<Int>(), PanelTaskPolicy.pruneTargets(tasks, PANEL_CLASS))
    }

    @Test
    fun `패널이 아닌 태스크만 있으면 패널 태스크가 없고 정리 대상도 없다`() {
        val tasks = listOf(other(1), other(2, last = 500))

        assertFalse(PanelTaskPolicy.hasPanelTask(tasks, PANEL_CLASS))
        assertEquals(emptyList<Int>(), PanelTaskPolicy.pruneTargets(tasks, PANEL_CLASS))
    }

    @Test
    fun `패널 태스크가 정확히 1개면 패널이 존재하고 그 하나는 보존되어 정리 대상이 없다`() {
        // 타 컴포넌트가 섞여 있어도 패널 개수 판정에는 영향이 없어야 한다.
        val tasks = listOf(other(1), panel(2, last = 100), other(3))

        assertTrue(PanelTaskPolicy.hasPanelTask(tasks, PANEL_CLASS))
        assertEquals(emptyList<Int>(), PanelTaskPolicy.pruneTargets(tasks, PANEL_CLASS))
    }

    @Test
    fun `패널 태스크 3개 중 lastActiveMs 최댓값 1개만 보존되고 나머지가 입력 순서대로 반환된다`() {
        // MRU = id20 (lastActiveMs=300 최댓값) → 보존. id10, id30 이 입력에 나온 순서 그대로 반환.
        val tasks = listOf(panel(10, last = 100), panel(20, last = 300), panel(30, last = 200))

        assertTrue(PanelTaskPolicy.hasPanelTask(tasks, PANEL_CLASS))
        val result = PanelTaskPolicy.pruneTargets(tasks, PANEL_CLASS)
        assertEquals(listOf(10, 30), result)
        assertEquals(2, result.size) // 반환 크기 == 패널 개수(3) - 보존 1
    }

    // ── componentClassName == null: 오제거 방지 ──────────────────────

    @Test
    fun `componentClassName 이 null 인 항목은 패널 후보에서 제외되어 반환에 나타나지 않는다`() {
        // null 항목(조회 실패, lastActiveMs=999 로 가장 커도)은 패널이 아니므로 MRU 계산에도
        // 참여하지 않고 반환에도 절대 나타나지 않는다. 진짜 패널 중 MRU = id1(last=100) → 보존,
        // id2 만 반환.
        val tasks = listOf(unknownComponent(99, last = 999), panel(1, last = 100), panel(2, last = 50))

        assertTrue(PanelTaskPolicy.hasPanelTask(tasks, PANEL_CLASS))
        val result = PanelTaskPolicy.pruneTargets(tasks, PANEL_CLASS)
        assertEquals(listOf(2), result)
        assertFalse(result.contains(99))
    }

    @Test
    fun `모든 항목이 componentClassName null 이면 패널로 셀 수 없다`() {
        val tasks = listOf(unknownComponent(1, last = 100), unknownComponent(2, last = 200))

        assertFalse(PanelTaskPolicy.hasPanelTask(tasks, PANEL_CLASS))
        assertEquals(emptyList<Int>(), PanelTaskPolicy.pruneTargets(tasks, PANEL_CLASS))
    }

    // ── 동률 lastActiveMs: 실전 주 경로 ───────────────────────────────

    @Test
    fun `lastActiveMs 가 전부 0으로 동률인 실전 주 경로에서는 입력상 첫 번째 패널이 보존된다`() {
        // 실제 배선에서는 lastActiveMs 가 전부 0L 로 들어온다(플랫폼이 appTasks 를 MRU-first 로
        // 넘긴다는 계약). 이때는 값으로 우열을 가릴 수 없으므로 입력에서 더 앞선 id1 을 보존하고
        // id2, id3 을 순서대로 반환해야 한다.
        val tasks = listOf(panel(1, last = 0L), panel(2, last = 0L), panel(3, last = 0L))

        val result = PanelTaskPolicy.pruneTargets(tasks, PANEL_CLASS)
        assertEquals(listOf(2, 3), result)
    }

    // ── taskId < 0 (조회 실패): 제외되지만 보존 대상 선정에는 정상 참여 ──

    @Test
    fun `taskId 가 음수인 패널이 MRU 로 보존 대상이어도 다른 패널들은 정상 반환된다`() {
        // MRU = id-1 (lastActiveMs=300 최댓값). 식별 불가라 반환 목록에는 절대 못 들어가지만,
        // 그것이 "보존"되는 개념적 대상이라는 사실 자체가 나머지 항목(id20, id30) 반환을 막지 않는다.
        val tasks = listOf(panel(-1, last = 300), panel(20, last = 100), panel(30, last = 50))

        val result = PanelTaskPolicy.pruneTargets(tasks, PANEL_CLASS)
        assertEquals(listOf(20, 30), result)
    }

    @Test
    fun `taskId 가 음수인 패널이 MRU 가 아니면 조작 불가라 정리 대상에서도 조용히 제외된다`() {
        // MRU = id10(last=300) → 보존. id-1(last=200)은 MRU 도 아니고 조작 불가(taskId<0)라
        // 반환에서 제외. id30(last=100)만 남아 반환된다.
        val tasks = listOf(panel(10, last = 300), panel(-1, last = 200), panel(30, last = 100))

        val result = PanelTaskPolicy.pruneTargets(tasks, PANEL_CLASS)
        assertEquals(listOf(30), result)
    }

    // ── 반환 순서: lastActiveMs 정렬이 아니라 입력 순서 ────────────────

    @Test
    fun `반환 순서는 lastActiveMs 정렬이 아니라 입력 순서를 따른다`() {
        // MRU = id200(last=999, 입력상 2번째) → 보존. 나머지는 lastActiveMs 내림차순(300>20>10>5 같은
        // 정렬)이 아니라 입력에 나타난 순서 그대로 [100, 300, 400] 반환돼야 한다.
        val tasks = listOf(
            panel(100, last = 10),
            panel(200, last = 999),
            panel(300, last = 20),
            panel(400, last = 5),
        )

        val result = PanelTaskPolicy.pruneTargets(tasks, PANEL_CLASS)
        assertEquals(listOf(100, 300, 400), result)
    }

    // ── 오제거 방지 회귀: 타 앱/타 액티비티는 절대 포함되지 않음 ────────

    @Test
    fun `다른 앱 다른 액티비티 컴포넌트는 정리 대상에 절대 포함되지 않는다`() {
        // 타 컴포넌트(id1000)는 lastActiveMs 가 가장 커도(99999) 애초에 패널 후보가 아니므로
        // MRU 경쟁에 끼지 않고 반환에도 등장하면 안 된다. 진짜 패널 중 MRU = id2(last=20) → 보존,
        // id1 만 반환.
        val tasks = listOf(other(1000, last = 99_999), panel(1, last = 10), panel(2, last = 20))

        val result = PanelTaskPolicy.pruneTargets(tasks, PANEL_CLASS)
        assertEquals(listOf(1), result)
        assertFalse(result.contains(1000))
    }
}
