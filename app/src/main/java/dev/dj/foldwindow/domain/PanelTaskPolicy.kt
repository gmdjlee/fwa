package dev.dj.foldwindow.domain

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙).
 *
 * [DESIGN_27 §3.1 A2 / §3.3] 18차 실기기 프로브 결정 근거:
 *  - [18차 G1] `finish()` 만으로 분할이 해소되고 패널 태스크 카드는 잔존한다(`removeTask` 는
 *    어떤 실측에도 요구되지 않은 초과 동작 — 그 초과분이 결함 #27 의 직접 원인이었다). 이
 *    사실이 서비스 측 finish 격하(A1)의 근거이며, 그 결과 "카드가 남는다"가 이 정책의 전제다.
 *  - [18차 G3] 액티비티가 죽은(Activities=[]) 잔존 카드를 분할 파트너 피커에서 탭해도
 *    **동일 taskId 재사용**으로 stage=side/bottom 정상 낙착한다(전체화면 강탈 0, 자가 가드
 *    로그 침묵). 즉 옛 purgeStalePanelTasks 의 전제("잔존 카드를 탭하면 전체화면 재사용으로
 *    분할 파괴")는 이 기기·OS 에서 반증됐다 — 그 원 실측(2026-07-25)은 launchMode=singleTask
 *    시절 + 버블 숨김(CLAUDE.md 함정 #22) 도입 이전이라 이미 다른 수정으로 해소된 상태였다.
 *
 * 따라서 이 정책은 "패널 태스크를 아예 없앤다"가 아니라 "여러 개 쌓이면 MRU(가장 최근 활성)
 * 1개만 남기고 축소한다"로 재정의된다 — **MRU 1개가 곧 SplitEntry step3(분할 파트너 피커 탭)의
 * 소환원**이므로 절대 제거 대상에 넣지 않는다.
 */

/**
 * FW Panel 소유 여부 판정을 위한 플랫폼 태스크 스냅샷. platform/service 계층
 * (`ActivityManager.AppTask` 등)이 이 형태로 변환해 넘긴다 — 이 파일 자체는 android.app.* 를
 * 전혀 모른다.
 *
 * @param taskId 조회 실패 시 -1. 식별 불가라 제거 대상으로 삼을 수 없다([PanelTaskPolicy.pruneTargets] 참고).
 * @param componentClassName 이 태스크의 base intent 컴포넌트 클래스명. 조회 실패 시 null —
 *   패널이 아닌 것으로 취급해 오제거를 방지한다.
 * @param lastActiveMs 마지막 활성 시각(ms). 공개 API 로 조회 불가한 플랫폼에서는 0 등 동일값을
 *   넣어 순수하게 입력 순서(= 플랫폼이 보장하는 MRU-first 순서)로 타이브레이크되게 한다.
 */
data class PanelTaskSnapshot(
    val taskId: Int,
    val componentClassName: String?,
    val lastActiveMs: Long,
)

/**
 * FW Panel 최근 태스크 카드의 존재·과잉 여부를 판정하는 순수 정책.
 *
 * [DESIGN_27 §3.2/§3.3] `hasPanelTask` 는 #28 폴백 가드([ArrangerAccessibilityService]
 * `performDismissSplit` 의 인텐트 폴백 진입 여부 판정)가 사용한다. `pruneTargets` 가
 * `pruneExtraPanelTasks`(구 `purgeStalePanelTasks`)의 유일한 판정 근거다.
 */
object PanelTaskPolicy {

    /**
     * [panelClassName] 소유 태스크가 [tasks] 안에 하나 이상 있으면 true.
     * `componentClassName == null`(조회 실패)은 패널이 아닌 것으로 취급한다(오탐으로 인한
     * 오판정 방지).
     *
     * 용도: #28 폴백 가드([ArrangerAccessibilityService.hasPanelTask]) — 우리 패널 태스크가
     * 없는데 `FLAG_ACTIVITY_NEW_TASK` 인텐트로 해제를 시도하면 새 태스크가 생겨 base intent
     * 오염(#28)이 발생하므로, 그 인텐트 폴백을 실행해도 되는지의 사전 조건으로 쓰인다.
     */
    fun hasPanelTask(tasks: List<PanelTaskSnapshot>, panelClassName: String): Boolean =
        tasks.any { it.componentClassName == panelClassName }

    /**
     * 패널([panelClassName]) 태스크가 2개 이상 쌓였을 때 MRU(가장 최근 활성) 1개를 제외한
     * 나머지의 [PanelTaskSnapshot.taskId] 목록을 **입력 순서대로** 반환한다. 그 MRU 1개가
     * [18차 G1]·[18차 G3] 이 실증한 step3 의 소환원이므로 절대 제거 대상에 넣지 않는다.
     *
     * - 패널 태스크가 0개 또는 1개면 [emptyList].
     * - MRU 선정 기준은 [PanelTaskSnapshot.lastActiveMs] 최댓값. 동률(예: 플랫폼이
     *   `lastActiveMs` 를 공개 API 로 못 줘서 전부 0인 경우 포함)이면 **입력 목록에서 더
     *   앞에 있는 항목**을 보존한다 — 플랫폼(`ActivityManager.appTasks`)이 MRU-first 순서로
     *   태스크를 넘긴다는 계약에 타이브레이크를 위임한다.
     * - `taskId < 0`(조회 실패로 식별 불가) 인 항목은 반환 목록에서 **제외**한다 — 조작 불가능
     *   하기 때문이다. 다만 MRU(보존) 선정 자체에는 정상적으로 참여한다 — 식별 불가 항목이라고
     *   보존 후보에서 배제하면 진짜 최신 카드가 오제거될 위험이 생긴다.
     */
    fun pruneTargets(tasks: List<PanelTaskSnapshot>, panelClassName: String): List<Int> {
        val panelIndices = tasks.indices.filter { tasks[it].componentClassName == panelClassName }
        if (panelIndices.size <= 1) return emptyList()

        // 동률이면 "입력 목록에서 더 앞" 이 이기도록 엄격 부등호(>)로만 갱신한다.
        var mruIndex = panelIndices.first()
        for (idx in panelIndices.drop(1)) {
            if (tasks[idx].lastActiveMs > tasks[mruIndex].lastActiveMs) mruIndex = idx
        }

        return panelIndices
            .filter { it != mruIndex }
            .map { tasks[it].taskId }
            .filter { it >= 0 }
    }
}
