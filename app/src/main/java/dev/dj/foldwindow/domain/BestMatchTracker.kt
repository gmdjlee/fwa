package dev.dj.foldwindow.domain

/**
 * 우선순위 있는 셀렉터 목록을 **단일 트리 순회**로 평가할 때 쓰는 최선 매치 추적기.
 *
 * ## 왜 존재하는가 (P1)
 * 셀렉터가 N개면 기존 구현은 트리를 N번 순회했다(`for (셀렉터) { 전체 DFS }`).
 * 폴링 주기(150ms)마다 반복되므로 비용이 셀렉터 수에 비례해 커진다. 순회를 1회로 줄이되
 * **어떤 노드가 선택되는지는 한 톨도 바뀌면 안 된다** — 그 우선순위 규칙을 Android 의존이 없는
 * 순수 도메인으로 올려 JVM 단위 테스트로 기계 강제하는 것이 이 클래스의 목적이다.
 *
 * ## 동작 등가 논증
 * - **구 동작**: `for (셀렉터 i in 순서) { roots 전체 DFS; 첫 매치면 즉시 반환 }`
 * - **신 동작**: `roots 전체를 DFS 1회; 각 노드에서 [accepts] 인 셀렉터만 평가; [offer]`
 *
 * 두 결과가 같은 근거 4종:
 *  1. **같은 인덱스 중복 매치** — [offer] 가 엄격 부등호(`<`)를 쓰므로 같은 인덱스에서는
 *     **먼저 만난 노드가 유지**된다. 구 코드의 "셀렉터별 DFS 첫 매치" 와 동일하다
 *     (순회 순서가 pre-order 로 동일하다는 전제 — 그래서 `walk` 는 재귀 pre-order 를 유지한다).
 *  2. **낮은 인덱스가 나중에 나오는 경우** — [offer] 가 교체한다. 구 코드는 낮은 인덱스
 *     셀렉터로 트리를 **먼저** 돌기 때문에 어차피 그 노드를 골랐다 → 같은 결과.
 *  3. **높은 인덱스** — [accepts] 가 false 라 술어 평가 자체를 건너뛴다. 구 코드도 낮은
 *     인덱스가 이미 매치됐으면 높은 인덱스 셀렉터를 아예 돌리지 않았다 → 같은 결과.
 *  4. **멀티 루트** — 루트를 순서대로 순회하고 [isTopPriority] 일 때만 루트 루프를 끊으므로,
 *     뒤쪽 루트에서 나온 더 낮은 인덱스 매치도 앞쪽 루트의 높은 인덱스 매치를 정상적으로 이긴다.
 *     구 코드도 셀렉터 루프가 바깥이라 낮은 인덱스가 모든 루트를 훑은 뒤에야 다음 셀렉터로
 *     넘어갔으므로 동일하다.
 *
 * 순수 Kotlin (Android 의존 0) — `domain/` 규칙 준수.
 */
class BestMatchTracker<T> {

    /** 현재 최선 매치를 만든 셀렉터 인덱스. 아직 매치가 없으면 [Int.MAX_VALUE]. */
    var bestIndex: Int = Int.MAX_VALUE
        private set

    /** 현재 최선 매치 값. 아직 매치가 없으면 null. */
    var best: T? = null
        private set

    /** [selectorIndex] 가 현재 최선을 이길 수 있는가. false 면 술어 평가 자체를 건너뛴다. */
    fun accepts(selectorIndex: Int): Boolean = selectorIndex < bestIndex

    /**
     * [selectorIndex] 의 매치 [value] 를 제안한다.
     *
     * 엄격 부등호(`<`)이므로 **같은 인덱스에서는 먼저 만난 값이 유지**된다
     * = 기존 "셀렉터별 DFS 첫 매치" 와 동일.
     */
    fun offer(selectorIndex: Int, value: T) {
        if (selectorIndex < bestIndex) {
            bestIndex = selectorIndex
            best = value
        }
    }

    /**
     * 0순위(최우선) 셀렉터가 이미 매치됐는가. true 면 더 나은 결과가 나올 수 없으므로
     * 호출자는 순회를 조기 종료해도 된다.
     */
    fun isTopPriority(): Boolean = bestIndex == 0
}
