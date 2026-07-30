package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

private const val TAG = "FWNodeActions"

/**
 * [M3] 접근성 노드 순회/클릭 공용 헬퍼.
 *
 * `SplitEntry` 와 `DividerPopupRotator` 에 private 함수 4개
 * (`clickableAncestorOrSelf` / `tapNodeCenter` / `tapPoint` / 트리 DFS)가 중복돼 있었다.
 * 드리프트를 막기 위해 top-level `internal` 로 단일화한다.
 *
 * [W7-C 정정] 구코드 기계 대조 결과: `tapNodeCenter` / `tapPoint` 는 두 파일에서 **바이트 단위로
 * 동일**했고, `clickableAncestorOrSelf` 는 포매팅(줄바꿈·중괄호)만 다르고 **제어 흐름은 동일**,
 * 트리 DFS(`searchNode`)도 재귀 pre-order + `runCatching` 관례가 동일했다. 통합 전 문서가 4개
 * 전부를 "바이트 단위로 동일" 이라 적었으나 그건 과장이라 여기서 바로잡는다(통합 근거 자체는
 * 그대로 유효하다 — 의미가 같은 구현이 두 벌 있으면 드리프트한다).
 *
 * `PaneSwapper` 의 `dispatchTap` / `tapNodeBounds` / `dispatchDoubleTap` / `dispatchNoWait` /
 * `searchClickableNodes` 는 **의도적으로 통합하지 않았다** — `dispatchNoWait` 는
 * `GestureResultCallback` 로그 콜백을 달고 있어 [tapPoint](null 콜백)와 동작이 다르고,
 * `tapNodeBounds` 는 `refresh()` 스테일 가드가 추가로 있다. 통합하면 동작이 바뀐다.
 */

/** 폴링 간격. 세 파일(SplitEntry·DividerPopupRotator·PaneSwapper)이 이미 같은 값을 쓰고 있었다. */
internal const val POLL_INTERVAL_MS = 150L

/** 탭 제스처 재생 duration. 세 파일이 이미 같은 값을 쓰고 있었다. */
internal const val TAP_DURATION_MS = 50L

/** [walk] 재귀 깊이 상한. 초과 서브트리는 잘라내고 경고 로그를 남긴다. */
internal const val MAX_TREE_DEPTH = 50

/**
 * [walk] 기반 셀렉터 탐색의 노드 방문 예산.
 *
 * **`PaneSwapper.MAX_NODES_VISITED`(=500) 와 일부러 다른 값이다.** 500 은 디바이더 팝업
 * (노드 수십 개 규모)의 클릭 가능 노드를 훑는 용도로 실기기에서 통했던 값이고, 이쪽은
 * Recents 런처 **전체 트리**를 훑는 진입 주 경로다. 여기에 500 을 씌우면 런처 트리가
 * 500 노드를 넘는 순간 카드 아이콘을 못 찾아 진입 경로 전체가 죽는다 — 측정 없이 검증된
 * 상수를 주 경로로 확대 적용하는 것이라 CLAUDE.md 함정 #7 위반이다. 그래서 별도 상수를 둔다.
 * (이 값 자체는 "무한 재귀 방어" 목적의 넉넉한 상한이며 실기기 측정값이 아니다.)
 */
internal const val MAX_NODES_VISITED_TREE = 4000

/**
 * [root] 서브트리를 **재귀 전위(pre-order) DFS** 로 순회하며 [visit] 를 호출한다.
 *
 * 순서가 계약이다 — `ArrayDeque.removeLast()` 방식의 스택 변환은 자식을 역순 방문해
 * DFS 순서를 바꾸고, 그러면 어떤 노드가 선택되는지가 달라진다. 스택 변환 금지.
 *
 * 노드 접근([visit]/`childCount`/`getChild`)은 전부 `runCatching` 으로 감싼다
 * (기존 `searchNode` 관례 유지 — 죽은 노드 접근이 순회 전체를 죽이지 않게).
 *
 * @param visit "계속할까?" 를 반환한다. false 면 즉시 전체 순회를 중단한다.
 * @param maxDepth 이 깊이를 넘는 서브트리는 잘라낸다(전체 중단이 아니다). 잘라낼 때는
 *   조용한 실패 금지 원칙에 따라 [Log.w] 로 드러낸다 — 단 폴링 주기(150ms)마다 반복 호출되는
 *   경로이므로 **[walk] 호출당 1회만** 찍는다(로그 폭주 방지).
 * @return 순회가 끝까지 돌았으면 true, [visit] 가 false 를 반환해 중단됐으면 false.
 */
internal fun walk(
    root: AccessibilityNodeInfo,
    maxDepth: Int,
    visit: (AccessibilityNodeInfo) -> Boolean,
): Boolean {
    var depthWarned = false

    fun recurse(node: AccessibilityNodeInfo, depth: Int): Boolean {
        if (depth > maxDepth) {
            if (!depthWarned) {
                depthWarned = true
                Log.w(TAG, "walk: 깊이 상한 $maxDepth 초과 — 해당 서브트리만 잘라냄 (전체 중단 아님)")
            }
            return true
        }
        // [W7-C] 예외 시 "매치 아님 = 계속 순회" 로 떨어뜨린다. 구 `searchNode` 는
        // `runCatching { predicate(node) }.getOrDefault(false)` = "이 노드는 매치가 아님" 으로
        // 처리하고 순회를 계속했다. [visit] 반환값의 의미는 "계속할까?" 이므로, 같은 동작이
        // 되려면 예외 시 기본값이 **true(계속)** 여야 한다.
        //
        // 현재 호출자 2곳(`SplitEntry.firstMatch` / `DividerPopupRotator.findRotateNode`)은 이미
        // 람다 안에서 자체 `runCatching` 을 하므로 이 방어로 오늘의 동작이 바뀌지는 않는다 —
        // 위 KDoc 이 선언한 계약을 **미래 호출자에게도 실제로** 이행하기 위한 것이다
        // (선언만 있고 강제가 없으면 방어 책임이 호출자로 조용히 이동한다).
        val keepGoing = runCatching { visit(node) }.getOrDefault(true)
        if (!keepGoing) return false
        val childCount = runCatching { node.childCount }.getOrDefault(0)
        for (i in 0 until childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            if (!recurse(child, depth + 1)) return false
        }
        return true
    }

    return recurse(root, depth = 0)
}

/** 매치 노드부터 최대 [maxDepth] 단계 부모로 올라가며 isClickable 인 첫 노드(자신 포함)를 찾는다. */
internal fun clickableAncestorOrSelf(
    node: AccessibilityNodeInfo,
    maxDepth: Int = 10,
): AccessibilityNodeInfo? {
    var cur: AccessibilityNodeInfo? = node
    repeat(maxDepth + 1) {
        val n = cur ?: return null
        if (runCatching { n.isClickable }.getOrDefault(false)) return n
        cur = runCatching { n.parent }.getOrNull()
    }
    return null
}

/** 노드 중심 좌표에 [TAP_DURATION_MS] 탭 제스처를 디스패치한다. 반환값은 dispatchGesture 수용 여부. */
internal fun AccessibilityService.tapNodeCenter(node: AccessibilityNodeInfo): Boolean {
    val bounds = Rect()
    runCatching { node.getBoundsInScreen(bounds) }
    if (bounds.isEmpty) return false
    return tapPoint(bounds.centerX(), bounds.centerY())
}

/** 임의 좌표에 [TAP_DURATION_MS] 탭 제스처를 디스패치한다. */
internal fun AccessibilityService.tapPoint(x: Int, y: Int): Boolean {
    val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
    val gesture = GestureDescription.Builder()
        .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS))
        .build()
    return runCatching { dispatchGesture(gesture, null, null) }.getOrDefault(false)
}
