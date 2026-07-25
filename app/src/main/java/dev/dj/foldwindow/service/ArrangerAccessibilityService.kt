package dev.dj.foldwindow.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
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
import dev.dj.foldwindow.data.ProfileStore
import dev.dj.foldwindow.data.ProfilesParseResult
import dev.dj.foldwindow.data.WindowProfilesParser
import dev.dj.foldwindow.domain.ArrangeConfig
import dev.dj.foldwindow.domain.ArrangeEffect
import dev.dj.foldwindow.domain.ArrangeEvent
import dev.dj.foldwindow.domain.ArrangeState
import dev.dj.foldwindow.domain.ArrangeStateMachine
import dev.dj.foldwindow.domain.AspectMeasurement
import dev.dj.foldwindow.domain.AspectResolver
import dev.dj.foldwindow.domain.AspectSource
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
import dev.dj.foldwindow.platform.DividerPopupRotator
import dev.dj.foldwindow.platform.DragStrategy
import dev.dj.foldwindow.platform.EntryContext
import dev.dj.foldwindow.platform.EntryRecipe
import dev.dj.foldwindow.platform.PaneSwapper
import dev.dj.foldwindow.platform.ResizeModeDetector
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
import kotlinx.coroutines.withTimeoutOrNull
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
    private val store by lazy { ProfileStore(this) }

    private val dividerLocator = DividerLocator()
    private lateinit var dividerDragger: DividerDragger
    private lateinit var paneSwapper: PaneSwapper
    private lateinit var splitEntry: SplitEntry
    private lateinit var popupRotator: DividerPopupRotator

    @Volatile
    private var lastForegroundPkg: String? = null

    // ── 상태 머신 ────────────────────────────────────────────────
    private var machineState: ArrangeState = ArrangeState.Idle
    private var arrangeConfig: ArrangeConfig = ArrangeConfig()
    private var tickJob: Job? = null

    /** startArrange 호출과 첫 dispatch(Start) 사이의 짧은 창에서 중복 세션 시작을 막는다 */
    @Volatile
    private var sessionInFlight = false

    /**
     * dismissSplit() 중복 실행 방지용 가드. dismissSplit 은 machineState 가 Idle 인 동안에만
     * 동작하는 별도 경로라 sessionInFlight 와 별개 플래그가 필요하다(P3-2).
     */
    @Volatile
    private var dismissInFlight = false

    // ── 세션 컨텍스트 (터미널 상태에서 cleanupSession() 이 초기화한다) ──
    private var targetPackage: String? = null
    private var targetLabel: String? = null
    private var desiredPlacement: Placement = Placement.TOP
    private var effectivePlacement: Placement = Placement.TOP
    private var plan: SplitPlan? = null
    private var resolvedAspect: ResolvedAspect? = null

    /**
     * [실기기 확인, 2026-07-25] UNRESIZEABLE 앱(넷플릭스류)은 드래그 레시피가 팝업(프리폼)으로
     * 라우팅돼 상하 분할-선택 진입이 불가능함이 확인됐다. `ResizeModeDetector` 판정 결과로
     * `beginSession` 이 세션마다 결정하고, `cleanupSession` 이 DRAG 로 리셋한다.
     */
    private var entryRecipe: EntryRecipe = EntryRecipe.DRAG
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
        // [#20] 오버레이 가드 판정원은 in-process 상태(FloatingLauncherService 창 부착 여부)다 —
        // a11y 창 목록은 touchable 플래그를 노출하지 않고 자기 오버레이가 목록 자체를 오염시킬 수
        // 있다(함정 #25). platform 계층은 service/ 를 몰라야 하므로 람다로 주입한다.
        splitEntry = SplitEntry(this) { FloatingLauncherService.hasAttachedOverlayWindow() }
        popupRotator = DividerPopupRotator(this)
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
        // [#20 포렌식, 2026-07-25] `View.performClick()` 은 리스너 유무와 무관하게 이 이벤트를
        // 무조건 발화한다(AOSP 검증, docs/DESIGN_20_CLICK_CYCLE.md §2-4) — "잘못된/리스너 없는
        // 뷰에서 실행됨" 과 "실행 자체가 없었음"을 logcat 에서 구분하기 위한 무행동(non-actuating)
        // 로그다. 세션 진행 중에만 남긴다 — 평시(세션 밖)까지 남기면 로그가 폭주한다.
        if (event?.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
            (machineState != ArrangeState.Idle || sessionInFlight)
        ) {
            // event.source 접근은 스테일 노드에서 예외를 던질 수 있어 runCatching 으로 방어한다.
            val viewId = runCatching { event.source?.viewIdResourceName }.getOrNull()
            Log.i(TAG, "FORENSIC viewClicked pkg=${event.packageName} cls=${event.className} viewId=$viewId")
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

    /**
     * [실측 확인, 결함 #24① 수정] 현재 활성 분할 화면을 해제해 대상 앱을 전체화면으로 복귀시킨다.
     * 진행 중인 배치 세션의 취소([cancelArrange])와는 다른 경로다 — 세션이 없어도(과거에
     * 배치된 분할이 그대로 남아 있는 경우 포함) 활성 분할 자체를 푸는 기능이다.
     *
     * ADR-2 준수: 완료 확인은 전부 조건 폴링([DISMISS_POLL_INTERVAL_MS] 간격,
     * [DISMISS_POLL_TIMEOUT_MS] 데드라인)이며 고정 지연으로 결과를 가정하지 않는다.
     */
    fun dismissSplit() {
        if (machineState != ArrangeState.Idle || sessionInFlight) {
            Log.w(TAG, "dismissSplit: 배치 진행 중 (state=$machineState) — 무시")
            toast("배치 진행 중")
            return
        }
        if (dismissInFlight) {
            Log.w(TAG, "dismissSplit: 이미 해제 진행 중 — 무시")
            return
        }

        dismissInFlight = true
        scope.launch {
            try {
                performDismissSplit()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 조용한 실패 금지: 예측하지 못한 예외도 사용자에게 드러낸다.
                Log.e(TAG, "performDismissSplit 중 예외", e)
                toast("분할 해제 실패: 내부 오류")
            } finally {
                dismissInFlight = false
            }
        }
    }

    /**
     * dismissSplit() 의 실제 로직.
     *
     * [실측 2026-07-25, 결함 #24①] 원래는 자기 패키지([PanelActivity]) 페인 쪽 가장자리로
     * 디바이더를 dispatchGesture SINGLE_STROKE 드래그해 분할을 해제하려 했으나, 실기기에서
     * 디바이더가 스냅백해 분할이 풀리지 않는 현상이 재현됐다(가로/세로 2회, onCompleted 콜백은
     * 정상 도착). 완전히 동일한 기하·시간을 `adb input swipe` 로 주입하면 3/3 성공 — One UI 가
     * dismiss 깊이의 드래그만 접근성 주입 제스처를 거부하는 것으로 추정된다(원인 불명, 경험 법칙).
     * 반면 [PanelActivity] 를 BACK 으로 finish 시키면 분할이 즉시 해소되고 상대 앱(유튜브)이
     * 전체화면으로 자동 복귀함이 실측 확인됐다 — 이 함수는 그 경로를 그대로 쓴다.
     *
     * [실측 2026-07-25, 추가 결함] 메뉴 "분할 해제" 탭 직후 `isSplitActive` 오판정(false-negative)이
     * 2/2 재현됐다 — dumpsys 로는 TYPE_SPLIT_SCREEN_DIVIDER a11y 창이 실제로 존재하는데도 이
     * 체크가 못 봤고, 1분 뒤 같은 체크는 정상이었다. 원인은 풀스크린 스크림(확장 메뉴 오버레이)이
     * 떠 있는 동안 시스템이 "가림(터치 영역 차감)" 기준으로 하위 창들을 접근성 창 목록에서
     * 제외하고, `dismissMenu()` 의 removeView 직후에도 그 스냅샷이 잠시 유지되기 때문으로
     * 추정된다(소형 WRAP_CONTENT 였던 구 메뉴 창에서는 디바이더 영역을 가리지 않아 미발생).
     *
     * [실측 2026-07-25, 재검증] 위 대응으로 넣은 [awaitWindowsSettled](APPLICATION 창 1개 이상
     * 관측) 게이트만으로는 불충분함이 재현됐다 — 게이트는 타임아웃 로그 없이 통과했는데도
     * `isSplitActive` 는 여전히 false-negative, 그 직후 dumpsys 는 TYPE_APPLICATION 3개 +
     * TYPE_SPLIT_SCREEN_DIVIDER 1개로 정상이었다. 즉 스크림 제거 후 a11y 창 목록 재구축이
     * 원자적이지 않다 — 앱 창 일부가 먼저 돌아오고(그래서 게이트는 통과) 디바이더 창/나머지
     * 페인은 나중에 온다. 그래서 "APPLICATION 존재" 라는 약한 조건 대신, 여기서는 직접
     * `isSplitActive` 자체를 조건으로 폴링한다 — 분할이 실제로 있으면 목록이 정착되는 즉시
     * 진행하고, 정말 없으면 [SPLIT_STATE_SETTLE_TIMEOUT_MS] 뒤 정직하게 실패를 보고한다.
     */
    private suspend fun performDismissSplit() {
        val screen = screenRect()
        val active = withTimeoutOrNull(SPLIT_STATE_SETTLE_TIMEOUT_MS) {
            while (!dividerLocator.isSplitActive(safeWindows(), screen)) {
                delay(DISMISS_POLL_INTERVAL_MS)
            }
            true
        } ?: false
        if (!active) {
            Log.i(TAG, "dismissSplit: 분할 화면이 아님")
            toast("분할 화면이 아닙니다")
            return
        }

        // 드래그 도중 버블 오버레이가 간섭하지 않도록 예방적으로 숨긴다(CLAUDE.md 함정 #22 계열 —
        // 메뉴를 거치지 않는 adb 트리거 경로 등에도 동일하게 대응해야 한다).
        FloatingLauncherService.instance?.setBubbleHiddenForArrange(true)
        try {
            // 우리가 만든 FoldWindow 분할인지 먼저 확인한다 — 자기 패키지([PanelActivity]) 페인이
            // 없으면 이 분할은 우리가 배치한 것이 아니므로(다른 앱/시스템이 만든 분할) 손대지 않는다.
            if (actualVideoPaneRect(packageName, screen) == null) {
                Log.w(TAG, "dismissSplit: 자기 패널 페인 미발견 — FoldWindow 분할이 아님으로 판단, 중단")
                toast("FoldWindow 분할이 아닙니다")
                return
            }

            val panel = PanelActivity.instance
            if (panel != null) {
                panel.finishAndRemoveTask()
            } else {
                // 프로세스는 살아 있는데 액티비티 인스턴스만 없는 희귀 경로 폴백 — 태스크를 다시
                // 전면으로 가져와 onCreate/onNewIntent 에서 즉시 finish 시킨다.
                Log.w(TAG, "dismissSplit: PanelActivity.instance null — 인텐트 폴백 경로 사용")
                startActivity(
                    Intent(this, PanelActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                        )
                        putExtra(PanelActivity.EXTRA_FINISH_PANEL, true)
                    },
                )
            }

            val settled = withTimeoutOrNull(DISMISS_POLL_TIMEOUT_MS) {
                while (dividerLocator.isSplitActive(safeWindows(), screen)) {
                    delay(DISMISS_POLL_INTERVAL_MS)
                }
                true
            } ?: false

            if (settled) {
                Log.i(TAG, "dismissSplit: 성공")
                toast("분할 해제 완료")
            } else {
                Log.w(TAG, "dismissSplit: 타임아웃 — 분할 해제 확인 실패")
                toast("분할 해제 실패: 시간 초과")
            }
        } finally {
            FloatingLauncherService.instance?.setBubbleHiddenForArrange(false)
        }
    }

    /**
     * [실측 2026-07-25, 추가 결함] 풀스크린 스크림(확장 메뉴, 터치 가능 오버레이)이 떠 있는 동안
     * 시스템이 "가림(터치 영역 차감)" 기준으로 하위 창을 접근성 창 목록에서 제외한다.
     * `dismissMenu()` 의 removeView 직후에도 그 스냅샷이 잠시 유지돼 `windows` 가 디바이더/앱
     * 창을 못 보는 현상이 실측됐다 — dismissSplit false-negative 2/2 재현("분할 화면이 아님"
     * 오판정), 1분 뒤 동일 체크는 active=true 로 정상. 소형(WRAP_CONTENT) 구 메뉴 창에서는
     * 디바이더 영역을 가리지 않아 미발생했다. 메뉴 경유 dismissSplit 뿐 아니라 배치 세션 시작
     * ([beginSession])도 같은 위험(직전에 [FloatingLauncherService.dismissMenuThenArrange] 가
     * 스크림을 제거함)이 있어 공용 헬퍼로 분리한다.
     *
     * ADR-2 준수: 고정 지연이 아니라 APPLICATION 창이 최소 1개 관측될 때까지의 조건 폴링이다
     * ([DISMISS_POLL_INTERVAL_MS] 간격 재사용). 타임아웃([WINDOWS_SETTLE_TIMEOUT_MS])에 도달해도
     * 예외를 던지지 않고 그냥 리턴한다 — 이후 로직(예: isSplitActive, activeAppPackage)이 스스로
     * 실패를 노출하므로 여기서 강제로 세션을 끊지 않는다(조용한 실패 금지는 타임아웃 로그로 충족).
     */
    private suspend fun awaitWindowsSettled() {
        val settled = withTimeoutOrNull(WINDOWS_SETTLE_TIMEOUT_MS) {
            while (safeWindows().none { runCatching { it.type }.getOrNull() == AccessibilityWindowInfo.TYPE_APPLICATION }) {
                delay(DISMISS_POLL_INTERVAL_MS)
            }
            true
        }
        if (settled == null) {
            Log.w(
                TAG,
                "awaitWindowsSettled: ${WINDOWS_SETTLE_TIMEOUT_MS}ms 내 APPLICATION 창 미관측 — 진행은 계속함",
            )
        }
    }

    // ══════════════════════════════════════════════════════════
    // 세션 시작 (ADR-1 3단 폴백 결정 + Start 디스패치)
    // ══════════════════════════════════════════════════════════

    private suspend fun beginSession(target: String, placementOverride: Placement?, aspectOverride: Float?) {
        // [실측 2026-07-25, 추가 결함] 메뉴 경유 트리거([FloatingLauncherService.dismissMenuThenArrange])
        // 직후에는 방금 제거된 풀스크린 스크림 탓에 접근성 창 목록 스냅샷이 잠시 불완전할 수 있다
        // (위 [awaitWindowsSettled] KDoc 실측 근거 참고). 세션이 windows 를 쓰기 시작하기 전
        // 최전방에서 선대기한다.
        awaitWindowsSettled()

        // [실측 2026-07-25, A/B 실험] 버블 오버레이 창이 떠 있는 동안 분할 진입을 실행하면
        // SplitEntry step3 피커發 PanelActivity 가 분할 페인이 아니라 전체화면으로 낙착해
        // 자가 가드 즉시 종료 → 분할 쌍 미수렴 → ENTRY_STEP_FAILED 로 귀결됨이 재현됐다
        // (버블 ON 2회 실패, 동일 빌드·경로에서 버블 OFF 는 즉시 성공). 세션 시작 시점에
        // 버블 창을 완전히 제거한다 — 복원은 cleanupSession() 이 모든 종료 경로(Done/Failed/
        // Cancel)에서 공통으로 수행한다.
        FloatingLauncherService.instance?.setBubbleHiddenForArrange(true)

        targetPackage = target
        targetLabel = runCatching {
            val appInfo = packageManager.getApplicationInfo(target, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()

        // [측정 2026-07-25] 잔존 FW Panel 태스크(직전 세션이 프로세스 강제 종료로 자가 가드를
        // 못 돌린 경우)가 분할 진입 피커의 "최근 앱" 섹션에 노출되면, SplitEntry 셀렉터가 그
        // 죽은 태스크 카드를 탭 → 재개 즉시 전체화면 자가 가드 종료 → 분할 쌍 미성립 →
        // 3회 재시도 소진 → Failed(ENTRY_STEP_FAILED) 로 귀결됨을 실기기에서 확인했다.
        // 진입 단계가 시작되기 전, target 확정 직후 동기적으로 청소해 피커에 죽은 카드가
        // 뜨지 않게 한다 (조용한 실패 금지: 청소 개수를 로그로 남긴다).
        purgeStalePanelTasks()

        // [실기기 확인, 2026-07-25] target 확정 직후 리사이즈 모드를 판정해 진입 레시피를 고른다.
        // null(판정 불가 — 리플렉션 실패 등)은 안전하게 DRAG 로 폴백한다 (조용한 실패 금지: 로그로 드러냄).
        val unresizeableDetection = ResizeModeDetector.isActivitiesUnresizeable(packageManager, target)
        entryRecipe = if (unresizeableDetection == true) EntryRecipe.MENU else EntryRecipe.DRAG
        Log.i(
            TAG,
            "resize-mode detection: target=$target unresizeableDetection=$unresizeableDetection " +
                "recipe=$entryRecipe",
        )

        // 1) 프로파일 로드 (성공만 캐싱, 실패 시 매번 재시도 + null config 로 진행)
        val config = loadProfilesConfig()

        // 2) ADR-1 티어 ②: 분할 진입 전 스냅샷 실측 (best effort — 실패해도 진행)
        val measurement = preMeasureAspect(SystemClock.uptimeMillis(), target)

        // 3) 종횡비/배치 결정
        val profile = config?.profiles?.firstOrNull { it.packageName == target }
        val presetAspect = aspectOverride ?: config?.defaults?.aspect ?: DEFAULT_ASPECT
        val resolved = AspectResolver.resolve(profile, measurement, presetAspect)
        resolvedAspect = resolved

        // P3-3: 명시 오버라이드(메뉴 위/아래 선택)가 항상 최우선이고, 그다음 이 앱의 "마지막 성공
        // 배치"(store.lastSuccessfulPlacement), 그다음 프로파일/기본값, 최종 폴백은 TOP. 조용한
        // 실패 금지 원칙에 따라 어느 티어가 이겼는지 arrange decision 로그에 남긴다.
        val lastSuccessPlacement = store.lastSuccessfulPlacement(target)
        val profilePlacement = profile?.placement
        val defaultsPlacement = config?.defaults?.placement
        val placement: Placement
        val placementSource: String
        when {
            placementOverride != null -> {
                placement = placementOverride
                placementSource = "OVERRIDE"
            }
            lastSuccessPlacement != null -> {
                placement = lastSuccessPlacement
                placementSource = "LAST_SUCCESS"
            }
            profilePlacement != null -> {
                placement = profilePlacement
                placementSource = "PROFILE"
            }
            defaultsPlacement != null -> {
                placement = defaultsPlacement
                placementSource = "DEFAULTS"
            }
            else -> {
                placement = Placement.TOP
                placementSource = "FALLBACK"
            }
        }
        desiredPlacement = placement
        effectivePlacement = placement

        val computedPlan = SplitPlanner.plan(geometry, resolved.aspect, placement)
        plan = computedPlan

        Log.i(
            TAG,
            "arrange decision: target=$target label=$targetLabel aspectSource=${resolved.source} " +
                "aspect=${resolved.aspect} placement=$placement placementSource=$placementSource " +
                "dividerCenterY=${computedPlan.dividerCenterY} clamp=${computedPlan.clampReason} " +
                "preMeasure=${measurement?.let { "conf=${it.confidence}" } ?: "none"}",
        )

        // [측정 2026-07-25] PROFILE(사용자가 고정한 진실) 종횡비 세션에서 오염된 재측정(컨트롤
        // 오버레이/DRM 잔상을 띠로 오인, residual=224)이 정확한 배치를 과축소했다(1235→1011).
        // PROFILE 소스는 재측정이 오염되기 쉬운데도 그 결과가 신뢰된 배치를 덮어쓰는 구조적 결함 —
        // 보정은 신뢰 가능한 측정 경로(MEASURED/PRESET)에서만 켠다. config?.defaults?.closedLoopCorrection
        // 이 false 면 어떤 소스든 보정하지 않는다(사용자/프로파일이 명시적으로 끈 값 우선).
        arrangeConfig = ArrangeConfig(
            residualTolerancePx = config?.defaults?.residualTolerancePx ?: 8,
            // [실기기 확인, 2026-07-25] 진입 단계 수는 레시피에 따라 달라진다 — DRAG(리사이저블
            // 앱 기본) 3단계, MENU(UNRESIZEABLE 전용, 회전 우회) 5단계.
            entryStepCount = entryRecipe.stepCount,
            // [측정 2026-07-25] 위치 교정(스왑 3s + 회전×2 폴백)이 Dragging 상태 안에서 실행돼
            // 기본 3000ms 로는 DRAG_TIMEOUT (실측). 교정 경로 포함 예산으로 확대.
            // ArrangeConfig 기본값 자체는 불변 — 이 세션 오버라이드만 확대한다.
            dragTimeoutMs = SESSION_DRAG_TIMEOUT_MS,
            closedLoopCorrection = (resolved.source != AspectSource.PROFILE) &&
                (config?.defaults?.closedLoopCorrection ?: true),
        )

        dispatch(ArrangeEvent.Start(SystemClock.uptimeMillis(), computedPlan.dividerCenterY))
        startTickLoop()
    }

    /**
     * [측정 2026-07-25] 앱 재설치 등으로 프로세스가 강제 종료되면 [PanelActivity]의 자가 가드
     * (onResume/onMultiWindowModeChanged 의 finishAndRemoveTask)가 실행될 기회를 얻지 못해
     * 태스크 레코드만 잔존한다. 이 잔존 카드가 분할 진입 피커의 "최근 앱" 섹션에 뜨면
     * SplitEntry 의 피커 셀렉터가 그 죽은 태스크를 탭해 재개 → 즉시 전체화면 감지로 자가 가드가
     * 종료 → 분할 쌍이 성립하지 못해 ENTRY_STEP_FAILED 로 귀결됨을 실기기 로그+스크린샷으로
     * 확인했다. `appTasks` 는 자기 앱(패키지) 소유 태스크만 반환하므로 다른 앱을 건드릴 위험은
     * 없지만, 그중에서도 [PanelActivity] 컴포넌트만 선별해 제거한다 (서비스 자체 태스크나
     * 다른 액티비티 태스크를 오제거하지 않도록).
     */
    private fun purgeStalePanelTasks() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        var purged = 0
        runCatching {
            am.appTasks.forEach { task ->
                val component = runCatching { task.taskInfo.baseIntent.component }.getOrNull()
                if (component?.className == PanelActivity::class.java.name) {
                    runCatching { task.finishAndRemoveTask() }.onSuccess { purged++ }
                }
            }
        }
        if (purged > 0) Log.i(TAG, "purgeStalePanelTasks: 잔존 패널 태스크 $purged 개 제거")
    }

    private suspend fun loadProfilesConfig(): WindowProfilesConfig? {
        cachedProfilesConfig?.let { return it }

        val text = withContext(Dispatchers.IO) {
            runCatching {
                assets.open(WindowProfilesParser.PROFILES_ASSET_NAME).bufferedReader().use { it.readText() }
            }.getOrNull()
        }
        if (text == null) {
            Log.w(TAG, "profiles asset 읽기 실패 (${WindowProfilesParser.PROFILES_ASSET_NAME}) — null config 로 진행")
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

    /**
     * [측정 2026-07-25] 분할 활성 상태에서 트리거된 세션은 전체 화면을 그대로 스캔하면
     * "상단 DRM 검정(넷플릭스) + 디바이더 + 하단 패널 UI"가 섞인 프레임을 스캔해 쓰레기 측정이
     * 고신뢰로 채택됐다 — 실측 1(전체화면): aspect=1.1423/conf=0.906 채택 → 오배치,
     * 실측 2(분할+재생중): aspect=2.9514/conf=0.974 채택 → 영상 페인 ~575px 로 압착.
     * targetPackage 의 실제 APPLICATION 창 rect 를 찾을 수 있으면(분할 활성 등) 그 rect 로
     * crop 해 대상 앱 콘텐츠만 스캔하고, 못 찾으면(전체화면 시나리오) 기존처럼 전체 비트맵을 스캔한다.
     */
    private suspend fun preMeasureAspect(now: Long, targetPackage: String?): AspectMeasurement? {
        if (now < lastScreenshotAtMs + SCREENSHOT_MIN_INTERVAL_MS) {
            Log.i(TAG, "pre-measure 스킵: 스크린샷 레이트리밋 백오프 중 (함정 #3)")
            return null
        }
        val bitmap = captureScreen() ?: return null
        try {
            val paneRect = actualVideoPaneRect(targetPackage, screenRect())
            var scanCrop: Bitmap? = null
            val scanTarget: Bitmap = if (paneRect != null) {
                val crop = cropToRect(bitmap, paneRect)
                if (crop == null) {
                    // crop 실패 시 전체 화면으로 폴백하지 않는다 — 그게 바로 위 쓰레기 측정의 원인이었다.
                    Log.w(TAG, "pre-measure: 대상 페인 crop 실패 — 측정 포기")
                    return null
                }
                scanCrop = crop
                crop
            } else {
                bitmap
            }
            return try {
                val scan = scanTarget.toLetterboxScan(rowStride = ROW_STRIDE)
                LetterboxDetector.resolveAspect(scan)
            } catch (e: Exception) {
                Log.w(TAG, "pre-measure 스캔 실패", e)
                null
            } finally {
                scanCrop?.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * bitmap 을 rect 로 crop 한다. bitmap 경계로 좌표를 coerce 하고, 실패 시 null 을 반환한다
     * (handleMeasureLetterbox 와 preMeasureAspect 가 공유하는 crop 로직 — 함정 #4 무관, 반환된
     * crop 은 호출자가 사용 후 recycle() 해야 한다. 원본 bitmap 은 이 함수가 건드리지 않는다).
     */
    private fun cropToRect(bitmap: Bitmap, rect: IntRect): Bitmap? {
        val left = rect.left.coerceIn(0, bitmap.width - 1)
        val top = rect.top.coerceIn(0, bitmap.height - 1)
        val right = rect.right.coerceIn(left + 1, bitmap.width)
        val bottom = rect.bottom.coerceIn(top + 1, bitmap.height)
        return try {
            Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        } catch (e: Exception) {
            Log.w(TAG, "cropToRect 실패", e)
            null
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

    /**
     * 세션 종료 공통 경로. [dispatch] 가 Done/Failed 터미널 상태에 도달할 때마다 부른다 —
     * [cancelArrange] 도 내부적으로 Cancel 이벤트를 dispatch 해 Failed(CANCELLED) 를 거치므로
     * Done·Failed·Cancel 세 종료 경로 모두 이 함수 하나로 수렴한다. 버블 재표시 복원을
     * 여기 한 곳에 두면 세 경로 전부를 자동으로 커버한다.
     */
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
        entryRecipe = EntryRecipe.DRAG
        // 세션 시작 시 숨긴 버블을 복원한다 (beginSession 의 setBubbleHiddenForArrange(true) 짝).
        FloatingLauncherService.instance?.setBubbleHiddenForArrange(false)
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
                recipe = entryRecipe,
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
                    paneSwapper.swap(handleForSwap, SWAP_TIMEOUT_MS, ::awaitDividerSettled) {
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
                    // [측정 2026-07-25] "창 전환" 클릭 result=true 인데 실제 스왑 미발생이 2회
                    // 연속 실측됐다 — 원인 미상. 최종 폴백으로 디바이더 핸들 회전을 2회 반복한다
                    // (T/B → 회전1 → L/R → 회전2 → B/T, SplitEntry MENU step5 와 동일 원리 —
                    // 회전 자체는 4회 전부 동작 실증됨).
                    Log.w(TAG, "DragDividerTo: pane swap 실패 — 회전×2 폴백 시도")
                    val rotated = rotateTwiceFallback(target, screen)

                    if (rotated) {
                        effectivePlacement = desiredPlacement
                        dividerLocator.locate(safeWindows(), screen)?.let { lastHandle = it }
                        Log.i(TAG, "DragDividerTo: 회전×2 폴백 성공 — 희망 위치($desiredPlacement) 유지")
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
                            "DragDividerTo: pane swap + 회전×2 폴백 모두 실패 — 실제 위치($recomputedActual) " +
                                "유지, 재계산 targetY=$realTargetY (사용자 의도 부분 달성: letterbox는 " +
                                "제거하되 위치는 유지 — 조용한 실패 금지: reportTerminal 이 최종 토스트로 고지)",
                        )
                    }
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

    /**
     * [#20] `PaneSwapper.swap` 의 정착 게이트 콜백. [DIVIDER_SETTLE_POLL_INTERVAL_MS] 간격으로
     * 연속 2회 [dividerLocator.locate] 결과(핸들)가 non-null 이고 완전히 동일하면([DividerHandle]
     * 은 data class 라 구조적 동등성) "정착"으로 본다 — 회전/드래그 직후 애니메이션이 끝나기 전의
     * 클릭 무효화(docs/DESIGN_20_CLICK_CYCLE.md §1 [inferred])에 대응한다.
     *
     * ADR-2 준수: 조건 폴링(withTimeoutOrNull + delay 루프)이며 고정 지연으로 "다 됐겠지" 를
     * 가정하지 않는다. best-effort — 타임아웃되면 false 를 돌려줄 뿐 예외를 던지지 않는다
     * (PaneSwapper 가 로그만 남기고 사이클 루프로 진행을 계속한다).
     */
    private suspend fun awaitDividerSettled(budgetMs: Long): Boolean =
        withTimeoutOrNull(budgetMs) {
            var prev: DividerHandle? = null
            while (true) {
                val cur = dividerLocator.locate(safeWindows(), screenRect())
                if (cur != null && cur == prev) return@withTimeoutOrNull true
                prev = cur
                delay(DIVIDER_SETTLE_POLL_INTERVAL_MS)
            }
            @Suppress("UNREACHABLE_CODE") false
        } ?: false

    /**
     * [측정 2026-07-25] `PaneSwapper.swap` ("창 전환" 노드 클릭) result=true 인데 실제 스왑이
     * 일어나지 않는 현상이 2회 연속 실측됐다(3000ms 대기에도 배치 불변, 원인 미상). 반면 디바이더
     * 핸들 탭 → "시계 방향으로 회전" 노드 클릭은 4회 전부 동작 실증됨(SplitEntry MENU step5).
     *
     * 이미 만들어진 상하(T/B) 분할을 90도씩 두 번 회전하면 위/아래 페인이 맞교환된다
     * (T/B → 회전1 → 좌우(L/R) → 회전2 → B/T). 회전 로직 자체는 [DividerPopupRotator] 로 추출돼
     * `SplitEntry.menuStep5` 와 공유한다.
     *
     * 1회차 성공 조건: target+self 두 페인이 [PaneGeometry.isLeftRightSplit].
     * 2회차 성공 조건: 두 페인이 [PaneGeometry.isTopBottomSplit] ∧ 실제 위치가 [desiredPlacement].
     */
    private suspend fun rotateTwiceFallback(target: String?, screen: IntRect): Boolean {
        fun paneRects(): List<IntRect> = listOfNotNull(
            actualVideoPaneRect(target, screen),
            actualVideoPaneRect(packageName, screen),
        )

        val firstRotated = popupRotator.rotateOnce(screen, ROTATE_STEP_TIMEOUT_MS) {
            PaneGeometry.isLeftRightSplit(paneRects(), screen)
        }
        if (!firstRotated) {
            Log.w(TAG, "rotateTwiceFallback: 1회차 회전 실패(좌우 분할 미도달)")
            return false
        }

        val secondRotated = popupRotator.rotateOnce(screen, ROTATE_STEP_TIMEOUT_MS) {
            PaneGeometry.isTopBottomSplit(paneRects(), screen) &&
                actualVideoPanePosition(target, screen) == desiredPlacement
        }
        if (!secondRotated) {
            Log.w(TAG, "rotateTwiceFallback: 2회차 회전 실패(상하 분할 또는 희망 위치 미도달)")
            return false
        }
        return true
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

                val crop = cropToRect(bitmap, paneRect)
                if (crop == null) {
                    Log.w(TAG, "MeasureLetterbox: crop 실패")
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

                // 앱별 "마지막 성공 placement" 저장(P3-3). 사용자가 의도한 위치(desiredPlacement)로
                // 실제로 낙착된 세션만 저장한다 — 스왑/회전 폴백이 모두 실패해 다른 위치로 낙착된
                // 세션(effectivePlacement != desiredPlacement)은 저장하지 않는다. 사용자가 고르지
                // 않은 위치가 다음 탭의 기본값을 조용히 오염시키면 안 된다(PROGRESS 열린 질문
                // #19/#20, 위치 전환 실패 실측 근거). verified(측정 검증) 여부는 배치 자체의 성공과
                // 무관하므로 저장 조건에 넣지 않는다. cleanupSession() 이 targetPackage 를 곧
                // null 로 되돌리므로 지역 변수로 캡처한 뒤 기존 서비스 스코프에서 저장한다.
                val pkg = targetPackage
                val placementToPersist = effectivePlacement
                if (pkg != null && effectivePlacement == desiredPlacement) {
                    // fire-and-forget — ProfileStore.saveLastSuccessfulPlacement 이 내부에서
                    // IOException 을 잡아 Log.w 로 드러내므로(safeWrite) 여기서 scope 예외 처리가
                    // 필요 없다. 이 launch 는 startArrange 의 바깥 try/catch 범위 밖(별도 코루틴)이라
                    // 방어가 없으면 저장소 오류 시 프로세스가 죽는다 — 반드시 ProfileStore 쪽 보호에 의존한다.
                    scope.launch {
                        store.saveLastSuccessfulPlacement(pkg, placementToPersist)
                    }
                }
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

    /**
     * targetPackage 의 APPLICATION 창 중 "분할 페인 같은" 것과 화면의 가시 교집합. 못 찾으면 null
     * (함정 #2: 오프스크린 슬라이드 대응).
     *
     * [측정 2026-07-25] 넷플릭스가 분할 진입 도중 "최소화된 플레이어" 팝업(같은 패키지의 좁은
     * 부유 창)을 함께 띄우는 현상이 실측됐다. 예전에는 targetPackage 의 APPLICATION 창을
     * `firstOrNull` 로 하나만 집었는데, windows 순회 순서에 따라 이 팝업이 먼저 걸리면 위치
     * 판정(TOP/BOTTOM)과 letterbox crop 이 세션 내내 오염됐다. 이제는 같은 패키지의 **모든**
     * APPLICATION 창 bounds 를 모아 `PaneGeometry.pickPaneLike` 로 선별한다 — 폭이 좁은 팝업은
     * 배제되고, 남은 후보 중 가시 면적이 가장 넓은 것(진짜 분할 페인)이 채택된다.
     */
    private fun actualVideoPaneRect(targetPackage: String?, screen: IntRect): IntRect? {
        if (targetPackage == null) return null
        val candidates = safeWindows()
            .filter { w ->
                runCatching { w.type }.getOrNull() == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    runCatching { w.root?.packageName?.toString() }.getOrNull() == targetPackage
            }
            .mapNotNull { w ->
                val bounds = Rect()
                val ok = runCatching { w.getBoundsInScreen(bounds) }.isSuccess
                if (ok) IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom) else null
            }
        return PaneGeometry.pickPaneLike(candidates, screen)
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
        /**
         * [측정 2026-07-25] 1200ms 예산에서 "창 전환" 클릭 성공(+0.8s 시점) 후 애니메이션 완료 전에
         * 실패 판정 → 하단 플랜 재드래그와 경합. 팝업 재시도(최대 3탭×700ms) + 전환 애니메이션까지
         * 흡수하도록 3000ms 로 확대.
         */
        private const val SWAP_TIMEOUT_MS = 3000L

        /** [#20] [awaitDividerSettled] 연속 2회 일치 판정 폴링 간격. 파일 내 다른 150ms 폴링 관례와 동일. */
        private const val DIVIDER_SETTLE_POLL_INTERVAL_MS = 150L

        /**
         * [측정 2026-07-25] PaneSwapper.swap 실패 폴백(회전×2)의 회전 1회당 예산.
         * SplitEntry MENU step5(회전 1회, 3000ms)와 동일한 예산으로 맞춘다 — 핸들 재조회 →
         * 탭 → 회전 팝업 노드 재시도(최대 3탭×700ms) → 정착 폴링까지 흡수해야 한다.
         */
        private const val ROTATE_STEP_TIMEOUT_MS = 3000L

        /**
         * [측정 2026-07-25] 위치 교정(스왑 3s + 회전×2 폴백 최대 6s)이 `Dragging` 상태 안에서
         * 실행돼 `ArrangeConfig.dragTimeoutMs` 기본값(3000ms)으로는 DRAG_TIMEOUT 이 실측됐다
         * (12:12:33 로그). 교정 경로 전체 + 실제 드래그 제스처까지 흡수하도록 세션 오버라이드를
         * 12000ms 로 확대한다. domain 의 ArrangeConfig 기본값 자체는 불변.
         */
        private const val SESSION_DRAG_TIMEOUT_MS = 12_000L

        /**
         * 머신 Tick 타임아웃(entryStepTimeoutMs)보다 먼저 결과 이벤트가 도착하도록 폴링 예산에
         * 여유를 둔다 — 실기기에서 3001ms > 3000ms 경합 관측 (2026-07-25).
         */
        private const val ENTRY_STEP_RESULT_MARGIN_MS = 400L

        /** ScreenshotSampler.toLetterboxScan 에 전달하는 값과 동일해야 band px → 실제 px 환산이 맞는다 */
        private const val ROW_STRIDE = 2

        private const val DEFAULT_ASPECT = 16f / 9f

        /**
         * P3-2 dismissSplit() 조건 폴링 파라미터. DividerPopupRotator/PaneSwapper 와 같은 관례
         * (150ms 간격)를 따른다. [PanelActivity] finish → 분할 해소는 실기기에서 즉시 반영됨이
         * 확인됐지만(결함 #24① 수정), 여유 있는 타임아웃(3s)은 유지한다.
         */
        private const val DISMISS_POLL_INTERVAL_MS = 150L
        private const val DISMISS_POLL_TIMEOUT_MS = 3_000L

        /**
         * [실측 2026-07-25, 추가 결함] [awaitWindowsSettled] 타임아웃. 풀스크린 스크림 제거 직후
         * 접근성 창 목록 스냅샷이 정상화될 때까지의 여유 — dismissSplit false-negative 재현(2/2)
         * 대응. 정상 케이스는 훨씬 짧게 끝날 것으로 예상되나(경험적 관측 없음), 세션 시작 경로에
         * 부담을 주지 않도록 짧게(1.2s) 잡는다.
         */
        private const val WINDOWS_SETTLE_TIMEOUT_MS = 1_200L

        /**
         * [실측 2026-07-25, 재검증] [awaitWindowsSettled] 만으로는 불충분함이 재현됐다 — 게이트가
         * 타임아웃 없이 통과한 뒤에도 `isSplitActive` false-negative 가 재현됐고(직후 dumpsys 는
         * TYPE_APPLICATION 3 + TYPE_SPLIT_SCREEN_DIVIDER 1 로 정상), 목록 재구축 비원자적 —
         * 앱 창이 먼저, 디바이더가 나중이라는 것으로 해석된다. [performDismissSplit] 진입 시
         * `isSplitActive` 자체를 조건으로 삼아 최대 이 시간만큼 폴링한다 — 분할이 있으면 목록이
         * 정착되는 즉시 통과하고, 정말 없으면 이 시간 뒤 정직하게 "분할 화면이 아닙니다".
         */
        private const val SPLIT_STATE_SETTLE_TIMEOUT_MS = 2_000L

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
