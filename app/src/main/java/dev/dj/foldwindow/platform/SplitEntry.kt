package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * P2-3: Recents 기반 분할 화면 진입 (docs/DEVICE_FACTS.md 2026-07-25 재검증 반영, 3단계 드래그 레시피).
 *
 * `GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN` 은 이 기기에서 분할 전/중 모두 false 를 반환함이
 * 실기기 3회 실행으로 확정됐다 (docs/DEVICE_FACTS.md #6 FAILS). Recents 를 여는 것까지는 맞았지만,
 * **카드 팝업 메뉴의 "분할 화면으로 열기" 경로는 [반증]됐다** — 가로(시청) 상태에서도 좌우(L/R)
 * 분할을 만들어 상하(T/B) 분할이 필요한 이 앱에는 무용하다 (실기기 확인, 2026-07-25).
 * 대신 **카드 아이콘을 화면 상단 가장자리로 홀드-드래그**하면 상하(T/B) 분할-선택 상태가 만들어짐이
 * `input draganddrop` 으로 실증됐다. 그래서 진입 레시피는 3단계다:
 *   1) Recents 열기 → 2) 카드 아이콘을 상단 가장자리로 드래그(분할-선택 진입) → 3) 파트너 배치.
 *
 * 이 클래스는 ArrangeStateMachine 을 모른다 — `ArrangeEffect.PerformEntryStep(step)` 을 받은
 * 서비스 레이어가 step 번호 하나씩 호출하고, 반환된 성공/실패를 ArrangeEvent.EntryStepResult 로
 * 머신에 되먹임한다. 상태(재시도 횟수, 타임아웃 판정)는 전부 머신 쪽 책임이다.
 *
 * ADR-2: 고정 지연 금지. 모든 대기는 (조건 폴링 + 타임아웃) 으로 구현한다.
 */
class SplitEntry(private val service: AccessibilityService) {

    /**
     * Recents 레시피의 step(1..3) 하나를 실행하고, 그 단계의 성공 조건을 [timeoutMs] 안에서
     * [POLL_INTERVAL_MS] 간격으로 폴링해 확인한다.
     *
     * 예외는 삼키고 false 로 변환한다 (CancellationException 은 구조적 동시성을 지키기 위해 재던짐).
     * 잘못된 step 번호는 즉시 false.
     */
    suspend fun performStep(step: Int, ctx: EntryContext, timeoutMs: Long): Boolean {
        return try {
            when (step) {
                1 -> step1OpenRecents(ctx, timeoutMs)
                2 -> step2DragToTopEdge(ctx, timeoutMs)
                3 -> step3PlacePartner(ctx, timeoutMs)
                else -> {
                    Log.w(TAG, "performStep: invalid step number $step (1..3 만 유효)")
                    false
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "performStep: step=$step 실행 중 예외", e)
            false
        }
    }

    // ══════════════════════════════════════════════════════════
    // Step 1 — Recents 열기
    // ══════════════════════════════════════════════════════════

    /**
     * 동작: GLOBAL_ACTION_RECENTS.
     * 성공 조건: step2 카드 아이콘 노드가 나타남 (launcher 패키지 창 안에서).
     */
    private suspend fun step1OpenRecents(ctx: EntryContext, timeoutMs: Long): Boolean {
        val accepted = runCatching {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
        }.getOrDefault(false)
        if (!accepted) {
            // 실측상 이 경로는 accepted=true 로 확인됐지만, 조용한 실패 금지 원칙상 로그는 남긴다.
            Log.w(TAG, "step1: GLOBAL_ACTION_RECENTS 가 false 반환 — 그래도 성공 조건 폴링은 계속함")
        }
        return pollUntil(timeoutMs) { findCardIconNode(ctx) != null }
    }

    // ══════════════════════════════════════════════════════════
    // Step 2 — 대상 앱 카드 아이콘을 화면 상단 가장자리로 홀드-드래그
    // ══════════════════════════════════════════════════════════

    /**
     * [실기기 확인, 2026-07-25] `input draganddrop 592 322 1092 150 800`
     * (카드 아이콘 중심 → 화면 상단 가장자리 중심) 이 상하(T/B) 분할-선택 상태를 만들어냄이 실증됐다.
     * `input draganddrop` = 롱프레스 후 드래그 — 접근성 제스처로는 [holdThenDrag] 로 재현한다.
     *
     * 동작: 카드 아이콘 노드를 찾되(클릭하지 않는다) bounds 중심을 시작점으로, 화면 상단 가장자리
     * (`screen.top + DROP_MARGIN_PX`) 를 도착점으로 holdThenDrag.
     * 성공 조건: 대상 앱이 "분할 선택" 상태로 들어감 — APPLICATION 타입 창 중 root 패키지가
     * targetPackage 이고, 화면과의 가시 교집합 높이가 화면 높이의 15~75% 인 창 존재.
     * (오늘 확인된 것: 이 비율 조건은 **상하 분할에서만** 유효하다 — 과거 좌우 분할 실패 시엔
     * 높이가 100%였다.)
     */
    private suspend fun step2DragToTopEdge(ctx: EntryContext, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        fun remaining() = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)

        val iconNode = pollForNode(remaining()) { findCardIconNode(ctx) }
        if (iconNode == null) {
            Log.w(TAG, "step2: 카드 아이콘 노드를 ${timeoutMs}ms 안에 찾지 못함")
            return false
        }
        val bounds = Rect()
        val gotBounds = runCatching { iconNode.getBoundsInScreen(bounds) }.isSuccess
        if (!gotBounds || bounds.isEmpty) {
            Log.w(TAG, "step2: 카드 아이콘 bounds 조회 실패")
            return false
        }

        val dropX = ctx.screen.centerX()
        val dropY = ctx.screen.top + EntrySelectors.DROP_MARGIN_PX
        Log.i(
            TAG,
            "step2: holdThenDrag icon(${bounds.centerX()},${bounds.centerY()}) -> ($dropX,$dropY)",
        )
        val gestureCompleted = suspendHoldThenDrag(
            fromX = bounds.centerX(),
            fromY = bounds.centerY(),
            toX = dropX,
            toY = dropY,
        )
        if (!gestureCompleted) {
            // step1 과 동일한 관례: 제스처 완료 콜백이 false 여도(예: 타이밍 경합) 실제 시스템
            // 반응은 비동기일 수 있으므로 성공 조건 폴링은 그대로 계속한다. 조용히 포기하지 않는다.
            Log.w(TAG, "step2: holdThenDrag 완료 콜백=false — 그래도 상태 폴링 계속")
        }
        return pollUntil(remaining()) { isTargetInSplitSelectState(ctx) }
    }

    /** [holdThenDrag] 의 콜백을 suspend 로 잇는다. 정확히 한 번만 재개된다. */
    private suspend fun suspendHoldThenDrag(fromX: Int, fromY: Int, toX: Int, toY: Int): Boolean =
        suspendCancellableCoroutine { cont ->
            holdThenDrag(
                service = service,
                fromX = fromX,
                fromY = fromY,
                toX = toX,
                toY = toY,
                holdMs = EntrySelectors.DRAG_HOLD_MS,
                moveMs = EntrySelectors.DRAG_MOVE_MS,
            ) { completed ->
                if (cont.isActive) cont.resume(completed)
            }
        }

    // ══════════════════════════════════════════════════════════
    // Step 3 — 파트너(우리 패널)를 반대쪽 페인에 배치
    // ══════════════════════════════════════════════════════════

    /**
     * 두 전략을 순서대로 시도한다:
     *  1. PRIMARY (실측 경로): launcher `FromRecentActivity` 피커에서 우리 패널 라벨 노드를 탭.
     *  2. FALLBACK [측정 2026-07-25]: `startActivity(panelIntent)` (LAUNCH_ADJACENT).
     *     분할 선택 중에는 전체화면으로 떠서 분할 선택 상태를 파괴함 — 피커 경로 실패 시에만.
     *
     * 성공 조건(공통): APPLICATION 타입 창 중 targetPackage 창과 selfPackage 창이 동시에 존재.
     */
    private suspend fun step3PlacePartner(ctx: EntryContext, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        fun remaining() = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0)

        // 전략 1 (실측 경로): Recents 파트너 피커에서 우리 패널 항목 탭. 예산의 약 60% 할당.
        val pickerBudget = minOf(timeoutMs * 6 / 10, remaining())
        val pickerClicked = clickWhenFound(pickerBudget, "step3 panel-picker") { findPanelPickerNode() }
        if (pickerClicked) {
            if (pollUntil(remaining()) { isSplitPairPresent(ctx) }) {
                Log.i(TAG, "step3: 전략1(실측 피커 경로) 로 성공")
                return true
            }
            Log.w(TAG, "step3: 전략1 클릭 후 분할 쌍 미수렴 — 전략2(LAUNCH_ADJACENT)로 폴백")
        } else {
            Log.w(TAG, "step3: 전략1 피커 노드 발견/클릭 실패 — 전략2(LAUNCH_ADJACENT)로 폴백")
        }

        // [측정 2026-07-25] 분할 선택 중 LAUNCH_ADJACENT 는 전체화면으로 떠서 분할 선택 상태를
        // 파괴함 — 최후 폴백으로만.
        val fallbackLaunched = runCatching { service.startActivity(ctx.panelIntent) }.isSuccess
        if (!fallbackLaunched) {
            Log.w(TAG, "step3: 전략2 startActivity 실패")
            return false
        }
        val fallbackOk = pollUntil(remaining()) { isSplitPairPresent(ctx) }
        if (fallbackOk) {
            Log.i(TAG, "step3: 전략2(startActivity LAUNCH_ADJACENT 폴백) 로 성공")
        }
        return fallbackOk
    }

    // ══════════════════════════════════════════════════════════
    // 성공 조건 판정
    // ══════════════════════════════════════════════════════════

    private fun isTargetInSplitSelectState(ctx: EntryContext): Boolean {
        val screenHeight = ctx.screen.height()
        if (screenHeight <= 0) return false
        val windows = runCatching { service.windows }.getOrDefault(emptyList())
        return windows.any { w ->
            val type = runCatching { w.type }.getOrNull()
            val pkg = runCatching { w.root?.packageName?.toString() }.getOrNull()
            if (type != AccessibilityWindowInfo.TYPE_APPLICATION || pkg != ctx.targetPackage) {
                false
            } else {
                val bounds = Rect()
                runCatching { w.getBoundsInScreen(bounds) }
                val visibleHeight = intersectHeight(bounds, ctx.screen)
                val ratio = visibleHeight.toFloat() / screenHeight.toFloat()
                ratio in EntrySelectors.SPLIT_SELECT_MIN_RATIO..EntrySelectors.SPLIT_SELECT_MAX_RATIO
            }
        }
    }

    private fun isSplitPairPresent(ctx: EntryContext): Boolean {
        val windows = runCatching { service.windows }.getOrDefault(emptyList())
        var hasTarget = false
        var hasSelf = false
        for (w in windows) {
            if (runCatching { w.type }.getOrNull() != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            when (runCatching { w.root?.packageName?.toString() }.getOrNull()) {
                ctx.targetPackage -> hasTarget = true
                ctx.selfPackage -> hasSelf = true
            }
        }
        return hasTarget && hasSelf
    }

    /** 화면 Rect 와의 가시 교집합 높이(px). 함정 #2 대응: 화면 밖으로 슬라이드된 창의 음수 좌표에 속지 않는다. */
    private fun intersectHeight(windowBounds: Rect, screen: Rect): Int {
        val top = maxOf(windowBounds.top, screen.top)
        val bottom = minOf(windowBounds.bottom, screen.bottom)
        return (bottom - top).coerceAtLeast(0)
    }

    // ══════════════════════════════════════════════════════════
    // 노드 탐색 (셀렉터 체인)
    // ══════════════════════════════════════════════════════════

    private fun findCardIconNode(ctx: EntryContext): AccessibilityNodeInfo? {
        val roots = launcherWindowRoots()
        if (roots.isEmpty()) return null
        val label = ctx.targetLabel

        val selectors = listOf<Pair<String, (AccessibilityNodeInfo) -> Boolean>>(
            "ko-content-desc" to { node ->
                val desc = node.contentDescription?.toString().orEmpty()
                desc.contains(EntrySelectors.CARD_ICON_DESC_KO) && (label == null || desc.contains(label))
            },
            "en-content-desc" to { node -> // [미검증] 영어 로케일
                val desc = node.contentDescription?.toString()?.lowercase().orEmpty()
                label != null &&
                    EntrySelectors.CARD_ICON_DESC_EN.any { desc.contains(it) } &&
                    desc.contains(label.lowercase())
            },
            "structural-clickable-label" to { node ->
                label != null && node.isClickable &&
                    node.contentDescription?.toString()?.contains(label) == true
            },
        )
        return firstMatch("step2 card-icon", roots, selectors)
    }

    // [반증] 2026-07-25: 과거 step3(카드 메뉴 "분할 화면으로 열기" 탭)의 노드 탐색 함수였다.
    // 세로에서도 좌우(L/R) 분할을 만들어 무용함이 확인돼 삭제됨 — EntrySelectors.SPLIT_MENU_*
    // 상수만 부활 방지 주석과 함께 남겨둔다. (구 함수명: findSplitMenuNode)

    /**
     * step3 주 경로: Recents 파트너 피커(`FromRecentActivity`)에서 우리 패널 라벨을 찾는다.
     * [실측 2026-07-25] 피커의 앱 카드 라벨은 클릭 불가 텍스트 노드(클릭 가능 카드 컨테이너의 자식)라
     * isClickable 을 요구하면 절대 매치되지 않는다 — 클릭 대상 해석은 clickWhenFound 가 맡는다.
     */
    private fun findPanelPickerNode(): AccessibilityNodeInfo? {
        val roots = launcherWindowRoots()
        if (roots.isEmpty()) return null
        val selectors = EntrySelectors.PANEL_LABEL_CANDIDATES.map { label ->
            "panel-label:$label" to { node: AccessibilityNodeInfo ->
                val text = node.text?.toString().orEmpty()
                val desc = node.contentDescription?.toString().orEmpty()
                text.contains(label) || desc.contains(label)
            }
        }
        return firstMatch("step3 panel-picker", roots, selectors)
    }

    private fun launcherWindowRoots(): List<AccessibilityNodeInfo> =
        runCatching { service.windows }.getOrDefault(emptyList())
            .filter { w ->
                runCatching { w.root?.packageName?.toString() }.getOrNull() == EntrySelectors.LAUNCHER_PACKAGE
            }
            .mapNotNull { w -> runCatching { w.root }.getOrNull() }

    /** 트리를 걸어 predicate 를 만족하는 첫 노드를 찾는다. 재귀 중 개별 노드 접근은 모두 runCatching. */
    private fun findNode(
        roots: List<AccessibilityNodeInfo>,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        for (root in roots) {
            val found = searchNode(root, predicate)
            if (found != null) return found
        }
        return null
    }

    private fun searchNode(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (runCatching { predicate(node) }.getOrDefault(false)) return node
        val childCount = runCatching { node.childCount }.getOrDefault(0)
        for (i in 0 until childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            searchNode(child, predicate)?.let { return it }
        }
        return null
    }

    /**
     * 셀렉터 체인을 순서대로 시도한다. 어떤 셀렉터가 매치됐는지 Log.i 로 남겨
     * 향후 로케일 확장(어떤 후보가 실제로 쓰였는지) 근거로 삼는다.
     */
    private fun firstMatch(
        logLabel: String,
        roots: List<AccessibilityNodeInfo>,
        selectors: List<Pair<String, (AccessibilityNodeInfo) -> Boolean>>,
    ): AccessibilityNodeInfo? {
        for ((name, predicate) in selectors) {
            val node = findNode(roots, predicate)
            if (node != null) {
                Log.i(TAG, "$logLabel matched via selector [$name]")
                return node
            }
        }
        return null
    }

    // ══════════════════════════════════════════════════════════
    // 조건 폴링 (ADR-2: 고정 지연 금지)
    // ══════════════════════════════════════════════════════════

    /** [timeoutMs] 안에서 [POLL_INTERVAL_MS] 간격으로 condition 이 true 가 될 때까지 대기한다. */
    private suspend fun pollUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        if (timeoutMs <= 0) return condition()
        return withTimeoutOrNull(timeoutMs) {
            while (!condition()) {
                delay(POLL_INTERVAL_MS)
            }
            true
        } ?: false
    }

    /**
     * [timeoutMs] 안에서 [POLL_INTERVAL_MS] 간격으로 [find] 가 노드를 찾을 때까지 폴링한다.
     * [clickWhenFound] 와 달리 클릭하지 않는다 — step2 처럼 좌표(bounds)만 필요하고 노드 자체를
     * 드래그 시작점으로 쓰는 경우에 쓴다.
     */
    private suspend fun pollForNode(
        timeoutMs: Long,
        find: () -> AccessibilityNodeInfo?,
    ): AccessibilityNodeInfo? {
        if (timeoutMs <= 0) return find()
        return withTimeoutOrNull(timeoutMs) {
            var node = find()
            while (node == null) {
                delay(POLL_INTERVAL_MS)
                node = find()
            }
            node
        }
    }

    /**
     * [budgetMs] 안에서 [find] 로 노드를 폴링 탐색해 발견 즉시 클릭한다.
     * 트리 갱신 직후 일시적 미조회(윈도우 churn)에 대비해 탐색 자체를 재시도한다.
     * 클릭까지 성공하면 true. 예산 소진 시 false.
     *
     * 클릭 해석 순서 (실측: 라벨 TextView 는 isClickable=false 인 경우가 있어 ACTION_CLICK 이 항상 실패할 수 있음):
     *  1. 매치 노드에서 부모로 올라가며 isClickable 인 조상(또는 자신)에 ACTION_CLICK
     *  2. 실패 시 매치 노드 중심 좌표에 50ms 탭 제스처 디스패치 (성공 여부는 호출자의 성공 조건 폴링이 판정)
     */
    private suspend fun clickWhenFound(
        budgetMs: Long,
        what: String,
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
                Log.i(TAG, "clickWhenFound: [$what] $how ($label)")
                return@attempt true
            }
            val tapped = tapNodeCenter(node)
            if (tapped) {
                Log.i(TAG, "clickWhenFound: [$what] gesture-tap-fallback ($label)")
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
            Log.w(TAG, "clickWhenFound: [$what] ${budgetMs}ms 예산 안에 노드 발견/클릭 실패")
        }
        return ok
    }

    /** 매치 노드부터 최대 [maxDepth] 단계 부모로 올라가며 isClickable 인 첫 노드(자신 포함)를 찾는다. */
    private fun clickableAncestorOrSelf(
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
    private fun tapNodeCenter(node: AccessibilityNodeInfo): Boolean {
        val bounds = Rect()
        runCatching { node.getBoundsInScreen(bounds) }
        if (bounds.isEmpty) return false
        val path = Path().apply { moveTo(bounds.exactCenterX(), bounds.exactCenterY()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS))
            .build()
        return runCatching { service.dispatchGesture(gesture, null, null) }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "FWSplitEntry"
        private const val POLL_INTERVAL_MS = 150L
        private const val PRIMARY_STRATEGY_MAX_MS = 1500L
        private const val TAP_DURATION_MS = 50L
    }
}

/** 진입에 필요한 컨텍스트. 서비스 레이어가 채워서 넘긴다 */
data class EntryContext(
    val targetPackage: String,
    /** PackageManager 로 조회한 대상 앱 표시 이름. 조회 실패 시 null */
    val targetLabel: String?,
    val selfPackage: String,
    /** PanelActivity 실행용 인텐트 (FLAG_ACTIVITY_LAUNCH_ADJACENT | FLAG_ACTIVITY_NEW_TASK 포함해서 서비스가 만든다) */
    val panelIntent: Intent,
    /** 현재 화면 전체 rect (landscape 2184×1968). 서비스가 WindowMetrics 로 채운다 */
    val screen: Rect,
)

/**
 * 셀렉터 리터럴 모음. docs/DEVICE_FACTS.md "분할 진입 전략" 실측값이 근거다.
 * 로케일 추가는 이 object 에 상수/후보를 한 줄 추가하는 것으로 끝나야 한다.
 */
private object EntrySelectors {
    // 실측 (한국어, One UI 8) — docs/DEVICE_FACTS.md. step2 카드 아이콘 노드 탐색에 사용.
    const val CARD_ICON_DESC_KO = "고급 옵션"

    // [미검증] 영어 로케일 후보 — PROGRESS.md 열린 질문 #6
    val CARD_ICON_DESC_EN: List<String> = listOf("more options", "advanced options")

    // [반증] 2026-07-25 실기기 확인: Recents 카드 팝업 메뉴에서 "분할 화면으로 열기" 를 탭하면
    // 가로(시청 시나리오, 상하 분할 필요) 상태에서도 좌우(L/R) 분할이 생성된다 — 이 앱엔 무용.
    // 진입 레시피에서 완전히 제거됐다(더 이상 어떤 함수도 이 상수를 참조하지 않는다).
    // 향후 실기기 재검토 없이 이 경로를 되살리지 말 것. 상수만 흔적으로 남긴다.
    const val SPLIT_MENU_TEXT_KO = "분할 화면"
    const val SPLIT_MENU_TEXT_EN = "split screen"

    const val LAUNCHER_PACKAGE = "com.sec.android.app.launcher"

    // [미검증] step3 폴백 피커에서 우리 패널을 찾기 위한 라벨 후보. 실제 앱 라벨 문자열 확정 전까지 후보로 유지
    val PANEL_LABEL_CANDIDATES: List<String> = listOf("FW Panel", "FoldWindow")

    const val SPLIT_SELECT_MIN_RATIO = 0.15f
    const val SPLIT_SELECT_MAX_RATIO = 0.75f

    // [측정] input draganddrop 592 322 1092 150 800 이 실기기에서 상하 분할-선택을 만들어냈다.
    // 드롭 Y = 화면 상단 + 150px. 화면 크기 하드코딩이 아니라 상단 기준 오프셋이므로 CLAUDE.md
    // 규칙(좌표 하드코딩 금지)을 지킨다 — 화면 자체의 top 기준 상대 오프셋만 쓴다.
    const val DROP_MARGIN_PX = 150

    // [측정 기반, 정확한 홀드/이동 시간 자체는 미세조정 여지 있음] input draganddrop 의
    // "롱프레스 후 드래그" 를 재현하는 holdThenDrag 파라미터.
    const val DRAG_HOLD_MS = 500L
    const val DRAG_MOVE_MS = 600L
}
