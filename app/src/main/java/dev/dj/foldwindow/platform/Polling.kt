package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [M3] 조건 폴링 공용 헬퍼 (ADR-2: 고정 지연 금지).
 *
 * `SplitEntry` 와 `DividerPopupRotator` 의 `pollUntil` 은 중괄호 스타일만 다른 동일 구현이었고,
 * `clickWhenFound` 는 **제어 흐름은 동일했으나 로그가 달랐다**(Divider 판에는 `text=/desc=` 라벨
 * 계산도, `clicked-self`/`clicked-ancestor` 구분도 없었다) — 더 자세한 `SplitEntry` 판을 채택해
 * 통합했으므로 Divider 경로의 로그 2줄이 상세해진다(정보 증가 방향, 동작 변화 0).
 * `pollForValue` 는 `SplitEntry` 에만 있었다 — 중복 제거가 아니라 단순 이동이다.
 * 드리프트를 막기 위해 top-level `internal` 로 단일화한다.
 * 모든 대기는 `withTimeoutOrNull` + [POLL_INTERVAL_MS] 루프이며 고정 지연은 쓰지 않는다.
 */

/** [timeoutMs] 안에서 [POLL_INTERVAL_MS] 간격으로 [condition] 이 true 가 될 때까지 대기한다. */
internal suspend fun pollUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
    if (timeoutMs <= 0) return condition()
    return withTimeoutOrNull(timeoutMs) {
        while (!condition()) {
            delay(POLL_INTERVAL_MS)
        }
        true
    } ?: false
}

/**
 * [timeoutMs] 안에서 [POLL_INTERVAL_MS] 간격으로 [find] 가 null 이 아닌 값을 반환할 때까지
 * 폴링한다. 제네릭 버전 — "목표 상태 이미 도달" 대 "유효 노드 확보" 이중 판정처럼, 매 폴링
 * 주기마다 여러 조건을 함께 확인해야 하는 경우에 재사용한다.
 */
internal suspend fun <T> pollForValue(
    timeoutMs: Long,
    find: () -> T?,
): T? {
    if (timeoutMs <= 0) return find()
    return withTimeoutOrNull(timeoutMs) {
        var value = find()
        while (value == null) {
            delay(POLL_INTERVAL_MS)
            value = find()
        }
        value
    }
}

/**
 * [budgetMs] 안에서 [find] 로 노드를 폴링 탐색해 발견 즉시 클릭한다.
 * 트리 갱신 직후 일시적 미조회(윈도우 churn)에 대비해 탐색 자체를 재시도한다.
 * 클릭까지 성공하면 true. 예산 소진 시 false.
 *
 * 클릭 해석 순서 (실측: 라벨 TextView 는 isClickable=false 인 경우가 있어 ACTION_CLICK 이
 * 항상 실패할 수 있음):
 *  1. 매치 노드에서 부모로 올라가며 isClickable 인 조상(또는 자신)에 ACTION_CLICK
 *  2. 실패 시 매치 노드 중심 좌표에 [TAP_DURATION_MS] 탭 제스처 디스패치
 *     (성공 여부는 호출자의 성공 조건 폴링이 판정)
 *
 * @param tag 호출자별 logcat 태그. 통합 전 각 클래스의 TAG 를 그대로 넘겨 로그 출처를 보존한다.
 */
internal suspend fun AccessibilityService.clickWhenFound(
    budgetMs: Long,
    what: String,
    tag: String,
    find: () -> AccessibilityNodeInfo?,
): Boolean {
    val attempt: () -> Boolean = attempt@{
        val node = find() ?: return@attempt false
        val label = runCatching {
            "text=${node.text}/desc=${node.contentDescription}"
        }.getOrDefault("?")
        val clickable = clickableAncestorOrSelf(node)
        if (clickable != null &&
            runCatching { clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
                .getOrDefault(false)
        ) {
            val how = if (clickable == node) "clicked-self" else "clicked-ancestor"
            Log.i(tag, "clickWhenFound: [$what] $how ($label)")
            return@attempt true
        }
        val tapped = tapNodeCenter(node)
        if (tapped) {
            Log.i(tag, "clickWhenFound: [$what] gesture-tap-fallback ($label)")
        }
        tapped
    }
    val ok = if (budgetMs <= 0) {
        attempt()
    } else {
        withTimeoutOrNull(budgetMs) {
            while (!attempt()) {
                delay(POLL_INTERVAL_MS)
            }
            true
        } ?: false
    }
    if (!ok) {
        Log.w(tag, "clickWhenFound: [$what] ${budgetMs}ms 예산 안에 노드 발견/클릭 실패")
    }
    return ok
}
