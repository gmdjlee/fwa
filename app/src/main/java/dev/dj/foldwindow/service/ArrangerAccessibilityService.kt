package dev.dj.foldwindow.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import dev.dj.foldwindow.data.ProfilesParseResult
import dev.dj.foldwindow.data.WindowProfilesParser
import dev.dj.foldwindow.domain.ArrangeConfig
import dev.dj.foldwindow.domain.ArrangeEffect
import dev.dj.foldwindow.domain.ArrangeEvent
import dev.dj.foldwindow.domain.ArrangeState
import dev.dj.foldwindow.domain.ArrangeStateMachine
import dev.dj.foldwindow.domain.AspectMeasurement
import dev.dj.foldwindow.domain.AspectResolver
import dev.dj.foldwindow.domain.FailureReason
import dev.dj.foldwindow.domain.IntRect
import dev.dj.foldwindow.domain.LetterboxDetector
import dev.dj.foldwindow.domain.PaneGeometry
import dev.dj.foldwindow.domain.Placement
import dev.dj.foldwindow.domain.ResolvedAspect
import dev.dj.foldwindow.domain.SplitPlan
import dev.dj.foldwindow.domain.SplitPlanner
import dev.dj.foldwindow.domain.WindowGeometry
import dev.dj.foldwindow.domain.WindowProfilesConfig
import dev.dj.foldwindow.platform.DividerDragger
import dev.dj.foldwindow.platform.DividerHandle
import dev.dj.foldwindow.platform.DividerLocator
import dev.dj.foldwindow.platform.DragStrategy
import dev.dj.foldwindow.platform.EntryContext
import dev.dj.foldwindow.platform.PaneSwapper
import dev.dj.foldwindow.platform.SplitEntry
import dev.dj.foldwindow.platform.toLetterboxScan
import dev.dj.foldwindow.ui.PanelActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * P2-1 실제 액추에이터 서비스. ArrangeStateMachine(순수 리듀서)을 구동하고,
 * platform/ 레이어(DividerLocator·SplitEntry·DividerDragger·PaneSwapper)와
 * 접근성 API(windows, dispatchGesture, takeScreenshot)를 연결한다.
 *
 * ADR-2 준수: 이 파일 어디에도 고정 지연(postDelayed/delay(상수))으로 타이밍을 맞추는 코드가 없다.
 * 모든 대기는 (a) 100ms 틱 폴링 — 머신이 타임아웃을 스스로 판정, (b) 머신이 강제하는
 * MeasureLetterbox.notBeforeMs 레이트리밋 백오프, (c) platform 레이어 내부의 조건 폴링뿐이다.
 *
 * 상태 머신 변이는 전부 메인 스레드에서만 일어난다([scope]가 Dispatchers.Main.immediate).
 */
class ArrangerAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    private val dividerLocator = DividerLocator()
    private lateinit var dividerDragger: DividerDragger
    private lateinit var paneSwapper: PaneSwapper
    private lateinit var splitEntry: SplitEntry

    @Volatile
    private var lastForegroundPkg: String? = null

    // ── 상태 머신 ────────────────────────────────────────────────
    private var machineState: ArrangeState = ArrangeState.Idle
    private var arrangeConfig: ArrangeConfig = ArrangeConfig()
    private var tickJob: Job? = null

    /** startArrange 호출과 첫 dispatch(Start) 사이의 짧은 창에서 중복 세션 시작을 막는다 */
    @Volatile
    private var sessionInFlight = false

    // ── 세션 컨텍스트 (터미널 상태에서 cleanupSession() 이 초기화한다) ──
    private var targetPackage: String? = null
    private var targetLabel: String? = null
    private var desiredPlacement: Placement = Placement.TOP
    private var effectivePlacement: Placement = Placement.TOP
    private var plan: SplitPlan? = null
    private var resolvedAspect: ResolvedAspect? = null
    private val geometry = WindowGeometry.foldSevenLandscape()
    private var lastScreenshotAtMs: Long = 0L
    private var lastDragCompletedAtMs: Long = 0L
    private var lastHandle: DividerHandle? = null
    private var lastDividerLocateAtMs: Long = 0L

    /** window_profiles.json 파싱 성공 결과만 캐싱한다. 실패는 매 세션 재시도(자산이 나중에 고쳐질 수 있음) */
    private var cachedProfilesConfig: WindowProfilesConfig? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        dividerDragger = DividerDragger(this)
        paneSwapper = PaneSwapper(this)
        splitEntry = SplitEntry(this)
        Log.i(TAG, "arranger service connected")
    }

    override fun onDestroy() {
        instance = null
        tickJob?.cancel()
        scope.cancel()
        screenshotExecutor.shutdown()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg != packageName && pkg !in EXCLUDED_FOREGROUND_PACKAGES) {
                // 포그라운드 추적은 배치 진행 중에도 계속 돈다. 세션은 시작 시점의 스냅샷([targetPackage])
                // 만 쓰므로 Recents 진입 중 런처가 잠깐 포그라운드가 돼도 세션이 오염되지 않는다.
                lastForegroundPkg = pkg
            }
        }
    }

    override fun onInterrupt() = Unit

    // ══════════════════════════════════════════════════════════
    // 공개 API
    // ══════════════════════════════════════════════════════════

    /**
     * 현재 포그라운드 앱을 대상으로 배치를 시작한다.
     * @param placementOverride 사용자가 명시적으로 고른 위치. null 이면 프로파일/기본값을 따른다
     * @param aspectOverride 사용자가 명시적으로 고른 종횡비(프리셋). null 이면 ADR-1 3단 폴백을 따른다
     */
    fun startArrange(placementOverride: Placement?, aspectOverride: Float?) {
        if (machineState != ArrangeState.Idle || sessionInFlight) {
            Log.w(TAG, "startArrange: 이미 배치 진행 중 (state=$machineState)")
            toast("이미 배치 진행 중")
            return
        }

        val activePkg = activeAppPackage()
        val target = activePkg ?: lastForegroundPkg
        if (target == null) {
            Log.w(TAG, "startArrange: 대상 앱(포그라운드 패키지)을 찾지 못함")
            toast("대상 앱을 찾지 못했습니다")
            return
        }
        Log.i(TAG, "startArrange: target=$target source=${if (activePkg != null) "active-window" else "event-tracked"}")

        sessionInFlight = true
        scope.launch {
            try {
                beginSession(target, placementOverride, aspectOverride)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 조용한 실패 금지: 예측하지 못한 예외도 사용자에게 드러낸다.
                Log.e(TAG, "beginSession 중 예외", e)
                toast("배치 실패: 내부 오류")
                cleanupSession()
            } finally {
                sessionInFlight = false
            }
        }
    }

    fun cancelArrange() {
        if (machineState == ArrangeState.Idle) return
        scope.launch { dispatch(ArrangeEvent.Cancel(SystemClock.uptimeMillis())) }
    }

    // ══════════════════════════════════════════════════════════
    // 세션 시작 (ADR-1 3단 폴백 결정 + Start 디스패치)
    // ══════════════════════════════════════════════════════════

    private suspend fun beginSession(target: String, placementOverride: Placement?, aspectOverride: Float?) {
        targetPackage = target
        targetLabel = runCatching {
            val appInfo = packageManager.getApplicationInfo(target, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()

        // 1) 프로파일 로드 (성공만 캐싱, 실패 시 매번 재시도 + null config 로 진행)
        val config = loadProfilesConfig()

        // 2) ADR-1 티어 ②: 분할 진입 전 스냅샷 실측 (best effort — 실패해도 진행)
        val measurement = preMeasureAspect(SystemClock.uptimeMillis())

        // 3) 종횡비/배치 결정
        val profile = config?.profiles?.firstOrNull { it.packageName == target }
        val presetAspect = aspectOverride ?: config?.defaults?.aspect ?: DEFAULT_ASPECT
        val resolved = AspectResolver.resolve(profile, measurement, presetAspect)
        resolvedAspect = resolved

        val placement = placementOverride ?: profile?.placement ?: config?.defaults?.placement ?: Placement.TOP
        desiredPlacement = placement
        effectivePlacement = placement

        val computedPlan = SplitPlanner.plan(geometry, resolved.aspect, placement)
        plan = computedPlan

        Log.i(
            TAG,
            "arrange decision: target=$target label=$targetLabel aspectSource=${resolved.source} " +
                "aspect=${resolved.aspect} placement=$placement dividerCenterY=${computedPlan.dividerCenterY} " +
                "clamp=${computedPlan.clampReason} preMeasure=${measurement?.let { "conf=${it.confidence}" } ?: "none"}",
        )

        // TODO(Phase 3): config?.defaults?.closedLoopCorrection 은 아직 배선하지 않았다.
        // ArrangeStateMachine 의 ADR-5 단일 보정(reduceVerifying)은 이 플래그와 무관하게 항상 켜져 있다.
        arrangeConfig = ArrangeConfig(
            residualTolerancePx = config?.defaults?.residualTolerancePx ?: 8,
            // [실기기 확인, 2026-07-25] SplitEntry 진입 레시피가 4단계(메뉴 탭)에서 3단계
            // (Recents → 상단 가장자리 홀드-드래그 → 파트너 배치)로 바뀌었다.
            entryStepCount = 3,
        )

        dispatch(ArrangeEvent.Start(SystemClock.uptimeMillis(), computedPlan.dividerCenterY))
        startTickLoop()
    }

    private suspend fun loadProfilesConfig(): WindowProfilesConfig? {
        cachedProfilesConfig?.let { return it }

        val text = withContext(Dispatchers.IO) {
            runCatching { assets.open(PROFILES_ASSET_NAME).bufferedReader().use { it.readText() } }.getOrNull()
        }
        if (text == null) {
            Log.w(TAG, "profiles asset 읽기 실패 ($PROFILES_ASSET_NAME) — null config 로 진행")
            return null
        }

        return when (val result = WindowProfilesParser.parse(text)) {
            is ProfilesParseResult.Success -> {
                cachedProfilesConfig = result.config
                result.config
            }

            is ProfilesParseResult.Failure -> {
                result.errors.forEach { Log.w(TAG, "profiles 파싱 오류: $it") }
                null
            }
        }
    }

    private suspend fun preMeasureAspect(now: Long): AspectMeasurement? {
        if (now < lastScreenshotAtMs + SCREENSHOT_MIN_INTERVAL_MS) {
            Log.i(TAG, "pre-measure 스킵: 스크린샷 레이트리밋 백오프 중 (함정 #3)")
            return null
        }
        val bitmap = captureScreen() ?: return null
        return try {
            val scan = bitmap.toLetterboxScan(rowStride = ROW_STRIDE)
            LetterboxDetector.resolveAspect(scan)
        } catch (e: Exception) {
            Log.w(TAG, "pre-measure 스캔 실패", e)
            null
        } finally {
            bitmap.recycle()
        }
    }

    // ══════════════════════════════════════════════════════════
    // dispatch: 리듀서 호출 + 효과 실행 + 터미널 처리 (메인 스레드 전용)
    // ══════════════════════════════════════════════════════════

    private fun dispatch(event: ArrangeEvent) {
        val transition = ArrangeStateMachine.reduce(machineState, event, arrangeConfig)
        if (transition.state != machineState) {
            Log.i(TAG, "transition: $machineState -> ${transition.state} (event=$event)")
        }
        machineState = transition.state
        transition.effects.forEach { executeEffect(it) }

        val terminal = machineState
        if (terminal is ArrangeState.Done || terminal is ArrangeState.Failed) {
            reportTerminal(terminal)
            cleanupSession()
        }
    }

    private fun isSessionActive(): Boolean = machineState != ArrangeState.Idle

    private fun startTickLoop() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isSessionActive()) {
                delay(TICK_INTERVAL_MS)
                if (isSessionActive()) {
                    dispatch(ArrangeEvent.Tick(SystemClock.uptimeMillis()))
                }
            }
        }
    }

    private fun cleanupSession() {
        tickJob?.cancel()
        tickJob = null
        machineState = ArrangeState.Idle
        targetPackage = null
        targetLabel = null
        plan = null
        resolvedAspect = null
        lastHandle = null
        desiredPlacement = Placement.TOP
        effectivePlacement = Placement.TOP
    }

    // ══════════════════════════════════════════════════════════
    // 효과 실행기
    // ══════════════════════════════════════════════════════════

    private fun executeEffect(effect: ArrangeEffect) {
        when (effect) {
            ArrangeEffect.QuerySplitState -> handleQuerySplitState()
            is ArrangeEffect.PerformEntryStep -> handlePerformEntryStep(effect.step)
            ArrangeEffect.QueryDivider -> handleQueryDivider()
            is ArrangeEffect.DragDividerTo -> handleDragDividerTo(effect.targetY)
            is ArrangeEffect.MeasureLetterbox -> handleMeasureLetterbox(effect.notBeforeMs)
        }
    }

    private fun handleQuerySplitState() {
        scope.launch {
            val windowList = safeWindows()
            val active = dividerLocator.isSplitActive(windowList, screenRect())
            dispatch(ArrangeEvent.SplitStateResult(SystemClock.uptimeMillis(), active))
        }
    }

    private fun handlePerformEntryStep(step: Int) {
        scope.launch {
            val target = targetPackage
            if (target == null) {
                Log.w(TAG, "PerformEntryStep: targetPackage null — 실패로 처리")
                dispatch(ArrangeEvent.EntryStepResult(SystemClock.uptimeMillis(), false))
                return@launch
            }

            val screen = screenRect()
            val panelIntent = Intent(this@ArrangerAccessibilityService, PanelActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val ctx = EntryContext(
                targetPackage = target,
                targetLabel = targetLabel,
                selfPackage = packageName,
                panelIntent = panelIntent,
                screen = Rect(screen.left, screen.top, screen.right, screen.bottom),
            )

            // 머신 Tick 타임아웃(entryStepTimeoutMs)보다 먼저 결과 이벤트가 도착하도록 폴링 예산에
            // 여유를 둔다 — 실기기에서 3001ms > 3000ms 경합 관측 (2026-07-25).
            val stepBudgetMs = (arrangeConfig.entryStepTimeoutMs - ENTRY_STEP_RESULT_MARGIN_MS).coerceAtLeast(500L)
            val success = splitEntry.performStep(step, ctx, stepBudgetMs)
            dispatch(ArrangeEvent.EntryStepResult(SystemClock.uptimeMillis(), success))
        }
    }

    private fun handleQueryDivider() {
        scope.launch {
            // 머신은 미발견(null) 응답 시 즉시 재요청한다 — 여기서 최소 간격을 강제해 폴링 폭주를 막는다.
            val now = SystemClock.uptimeMillis()
            val elapsed = now - lastDividerLocateAtMs
            if (elapsed in 0 until DIVIDER_LOCATE_MIN_INTERVAL_MS) {
                delay(DIVIDER_LOCATE_MIN_INTERVAL_MS - elapsed)
            }
            lastDividerLocateAtMs = SystemClock.uptimeMillis()

            val windowList = safeWindows()
            val handle = dividerLocator.locate(windowList, screenRect())
            if (handle != null) lastHandle = handle
            dispatch(ArrangeEvent.DividerResult(SystemClock.uptimeMillis(), handle?.centerY))
        }
    }

    private fun handleDragDividerTo(targetY: Int) {
        scope.launch {
            val target = targetPackage
            val screen = screenRect()
            val actualPosition = actualVideoPanePosition(target, screen) ?: desiredPlacement

            var realTargetY = targetY
            if (actualPosition != desiredPlacement) {
                val handleForSwap = lastHandle
                val swapped = if (handleForSwap != null) {
                    paneSwapper.swap(handleForSwap, SWAP_TIMEOUT_MS) {
                        actualVideoPanePosition(target, screenRect()) == desiredPlacement
                    }
                } else {
                    Log.w(TAG, "DragDividerTo: swap 시도 불가 — lastHandle null")
                    false
                }

                if (swapped) {
                    effectivePlacement = desiredPlacement
                    dividerLocator.locate(safeWindows(), screen)?.let { lastHandle = it }
                    Log.i(TAG, "DragDividerTo: pane swap 성공 — 희망 위치($desiredPlacement) 유지")
                } else {
                    val recomputedActual = actualVideoPanePosition(target, screenRect()) ?: actualPosition
                    effectivePlacement = recomputedActual
                    val aspect = resolvedAspect?.aspect
                    if (aspect != null) {
                        val recomputedPlan = SplitPlanner.plan(geometry, aspect, recomputedActual)
                        plan = recomputedPlan
                        realTargetY = recomputedPlan.dividerCenterY
                    }
                    Log.w(
                        TAG,
                        "DragDividerTo: pane swap 실패 — 실제 위치($recomputedActual) 유지, " +
                            "재계산 targetY=$realTargetY (사용자 의도 부분 달성: letterbox는 제거하되 위치는 유지)",
                    )
                }
            }

            // [측정 2026-07-25] 보정 드래그가 이전 위치(984)의 스테일 핸들로 허공을 스와이프함 — 매 드래그 직전 재조회 필수.
            val handle = dividerLocator.locate(safeWindows(), screen) ?: lastHandle
            if (handle == null) {
                Log.w(TAG, "DragDividerTo: lastHandle null — 드래그 불가")
                dispatch(ArrangeEvent.DragResult(SystemClock.uptimeMillis(), false))
                return@launch
            }
            lastHandle = handle

            // [실기기 확인, 2026-07-25] `input swipe 1092 984 1092 1235 500` (단일 스트로크)만으로
            // 디바이더가 984→1235 로 정확히 이동해 검은 띠가 완전히 제거됐다 — 홀드가 필요 없음이
            // 실증됐다. DividerDragger 의 기본값과 일치시켜 SINGLE_STROKE 를 명시적으로 쓴다.
            dividerDragger.drag(handle, realTargetY, screen, DragStrategy.SINGLE_STROKE) { completed ->
                scope.launch {
                    if (completed) lastDragCompletedAtMs = SystemClock.uptimeMillis()
                    dispatch(ArrangeEvent.DragResult(SystemClock.uptimeMillis(), completed))
                }
            }
        }
    }

    private fun handleMeasureLetterbox(notBeforeMs: Long) {
        scope.launch {
            val now = SystemClock.uptimeMillis()
            val waitUntil = maxOf(
                notBeforeMs,
                lastScreenshotAtMs + SCREENSHOT_MIN_INTERVAL_MS,
                lastDragCompletedAtMs + DIVIDER_SETTLE_MS,
            )
            if (now < waitUntil) {
                // ADR-2 예외 허용 지점: 머신이 강제하는 레이트리밋 백오프(함정 #3). 고정 지연이 아니라
                // notBeforeMs 계산 결과를 그대로 따르는 조건부 대기다.
                delay(waitUntil - now)
            }

            val bitmap = captureScreen()
            if (bitmap == null) {
                // takeScreenshot 실패 콜백을 절대 무시하지 않는다 — 머신이 Done(verified=false)로 드러낸다.
                dispatch(ArrangeEvent.MeasureResult(SystemClock.uptimeMillis(), null, null))
                return@launch
            }

            try {
                val screen = screenRect()
                val paneRect = actualVideoPaneRect(targetPackage, screen)
                    ?: plan?.videoRect?.let { PaneGeometry.visibleRect(it, screen) }

                if (paneRect == null) {
                    Log.w(TAG, "MeasureLetterbox: 유효한 video pane rect 없음")
                    dispatch(ArrangeEvent.MeasureResult(SystemClock.uptimeMillis(), null, null))
                    return@launch
                }

                val left = paneRect.left.coerceIn(0, bitmap.width - 1)
                val top = paneRect.top.coerceIn(0, bitmap.height - 1)
                val right = paneRect.right.coerceIn(left + 1, bitmap.width)
                val bottom = paneRect.bottom.coerceIn(top + 1, bitmap.height)

                val crop = try {
                    Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
                } catch (e: Exception) {
                    Log.w(TAG, "MeasureLetterbox: crop 실패", e)
                    null
                }

                if (crop == null) {
                    dispatch(ArrangeEvent.MeasureResult(SystemClock.uptimeMillis(), null, null))
                    return@launch
                }

                try {
                    val scan = crop.toLetterboxScan(rowStride = ROW_STRIDE)
                    val measurement = LetterboxDetector.resolveAspect(scan)
                    // band px 는 스캔 행 단위 — 실제 화면 px 로 환산하려면 rowStride 를 곱한다 (함정: ScreenshotSampler 참고).
                    //
                    // [실기기 확인, 2026-07-25] resolveAspect/detect() 는 NO_LETTERBOX_FRACTION(0.99) 이상이면
                    // "밴드 없음" 으로 null 을 반환한다 — 완벽한 배치(잔여 띠 0px)와 "측정 불가"를 구분하지
                    // 못해, 드래그가 정확히 성공한 경우에도 Done(verified=false) 로 잘못 보고됐다.
                    // residualBars() 는 이 상한 거부가 없어 "띠 없음(성공)" 을 (0,0) 으로, 판정 자체가
                    // 불가능한 경우(전면 검정/콘텐츠 과소)만 null 로 정확히 구분한다.
                    val residualPx = LetterboxDetector.residualBars(scan)?.totalPx?.times(ROW_STRIDE)
                    val correctedTargetY = measurement?.let {
                        SplitPlanner.plan(geometry, it.value, effectivePlacement).dividerCenterY
                    }
                    // 알려진 한계(PROGRESS.md 열린 질문): 이 스캔은 상/하 행만 본다. 필러박스(좌우
                    // 검은 띠, 과소 이동으로 세로 방향이 남는 경우)는 residual 0 으로 오판될 수 있다.
                    // correctedTargetY 가 null 이면서 residualPx==0 인 상태는 정상이다 — 밴드를 못 찾은
                    // 게 아니라 "이미 완벽해서 보정할 밴드 자체가 없다"는 뜻이며, 머신은 그대로
                    // Done(verified=true, residual=0) 으로 진행한다.
                    dispatch(ArrangeEvent.MeasureResult(SystemClock.uptimeMillis(), residualPx, correctedTargetY))
                } finally {
                    crop.recycle()
                }
            } finally {
                bitmap.recycle()
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // 터미널 보고 (조용한 실패 금지 — Toast + Log 동시)
    // ══════════════════════════════════════════════════════════

    private fun reportTerminal(state: ArrangeState) {
        when (state) {
            is ArrangeState.Done -> {
                val message = if (state.verified) {
                    buildString {
                        append("배치 완료 · 잔여 ${state.finalResidualPx ?: 0}px")
                        if (state.adjusted) append(" · 보정 1회")
                        if (effectivePlacement != desiredPlacement) {
                            append(" · 위치 전환 실패로 ${effectivePlacement.toKoreanLabel()} 유지")
                        }
                    }
                } else {
                    "배치 완료 · 검증 불가(측정 실패)"
                }
                Log.i(
                    TAG,
                    "arrange done: verified=${state.verified} residual=${state.finalResidualPx} " +
                        "adjusted=${state.adjusted} desired=$desiredPlacement effective=$effectivePlacement",
                )
                toast(message)
            }

            is ArrangeState.Failed -> {
                Log.i(TAG, "arrange failed: reason=${state.reason}")
                toast("배치 실패: ${failureReasonKo(state.reason)}")
            }

            else -> Unit
        }
    }

    private fun failureReasonKo(reason: FailureReason): String = when (reason) {
        FailureReason.SPLIT_CHECK_TIMEOUT -> "분할 상태 확인 시간 초과"
        FailureReason.ENTRY_STEP_FAILED -> "분할 진입 단계 실패"
        FailureReason.ENTRY_TIMEOUT -> "분할 진입 시간 초과"
        FailureReason.DIVIDER_NOT_FOUND -> "디바이더를 찾지 못함"
        FailureReason.DRAG_FAILED -> "디바이더 이동 실패"
        FailureReason.DRAG_TIMEOUT -> "디바이더 이동 시간 초과"
        FailureReason.CANCELLED -> "사용자 취소"
    }

    private fun Placement.toKoreanLabel(): String = when (this) {
        Placement.TOP -> "상단"
        Placement.BOTTOM -> "하단"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ══════════════════════════════════════════════════════════
    // 창/화면 기하 헬퍼
    // ══════════════════════════════════════════════════════════

    /** Phase 0 프로브로 실기기 검증된 방식 — 화면 전체 픽셀 크기 */
    private fun screenRect(): IntRect {
        val dm = resources.displayMetrics
        return IntRect(0, 0, dm.widthPixels, dm.heightPixels)
    }

    private fun safeWindows(): List<AccessibilityWindowInfo> = runCatching { windows }.getOrDefault(emptyList())

    /** 트리거 시점의 활성 APPLICATION 창 패키지. 이벤트 추적(lastForegroundPkg)의 오버레이 오염을 우회한다 */
    private fun activeAppPackage(): String? {
        val window = safeWindows().firstOrNull { w ->
            runCatching { w.type }.getOrNull() == AccessibilityWindowInfo.TYPE_APPLICATION &&
                runCatching { w.isActive }.getOrDefault(false)
        } ?: safeWindows().firstOrNull { w ->
            runCatching { w.type }.getOrNull() == AccessibilityWindowInfo.TYPE_APPLICATION &&
                runCatching { w.isFocused }.getOrDefault(false)
        } ?: return null
        val pkg = runCatching { window.root?.packageName?.toString() }.getOrNull() ?: return null
        if (pkg == packageName || pkg in EXCLUDED_FOREGROUND_PACKAGES) return null
        return pkg
    }

    /** targetPackage 의 APPLICATION 창이 화면의 위/아래 중 어느 쪽에 실제로 있는지. 못 찾으면 null */
    private fun actualVideoPanePosition(targetPackage: String?, screen: IntRect): Placement? {
        val visible = actualVideoPaneRect(targetPackage, screen) ?: return null
        val visibleCenterY = (visible.top + visible.bottom) / 2
        val screenCenterY = (screen.top + screen.bottom) / 2
        return if (visibleCenterY < screenCenterY) Placement.TOP else Placement.BOTTOM
    }

    /** targetPackage 의 APPLICATION 창과 화면의 가시 교집합. 못 찾으면 null (함정 #2: 오프스크린 슬라이드 대응) */
    private fun actualVideoPaneRect(targetPackage: String?, screen: IntRect): IntRect? {
        if (targetPackage == null) return null
        val window = safeWindows().firstOrNull { w ->
            runCatching { w.type }.getOrNull() == AccessibilityWindowInfo.TYPE_APPLICATION &&
                runCatching { w.root?.packageName?.toString() }.getOrNull() == targetPackage
        } ?: return null
        val bounds = Rect()
        val ok = runCatching { window.getBoundsInScreen(bounds) }.isSuccess
        if (!ok) return null
        return PaneGeometry.visibleRect(IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom), screen)
    }

    // ══════════════════════════════════════════════════════════
    // 스크린샷 (probe/ProbeAccessibilityService 와 동일 패턴 — 함정 #3·#4 대응)
    // ══════════════════════════════════════════════════════════

    private suspend fun captureScreen(): Bitmap? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cont.resume(null); return@suspendCancellableCoroutine
        }
        lastScreenshotAtMs = SystemClock.uptimeMillis() // 호출 시점 기준 — 성공/실패와 무관하게 레이트리밋을 소비한다
        runCatching {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val bmp = try {
                            Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } finally {
                            buffer.close() // 함정 #4: HardwareBuffer 누수 방지
                        }
                        if (cont.isActive) cont.resume(bmp)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "takeScreenshot failed: $errorCode")
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    companion object {
        private const val TAG = "FWArranger"

        /** 상태 머신 타임아웃 판정을 위한 Tick 간격. 조건 폴링이지 타이밍 맞추기용 고정 지연이 아니다 */
        private const val TICK_INTERVAL_MS = 100L

        /** ArrangeConfig.screenshotMinIntervalMs 기본값(1100)과 동일하게 유지한다 (함정 #3) */
        private const val SCREENSHOT_MIN_INTERVAL_MS = 1100L

        /**
         * 분할 드래그 직후 애니메이션/레이아웃 정착 대기. [측정 2026-07-25] 드래그 완료 50ms 후
         * 측정 시 잔여 218px 오측 → 보정 오산출. 스크린샷 레이트리밋 백오프와 같은 부류의
         * 측정 타이밍 파라미터다 (ADR-2 위반 아님).
         */
        private const val DIVIDER_SETTLE_MS = 600L

        private const val DIVIDER_LOCATE_MIN_INTERVAL_MS = 200L
        private const val SWAP_TIMEOUT_MS = 1200L

        /**
         * 머신 Tick 타임아웃(entryStepTimeoutMs)보다 먼저 결과 이벤트가 도착하도록 폴링 예산에
         * 여유를 둔다 — 실기기에서 3001ms > 3000ms 경합 관측 (2026-07-25).
         */
        private const val ENTRY_STEP_RESULT_MARGIN_MS = 400L

        /** ScreenshotSampler.toLetterboxScan 에 전달하는 값과 동일해야 band px → 실제 px 환산이 맞는다 */
        private const val ROW_STRIDE = 2

        private const val DEFAULT_ASPECT = 16f / 9f
        private const val PROFILES_ASSET_NAME = "window_profiles.json"

        private val EXCLUDED_FOREGROUND_PACKAGES = setOf(
            "com.sec.android.app.launcher",
            "com.android.systemui",
            "com.samsung.android.app.cocktailbarservice",
            // 2026-07-25 실기기: 오버레이가 포그라운드 추적을 오염 (One Hand Operation +)
            "com.samsung.android.sidegesturepad",
        )

        @Volatile
        var instance: ArrangerAccessibilityService? = null
            private set
    }
}
