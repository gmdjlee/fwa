package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import dev.dj.foldwindow.domain.IntRect

/**
 * 디바이더 핸들 탭 → 팝업의 "시계 방향으로 회전" 노드 클릭 공용 헬퍼.
 *
 * [실측 2026-07-25] `SplitEntry.menuStep5`(UNRESIZEABLE 앱 진입 경로의 좌우→상하 1회 회전)와
 * `ArrangerAccessibilityService` 의 위치 교정 폴백(`PaneSwapper.swap` 실패 시 회전×2 로
 * 상하 페인을 맞교환)이 동일한 "핸들 재조회 → 핸들 중심 탭 → 회전 팝업 노드 폴링·클릭" 로직을
 * 필요로 해 이 클래스로 추출했다. 셀렉터 상수(`ROTATE_DESC_*`)도 이 파일 한 곳에만 있다.
 *
 * ADR-2 준수: 모든 대기는 조건 폴링(`withTimeoutOrNull` + `delay` 루프) + 데드라인이며
 * 고정 지연은 쓰지 않는다. 탭/클릭 제스처의 duration 은 재생 파라미터일 뿐 완료 판정 근거가 아니다.
 *
 * [M3, 2026-07-29] `pollUntil`/`clickWhenFound`/`clickableAncestorOrSelf`/`tapNodeCenter`/
 * `tapPoint` 를 각각 platform/Polling.kt, platform/NodeActions.kt 로 단일화했다
 * (폴링 루프 구현체도 그쪽에 있다).
 *
 * [W7-C 정정] 구코드 기계 대조 결과는 "바이트 단위로 동일" 이 아니었다:
 *  - `tapNodeCenter` / `tapPoint` — `SplitEntry` 판과 **바이트 단위로 동일**
 *  - `clickableAncestorOrSelf` / `pollUntil` — 포매팅(줄바꿈·중괄호)만 다르고 의미 동일
 *  - `pollForValue` — 이 클래스에는 **아예 없었다**(중복 통합이 아니라 단순 이동)
 *  - `clickWhenFound` — **동일하지 않았다.** 제어 흐름은 완전히 같았지만 로그가 달라
 *    (여기엔 `text=/desc=` 라벨 계산도, `clicked-self`/`clicked-ancestor` 구분도 없었다)
 *    더 자세한 `SplitEntry` 판을 채택했다.
 *
 * 그 결과 이 클래스에서 실제로 바뀌는 로그는 **2건**이다(둘 다 `FWDividerRotator` 태그,
 * 회전 폴백 경로 전용 — 실기기 DoD 대조 대상 로그 문자열은 아니다):
 *  1. `clickWhenFound: [$what] clicked`
 *     → `clickWhenFound: [$what] clicked-self|clicked-ancestor (text=…/desc=…)`
 *  2. `clickWhenFound: [$what] gesture-tap-fallback`
 *     → `clickWhenFound: [$what] gesture-tap-fallback (text=…/desc=…)`
 */
class DividerPopupRotator(private val service: AccessibilityService) {

    private val dividerLocator = DividerLocator()

    /**
     * 회전 1회 시도: 디바이더 핸들 재조회 → 핸들 중심 탭 → "시계 방향으로 회전" 팝업 노드
     * 폴링·클릭 → [settled] 조건 폴링. 핸들을 못 찾거나 회전 노드를 못 찾거나 클릭 후
     * [settled] 이 [timeoutMs] 안에 true 가 되지 않으면 false.
     *
     * @param screen 현재 화면 전체 rect. 호출자가 WindowMetrics/displayMetrics 로 채운다
     * @param timeoutMs 이 시도 전체(핸들 조회 ~ settled 확인)에 허용된 예산
     * @param settled 회전 클릭 후 "정착"으로 볼 조건 (예: 좌우/상하 분할 판정)
     */
    suspend fun rotateOnce(screen: IntRect, timeoutMs: Long, settled: () -> Boolean): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        fun remaining() = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)

        val windowList = runCatching { service.windows }.getOrDefault(emptyList())
        val handle = dividerLocator.locate(windowList, screen)
        if (handle == null) {
            Log.w(TAG, "rotateOnce: 디바이더 핸들을 찾지 못함")
            return false
        }

        val tapped = service.tapPoint(handle.centerX, handle.centerY)
        if (!tapped) {
            Log.w(TAG, "rotateOnce: 핸들 탭 제스처 디스패치 실패 — 그래도 회전 노드 폴링 계속")
        }

        val rotateClicked = service.clickWhenFound(remaining(), "rotateOnce rotate-node", TAG) { findRotateNode() }
        if (!rotateClicked) {
            Log.w(TAG, "rotateOnce: 회전 노드 발견/클릭 실패")
            return false
        }

        return pollUntil(remaining(), settled)
    }

    /**
     * 팝업은 launcher 가 아니라 시스템 오버레이 창일 수 있어 전체 windows 의 root 를 뒤진다.
     *
     * [M3, 2026-07-29] 자체 `searchNode` 재귀를 공용 [walk] 로 대체했다. 재귀 pre-order 순회와
     * `runCatching` 관례가 그대로이므로 어떤 노드가 선택되는지는 바뀌지 않는다 — 유일한 차이는
     * 깊이 상한([MAX_TREE_DEPTH]) 이 생긴 것(기존엔 상한 없음).
     */
    private fun findRotateNode(): AccessibilityNodeInfo? {
        val roots = runCatching { service.windows.mapNotNull { it.root } }.getOrDefault(emptyList())
        for (root in roots) {
            var found: AccessibilityNodeInfo? = null
            walk(root, maxDepth = MAX_TREE_DEPTH) { node ->
                val match = runCatching {
                    val desc = node.contentDescription?.toString()
                    (desc != null && desc.contains(ROTATE_DESC_KO)) ||
                        (desc != null && desc.lowercase().contains(ROTATE_DESC_EN))
                }.getOrDefault(false)
                if (match) found = node
                !match // 첫 매치에서 즉시 순회 중단 = 기존 searchNode 의 조기 반환과 동일
            }
            found?.let { return it }
        }
        return null
    }

    companion object {
        private const val TAG = "FWDividerRotator"

        // [측정] 디바이더 핸들 탭 팝업 노드 3종(content-desc) 중 회전 버튼.
        // docs/DEVICE_FACTS.md "분할 진입 전략" 실측값이 근거다.
        const val ROTATE_DESC_KO = "시계 방향으로 회전"

        // [미검증] 영어 로케일 후보
        const val ROTATE_DESC_EN = "rotate"
    }
}
