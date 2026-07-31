package dev.dj.foldwindow.service

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.hardware.display.DisplayManager
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
import dev.dj.foldwindow.domain.AutoTriggerLedger
import dev.dj.foldwindow.domain.ConfirmOutcome
import dev.dj.foldwindow.domain.CoverDismissPolicy
import dev.dj.foldwindow.domain.FailureReason
import dev.dj.foldwindow.domain.FlexModePolicy
import dev.dj.foldwindow.domain.FoldPosture
import dev.dj.foldwindow.domain.ForegroundSignalPolicy
import dev.dj.foldwindow.domain.FullscreenPlaybackPolicy
import dev.dj.foldwindow.domain.IntRect
import dev.dj.foldwindow.domain.LetterboxDetector
import dev.dj.foldwindow.domain.MeasurementConsensus
import dev.dj.foldwindow.domain.PaneGeometry
import dev.dj.foldwindow.domain.PanelTaskPolicy
import dev.dj.foldwindow.domain.PanelTaskSnapshot
import dev.dj.foldwindow.domain.Placement
import dev.dj.foldwindow.domain.PopupPlanner
import dev.dj.foldwindow.domain.ResidualBars
import dev.dj.foldwindow.domain.ResolvedAspect
import dev.dj.foldwindow.domain.SplitPlan
import dev.dj.foldwindow.domain.SplitPlanner
import dev.dj.foldwindow.domain.StackListParser
import dev.dj.foldwindow.domain.TriggerSource
import dev.dj.foldwindow.domain.WindowGeometry
import dev.dj.foldwindow.domain.WindowProfilesConfig
import dev.dj.foldwindow.platform.DividerDragger
import dev.dj.foldwindow.platform.DividerHandle
import dev.dj.foldwindow.platform.DividerLocator
import dev.dj.foldwindow.platform.DividerPopupRotator
import dev.dj.foldwindow.platform.DragStrategy
import dev.dj.foldwindow.platform.EntryContext
import dev.dj.foldwindow.platform.EntryRecipe
import dev.dj.foldwindow.platform.FoldStateMonitor
import dev.dj.foldwindow.platform.FullscreenSignalSampler
import dev.dj.foldwindow.platform.HingeAngleMonitor
import dev.dj.foldwindow.platform.MediaPlaybackProbe
import dev.dj.foldwindow.platform.PaneSwapper
import dev.dj.foldwindow.platform.ResizeModeDetector
import dev.dj.foldwindow.platform.SplitEntry
import dev.dj.foldwindow.platform.toLetterboxScan
import dev.dj.foldwindow.platform.toPillarboxScan
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
import kotlin.math.abs

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
    private var tickJob: Job? = null

    /**
     * [F6] dispatch() 재진입 가드. [scope] 가 Dispatchers.Main.immediate 라
     * [executeEffect] 안의 `scope.launch { }` 가 중단점 없이(예: [handleQuerySplitState] 의
     * safeWindows()/isSplitActive() 는 suspend 가 아니다) 곧바로 [dispatch] 를 다시 부를 수 있다.
     * 큐가 없으면 바깥 dispatch 의 `val terminal = machineState` 가 "자기가 만든 상태"가 아니라
     * 안쪽 dispatch 가 이미 바꿔놓은 값을 읽어 터미널 판정(Done/Failed)이 틀어진다 — 지금은
     * 모든 Transition 이 effect 를 0~1개만 내서 우연히 무해하지만, effect 2개짜리 전이가
     * 추가되는 순간 이중 reportTerminal 등으로 발현한다. 재진입 호출은 즉시 처리하지 않고
     * 여기 적재만 해, 최상위 dispatch 호출 하나가 while 루프로 순서대로 드레인한다.
     */
    private var dispatching = false
    private val pendingEvents = ArrayDeque<ArrangeEvent>()

    /** startArrange 호출과 첫 dispatch(Start) 사이의 짧은 창에서 중복 세션 시작을 막는다 */
    @Volatile
    private var sessionInFlight = false

    /**
     * dismissSplit() 중복 실행 방지용 가드. dismissSplit 은 machineState 가 Idle 인 동안에만
     * 동작하는 별도 경로라 sessionInFlight 와 별개 플래그가 필요하다(P3-2).
     */
    @Volatile
    private var dismissInFlight = false

    /**
     * [P4-4] [startArrangeWhenForeground] 의 조건 폴링이 이미 진행 중임을 나타내는 가드.
     * 바로가기를 빠르게 여러 번 탭하는 등으로 동일/다른 대상에 대한 폴링이 중복 기동되는 것을
     * 막는다 — sessionInFlight/dismissInFlight 와 같은 패턴이지만, 이 폴링은 상태 머신이 시작되기
     * 전 단계(머신은 아직 Idle)라 그 두 플래그만으로는 커버되지 않는다.
     */
    @Volatile
    private var foregroundAwaitInFlight = false

    /**
     * [P4-1] [startPopup] 진행 중 재진입 가드. 상태 머신을 쓰지 않는 경로라
     * sessionInFlight/dismissInFlight 와 별개 플래그가 필요하다(같은 패턴).
     */
    @Volatile
    private var popupInFlight = false

    /**
     * [#31] [recoverAfterAutoFailure] 의 BACK 주입 + 조건 폴링 구간 표시.
     * [ForegroundSignalPolicy.releasesLatch] 의 `sessionActive` 입력으로만 쓰인다.
     *
     * 왜 별개 플래그가 필요한가: 자동 세션이 실패하면 [dispatch] 가 [reportTerminal] 직후
     * [cleanupSession] 으로 [machineState] 를 Idle 로 되돌리고, [sessionInFlight] 는 그보다도
     * 훨씬 앞선 [beginSession] 반환 시점에 이미 false 다. 즉 복구 폴링이 도는
     * [AUTO_RECOVERY_TIMEOUT_MS] 창 동안 기존 3개 플래그는 전부 "한가함"을 가리킨다 — 그 창에
     * 도착하는 런처(Recents) 이벤트가 래치를 풀어버리면, 실패한 자동 세션이 스스로 재무장 엣지를
     * 만들어 같은 실패를 반복하게 된다(설계서 D3 이 막으려던 시나리오).
     */
    @Volatile
    private var autoRecoveryInFlight = false

    // ── P3-5 FoldingFeature 연동 (서비스 수명 전체 유지 — cleanupSession() 이 건드리지 않는다.
    // placement 결정 소스 스냅샷([Session.placementSource]) 만 예외로 세션 상태다, 아래 [Session] 참고) ──
    private val flexPolicy = FlexModePolicy()
    private var foldMonitor: FoldStateMonitor? = null
    private var flexCheckJob: Job? = null

    /**
     * 힌지 각도 구독(각도 안정성 게이트 입력). 플렉스 진입(arm)에서만 켜고 이탈/발화/게이트 거부
     * 시 끈다 — 상시 구독은 배터리 낭비다. 인스턴스는 재사용한다("센서 없음" 1회 로그 상태 보존).
     */
    private var hingeMonitor: HingeAngleMonitor? = null

    /**
     * 기본 런처 패키지. onServiceConnected() 에서 1회 해석 — 자동 트리거 게이트 5(포그라운드 부적합)의 제외 대상
     *
     * [#31] [onAccessibilityEvent] 의 래치 해제 판정([ForegroundSignalPolicy.releasesLatch])도
     * **이 값을 그대로 재사용**한다. 그 이벤트는 최대 10Hz 급으로 들어오므로 거기서
     * `PackageManager.resolveActivity` 를 다시 부르면 설계서 D12(메인 스레드 IPC 부담)를 정면으로
     * 위반한다 — 새 캐시를 만들지 않고 기존 1회 해석 결과를 공유하는 이유다(게이트 5/8 의 동작은
     * 그대로다). 해석 실패 시 null 이며, 그 경우 래치 재허용도 적용되지 않는다(fail-safe = 구 동작).
     *
     * 스레딩: 쓰기는 [onServiceConnected], 읽기는 [onAccessibilityEvent]·자동 트리거 평가·
     * [foregroundPackageForExport] 로 전부 메인 스레드다.
     */
    private var homePackage: String? = null

    // ── P4-3 커버 화면 전환 자동 분할 해제 (서비스 수명 전체 유지, flexPolicy 와 별개 정책 인스턴스) ──
    private val coverPolicy = CoverDismissPolicy()
    private var coverCheckJob: Job? = null

    // ── #30 전체화면 재생 자동 트리거 (docs/DESIGN_30_FULLSCREEN_AUTO.md) ──
    // 전부 서비스 수명 전체 유지다 — cleanupSession() 이 건드리지 않는다.
    private val fullscreenPolicy = FullscreenPlaybackPolicy()

    /**
     * 자동 트리거의 패키지 단위 에피소드 래치 + 연속 실패 서킷브레이커(설계서 D1·D2·D3).
     * [FlexModePolicy] 와 달리 **자세가 아니라 사건**으로 해제되므로, 서비스 수명 동안 상태를
     * 유지해야 의미가 있다(부팅 세션 단위 서킷브레이커 포함).
     */
    private val autoLedger = AutoTriggerLedger()
    private val fullscreenSampler = FullscreenSignalSampler()

    /** DI 프레임워크 없음(CLAUDE.md) — 필요한 협력자는 by lazy 로 직접 만든다 */
    private val mediaProbe by lazy { MediaPlaybackProbe(this) }

    private var fullscreenCheckJob: Job? = null

    /**
     * 「개발자 킬스위치(`defaults.fullscreenAutoArrange`) ∧ 사용자 토글
     * (`ProfileStore.isFullscreenAutoEnabled`)」 의 스냅샷. [onServiceConnected] 에서 1회 워밍하고,
     * 사용자가 버블 롱프레스 메뉴에서 토글하면 [refreshFullscreenAutoSnapshot] 이 갱신한다.
     *
     * 왜 스냅샷인가: [onAccessibilityEvent] 는 메인 스레드 동기 콜백이라 suspend 인 자산 파싱/
     * DataStore 읽기를 그 자리에서 할 수 없다. 이 값이 false 면 `windows` 를 **읽기도 전에**
     * 이벤트를 되돌려보내므로(설계서 D12 의 N+1 IPC 방어), 기능이 꺼져 있는 평시에는 이 기능이
     * 메인 스레드에 얹는 비용이 boolean 비교 한 번이다.
     *
     * 초기값 false = "확인되기 전에는 발화하지 않는다". 워밍 실패 시에도 false 로 남는다.
     * [onAccessibilityEvent] 는 메인 스레드, 워밍은 [scope] 코루틴이라 `@Volatile` 을 붙인다.
     */
    @Volatile
    private var fullscreenLeverSnapshot: Boolean = false

    /**
     * [M1 1단계] 배치 세션 1회분의 상태 전부. 종전엔 서비스의 개별 필드 17개였고
     * [cleanupSession] 이 그중 16개를 하나씩 손으로 리셋했다 — 필드를 추가하면서 리셋 한 줄을 잊으면
     * 이전 세션 값이 다음 세션으로 누수되는데, 컴파일러도 테스트도 이를 잡지 못한다.
     * 한 객체로 묶어 `session = null` 하나로 정리하면 그 버그 클래스가 구조적으로 사라진다.
     *
     * val = 세션 시작([beginSession]) 시점에 확정되는 값, var = 세션 중 변이하는 값.
     *
     * **읽기 규약**: 읽기 사이트는 매번 `session?.x ?: <기본값>` 을 **인라인으로** 쓴다.
     * (a) 기본값은 종전 [cleanupSession] 이 되돌리던 값과 정확히 같다 — 세션 종료 뒤 늦게 도착한
     * 코루틴이 이 상태를 읽는 경로가 실재하고(취소 직후 도착하는 [handleMeasureLetterbox] 등),
     * 종전엔 그 경로가 리셋된 기본값을 읽었으므로 이렇게 해야 동작이 문자 그대로 보존된다.
     * (b) `val s = session` 으로 캡처한 뒤 중단점(suspend 호출)을 건너 그 로컬을 재사용하면 안
     * 된다 — 종전 코드는 매번 필드를 다시 읽었고, 그 사이 [cleanupSession] 이 값을 되돌렸을 수
     * 있기 때문이다. 유일한 예외는 [reportTerminal] 로, 본문에 중단점이 없어 상단 캡처가 안전하다.
     *
     * 이 클래스를 `domain/` 으로 옮기지 않는 이유: [DividerHandle] 이 `platform/` 타입이라
     * 순수성(CLAUDE.md 철칙, ArchitectureTest)이 깨지고, 로직이 없어 테스트 이득도 0이다.
     */
    private class Session(
        val targetPackage: String,
        val targetLabel: String?,
        val desiredPlacement: Placement,
        /**
         * P3-5 저장 억제 판정용. placementSource 체인의 최종 결정값 스냅샷
         * ("OVERRIDE"/"FLEX"/"LAST_SUCCESS"/"PROFILE"/"DEFAULTS"/"FALLBACK"). FLEX 는 사용자가 고른
         * 값이 아니라 자세 자동 결정이므로 reportTerminal 의 last-success 저장에서 제외한다.
         */
        val placementSource: String,
        /**
         * [#30, 설계서 D4] 이 세션을 **누가** 시작했는가. [placementSource] 를 트리거 기원의
         * 프록시로 재사용하던 옛 구조는 두 가지를 동시에 틀리게 했다: 수동 세션이 자세만 플렉스면
         * posture-exit 로 오취소됐고("FLEX" 판정), 자동 세션이라도 placement 티어가 달리 낙착하면
         * 취소되지 않았다. 배치 근거(placementSource)와 세션 기원(이 필드)은 직교하는 값이므로
         * 분리한다 — 자동 세션의 토스트 억제(D19)와 last-success 저장 제외도 이 필드가 판정한다.
         */
        val triggerSource: TriggerSource,
        /** 합치 실패 시 폴백할 이번 세션의 PRESET 값(aspectOverride ?: config.defaults.aspect ?: DEFAULT_ASPECT) */
        val presetAspect: Float,
        /** window_profiles.json defaults.requireMeasurementAgreement 의 세션 스냅샷(#12 롤백 레버) */
        val requireAgreement: Boolean,
        /** defaults.cacheMeasuredAspect 의 세션 스냅샷(§6 롤백 레버) */
        val cacheAspectEnabled: Boolean,
        /** 이 앱의 캐시된 실측 종횡비(DESIGN_12 §6). 합치 불합치 시 PRESET 보다 우선하는 폴백 사전값 */
        val cachedAspect: Float?,
        /** confirm 합치 게이트의 비교 대상(진입 전 행축 pre-measure 결과) */
        val preMeasurement: AspectMeasurement?,
        /**
         * [실기기 확인, 2026-07-25] UNRESIZEABLE 앱(넷플릭스류)은 드래그 레시피가 팝업(프리폼)으로
         * 라우팅돼 상하 분할-선택 진입이 불가능함이 확인됐다. `ResizeModeDetector` 판정 결과로
         * `beginSession` 이 세션마다 결정한다(세션 밖 폴백은 종전 `cleanupSession` 과 동일하게 DRAG).
         */
        val entryRecipe: EntryRecipe,
        /**
         * 이번 세션의 오케스트레이션 설정. 종전 `arrangeConfig` 필드는 [cleanupSession] 이 리셋하지
         * **않아서** 세션 종료 후에도 직전 값이 남아 있었지만, 세션 밖에서 이 값이 관측될 수 있는
         * 경로가 없으므로 `ArrangeConfig()` 기본값 폴백과 등가다:
         * (a) session==null ⟺ [machineState]==Idle 이고, `ArrangeStateMachine.reduce` 의 Idle 분기
         *     (`reduceIdle`)는 config 를 인자로 받지도 않는다 → 스테일 이벤트 경로에서 관측 불가,
         * (b) [handlePerformEntryStep] 은 세션 진행 중에만 도달하고,
         * (c) [reportTerminal] 은 [dispatch] 안에서 [cleanupSession] **보다 먼저** 호출돼 세션이
         *     아직 살아 있으며,
         * (d) Start 이벤트는 [beginSession] 이 이 값을 확정한 **뒤** 디스패치된다.
         */
        val config: ArrangeConfig,
    ) {
        var effectivePlacement: Placement = desiredPlacement
        var plan: SplitPlan? = null
        var resolvedAspect: ResolvedAspect? = null

        /** confirm 은 세션당 1회만 — 보정 재드래그(Verifying→Dragging 재진입)에서 반복하지 않는다 */
        var aspectConfirmed: Boolean = false

        /** 이번 세션에서 합치로 채택된 종횡비. non-null ∧ Done(verified=true) 이면 캐시에 저장한다(§6 admission) */
        var consensusAdoptedAspect: Float? = null
        var lastHandle: DividerHandle? = null
    }

    /** 진행 중인 배치 세션. 터미널 상태에서 [cleanupSession] 이 null 로 되돌린다 */
    private var session: Session? = null

    // ── 서비스 수명 필드 (세션 상태가 아니다 — cleanupSession() 이 건드리지 않는다) ──
    private val geometry = WindowGeometry.foldSevenLandscape()
    private var lastScreenshotAtMs: Long = 0L
    private var lastDragCompletedAtMs: Long = 0L
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

        // P3-5: 기본 런처 패키지를 1회 해석해둔다 — 자동 트리거 게이트에서 "포그라운드가 런처"(배치할
        // 대상 앱이 없는 상태)를 걸러내는 데 쓴다. 실패해도 크래시하지 않고 null 로 안전 폴백한다.
        homePackage = runCatching {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.onFailure { Log.w(TAG, "onServiceConnected: 기본 런처 패키지 조회 실패", it) }.getOrNull()

        // P3-5: 폴드 상태 구독 시작. 콜백은 scope(Main.immediate)에서 호출된다(FoldStateMonitor 계약).
        foldMonitor = FoldStateMonitor(this).also { monitor ->
            monitor.start(scope) { posture -> onFoldPosture(posture) }
        }

        // [#30] 전체화면 자동 트리거 선차단 스냅샷 워밍. 도착 전까지는 false(발화 안 함)라
        // 콜드스타트 구간에 오발화가 없다 — 어차피 FullscreenPlaybackPolicy 의 콜드스타트
        // 가드(D5)가 첫 샘플을 베이스라인으로만 쓰므로 이중 방어다.
        refreshFullscreenAutoSnapshot()

        Log.i(TAG, "arranger service connected")
    }

    override fun onDestroy() {
        instance = null
        tickJob?.cancel()
        foldMonitor?.stop()
        hingeMonitor?.stop()
        scope.cancel()
        screenshotExecutor.shutdown()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            // [#31] 종전의 `?: return` 을 제거했다. 아래 두 판정을 **모두** 통과시켜야 하므로
            // null 은 정책([ForegroundSignalPolicy])이 처리한다. 이 return 은 같은 이벤트 타입
            // 안에서만 의미가 있었다 — 아래 TYPE_WINDOWS_CHANGED / TYPE_VIEW_CLICKED 분기는
            // 동일한 `event.eventType` 을 서로 다른 상수와 `==` 로 비교하는 배타적 분기라,
            // 이 분기에 들어온 이벤트는 애초에 그 둘을 만족할 수 없다(동작 변화 0).
            val pkg = event.packageName?.toString()

            if (ForegroundSignalPolicy.tracksAsForeground(pkg, packageName, EXCLUDED_FOREGROUND_PACKAGES)) {
                // 포그라운드 추적은 배치 진행 중에도 계속 돈다. 세션은 시작 시점의 스냅샷
                // ([Session.targetPackage])만 쓰므로 Recents 진입 중 런처가 잠깐 포그라운드가 돼도
                // 세션이 오염되지 않는다.
                lastForegroundPkg = pkg
            }

            // [#30, 설계서 D1] 자동 트리거 래치의 **유일한 시간 무관 해제 조건**. 래치 패키지를
            // 실제로 떠났을 때만 풀린다(AutoTriggerLedger.onForeground 가 판정) — 여기서
            // 이벤트를 흘려보내면 사용자가 다른 앱에 갔다 돌아와도 영영 자동 발화하지 않는다.
            //
            // [#31, 21차 실측] 그 "흘려보냄"이 실제로 일어나고 있었다 — 추적용 제외 목록에
            // 홈 런처가 들어 있어 「홈 → 복귀」 경로에서 이 호출 자체가 없었다(W1-3 불성립).
            // 그래서 추적 신호와 해제 신호를 분리한다. sessionActive 는 우리 진입 경로가 지나는
            // Recents(= 런처) 이벤트로 자동 세션이 스스로 래치를 푸는 것을 막는다(설계서 D3).
            val sessionActive = machineState != ArrangeState.Idle ||
                sessionInFlight || dismissInFlight || autoRecoveryInFlight
            if (ForegroundSignalPolicy.releasesLatch(
                    pkg,
                    packageName,
                    homePackage,
                    EXCLUDED_FOREGROUND_PACKAGES,
                    sessionActive,
                )
            ) {
                // 조용한 실패 금지(CLAUDE.md): 래치 해제는 자동 재발화의 전제 조건인데 종전에는
                // logcat 에 아무 흔적도 남지 않아, #31 의 원인 규명에 실기기 분리 실험이 필요했다.
                //
                // [#31 2차] 홈 여부를 장부에 그대로 넘긴다 — 이 이벤트가 **실제로** 래치를 푸는지는
                // 래치의 기원(sticky = 분할 해제발)이 정하며, 그 판정은 순수 도메인
                // ([AutoTriggerLedger.onForeground])에 있다. 서비스는 사실만 전달한다.
                if (autoLedger.onForeground(pkg, isHome = pkg != null && pkg == homePackage)) {
                    Log.i(TAG, "auto latch released: foreground=$pkg")
                }
            }
        }

        // [#30] 전체화면 재생 진입/이탈 신호원. 최전방 선차단은 onWindowsChangedEvent 안에 있다.
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            onWindowsChangedEvent()
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
     * @param triggerSource 이 세션을 시작한 주체(#30 D4). **기본값이 있어 기존 호출부는 무변경**이며,
     *   자동 경로만 명시적으로 넘긴다(`evaluateFlexAutoTrigger` → FLEX_AUTO,
     *   `evaluateFullscreenAutoTrigger` → FULLSCREEN_AUTO, `startArrangeWhenForeground` → SHORTCUT).
     *   아래 가드 3개의 토스트는 자동 세션에서 억제된다(D19) — 사용자가 요청한 적 없는 동작의
     *   진단 문구를 띄우는 것은 "조용한 실패 금지" 원칙의 대상이 아니라 방해다(evaluateFlexAutoTrigger
     *   KDoc 의 동일 원칙 승계). 사유는 언제나 Log.w 로 남으므로 진단 가능성은 유지된다.
     */
    fun startArrange(
        placementOverride: Placement?,
        aspectOverride: Float?,
        triggerSource: TriggerSource = TriggerSource.MANUAL,
    ) {
        // F2 1단계 가드: 계산 기하가 실화면과 다르면 디바이더를 조용히 틀린 곳으로 옮기게 되므로,
        // 세션 상태(machineState/sessionInFlight)를 건드리기 전에 가장 먼저 명시적 미지원으로 떨어뜨린다.
        val screen = screenRect()
        if (!geometry.matchesScreen(screen)) {
            Log.w(TAG, "startArrange: 화면 기하 불일치 — screen=${screen.width}x${screen.height} " +
                       "expected=${geometry.usableWidth}x${geometry.usableHeight} (v1 미지원)")
            if (!triggerSource.isAuto) toast("이 화면 방향/디스플레이는 아직 지원하지 않습니다")
            return
        }

        // [#30] busy 가드에 dismissInFlight 를 추가한다 — 분할 해제 폴링(최대 2초) 도중에 자동
        // 트리거가 배치를 시작하면 사용자가 방금 되돌린 것을 즉시 되돌려 놓는 꼴이 된다.
        // machineState 는 dismissSplit 경로에서 Idle 이라 종전 두 조건만으로는 잡히지 않는다.
        if (machineState != ArrangeState.Idle || sessionInFlight || dismissInFlight) {
            Log.w(TAG, "startArrange: 이미 배치 진행 중 (state=$machineState dismissInFlight=$dismissInFlight)")
            if (!triggerSource.isAuto) toast("이미 배치 진행 중")
            return
        }

        val activePkg = activeAppPackage()
        val target = activePkg ?: lastForegroundPkg
        if (target == null) {
            Log.w(TAG, "startArrange: 대상 앱(포그라운드 패키지)을 찾지 못함")
            if (!triggerSource.isAuto) toast("대상 앱을 찾지 못했습니다")
            return
        }
        Log.i(
            TAG,
            "startArrange: target=$target trigger=$triggerSource " +
                "source=${if (activePkg != null) "active-window" else "event-tracked"}",
        )

        // [#30, 설계서 §2.1 래치 계약] 사용자가 **수동으로** 배치를 트리거한 것은 자동화에 대한
        // 재신뢰 신호다 — 이 패키지의 래치와 연속 실패 스트릭을 함께 푼다. 래치 트레이드오프
        // (같은 앱의 다음 영상에서 자동 재발화하지 않음, R7)의 유일한 탈출구가 이 경로다.
        if (!triggerSource.isAuto) autoLedger.onManualTrigger(target)

        sessionInFlight = true
        scope.launch {
            try {
                beginSession(target, placementOverride, aspectOverride, triggerSource)
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
                // [#27/A1, 18차 G1] removeTask 는 step3 소환원인 카드까지 지우는 초과 동작 —
                // finish 만으로 분할이 해소됨이 18차 G1 로 실증됐다.
                panel.finish()
            } else if (hasPanelTask()) {
                // 프로세스는 살아 있는데 액티비티 인스턴스만 없는 희귀 경로 폴백 — 태스크를 다시
                // 전면으로 가져와 onCreate/onNewIntent 에서 즉시 finish 시킨다. [#28] 이 인텐트는
                // FLAG_ACTIVITY_NEW_TASK 를 포함하므로, 우리 패널 태스크가 실제로 존재할 때만
                // 태워야 한다. [S4] 과거에는 태스크가 없을 때 이 인텐트를 태우면 새 태스크의
                // base intent 에 EXTRA_FINISH_PANEL 이 영구히 박혀 이후 step3 소환 시마다 즉시
                // finish 되는 결함이 됐으나, boolean extra 를 1회용 토큰으로 교체하면서 그 결함
                // 클래스는 구조적으로 소멸했다 — base intent 에 토큰 문자열이 남아 있어도 소비
                // 시점의 PanelActivity.finishToken 과 일치하지 않으면 무시된다(PanelActivity.
                // EXTRA_FINISH_TOKEN KDoc 계약 참고). 이 hasPanelTask() 사전 확인은 그와
                // 무관하게 계속 유지한다 — 해제할 패널이 없는데 새 태스크를 만드는 낭비를 막는
                // 별개 목적이다(#28).
                Log.w(TAG, "dismissSplit: PanelActivity.instance null — 인텐트 폴백 경로 사용")
                startActivity(
                    Intent(this, PanelActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP,
                        )
                        putExtra(PanelActivity.EXTRA_FINISH_TOKEN, PanelActivity.issueFinishToken())
                    },
                )
            } else {
                // [#28] 패널 태스크 자체가 없다 = 해제할 우리 분할이 없다는 뜻 — 없는 것을
                // 해제하려고 새 태스크를 만들면 그 부산물(base intent 오염)이 영구 결함이 된다.
                // 폴백을 생략하고 실패를 명시적으로 드러낸다(조용한 실패 금지, CLAUDE.md 규칙 5).
                Log.w(
                    TAG,
                    "dismissSplit: PanelActivity.instance null ∧ 패널 태스크 부재 — " +
                        "인텐트 폴백 생략(#28 base intent 오염 방지)",
                )
                toast("해제할 FoldWindow 패널을 찾지 못했습니다")
                // 해제 액션 자체를 아무것도 태우지 않았으므로 아래 settle 폴링은 반드시 타임아웃
                // 될 뿐 아니라 위 토스트와 중복·혼란만 준다 — 여기서 실패를 확정하고 return 한다.
                // try/finally 안이므로 버블 재표시는 finally 에서 정상 실행된다.
                return
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

    // ══════════════════════════════════════════════════════════
    // P4-1: 팝업(freeform) 모드 — Shizuku 셸 명령
    // ══════════════════════════════════════════════════════════

    /**
     * 현재 전면 앱을 One UI 팝업(freeform) 창으로 재배치한다(DESIGN_P41_FREEFORM.md 후보 A).
     *
     * `ArrangeStateMachine` 은 쓰지 않는다(머신 무변경 원칙) — 셸 명령 1회 + 태스크 조회 +
     * 리사이즈 1회뿐이라 상태 머신을 구동할 만큼 단계가 많지 않고, 이 흐름 자체가 조건 폴링 +
     * 타임아웃 + 명시적 실패로 이미 ADR-2 를 만족한다.
     *
     * 흐름: 전면 패키지 확인 → 종횡비 결정(override 없이 profile → defaults.aspect →
     * DEFAULT_ASPECT, [beginSession] 과 동일한 소스) → [PopupPlanner] 로 bounds 산출 →
     * `am start --windowingMode 5` → 대상 창 출현 폴링 → `am stack list` 로 taskId 조회 →
     * `am task resize` → bounds 검증 폴링. 각 단계 실패는 Log.w + 토스트로 드러내고 즉시
     * 중단한다(조용한 실패 금지).
     *
     * F2 1단계 가드(`geometry.matchesScreen`, [startArrange]/`evaluateFlexAutoTrigger` 참고)는
     * 여기 적용하지 않는다 — 위 bounds 산출이 캐시된 [WindowGeometry] 가 아니라 `screenRect()` 로
     * 실화면을 직접 읽으므로 애초에 그 결함(세로 등에서 계산 기하와 실화면이 어긋나는 문제)에
     * 노출되지 않는다.
     */
    fun startPopup() {
        if (machineState != ArrangeState.Idle || sessionInFlight) {
            Log.w(TAG, "startPopup: 이미 배치 진행 중 (state=$machineState)")
            toast("이미 배치 진행 중")
            return
        }
        if (popupInFlight) {
            Log.w(TAG, "startPopup: 이미 팝업 진행 중 — 중복 요청 무시")
            return
        }
        if (!ShizukuShell.isReady()) {
            Log.w(TAG, "startPopup: Shizuku 미가용")
            toast("Shizuku 를 사용할 수 없습니다")
            return
        }

        val activePkg = activeAppPackage()
        val target = activePkg ?: lastForegroundPkg
        if (target == null) {
            Log.w(TAG, "startPopup: 대상 앱(포그라운드 패키지)을 찾지 못함")
            toast("대상 앱을 찾지 못했습니다")
            return
        }
        Log.i(TAG, "startPopup: target=$target source=${if (activePkg != null) "active-window" else "event-tracked"}")

        popupInFlight = true
        scope.launch {
            try {
                performStartPopup(target)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 조용한 실패 금지: 예측하지 못한 예외도 사용자에게 드러낸다.
                Log.e(TAG, "performStartPopup 중 예외", e)
                toast("팝업 배치 실패: 내부 오류")
            } finally {
                popupInFlight = false
            }
        }
    }

    private suspend fun performStartPopup(target: String) {
        // 종횡비: [beginSession] 과 동일한 폴백 소스지만 팝업엔 override/측정/캐시 티어가 없다
        // (v1 정책 — 단발 배치라 폐루프 보정 대상이 아님).
        val config = loadProfilesConfig()
        val profile = config?.profiles?.firstOrNull { it.packageName == target }
        val aspect = profile?.aspect ?: config?.defaults?.aspect ?: DEFAULT_ASPECT

        val screen = screenRect()
        val bounds = PopupPlanner.plan(screen.width, screen.height, aspect)
        Log.i(TAG, "startPopup: target=$target aspect=$aspect bounds=$bounds")

        val component = runCatching { packageManager.getLaunchIntentForPackage(target)?.component }.getOrNull()
        if (component == null) {
            Log.w(TAG, "startPopup: 런치 컴포넌트를 찾지 못함 (target=$target)")
            toast("앱을 실행할 수 없습니다")
            return
        }

        // argv 로 직접 전달되므로(F3+F4+S2+S3) 셸 파싱이 없다 — 컴포넌트 클래스명에 올 수 있는
        // '$'(예: YouTube Shell$HomeActivity, DEVICE_FACTS.md 「P4-1 프로브 F1~F6」)도 셸 변수
        // 치환 대상이 아니므로 원천적으로 무해하다. 구 `sh -c` + 작은따옴표 인용 방식은 폐기됐다.
        val startResult = ShizukuShell.exec(
            arrayOf("am", "start", "--windowingMode", "5", "-n", component.flattenToString()),
            SHELL_EXEC_TIMEOUT_MS,
        )
        if (startResult == null) {
            Log.w(TAG, "startPopup: am start 실행 실패 (Shizuku exec 응답 없음)")
            toast("팝업 실행 실패")
            return
        }
        Log.i(TAG, "startPopup: am start result=$startResult")

        val windowAppeared = withTimeoutOrNull(POPUP_WINDOW_POLL_TIMEOUT_MS) {
            while (!hasApplicationWindow(target)) {
                delay(POPUP_POLL_INTERVAL_MS)
            }
            true
        } ?: false
        if (!windowAppeared) {
            Log.w(TAG, "startPopup: ${POPUP_WINDOW_POLL_TIMEOUT_MS}ms 내 대상 창 미관측")
            toast("팝업 배치 실패: 창을 찾지 못함")
            return
        }

        val stackListOutput = ShizukuShell.exec(arrayOf("am", "stack", "list"), SHELL_EXEC_TIMEOUT_MS)
        val taskId = stackListOutput?.let { StackListParser.taskIdFor(it, target) }
        if (taskId == null) {
            Log.w(TAG, "startPopup: taskId 조회 실패 (target=$target)")
            toast("팝업 배치 실패: 태스크를 찾지 못함")
            return
        }

        val resizeResult = ShizukuShell.exec(
            arrayOf(
                "am", "task", "resize", "$taskId",
                "${bounds.left}", "${bounds.top}", "${bounds.right}", "${bounds.bottom}",
            ),
            SHELL_EXEC_TIMEOUT_MS,
        )
        if (resizeResult == null) {
            Log.w(TAG, "startPopup: am task resize 실행 실패 (taskId=$taskId)")
            toast("팝업 배치 실패: 크기 조정 실패")
            return
        }

        val verified = withTimeoutOrNull(POPUP_VERIFY_TIMEOUT_MS) {
            while (!boundsMatch(target, bounds)) {
                delay(POPUP_POLL_INTERVAL_MS)
            }
            true
        } ?: false

        if (verified) {
            Log.i(TAG, "popup done: pkg=$target bounds=$bounds")
        } else {
            // 명시적 실패 상태(ADR-2) — 조용히 넘어가지 않고 잔여 상태를 그대로 보고한다.
            Log.w(TAG, "startPopup: ${POPUP_VERIFY_TIMEOUT_MS}ms 내 bounds 일치 확인 실패 (taskId=$taskId)")
            toast("팝업 배치 실패")
        }
    }

    private fun hasApplicationWindow(targetPackage: String): Boolean =
        safeWindows().any { w ->
            runCatching { w.type }.getOrNull() == AccessibilityWindowInfo.TYPE_APPLICATION &&
                runCatching { w.root?.packageName?.toString() }.getOrNull() == targetPackage
        }

    /**
     * [target] 의 APPLICATION 창 bounds 가 [expected] 와 [POPUP_BOUNDS_TOLERANCE_PX] 이내로
     * 일치하는지. F6(DEVICE_FACTS.md)에서 팝업 창 bounds = 태스크 bounds 1:1 로 노출됨이
     * 확인됐으므로, 기존 창 열거 경로를 그대로 재사용해 폴링할 수 있다.
     */
    private fun boundsMatch(target: String, expected: IntRect): Boolean {
        val window = safeWindows().firstOrNull { w ->
            runCatching { w.type }.getOrNull() == AccessibilityWindowInfo.TYPE_APPLICATION &&
                runCatching { w.root?.packageName?.toString() }.getOrNull() == target
        } ?: return false
        val rect = Rect()
        if (!runCatching { window.getBoundsInScreen(rect) }.isSuccess) return false
        val tol = POPUP_BOUNDS_TOLERANCE_PX
        return abs(rect.left - expected.left) <= tol &&
            abs(rect.top - expected.top) <= tol &&
            abs(rect.right - expected.right) <= tol &&
            abs(rect.bottom - expected.bottom) <= tol
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
    // P4-4: 홈 화면 고정 바로가기 지원
    // ══════════════════════════════════════════════════════════

    /**
     * 현재 전면 앱 패키지. 자기 앱·런처(homePackage)·시스템 UI 는 null 로 취급한다.
     * [beginSession] 의 타깃 결정과 같은 소스([activeAppPackage] 1차, [lastForegroundPkg] 이벤트
     * 추적 폴백)를 읽되, 세션 상태([Session.targetPackage] 등)는 전혀 건드리지 않는 읽기 전용 함수다.
     * [FloatingLauncherService] 의 바로가기 export(exportAppPair)와 [startArrangeWhenForeground]
     * 양쪽이 쓴다.
     */
    fun foregroundPackageForExport(): String? {
        val pkg = activeAppPackage() ?: lastForegroundPkg ?: return null
        if (pkg == packageName || pkg == homePackage || pkg == "com.android.systemui") return null
        return pkg
    }

    /**
     * `PairShortcutActivity` 발 트램펄린 진입점 — 대상 앱이 전면에 올 때까지 조건 폴링한 뒤
     * [startArrange] 를 호출한다. 바로가기가 대상 앱을 막 `startActivity` 로 실행한 직후라
     * 접근성 창 목록/포그라운드 추적이 그 전환을 아직 반영하지 못했을 수 있어([awaitWindowsSettled]
     * 와 동일 계열의 문제) 고정 지연 대신 [foregroundPackageForExport] 결과 자체를 조건으로
     * 폴링한다(ADR-2: 조건 폴링 + 타임아웃 + 명시적 실패).
     *
     * 이 폴링은 상태 머신 진입 전 사전 단계라 [machineState] 는 시작조차 하지 않는다 — 타임아웃은
     * 머신의 Failed 가 아니라 이 함수 자신의 토스트+로그로 드러낸다.
     */
    fun startArrangeWhenForeground(targetPkg: String) {
        if (machineState != ArrangeState.Idle || sessionInFlight) {
            Log.w(TAG, "startArrangeWhenForeground: 이미 배치 진행 중 (state=$machineState)")
            toast("이미 배치 진행 중")
            return
        }
        if (foregroundAwaitInFlight) {
            Log.w(TAG, "startArrangeWhenForeground: 이미 대기 중인 폴링이 있음 — 중복 요청 무시 (target=$targetPkg)")
            return
        }

        foregroundAwaitInFlight = true
        scope.launch {
            try {
                val reached = withTimeoutOrNull(SHORTCUT_FOREGROUND_TIMEOUT_MS) {
                    while (foregroundPackageForExport() != targetPkg) {
                        delay(SHORTCUT_FOREGROUND_POLL_INTERVAL_MS)
                    }
                    true
                } ?: false

                if (reached) {
                    Log.i(TAG, "startArrangeWhenForeground: 대상 전면 확인 — 배치 시작 (target=$targetPkg)")
                    // 바로가기 탭은 사용자의 명시적 조작이다(TriggerSource.isAuto == false) —
                    // 토스트도 그대로 나가고 자동 트리거 래치도 풀린다(#30 §2.1 래치 계약).
                    startArrange(
                        placementOverride = null,
                        aspectOverride = null,
                        triggerSource = TriggerSource.SHORTCUT,
                    )
                } else {
                    Log.w(
                        TAG,
                        "startArrangeWhenForeground: ${SHORTCUT_FOREGROUND_TIMEOUT_MS}ms 내 대상 전면 " +
                            "미도달 — 배치 취소 (target=$targetPkg)",
                    )
                    toast("대상 앱이 전면으로 오지 않아 배치를 취소했습니다")
                }
            } finally {
                foregroundAwaitInFlight = false
            }
        }
    }

    // ══════════════════════════════════════════════════════════
    // P3-5: 플렉스(노트북 자세) 자동 트리거
    // ══════════════════════════════════════════════════════════

    /**
     * [FoldStateMonitor] 콜백. 매 자세 방출마다 호출되지만 실제 스케줄은 [FlexModePolicy] 가
     * HALF_OPENED_HORIZONTAL "진입"에서만 발급한다(동일 자세 중복 방출은 흡수됨).
     *
     * 진입 시 [HingeAngleMonitor] 구독을 함께 켜고(각도 안정성 게이트 입력), 이탈 시 폴링 종료와
     * 함께 끈다(배터리 위생). [FlexModePolicy] 는 이탈 분기에서 스스로 disarm + 각도 히스토리
     * 클리어를 수행한다.
     */
    private fun onFoldPosture(posture: FoldPosture) {
        val checkAtMs = flexPolicy.onPosture(posture, SystemClock.uptimeMillis())

        // ── P4-3 커버 화면 전환 자동 분할 해제 ──────────────────────
        // flex 분기보다 먼저 처리한다: 아래 flex 분기는 posture != HALF_OPENED_HORIZONTAL(UNKNOWN
        // 포함)이면 조기 return 하므로, 그 뒤에 두면 정작 UNKNOWN 진입에서 이 로직이 실행되지 않는다.
        //
        // ADR-2 준수(기존 flexCheckJob/awaitFlexTrigger 와 동일 패턴): 아래 delay 는
        // CoverDismissPolicy 가 예약한 디바운스 시각에 "도달"하기 위한 수단일 뿐 성공을 가정하지
        // 않는다 — 도달 후 evaluateCoverAutoDismiss() 가 shouldDismissNow 로 자세가 여전히
        // UNKNOWN 인지 실제로 재검증한다.
        val coverCheckAtMs = coverPolicy.onPosture(posture, SystemClock.uptimeMillis())
        if (posture != FoldPosture.UNKNOWN) {
            // 커버 화면 이탈(또는 애초에 UNKNOWN 이 아님) — 예약된 재검증을 취소한다.
            coverCheckJob?.cancel()
            coverCheckJob = null
        } else if (coverCheckAtMs != null) {
            // UNKNOWN 새 진입 — 재검증을 예약한다. 동일 자세 중복 보고(coverCheckAtMs == null)는
            // 이미 진행 중인 예약을 건드리지 않는다.
            coverCheckJob?.cancel()
            coverCheckJob = scope.launch {
                delay((coverCheckAtMs - SystemClock.uptimeMillis()).coerceAtLeast(0))
                evaluateCoverAutoDismiss()
            }
        }

        if (posture != FoldPosture.HALF_OPENED_HORIZONTAL) {
            // 플렉스 이탈 — 진행 중인 조건 폴링을 끊고 각도 구독을 해제한다.
            flexCheckJob?.cancel()
            flexCheckJob = null
            hingeMonitor?.stop()

            // [실기기 물증, 2026-07-27 23:54] 닫힌 기기를 여는 도중 ~90°에서 멈칫하면 힌지 각도
            // 게이트가 "노트북 자세"로 오판해 FLEX 자동 배치가 발화한다. 발화 시점엔 "열다 멈칫"과
            // "노트북 자세 의도"가 힌지 신호만으로 구분 불가 — 유일한 판별자는 사후 신호다. FLEX 로
            // 시작된 세션이 진행 중인데 자세가 HALF_OPENED_HORIZONTAL 을 벗어나면(FLAT/UNKNOWN 등
            // 사용자가 이어서 완전히 펴거나 접었다는 뜻) "의도 번복"으로 보고 세션을 취소한다.
            // 세션 활성 판정은 startArrange 의 "이미 배치 진행 중" 체크와 동일한 관용구를 그대로
            // 쓴다. 세션이 이미 Done/Failed 로 정리됐다면(cleanupSession 이 machineState 를 Idle 로
            // 되돌리므로) 이 조건 자체가 거짓이 돼 배치 완료 후 기기를 펴서 시청하는 정상 사용례를
            // 건드리지 않는다.
            //
            // [#30, 설계서 D4] 판정 기준을 `placementSource == "FLEX"` 에서
            // [Session.triggerSource] 로 교체했다. placementSource 는 "어느 쪽에 붙일지를 자세로
            // 정했다"는 배치 근거일 뿐 "자세가 세션을 시작했다"는 뜻이 아니다 — 노트북 자세로
            // 거치한 채 사용자가 버블을 탭한 수동 세션도 FLEX 티어를 타므로, 옛 조건은 그 수동
            // 세션을 자세 변화만으로 오취소했다. 반대로 FULLSCREEN_AUTO 세션은 자세와 무관하므로
            // 이 취소 대상이 아니어야 한다 — 두 오류를 이 한 줄이 동시에 닫는다.
            if ((machineState != ArrangeState.Idle || sessionInFlight) &&
                session?.triggerSource == TriggerSource.FLEX_AUTO
            ) {
                Log.i(TAG, "flex session cancelled: posture-exit")
                cancelArrange()
            }
            return
        }
        // 동일 자세 중복 보고(checkAtMs == null)는 진행 중인 폴링을 방해하지 않는다.
        if (checkAtMs == null) return

        val monitor = hingeMonitor ?: HingeAngleMonitor(this).also { hingeMonitor = it }
        monitor.start { angleDeg, atMs -> flexPolicy.onHingeAngle(angleDeg, atMs) }

        flexCheckJob?.cancel()
        flexCheckJob = scope.launch { awaitFlexTrigger(checkAtMs) }
    }

    /**
     * 플렉스 자동 발화 대기 루프.
     *
     * ADR-2: 첫 delay 는 [FlexModePolicy] 가 발급한 디바운스 시각에 "도달"하기 위한 수단일 뿐
     * 성공을 가정하지 않는다 — 도달 후 [FlexModePolicy.shouldTriggerNow] 가 (a) 자세 유지 (b)
     * 디바운스 경과 (c) 힌지 각도 정지를 실제로 재검증한다. 그중 각도 조건은 시간이 지난다고
     * 저절로 참이 되지 않으므로(기기를 계속 접는 중이면 계속 거짓) 이후는
     * [FLEX_ANGLE_POLL_INTERVAL_MS] 간격 **조건 폴링**이다.
     *
     * [실기기 물증, 2026-07-27] 완전히 닫는 동작이 HALF_OPENED 대역을 ~2s(2표본 2.1s/1.95s)
     * 체류해 800ms 디바운스가 닫는 도중 만료 → 오발화(닫힌 기기에서 Recents 진입 시도 →
     * ENTRY_STEP_FAILED). 예전에는 이 재검증이 1회뿐이라 "지금 불안정" == "이번 진입 포기"였는데,
     * 그 판정을 폴링으로 바꿔 "접는 중엔 안 쏘고, 노트북 자세로 멎으면 그때 쏜다"가 됐다.
     *
     * 종료 조건: 발화(게이트 체인까지 진행) 또는 [FlexModePolicy.isArmed] == false — 자세 이탈이
     * 무장을 해제하므로 별도 타임아웃이 필요 없다(이탈은 [onFoldPosture] 가 job 자체도 취소한다).
     * 폴링 재시도는 로그를 남기지 않는다(스팸 방지) — 최종 발화/스킵 로그만 남는다.
     */
    private suspend fun awaitFlexTrigger(checkAtMs: Long) {
        try {
            delay((checkAtMs - SystemClock.uptimeMillis()).coerceAtLeast(0))
            while (flexPolicy.isArmed) {
                if (flexPolicy.shouldTriggerNow(SystemClock.uptimeMillis())) {
                    evaluateFlexAutoTrigger()
                    return
                }
                delay(FLEX_ANGLE_POLL_INTERVAL_MS)
            }
        } finally {
            // 이 진입 구간이 실제로 끝났을 때만 센서를 놓는다. 취소 사유가 "재진입"이면
            // (onFoldPosture 가 새 구독을 이미 시작하고 재무장했으므로) 건드리면 안 된다.
            if (!flexPolicy.isArmed) hingeMonitor?.stop()
        }
    }

    /**
     * 자동 발화 게이트 체인. 순서대로 검사하고 첫 실패에서 [FlexModePolicy.disarm] + 사유 로그 후
     * 반환한다 — 이번 플렉스 진입에서는 재발화하지 않는다(재접기만이 재무장 경로).
     *
     * 자동 스킵은 토스트를 띄우지 않는다: "조용한 실패 금지" 원칙은 *사용자가 시작한 행위*의 실패를
     * 사용자가 알 수 있게 하자는 것인데, 이 트리거는 사용자 조작이 아니라 센서 이벤트로 자동
     * 발화되는 것이라 매 스킵마다 토스트를 띄우면 오히려 사용자를 방해한다(원칙 비대상 — 대신
     * Log.i 로 사유를 항상 남겨 디버깅 가능성은 유지한다).
     */
    private suspend fun evaluateFlexAutoTrigger() {
        val flexLeverOn = loadProfilesConfig()?.defaults?.flexAutoTopPlacement ?: true
        if (!flexLeverOn) {
            flexPolicy.disarm()
            Log.i(TAG, "flex auto-arrange skipped: reason=lever-off")
            return
        }

        if (machineState != ArrangeState.Idle || sessionInFlight || dismissInFlight) {
            flexPolicy.disarm()
            Log.i(TAG, "flex auto-arrange skipped: reason=busy")
            return
        }

        // F2 1단계 가드: 자동 트리거도 동일 결함(세로 등에서 디바이더가 조용히 틀린 곳으로 이동)에
        // 노출된다. 토스트는 없다 — 자동 발화는 "조용한 실패 금지" 원칙 비대상(위 KDoc 참고, 기존
        // 게이트들의 선례 그대로).
        val screen = screenRect()
        if (!geometry.matchesScreen(screen)) {
            flexPolicy.disarm()
            Log.i(TAG, "flex auto-arrange skipped: reason=geometry-mismatch " +
                       "screen=${screen.width}x${screen.height}")
            return
        }

        if (dividerLocator.isSplitActive(safeWindows(), screen)) {
            // 기존 분할 재배치(예: 위/아래 재조정)는 v1.5 범위 — 여기서는 새 분할 진입만 다룬다.
            flexPolicy.disarm()
            Log.i(TAG, "flex auto-arrange skipped: reason=split-already-active")
            return
        }

        // 닫는 동작의 오발화 2차 방어: 화면이 꺼져 있으면(완전히 접히는 도중/직후) 자동 배치를 하지 않는다.
        val displayManager = getSystemService(DISPLAY_SERVICE) as? DisplayManager
        val displayState = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.state
        if (displayState != Display.STATE_ON) {
            flexPolicy.disarm()
            Log.i(TAG, "flex auto-arrange skipped: reason=display-off")
            return
        }

        val foregroundPkg = activeAppPackage() ?: lastForegroundPkg
        val unsuitable = foregroundPkg == null ||
            foregroundPkg == packageName ||
            foregroundPkg == homePackage ||
            foregroundPkg in EXCLUDED_FOREGROUND_PACKAGES
        if (unsuitable) {
            flexPolicy.disarm()
            Log.i(TAG, "flex auto-arrange skipped: reason=foreground-unsuitable pkg=$foregroundPkg")
            return
        }

        Log.i(TAG, "flex auto-arrange trigger: target=$foregroundPkg")
        // placement 는 여기서 넘기지 않는다 — beginSession 의 placement 체인(FLEX 티어)이
        // flexPolicy.posture 를 직접 참조해 TOP 을 강제한다(자동·수동 단일 메커니즘, 브리프 근거).
        // [#30 D4] 세션 기원을 명시한다 — posture-exit 오취소 판정과 토스트 억제가 이 값을 본다.
        startArrange(
            placementOverride = null,
            aspectOverride = null,
            triggerSource = TriggerSource.FLEX_AUTO,
        )
    }

    // ══════════════════════════════════════════════════════════
    // P4-3: 커버 화면 전환 자동 분할 해제
    // ══════════════════════════════════════════════════════════

    /**
     * [CoverDismissPolicy] 가 예약한 디바운스 시각 이후 호출되는 게이트 체인. 순서대로 검사하고
     * 첫 실패에서 사유를 로그로 남긴 뒤 반환한다. [FlexModePolicy] 의 disarm 과 달리
     * [CoverDismissPolicy] 는 게이트 거부 시 별도 해제가 필요 없다 — 에피소드는 살아있는 채로
     * 남고(같은 디바운스 재검증을 다시 통과할 일이 없으므로 재발화 위험 없음), 자세가 실제로
     * 바뀌면 [onFoldPosture] 가 에피소드 자체를 취소한다.
     *
     * 자동 스킵은 토스트를 띄우지 않는다: 이 트리거는 사용자 조작이 아니라 자세(센서) 이벤트로
     * 자동 발화되므로 매 스킵마다 토스트를 띄우면 오히려 사용자를 방해한다(원칙 비대상 — P3-5
     * evaluateFlexAutoTrigger 의 동일한 선례를 따른다). 대신 Log.i 로 사유를 항상 남긴다.
     *
     * [dismissSplit] 을 호출하지 않는다: dismissSplit 의 isSplitActive 2초 폴링은 커버 디스플레이의
     * 접근성 창 목록 상태가 미지수라 신뢰할 수 없다. [PanelActivity] 를 직접 finish 시키는 것이
     * 분할 해소 트리거라는 사실은 실측으로 확정됐다([PanelActivity] companion object KDoc 참고).
     */
    private suspend fun evaluateCoverAutoDismiss() {
        val leverOn = loadProfilesConfig()?.defaults?.coverAutoDismiss ?: true
        if (!leverOn) {
            Log.i(TAG, "cover auto-dismiss skipped: reason=lever-off")
            return
        }

        // flexPolicy.posture 를 "최신 자세" 필드로 재사용한다 — onFoldPosture 가 매 콜백마다
        // flexPolicy.onPosture 를 가장 먼저 호출하므로 이 시점의 값이 곧 최신 관측 자세다.
        if (!coverPolicy.shouldDismissNow(flexPolicy.posture, SystemClock.uptimeMillis())) {
            Log.i(TAG, "cover auto-dismiss skipped: reason=posture-bounced")
            return
        }

        if (machineState != ArrangeState.Idle || sessionInFlight) {
            // 진행 중인 배치 세션에서 패널을 뽑으면 세션이 파괴된다 — 다음 폴드 이벤트로 재시도되게 둔다.
            Log.i(TAG, "cover auto-dismiss skipped: reason=session-active")
            return
        }

        val panel = PanelActivity.instance
        if (panel == null) {
            Log.i(TAG, "cover auto-dismiss skipped: reason=no-panel")
            return
        }

        Log.i(TAG, "cover auto-dismiss fired")
        // [#27/A1, 18차 G1] removeTask 는 step3 소환원인 카드까지 지우는 초과 동작 — finish
        // 만으로 분할이 해소됨이 18차 G1 로 실증됐다.
        runCatching { panel.finish() }
            .onFailure { Log.w(TAG, "cover auto-dismiss: finish 실패", it) }
    }

    // ══════════════════════════════════════════════════════════
    // #30: 전체화면 재생 자동 트리거 (docs/DESIGN_30_FULLSCREEN_AUTO.md)
    // ══════════════════════════════════════════════════════════

    /**
     * [fullscreenLeverSnapshot] 을 다시 읽는다. [onServiceConnected] 워밍과 사용자 토글 변경
     * ([FloatingLauncherService] 롱프레스 메뉴) 양쪽이 이 함수 하나를 쓴다 — 갱신 경로가 둘로
     * 갈라지면 "메뉴에서 켰는데 발화하지 않는다" 는 진단 불가능한 상태가 생긴다(설계서 D14·D22).
     *
     * 실패는 전부 false 방향이다: 자산 파싱 실패 시 킬스위치는 관례대로 `?: true` 지만
     * ([ProfileDefaults.fullscreenAutoArrange] 는 부재=true 인 개발자 킬스위치), DataStore 읽기
     * 실패는 [ProfileStore.isFullscreenAutoEnabled] 가 false 로 폴백하므로 곱이 false 가 된다.
     */
    fun refreshFullscreenAutoSnapshot() {
        scope.launch {
            val lever = loadProfilesConfig()?.defaults?.fullscreenAutoArrange ?: true
            val userOptIn = store.isFullscreenAutoEnabled()
            val enabled = lever && userOptIn
            fullscreenLeverSnapshot = enabled
            Log.i(TAG, "fullscreen auto snapshot: lever=$lever userToggle=$userOptIn enabled=$enabled")
        }
    }

    /**
     * `PanelActivity.onDestroy` 훅 — 우리 분할이 해제됐다는 신호를 자동 트리거 장부에 전달한다.
     *
     * 이 지점이 (a) 메뉴 "분할 해제" (b) 커버 화면 자동 해제 (c) 패널 자가 가드 finish
     * (d) 사용자의 BACK / 디바이더 드래그 — **네 경로를 모두 포착하는 유일한 지점**이다(설계서
     * §2.3). 해제 직후 대상 앱은 대개 전체화면 재생으로 자동 복귀하는데, 그 복귀가 그대로 새 진입
     * 엣지가 되어 방금 사용자가 되돌린 배치를 다시 실행하는 P-1 루프가 된다 — 재래치가 그 엣지를
     * 게이트 10 에서 조용히 죽인다(D1·D2).
     */
    fun onPanelDestroyed() {
        autoLedger.onSplitDismissed()
        Log.i(TAG, "panel destroyed — 자동 트리거 재래치(P-1 루프 차단)")
    }

    /**
     * `TYPE_WINDOWS_CHANGED` 이벤트 진입점. **메인 스레드 동기 콜백**이다.
     *
     * 최전방 선차단(설계서 D12·R6): 아래 5개 조건 중 하나라도 걸리면 `windows` 를 **읽기 전에**
     * 반환한다. 이 이벤트는 창 구성이 바뀔 때마다(최대 10Hz 급) 오므로, 통과분마다 바인더 왕복
     * 1회(`windows`)가 메인 스레드에 얹힌다 — 기능이 꺼져 있거나(기본값) 세션이 도는 동안에는
     * boolean 비교 몇 번으로 끝나야 기존 세션의 시간 예산(ENTRY_STEP_FAILED 여유)을 잠식하지 않는다.
     *
     * 통과 후에는 `windows` 를 **한 번만** 읽어 신호 판정과 §9 전이 로깅에 공유한다 —
     * [FullscreenSignalSampler] 가 `root?.packageName` 을 조회하지 않으므로 창 개수와 무관하게
     * 바인더 왕복은 그 1회뿐이다.
     */
    private fun onWindowsChangedEvent() {
        // ① 기능 자체가 꺼져 있음(킬스위치 ∧ 사용자 토글의 스냅샷). 기본값 false 라 평시 여기서 끝난다.
        if (!fullscreenLeverSnapshot) return
        // ② 배치 세션/분할 해제가 진행 중 — 그 상태에서 관측되는 창 변화는 전부 우리가 만든 것이다.
        if (machineState != ArrangeState.Idle || sessionInFlight || dismissInFlight) return
        // ③ 버블이 꺼져 있으면 사용자가 되돌릴 표면 자체가 없다(D16) — 화면을 바꾸지 않는다.
        if (!FloatingLauncherService.isRunning) return

        val screen = screenRect()
        val windowList = safeWindows()
        val signal = fullscreenSampler.sample(windowList, screen)

        val prev = fullscreenPolicy.lastSignal
        val checkAtMs = fullscreenPolicy.onSignal(signal, SystemClock.uptimeMillis())
        val next = fullscreenPolicy.lastSignal

        // [설계서 §9 Advisor 추가 1] 전이에서만 남긴다 — 매 샘플 로깅은 최대 10Hz 스팸이다.
        // appFull/topBars 는 판정 근거 개수로, W0-1~W0-6 의 창 목록 판정을 별도 프로브 빌드 없이
        // logcat 만으로 재구성하게 해 준다(어느 절이 판정을 갈랐는지 3값만으로는 알 수 없다).
        if (next != prev) {
            val evidence = fullscreenSampler.evidence(windowList, screen)
            Log.i(
                TAG,
                "fullscreen signal: $prev -> $next screen=${screen.width}x${screen.height} " +
                    "appFull=${evidence.appFullCount} topBars=${evidence.topBarCount}",
            )
        }

        // 진입 엣지가 확정된 경우에만 재검증을 예약한다. 동일 상태 중복 보고는 null 이므로
        // 진행 중인 폴링을 방해하지 않는다(onFoldPosture 의 flex/cover 예약과 동일 관례).
        if (checkAtMs == null) return
        fullscreenCheckJob?.cancel()
        fullscreenCheckJob = scope.launch { awaitFullscreenTrigger(checkAtMs) }
    }

    /**
     * 전체화면 자동 발화 대기 루프.
     *
     * ADR-2: 첫 delay 는 [FullscreenPlaybackPolicy.onSignal] 이 발급한 진입 디바운스 시각에
     * "도달"하기 위한 수단일 뿐 성공을 가정하지 않는다 — 도달 후에도 매 틱마다 라이브 창 목록을
     * 다시 샘플링해 정책에 먹이고([onSignal]), [FullscreenPlaybackPolicy.shouldTriggerNow] 로
     * 조건을 재검증한다. [awaitFlexTrigger] 와 동일한 형태다.
     *
     * **미디어 게이트가 게이트 체인이 아니라 이 루프 안에 있는 이유(설계서 §3.1 하단)**:
     * [MediaPlaybackProbe.isMediaPlaying] 은 광고 전환·seek·버퍼 언더런에서 순간적으로 요동치는
     * 상태다. 기하·포그라운드 같은 다른 게이트처럼 첫 실패에서 즉시 disarm 하면 정당한 발화를
     * 잃는다(진입 1회당 재무장 경로는 "이탈 후 재진입" 뿐이다). 그렇다고
     * [evaluateFullscreenAutoTrigger] 안에 두면 `busy`~`startArrange` 원자 구간이 깨진다(D7 — 그
     * 구간에 suspend 호출이 들어가면 다른 트리거와 경합한다). 두 요구를 동시에 만족하는 위치는
     * 이 루프뿐이다: **실패해도 disarm 하지 않고 다음 틱에 다시 묻는다.**
     *
     * 절대 데드라인([FULLSCREEN_TRIGGER_POLL_TIMEOUT_MS])이 반드시 있어야 한다. 힌지 각도와 달리
     * 창 이벤트는 **화면이 정적이면 아예 오지 않으므로**, 이탈이 영영 관측되지 않아
     * [FullscreenPlaybackPolicy.isArmed] 가 참인 채로 루프가 남을 수 있다. 이 데드라인이 폴링의
     * 무조건 종료를 보장한다.
     */
    private suspend fun awaitFullscreenTrigger(checkAtMs: Long) {
        delay((checkAtMs - SystemClock.uptimeMillis()).coerceAtLeast(0))

        val deadlineMs = SystemClock.uptimeMillis() + FULLSCREEN_TRIGGER_POLL_TIMEOUT_MS
        // [설계서 §9 Advisor 추가 2] 미디어 관측 usage 목록은 W0-7(재생/일시정지/볼륨0/앱내 음소거
        // 4상태 확인 + One UI 변형 확인)을 logcat 만으로 수행하기 위한 것이다. 매 틱 로깅은
        // 4Hz 스팸이므로 **판정이 바뀐 틱에서만** 남긴다(전이 로깅 관례를 그대로 따른다).
        var lastMediaPlaying: Boolean? = null

        while (fullscreenPolicy.isArmed && SystemClock.uptimeMillis() < deadlineMs) {
            val now = SystemClock.uptimeMillis()
            fullscreenPolicy.onSignal(fullscreenSampler.sample(safeWindows(), screenRect()), now)

            if (fullscreenPolicy.isTriggerReady(now)) {
                val playing = mediaProbe.isMediaPlaying()
                if (playing != lastMediaPlaying) {
                    lastMediaPlaying = playing
                    Log.i(TAG, "fullscreen media probe: playing=$playing usages=${mediaProbe.activeUsages()}")
                }
                if (!playing) {
                    // disarm 하지 않는다 — 위 KDoc 참고. 다음 틱에 다시 묻는다.
                    delay(FULLSCREEN_POLL_INTERVAL_MS)
                    continue
                }
                if (fullscreenPolicy.shouldTriggerNow(now)) {
                    evaluateFullscreenAutoTrigger()
                    return
                }
            }
            delay(FULLSCREEN_POLL_INTERVAL_MS)
        }

        // 데드라인 도달(이탈이 관측되지 않은 채 시간이 다 됨) — 조용히 남겨두지 않고 명시적으로
        // 이번 진입 구간을 닫는다. 재무장은 이탈 확정 후 재진입만이 경로다.
        if (fullscreenPolicy.isArmed) {
            fullscreenPolicy.disarm()
            Log.i(TAG, "fullscreen auto-arrange skipped: reason=trigger-poll-timeout")
        }
    }

    /**
     * 전체화면 자동 발화 게이트 체인(설계서 §3.2, 11개). 순서대로 검사하고 첫 실패에서
     * [FullscreenPlaybackPolicy.disarm] + 사유 로그 후 반환한다 — 이번 전체화면 진입 구간에서는
     * 재발화하지 않는다(이탈 후 재진입만이 재무장 경로).
     *
     * **토스트 없음**: [evaluateFlexAutoTrigger] KDoc 의 원칙을 그대로 승계한다 — "조용한 실패
     * 금지"는 *사용자가 시작한 행위*의 실패를 사용자가 알게 하자는 것인데, 이 트리거는 창 이벤트로
     * 자동 발화되므로 매 스킵마다 토스트를 띄우면 오히려 방해다. 사유는 항상 Log.i 로 남는다.
     *
     * **⚠ 원자 구간 계약(설계서 D7)**: 게이트 3(bubble-off)부터 [startArrange] 호출까지 **suspend
     * 호출을 넣지 마라.** 그 사이에 중단점이 생기면 게이트 4(busy)가 참으로 통과한 뒤 다른 트리거
     * (FLEX_AUTO·수동 탭)가 먼저 세션을 시작해, 이 경로가 이미 진행 중인 세션 위에 두 번째
     * 세션을 요청하게 된다(R8). 게이트 1·2 만이 suspend 이며, 그 결과([config])는 원자 구간에서
     * 재조회 없이 그대로 쓴다.
     */
    private suspend fun evaluateFullscreenAutoTrigger() {
        // ── 게이트 1: lever-off (개발자 킬스위치, 부재=true) ──
        val config = loadProfilesConfig()
        if (!(config?.defaults?.fullscreenAutoArrange ?: true)) {
            fullscreenPolicy.disarm()
            Log.i(TAG, "fullscreen auto-arrange skipped: reason=lever-off")
            return
        }

        // ── 게이트 2: user-toggle-off (사용자 옵트인, 기본 false — 실질 방어선, R9) ──
        if (!store.isFullscreenAutoEnabled()) {
            fullscreenPolicy.disarm()
            Log.i(TAG, "fullscreen auto-arrange skipped: reason=user-toggle-off")
            return
        }

        // ══ 이하 startArrange 까지 원자 구간 — suspend 호출 금지 (위 KDoc D7 계약) ══

        // ── 게이트 3: bubble-off (되돌리기 표면 부재, D16) ──
        if (!FloatingLauncherService.isRunning) {
            fullscreenPolicy.disarm()
            Log.i(TAG, "fullscreen auto-arrange skipped: reason=bubble-off")
            return
        }

        // ── 게이트 4: busy ──
        if (machineState != ArrangeState.Idle || sessionInFlight || dismissInFlight) {
            fullscreenPolicy.disarm()
            Log.i(TAG, "fullscreen auto-arrange skipped: reason=busy")
            return
        }

        // ── 게이트 5: geometry-mismatch (세로 화면 = Shorts 등을 여기서 차단한다) ──
        val screen = screenRect()
        if (!geometry.matchesScreen(screen)) {
            fullscreenPolicy.disarm()
            Log.i(
                TAG,
                "fullscreen auto-arrange skipped: reason=geometry-mismatch " +
                    "screen=${screen.width}x${screen.height}",
            )
            return
        }

        // ── 게이트 6: split-already-active (기존 분할 재배치는 명시적 비목표) ──
        if (dividerLocator.isSplitActive(safeWindows(), screen)) {
            fullscreenPolicy.disarm()
            Log.i(TAG, "fullscreen auto-arrange skipped: reason=split-already-active")
            return
        }

        // ── 게이트 7: display-off ──
        val displayManager = getSystemService(DISPLAY_SERVICE) as? DisplayManager
        if (displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.state != Display.STATE_ON) {
            fullscreenPolicy.disarm()
            Log.i(TAG, "fullscreen auto-arrange skipped: reason=display-off")
            return
        }

        // ── 게이트 8: foreground-unsuitable ──
        // 조건을 val 로 빼지 않고 if 에 직접 쓴다 — 그래야 이후 targetPkg 가 non-null 로 스마트
        // 캐스트되어 `!!` 없이 진행할 수 있다(evaluateFlexAutoTrigger 는 대상 패키지를 이후에
        // 쓰지 않아 val 로 뺄 수 있었지만, 여기는 게이트 9~11 이 전부 패키지명을 쓴다).
        val targetPkg = activeAppPackage() ?: lastForegroundPkg
        if (targetPkg == null ||
            targetPkg == packageName ||
            targetPkg == homePackage ||
            targetPkg in EXCLUDED_FOREGROUND_PACKAGES
        ) {
            fullscreenPolicy.disarm()
            Log.i(TAG, "fullscreen auto-arrange skipped: reason=foreground-unsuitable pkg=$targetPkg")
            return
        }

        // ── 게이트 9: not-auto-target (프로파일 보유와 자동 대상은 별개 — 시드에서 유튜브 1개, D11·D13) ──
        // config == null(자산 파싱 실패) 은 레버 게이트의 `?: true` 관용구를 따르지 않고 **실패**
        // 처리한다 — 설정을 못 읽는 상태에서 자동으로 화면을 바꾸는 것은 정당화되지 않는다.
        // 로그에 실제 관측 패키지명을 반드시 넣는다: 시드 JSON 의 [미확인] 패키지명을 사용자 로그
        // 한 줄로 진단할 수 있게 하는 유일한 수단이다(D13).
        if (config?.profiles?.firstOrNull { it.packageName == targetPkg }?.autoArrange != true) {
            fullscreenPolicy.disarm()
            Log.i(TAG, "fullscreen auto-arrange skipped: reason=not-auto-target pkg=$targetPkg")
            return
        }

        // ── 게이트 10: latched (D1·D2 의 실질 해법) ──
        if (autoLedger.isLatched(targetPkg)) {
            fullscreenPolicy.disarm()
            Log.i(TAG, "fullscreen auto-arrange skipped: reason=latched pkg=$targetPkg")
            return
        }

        // ── 게이트 11: auto-disabled (연속 실패 서킷브레이커, D3) ──
        if (autoLedger.isDisabled(targetPkg)) {
            fullscreenPolicy.disarm()
            Log.i(
                TAG,
                "fullscreen auto-arrange skipped: reason=auto-disabled pkg=$targetPkg " +
                    "streak=${autoLedger.failStreak(targetPkg)}",
            )
            return
        }

        // 성공·실패와 무관하게 발화 **직전**에 래치한다(D3) — 실패한 자동 세션이 스스로 재무장
        // 엣지를 생산해 같은 실패를 반복하는 것을 막는다.
        autoLedger.onAutoFired(targetPkg)
        Log.i(TAG, "fullscreen auto-arrange trigger: target=$targetPkg")
        startArrange(
            placementOverride = null,
            aspectOverride = null,
            triggerSource = TriggerSource.FULLSCREEN_AUTO,
        )
    }

    /**
     * 자동 세션이 실패했을 때의 복구(설계서 D15). 자동 배치는 사용자가 요청한 적이 없으므로,
     * 실패로 Recents/분할 파트너 피커에 사용자를 유기하면 "가만히 있었는데 화면이 바뀌었고 되돌릴
     * 방법도 없다" 가 된다.
     *
     * [#30 D15 사전 가드] 이 복구가 의미를 갖는 전제는 단 하나 — **우리가 사용자를 대상 앱 밖으로
     * 데려간 경우**다. 그런데 실패 사유 중에는 그 이전 단계에서 끝나는 것들이 있다: 예컨대
     * [FailureReason.SPLIT_CHECK_TIMEOUT] 은 분할 **상태 확인** 단계라 아직 Recents 를 열지도
     * 않았고, 대상 앱은 여전히 전면에 있다. 사전 가드 없이 BACK 을 주입하면 사용자가 보던 영상
     * 앱을 뒤로 밀어낸다(전체화면 재생 중이면 전체화면을 빠져나가거나 앱 자체가 뒤로 간다).
     * 사용자가 요청한 적조차 없는 자동화가 **요청하지 않은 입력을 주입**하는 것이므로 순수한
     * 해악이다. 따라서 주입 **직전**에 `activeAppPackage() == targetPackage` 를 확인하고, 이미
     * 전면이면 아무것도 하지 않고 반환한다(생략 사실은 로그로 드러낸다 — 조용한 실패 금지).
     *
     * `activeAppPackage() == null` 이면 주입을 **진행**한다. null 은 "대상 앱이 전면이다" 가 아니라
     * "전면 앱을 식별하지 못했다" 이며, 그 대표적 형태가 활성 APPLICATION 창이 런처/SystemUI 등
     * 제외 패키지인 경우 — 이는 정확히 "유기됨" 의 모습이다. 전면임을 **확인하지 못했다**는 것은
     * 곧 유기됐을 수 있다는 뜻이므로 복구를 시도하는 쪽이 옳다.
     *
     * ADR-2 준수 계약: `GLOBAL_ACTION_BACK` 을 **1회만** 주입하고, 성공을 가정하지 않고
     * `activeAppPackage() == targetPackage` 를 [AUTO_RECOVERY_POLL_INTERVAL_MS] 간격으로 조건
     * 폴링한다([AUTO_RECOVERY_TIMEOUT_MS] 데드라인). 타임아웃 시 **추가 주입 없이** 로그만 남긴다 —
     * BACK 을 반복 주입하면 사용자가 보던 화면을 계속 뒤로 밀어내는 더 나쁜 결과가 된다.
     * 복구가 무효여도 사용자는 오늘의 수동 실패와 동일한 상태에 있을 뿐이다(회귀 아님, R4).
     */
    private suspend fun recoverAfterAutoFailure(targetPackage: String) {
        // [#30 D15 사전 가드] 대상 앱이 이미 전면 = 우리가 밖으로 데려간 적이 없다 → 주입 금지.
        if (activeAppPackage() == targetPackage) {
            Log.i(TAG, "auto recovery: 대상 앱이 이미 전면 — BACK 주입 생략 (target=$targetPackage)")
            return
        }

        val injected = runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }.getOrDefault(false)
        if (!injected) {
            Log.w(TAG, "auto recovery: GLOBAL_ACTION_BACK 주입 실패 (target=$targetPackage)")
            return
        }

        val recovered = withTimeoutOrNull(AUTO_RECOVERY_TIMEOUT_MS) {
            while (activeAppPackage() != targetPackage) {
                delay(AUTO_RECOVERY_POLL_INTERVAL_MS)
            }
            true
        } ?: false

        if (recovered) {
            Log.i(TAG, "auto recovery: 대상 앱 전면 복귀 확인 (target=$targetPackage)")
        } else {
            Log.i(
                TAG,
                "auto recovery: ${AUTO_RECOVERY_TIMEOUT_MS}ms 내 대상 앱 전면 복귀 미확인 " +
                    "(target=$targetPackage) — 추가 주입 없이 종료",
            )
        }
    }

    // ══════════════════════════════════════════════════════════
    // 세션 시작 (ADR-1 3단 폴백 결정 + Start 디스패치)
    // ══════════════════════════════════════════════════════════

    private suspend fun beginSession(
        target: String,
        placementOverride: Placement?,
        aspectOverride: Float?,
        triggerSource: TriggerSource,
    ) {
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

        // [M1 1단계] 세션 상태는 전부 로컬 val 로 계산한 뒤 이 함수 끝에서 [Session] 하나로 확정한다.
        // 종전에는 여기서부터 서비스 필드에 순차 대입해, 중간 suspend 호출이 예외로 이탈하면 반쯤
        // 채워진 세션이 필드에 남았다 — 이제 그 상태에서는 session 이 null 로 남는다.
        val targetLabel = runCatching {
            val appInfo = packageManager.getApplicationInfo(target, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrNull()

        // [#27, 18차 G3 로 premise 반증] 예전 주석은 "잔존 카드 탭 = 전체화면 재사용으로 분할
        // 파괴" 를 전제했으나, 18차 실기기 프로브(G3)가 죽은 카드(Activities=[])를 탭해도
        // 동일 taskId 로 재사용되며 stage=side/bottom 정상 낙착함을 실증해 그 전제를 반증했다
        // (2026-07-25 원 실측은 launchMode=singleTask 시절 + 버블 숨김 도입 이전이라 이미 다른
        // 수정으로 해소된 상태였다). 이 카드는 오히려 SplitEntry step3 의 소환원이라 반드시
        // 보존해야 한다 — 그럼에도 패널 태스크가 여러 개로 쌓이는 것 자체는 무의미하므로,
        // target 확정 직후 동기적으로 MRU 1개만 남기고 축소한다 (조용한 실패 금지: 보존/제거
        // 개수를 로그로 남긴다).
        pruneExtraPanelTasks()

        // [실기기 확인, 2026-07-25] target 확정 직후 리사이즈 모드를 판정해 진입 레시피를 고른다.
        // null(판정 불가 — 리플렉션 실패 등)은 안전하게 DRAG 로 폴백한다 (조용한 실패 금지: 로그로 드러냄).
        val unresizeableDetection = ResizeModeDetector.isActivitiesUnresizeable(packageManager, target)
        val entryRecipe = if (unresizeableDetection == true) EntryRecipe.MENU else EntryRecipe.DRAG
        Log.i(
            TAG,
            "resize-mode detection: target=$target unresizeableDetection=$unresizeableDetection " +
                "recipe=$entryRecipe",
        )

        // 1) 프로파일 로드 (성공만 캐싱, 실패 시 매번 재시도 + null config 로 진행)
        val config = loadProfilesConfig()

        // 2) 종횡비 결정 (ADR-1 3단 폴백 + DESIGN_12 §4 override tier 0 + §6 캐시 tier ②.5)
        val profile = config?.profiles?.firstOrNull { it.packageName == target }
        val presetAspect = aspectOverride ?: config?.defaults?.aspect ?: DEFAULT_ASPECT
        // [DESIGN_12 §6] 아래 else 분기의 캐시 조회가 이 값을 바로 써야 하므로 requireAgreement
        // 스냅샷과 나란히 여기서 먼저 확정한다(대입을 사용 지점보다 늦추면 조회 자체가 불가능하다).
        val cacheAspectEnabled = config?.defaults?.cacheMeasuredAspect ?: true
        val measurement: AspectMeasurement?
        val sessionCachedAspect: Float?
        val resolved: ResolvedAspect
        if (aspectOverride != null) {
            // [DESIGN_12 §4, 인접 결함 수정] aspectOverride(메뉴 프리셋 선택 / adb --ef aspect,
            // 문서화된 "강제" 의미론)가 예전에는 tier ③ presetAspect 로만 주입돼 tier ② 측정에
            // 밀렸다 — 사용자가 21:9 를 명시 선택해도 측정 1.778 이 조용히 이겼다(실증 확인).
            // override 존재 시 tier 0 로 직접 채택하고 pre/confirm 샷을 전부 생략한다(지연 0,
            // 레이트리밋 예산 절약, "강제" 의미론 정상화). 캐시 조회도 동일 논리로 생략한다 —
            // override 는 이미 확정값이라 tier ②.5 비교 자체가 무의미하다.
            measurement = null
            // 종전에도 이 분기는 sessionCachedAspect 에 대입하지 않아 cleanupSession 이 되돌린
            // null 이 그대로 유지됐다 — 로컬 val 로 옮기면서 그 값을 명시적으로 적는다.
            sessionCachedAspect = null
            resolved = ResolvedAspect(aspectOverride, AspectSource.PRESET, null)
        } else {
            // ADR-1 티어 ②: 분할 진입 전 스냅샷 실측 (best effort — 실패해도 진행)
            measurement = preMeasureAspect(SystemClock.uptimeMillis(), target)
            val cachedAspect = if (cacheAspectEnabled) store.measuredAspect(target) else null
            sessionCachedAspect = cachedAspect
            resolved = AspectResolver.resolve(profile, measurement, presetAspect, cachedAspect)
        }
        // [DESIGN_12 §3.2] confirm 합치 게이트(handleDragDividerTo 첫 호출)가 쓸 세션 상태.
        val requireAgreement = config?.defaults?.requireMeasurementAgreement ?: true

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
            // [P3-5] 플렉스(노트북 자세, 힌지 수평 반접힘) 중에는 명시 override 를 제외한 모든
            // placement 결정보다 TOP 이 우선한다 — 하단 페인이 책상 면에 눕는 물리적 이유다.
            // 순간 자세(flexPolicy.posture)를 그대로 쓴다: 접는 도중 수동 탭은 물리적으로
            // 비현실적이고, 자동 트리거 자체는 이미 FlexModePolicy 의 안정화(디바운스)를 거쳤으므로
            // 여기서 다시 안정화를 요구할 필요가 없다. 레버(flexAutoTopPlacement=false)로 이
            // 티어 전체를 끌 수 있다.
            (config?.defaults?.flexAutoTopPlacement ?: true) &&
                flexPolicy.posture == FoldPosture.HALF_OPENED_HORIZONTAL -> {
                placement = Placement.TOP
                placementSource = "FLEX"
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
        val computedPlan = SplitPlanner.plan(geometry, resolved.aspect, placement)

        Log.i(
            TAG,
            "arrange decision: target=$target label=$targetLabel aspectSource=${resolved.source} " +
                "aspectOverride=$aspectOverride aspect=${resolved.aspect} placement=$placement " +
                "placementSource=$placementSource dividerCenterY=${computedPlan.dividerCenterY} " +
                "clamp=${computedPlan.clampReason} preMeasure=${measurement?.let { "conf=${it.confidence}" } ?: "none"} " +
                "cachedAspect=${sessionCachedAspect ?: "none"}",
        )

        // [측정 2026-07-25] PROFILE(사용자가 고정한 진실) 종횡비 세션에서 오염된 재측정(컨트롤
        // 오버레이/DRM 잔상을 띠로 오인, residual=224)이 정확한 배치를 과축소했다(1235→1011).
        // PROFILE 소스는 재측정이 오염되기 쉬운데도 그 결과가 신뢰된 배치를 덮어쓰는 구조적 결함 —
        // 보정은 신뢰 가능한 측정 경로(MEASURED/PRESET)에서만 켠다. config?.defaults?.closedLoopCorrection
        // 이 false 면 어떤 소스든 보정하지 않는다(사용자/프로파일이 명시적으로 끈 값 우선).
        // [DESIGN_12 §4] aspectOverride(사용자 명시 "강제")도 동일 논리로 보정에서 제외한다 —
        // verify 재측정이 사용자가 고른 종횡비를 재차 덮어쓰면 "강제" 의미론이 깨진다(잔여값은
        // reportTerminal 이 토스트로 정직하게 보고한다).
        val arrangeConfig = ArrangeConfig(
            residualTolerancePx = config?.defaults?.residualTolerancePx ?: 8,
            // [실기기 확인, 2026-07-25] 진입 단계 수는 레시피에 따라 달라진다 — DRAG(리사이저블
            // 앱 기본) 3단계, MENU(UNRESIZEABLE 전용, 회전 우회) 5단계.
            entryStepCount = entryRecipe.stepCount,
            // [측정 2026-07-25] 위치 교정(스왑 3s + 회전×2 폴백)이 Dragging 상태 안에서 실행돼
            // 기본 3000ms 로는 DRAG_TIMEOUT (실측). 교정 경로 포함 예산으로 확대.
            // ArrangeConfig 기본값 자체는 불변 — 이 세션 오버라이드만 확대한다.
            dragTimeoutMs = SESSION_DRAG_TIMEOUT_MS,
            closedLoopCorrection = (resolved.source != AspectSource.PROFILE) &&
                aspectOverride == null &&
                (config?.defaults?.closedLoopCorrection ?: true),
        )

        // [M1 1단계] 여기서 처음으로 세션이 "존재"하게 된다. dispatch(Start) 보다 반드시 먼저여야
        // 한다 — Start 리듀스가 만들어내는 첫 효과(QuerySplitState 등)의 핸들러가 session 을 읽는다.
        session = Session(
            targetPackage = target,
            targetLabel = targetLabel,
            desiredPlacement = placement,
            placementSource = placementSource,
            // [#30 D4] 세션 기원은 호출자가 확정해 넘긴다 — placementSource 체인과 달리 여기서
            // 재추론할 여지가 없어야 한다(추론하는 순간 그것이 D4 의 프록시 재사용 결함이다).
            triggerSource = triggerSource,
            presetAspect = presetAspect,
            requireAgreement = requireAgreement,
            cacheAspectEnabled = cacheAspectEnabled,
            cachedAspect = sessionCachedAspect,
            preMeasurement = measurement,
            entryRecipe = entryRecipe,
            config = arrangeConfig,
        ).also {
            it.plan = computedPlan
            it.resolvedAspect = resolved
        }

        dispatch(ArrangeEvent.Start(SystemClock.uptimeMillis(), computedPlan.dividerCenterY))
        startTickLoop()
    }

    /**
     * [#27/A2, 18차 G1·G3] 구 `purgeStalePanelTasks` 의 개명·범위 축소. 옛 KDoc 전제("잔존 카드를
     * 탭하면 전체화면 재사용으로 분할이 파괴된다")는 18차 G3 실기기 프로브로 **반증**됐다 — 죽은
     * (Activities=[]) 패널 카드를 분할 피커에서 탭해도 동일 taskId 로 재사용되며 stage=side/bottom
     * 정상 낙착한다(전체화면 강탈 0). 그 원 실측(2026-07-25)은 launchMode=singleTask 시절 +
     * 버블 숨김(CLAUDE.md 함정 #22) 도입 이전이라 이미 다른 수정으로 해소된 상태였다.
     *
     * 그 결과 이 함수의 목적은 "잔존 카드 청소" 가 아니라 **다중 패널 태스크 누적 억제뿐**이다.
     * MRU(가장 최근 활성) 패널 태스크 1개는 SplitEntry step3(분할 파트너 피커 탭)의 소환원이므로
     * 반드시 보존한다 — 판정은 순수 도메인 [PanelTaskPolicy.pruneTargets] 에 위임한다(CLAUDE.md
     * 철칙: domain/ 에 android.* import 금지, 대응 단위 테스트는 domain 쪽에서 별도 작성됨).
     *
     * `lastActiveMs` 는 공개 API 로 조회할 수단이 없어 전부 0으로 넘긴다 — `ActivityManager
     * .appTasks` 가 MRU-first 순서로 태스크를 넘긴다는 계약에 타이브레이크(= 입력 순서상 더 앞)를
     * 위임한다([PanelTaskPolicy.pruneTargets] KDoc 참고).
     */
    private fun pruneExtraPanelTasks() {
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val tasks = runCatching { am.appTasks }.getOrNull() ?: return
        val panelClassName = PanelActivity::class.java.name
        val snapshots = panelTaskSnapshots(tasks)
        val targets = PanelTaskPolicy.pruneTargets(snapshots, panelClassName).toSet()
        if (targets.isEmpty()) return
        var removed = 0
        tasks.forEach { task ->
            val id = runCatching { task.taskInfo.taskId }.getOrNull() ?: return@forEach
            if (id in targets) runCatching { task.finishAndRemoveTask() }.onSuccess { removed++ }
        }
        if (removed > 0) Log.i(TAG, "pruneExtraPanelTasks: 보존 1 / 제거 $removed")
    }

    /**
     * `ActivityManager.appTasks` 를 순수 도메인 스냅샷([PanelTaskSnapshot])으로 변환한다.
     * [#28] [pruneExtraPanelTasks] 와 [hasPanelTask] 가 공유하는 매핑 헬퍼다.
     *
     * [rawTasks] 를 주면 그 목록을 그대로 매핑한다([pruneExtraPanelTasks] 가 제거 실행에도 같은
     * `AppTask` 목록이 필요해 이미 조회해 둔 것을 재사용 — 중복 `appTasks` 바인더 호출 방지).
     * 생략하면 이 함수가 직접 `am.appTasks` 를 조회한다(원시 `AppTask` 목록이 필요 없는
     * [hasPanelTask] 등 호출부용). 조회 실패는 빈 목록.
     */
    private fun panelTaskSnapshots(
        rawTasks: List<ActivityManager.AppTask>? = null,
    ): List<PanelTaskSnapshot> {
        val tasks = rawTasks ?: run {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            runCatching { am.appTasks }.getOrNull() ?: return emptyList()
        }
        return tasks.map { task ->
            val info = runCatching { task.taskInfo }.getOrNull()
            PanelTaskSnapshot(
                taskId = info?.taskId ?: -1,
                componentClassName = runCatching { info?.baseIntent?.component?.className }.getOrNull(),
                // 공개 API 로 lastActiveTime 조회 불가 — appTasks 목록 순서(MRU-first)에
                // 타이브레이크를 위임한다.
                lastActiveMs = 0L,
            )
        }
    }

    /**
     * [#28] 우리 패널 태스크(= SplitEntry step3 의 소환원)가 최소 1개 존재하는지. 이 판정은
     * [performDismissSplit] 의 인텐트 폴백 진입 여부를 가른다 — 패널 태스크가 없다 = 해제할
     * 분할이 없다는 뜻이므로, 없는 것을 해제하려고 `FLAG_ACTIVITY_NEW_TASK` 인텐트로 새 태스크를
     * 만들면 base intent 오염(#28)이 발생한다.
     */
    private fun hasPanelTask(): Boolean =
        PanelTaskPolicy.hasPanelTask(panelTaskSnapshots(), PanelActivity::class.java.name)

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
                val measurement = LetterboxDetector.resolveAspect(scan)
                logMeasurement("pre/rows", measurement, LetterboxDetector.residualBars(scan), ROW_STRIDE)
                measurement
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

    /**
     * [F6] 리듀서 호출 + 효과 실행 + 터미널 처리를 담당하는 단일 진입점.
     *
     * 재진입 방지: [dispatching] 이 true 인 동안 들어오는 호출(= executeEffect 가 중단점 없이
     * 동기적으로 다시 부르는 경우)은 리듀스하지 않고 [pendingEvents] 에 적재만 한다. 항상
     * 최상위(가장 바깥) 호출 하나만 아래 while 루프로 실제 reduce 를 수행한다.
     *
     * 동작 등가 근거(구 재귀 재진입 버전 대비):
     * - 리듀서에 이벤트를 먹이는 순서가 동일하다 — 재진입으로 발생한 이벤트도 발생한 순서
     *   그대로 큐에 쌓여 차례로 처리되므로 최종 [machineState] 는 동일하다.
     * - 이벤트 하나당 터미널 검사([ArrangeState.Done]/[ArrangeState.Failed] 여부)가 **정확히
     *   1회씩**, 그 이벤트가 실제로 만들어낸 machineState 기준으로 수행된다 — 구 버전처럼
     *   안쪽 dispatch 가 먼저 끝나 바깥이 자기 것이 아닌 상태를 검사하는 일이 없다.
     * - 디스패처 홉을 추가하지 않는다(여전히 동일 스레드 동기 실행) — 타이밍 영향 0.
     */
    private fun dispatch(event: ArrangeEvent) {
        if (dispatching) {
            pendingEvents.addLast(event)
            return
        }
        dispatching = true
        try {
            var e: ArrangeEvent? = event
            while (e != null) {
                // [M1 1단계] 매 반복마다 다시 읽는다 — 루프 안에서 cleanupSession() 이 돌 수 있다.
                // session==null(=Idle) 일 때의 기본값 폴백이 관측 불가능한 근거는 [Session.config] 참고.
                val transition = ArrangeStateMachine.reduce(machineState, e, session?.config ?: ArrangeConfig())
                if (transition.state != machineState) {
                    Log.i(TAG, "transition: $machineState -> ${transition.state} (event=$e)")
                }
                machineState = transition.state
                transition.effects.forEach { executeEffect(it) }

                val terminal = machineState
                if (terminal is ArrangeState.Done || terminal is ArrangeState.Failed) {
                    reportTerminal(terminal)
                    cleanupSession()
                }
                e = pendingEvents.removeFirstOrNull()
            }
        } finally {
            dispatching = false
            pendingEvents.clear()   // 예외 이탈 시 잔여 이벤트는 폐기(상태 불명)
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
        session = null
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
            val target = session?.targetPackage
            if (target == null) {
                Log.w(TAG, "PerformEntryStep: targetPackage null — 실패로 처리")
                dispatch(ArrangeEvent.EntryStepResult(SystemClock.uptimeMillis(), false))
                return@launch
            }

            val screen = screenRect()
            val ctx = EntryContext(
                targetPackage = target,
                targetLabel = session?.targetLabel,
                selfPackage = packageName,
                screen = Rect(screen.left, screen.top, screen.right, screen.bottom),
                recipe = session?.entryRecipe ?: EntryRecipe.DRAG,
            )

            // 머신 Tick 타임아웃(entryStepTimeoutMs)보다 먼저 결과 이벤트가 도착하도록 폴링 예산에
            // 여유를 둔다 — 실기기에서 3001ms > 3000ms 경합 관측 (2026-07-25).
            val stepBudgetMs = ((session?.config ?: ArrangeConfig()).entryStepTimeoutMs - ENTRY_STEP_RESULT_MARGIN_MS)
                .coerceAtLeast(500L)
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
            if (handle != null) session?.lastHandle = handle
            dispatch(ArrangeEvent.DividerResult(SystemClock.uptimeMillis(), handle?.centerY))
        }
    }

    private fun handleDragDividerTo(targetY: Int) {
        scope.launch {
            // [M1 1단계] session 은 매 사용 지점에서 인라인으로 다시 읽는다 — 아래에 suspend 호출이
            // 여러 개 있고, 그 사이 cleanupSession() 이 세션을 끝냈을 수 있다([Session] 읽기 규약).
            val target = session?.targetPackage
            val screen = screenRect()
            var realTargetY = targetY

            // [DESIGN_12 §3.2] confirm 합치 게이트 — 세션의 첫 드래그 직전에 정확히 1회만 실행되고,
            // MEASURED 후보 세션에서만 발동한다(PRESET/PROFILE/override 는 이미 확정값이라 스킵).
            // 머신이 넘긴 targetY 는 이미 "자문(advisory) 값"이라는 선례가 있다(아래 스왑 실패
            // 분기가 동일 변수를 재계산해 덮어씀) — confirm 재계획도 같은 realTargetY 를 덮어쓴다.
            if (!(session?.aspectConfirmed ?: false)) {
                session?.aspectConfirmed = true
                if ((session?.requireAgreement ?: true) &&
                    session?.resolvedAspect?.source == AspectSource.MEASURED
                ) {
                    confirmMeasuredAspect(target, screen)?.let { newTargetY -> realTargetY = newTargetY }
                }
            }

            val actualPosition = actualVideoPanePosition(target, screen)
                ?: session?.desiredPlacement ?: Placement.TOP

            if (actualPosition != (session?.desiredPlacement ?: Placement.TOP)) {
                val handleForSwap = session?.lastHandle
                val swapped = if (handleForSwap != null) {
                    paneSwapper.swap(handleForSwap, SWAP_TIMEOUT_MS, ::awaitDividerSettled) {
                        actualVideoPanePosition(target, screenRect()) ==
                            (session?.desiredPlacement ?: Placement.TOP)
                    }
                } else {
                    Log.w(TAG, "DragDividerTo: swap 시도 불가 — lastHandle null")
                    false
                }

                if (swapped) {
                    session?.effectivePlacement = session?.desiredPlacement ?: Placement.TOP
                    dividerLocator.locate(safeWindows(), screen)?.let { session?.lastHandle = it }
                    Log.i(
                        TAG,
                        "DragDividerTo: pane swap 성공 — 희망 위치(${session?.desiredPlacement ?: Placement.TOP}) 유지",
                    )
                } else {
                    // [측정 2026-07-25] "창 전환" 클릭 result=true 인데 실제 스왑 미발생이 2회
                    // 연속 실측됐다 — 원인 미상. 최종 폴백으로 디바이더 핸들 회전을 2회 반복한다
                    // (T/B → 회전1 → L/R → 회전2 → B/T, SplitEntry MENU step5 와 동일 원리 —
                    // 회전 자체는 4회 전부 동작 실증됨).
                    Log.w(TAG, "DragDividerTo: pane swap 실패 — 회전×2 폴백 시도")
                    val rotated = rotateTwiceFallback(target, screen)

                    if (rotated) {
                        session?.effectivePlacement = session?.desiredPlacement ?: Placement.TOP
                        dividerLocator.locate(safeWindows(), screen)?.let { session?.lastHandle = it }
                        Log.i(
                            TAG,
                            "DragDividerTo: 회전×2 폴백 성공 — " +
                                "희망 위치(${session?.desiredPlacement ?: Placement.TOP}) 유지",
                        )
                    } else {
                        val recomputedActual = actualVideoPanePosition(target, screenRect()) ?: actualPosition
                        session?.effectivePlacement = recomputedActual
                        val aspect = session?.resolvedAspect?.aspect
                        if (aspect != null) {
                            val recomputedPlan = SplitPlanner.plan(geometry, aspect, recomputedActual)
                            session?.plan = recomputedPlan
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
            val handle = dividerLocator.locate(safeWindows(), screen) ?: session?.lastHandle
            if (handle == null) {
                Log.w(TAG, "DragDividerTo: lastHandle null — 드래그 불가")
                dispatch(ArrangeEvent.DragResult(SystemClock.uptimeMillis(), false))
                return@launch
            }
            session?.lastHandle = handle

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
     * [DESIGN_12 §3.2] MEASURED 후보 세션의 첫 드래그 직전 confirm 측정 + 합치 판정.
     * 합치 → MEASURED 확정 / 불합치·확인불가 → PRESET 폴백. 어느 쪽이든 [Session.resolvedAspect] 를
     * 갱신하고 재계획한 새 dividerCenterY 를 반환한다(모든 경로가 [finishConfirm] 으로 모이므로
     * "측정 실패"가 "재계획 실패"로 번지지 않는다).
     *
     * 오염원(컨트롤/인트로)은 일시적이므로 시각(t0+진입 2~4s)·축(행→열)·컨텍스트(전체화면→페인
     * 크롭)가 다른 두 측정의 합치만 신뢰한다 — 단일 프레임 conf 는 오염을 못 거른다(실측
     * 0.60~0.97, DESIGN_12 §1).
     */
    private suspend fun confirmMeasuredAspect(targetPackage: String?, screen: IntRect): Int? {
        val now = SystemClock.uptimeMillis()
        val waitUntil = lastScreenshotAtMs + SCREENSHOT_MIN_INTERVAL_MS
        if (now < waitUntil) {
            // ADR-2 예외 허용 지점(함정 #3): 고정 지연이 아니라 lastScreenshotAtMs 기반
            // 레이트리밋 계산 결과를 따르는 조건부 대기다.
            delay(waitUntil - now)
        }

        val bitmap = captureScreen()
        if (bitmap == null) {
            Log.w(TAG, "confirm: 스크린샷 실패 — Unavailable 로 진행")
            return finishConfirm(ConfirmOutcome.Unavailable, paneAspect = 0f)
        }
        try {
            val paneRect = actualVideoPaneRect(targetPackage, screen)
            if (paneRect == null) {
                Log.w(TAG, "confirm: 대상 페인 rect 미발견 — Unavailable 로 진행")
                return finishConfirm(ConfirmOutcome.Unavailable, paneAspect = 0f)
            }

            val crop = cropToRect(bitmap, paneRect)
            if (crop == null) {
                Log.w(TAG, "confirm: crop 실패 — Unavailable 로 진행")
                return finishConfirm(ConfirmOutcome.Unavailable, paneAspect = 0f)
            }
            try {
                // [DESIGN_12 §3.4] 분할 페인은 보통 영상보다 넓어(AR≈2.2) 좌우 필러박스가 생긴다 —
                // 행축과 열축을 모두 스캔해 MeasurementConsensus.classifyConfirm 이 exactly-one
                // 규칙으로 판정하게 한다.
                val rowScan = crop.toLetterboxScan(rowStride = ROW_STRIDE)
                val colScan = crop.toPillarboxScan(colStride = COL_STRIDE)
                val rowMeasurement = LetterboxDetector.resolveAspect(rowScan)
                val colMeasurement = LetterboxDetector.resolveAspectPillarbox(colScan)
                val rowResidual = LetterboxDetector.residualBars(rowScan)
                val colResidual = LetterboxDetector.residualBars(colScan)
                logMeasurement("confirm/rows", rowMeasurement, rowResidual, ROW_STRIDE)
                logMeasurement("confirm/cols", colMeasurement, colResidual, COL_STRIDE)

                val outcome =
                    MeasurementConsensus.classifyConfirm(rowMeasurement, rowResidual, colMeasurement, colResidual)
                val paneAspect = paneRect.width.toFloat() / paneRect.height.toFloat()
                return finishConfirm(outcome, paneAspect)
            } finally {
                crop.recycle()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * [DESIGN_12 §3.2 4~5단계] pre×confirm 합치 판정 → [Session.resolvedAspect] 갱신 → 재계획. confirm 의
     * 모든 경로(성공/불합치/측정 실패)가 여기로 모인다 — 불합치도 PRESET 폴백으로 "결정"이라는
     * 점에서 성공 경로와 동일하게 취급한다(조용한 실패 금지: verdict 를 항상 로그로 남긴다).
     */
    private fun finishConfirm(outcome: ConfirmOutcome, paneAspect: Float): Int {
        val result = MeasurementConsensus.agree(session?.preMeasurement, outcome, paneAspect)
        // [DESIGN_12 §6] 불합치 폴백은 정적 PRESET 보다 이 앱의 과거 합치∧verified 이력(캐시)이
        // 더 나은 사전값이다 — 오측 시 치유 경로(closedLoopCorrection)가 PRESET 소스와 동일하게
        // 적용되므로 안전망은 그대로 유지된다(§3.5 의 "신규 증거 없으면 차선 증거라도 정적 기본값
        // 보다 우선" 비대칭 논리를 그대로 확장한 것).
        val newResolved = result.adopted?.let { ResolvedAspect(it.value, AspectSource.MEASURED, it) }
            ?: session?.cachedAspect?.let { ResolvedAspect(it, AspectSource.CACHED, null) }
            ?: ResolvedAspect(session?.presetAspect ?: DEFAULT_ASPECT, AspectSource.PRESET, null)
        session?.resolvedAspect = newResolved
        // 캐시 admission(§6) 은 합치로 채택된 값만 대상이다 — result.agreed(=adopted != null) 가
        // 아니면 기록하지 않는다. 이 세션이 CACHED/PRESET 으로 낙착하는 것과는 별개로, "이번 세션이
        // 새 합치 증거를 냈는가"만이 저장 여부를 결정한다.
        if (result.agreed) {
            session?.consensusAdoptedAspect = result.adopted?.value
        }
        Log.i(
            TAG,
            "consensus: verdict=${result.verdict} outcome=$outcome " +
                "pre=${session?.preMeasurement?.let { "raw=${it.raw} snapped=${it.snapped}" } ?: "none"} " +
                "→ aspect=${newResolved.aspect} source=${newResolved.source}",
        )

        val newPlan = SplitPlanner.plan(geometry, newResolved.aspect, session?.desiredPlacement ?: Placement.TOP)
        session?.plan = newPlan
        return newPlan.dividerCenterY
    }

    /**
     * [DESIGN_12 §5] 측정 로깅 표준화 — 사고 당시 밴드 기하(topBarPx/bottomBarPx) 미기록이 사후
     * 판별력 검증(설계 §2 대칭성 휴리스틱 등)을 막았다. pre/confirm 모든 측정 지점이 이 함수를
     * 거쳐 축·raw·snap·conf·밴드 px 를 남긴다. band px 는 스캔 entry 단위이므로 실제 화면 px 로
     * 환산하려면 stride 를 곱한다(ROW_STRIDE/COL_STRIDE — ScreenshotSampler 함정 참고).
     */
    private fun logMeasurement(tag: String, m: AspectMeasurement?, residual: ResidualBars?, stride: Int) {
        if (m != null) {
            Log.i(
                TAG,
                "measure[$tag]: method=${m.method} raw=${m.raw} snapped=${m.snapped} conf=${m.confidence} " +
                    "band=${m.band.topBarPx * stride}/${m.band.bottomBarPx * stride}px",
            )
        } else if (residual != null) {
            Log.i(TAG, "measure[$tag]: 밴드 없음 residual=${residual.totalPx * stride}px")
        } else {
            Log.i(TAG, "measure[$tag]: 밴드 없음 판정불가")
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
     * 2회차 성공 조건: 두 페인이 [PaneGeometry.isTopBottomSplit] ∧ 실제 위치가 [Session.desiredPlacement].
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
                actualVideoPanePosition(target, screen) == (session?.desiredPlacement ?: Placement.TOP)
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
                val paneRect = actualVideoPaneRect(session?.targetPackage, screen)
                    ?: session?.plan?.videoRect?.let { PaneGeometry.visibleRect(it, screen) }

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

                    // [DESIGN_12 §3.4/§7 v1 범위] 필러박스(좌우 잔여, 과소 이동) 맹점의 가시화 —
                    // v1 은 로그 보고만 한다. verified 플래그/MeasureResult 이벤트 의미론 변경은
                    // v1.5 로 이월(설계 §7 OUT) — 머신에 전달하는 인자는 절대 바꾸지 않는다.
                    val colScan = crop.toPillarboxScan(colStride = COL_STRIDE)
                    val residualColsPx = LetterboxDetector.residualBars(colScan)?.totalPx?.times(COL_STRIDE)
                    Log.i(TAG, "verify: residualRows=${residualPx}px residualCols=${residualColsPx}px")

                    val correctedTargetY = measurement?.let {
                        val placement = session?.effectivePlacement ?: Placement.TOP
                        SplitPlanner.plan(geometry, it.value, placement).dividerCenterY
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
        // [M1 1단계] [Session] 읽기 규약의 유일한 예외 — 이 함수 본문에는 중단점이 없고(아래
        // `scope.launch { }` 들은 pkg·placementToPersist·adoptedAspect 를 이미 로컬로 캡처해
        // 넘긴다), [dispatch] 가 [cleanupSession] 보다 먼저 호출하므로 세션이 아직 살아 있다.
        val s = session
        val arrangeConfig = s?.config ?: ArrangeConfig()
        val desiredPlacement = s?.desiredPlacement ?: Placement.TOP
        val effectivePlacement = s?.effectivePlacement ?: Placement.TOP
        // [#30 D19] 세션 밖 폴백은 MANUAL — 종전 동작(항상 토스트)과 정확히 같다.
        val triggerSource = s?.triggerSource ?: TriggerSource.MANUAL

        when (state) {
            is ArrangeState.Done -> {
                // [F9 v1 대응, 계획서 §F9] verified=true 는 "잔여가 허용치 이내" 를 보장하지
                // 않는다 — 보정 비활성(closedLoopCorrection=false) 또는 이미 1회 보정한 뒤에도
                // 잔여값을 그대로 보고하는 경로가 있다(ArrangeStateMachine.reduceVerifying).
                // 필드 개명(verified 의미 재정의)은 리듀서·기존 테스트 전반에 영향을 주므로
                // v1.5 로 미루고, v1 은 사용자에게 보이는 메시지에서만 허용치 초과 여부를 구분한다.
                val residual = state.finalResidualPx ?: 0
                val message = if (state.verified) {
                    buildString {
                        append("배치 완료 · 잔여 ${residual}px")
                        if (residual > arrangeConfig.residualTolerancePx) append(" (허용치 초과)")
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
                        "adjusted=${state.adjusted} desired=$desiredPlacement effective=$effectivePlacement " +
                        "tolerance=${arrangeConfig.residualTolerancePx} trigger=$triggerSource",
                )
                // [#30 D19] 자동 세션은 사용자가 요청한 적이 없는 동작이라 진단 문구를 LENGTH_LONG
                // 토스트로 띄우면 방해다 — 같은 내용을 Log.i 로만 남긴다.
                if (!triggerSource.isAuto) {
                    toast(message)
                } else {
                    Log.i(TAG, "auto arrange result (토스트 억제): $message")
                }

                // 앱별 "마지막 성공 placement" 저장(P3-3). 사용자가 의도한 위치(desiredPlacement)로
                // 실제로 낙착된 세션만 저장한다 — 스왑/회전 폴백이 모두 실패해 다른 위치로 낙착된
                // 세션(effectivePlacement != desiredPlacement)은 저장하지 않는다. 사용자가 고르지
                // 않은 위치가 다음 탭의 기본값을 조용히 오염시키면 안 된다(PROGRESS 열린 질문
                // #19/#20, 위치 전환 실패 실측 근거). verified(측정 검증) 여부는 배치 자체의 성공과
                // 무관하므로 저장 조건에 넣지 않는다. cleanupSession() 이 session 을 곧 null 로
                // 되돌리므로 지역 변수로 캡처한 뒤 기존 서비스 스코프에서 저장한다.
                // [P3-5] FLEX(자세 자동 결정) placement 도 동일 논리로 제외한다 — 사용자가 고른
                // 값이 아닌 자동화 결과가 last-success 를 조용히 오염시키면 안 된다.
                val pkg = s?.targetPackage
                val placementToPersist = effectivePlacement
                // [#30 D3] 자동 세션의 터미널 보고 — 성공은 연속 실패 스트릭을 0으로 되돌린다.
                if (pkg != null && triggerSource.isAuto) autoLedger.onAutoResult(pkg, success = true)
                // [#30 D4] 자동 세션(FLEX_AUTO/FULLSCREEN_AUTO)은 last-success 저장에서 제외한다 —
                // 종전에는 placementSource=="FLEX" 로 걸렀는데 그건 배치 근거일 뿐 세션 기원이
                // 아니어서, 자동 세션이 다른 placement 티어로 낙착하면 그대로 저장돼 사용자가
                // 고르지 않은 값이 다음 수동 탭의 기본값을 오염시켰다.
                if (pkg != null &&
                    effectivePlacement == desiredPlacement &&
                    !triggerSource.isAuto &&
                    (s?.placementSource ?: "FALLBACK") != "FLEX"
                ) {
                    // fire-and-forget — ProfileStore.saveLastSuccessfulPlacement 이 내부에서
                    // IOException 을 잡아 Log.w 로 드러내므로(safeWrite) 여기서 scope 예외 처리가
                    // 필요 없다. 이 launch 는 startArrange 의 바깥 try/catch 범위 밖(별도 코루틴)이라
                    // 방어가 없으면 저장소 오류 시 프로세스가 죽는다 — 반드시 ProfileStore 쪽 보호에 의존한다.
                    scope.launch {
                        store.saveLastSuccessfulPlacement(pkg, placementToPersist)
                    }
                }

                // 측정 종횡비 캐싱(DESIGN_12 §6). admission = 이번 세션 합치 통과 ∧ verified=true.
                // placement 저장과 달리 effectivePlacement 조건은 무관하다(종횡비는 콘텐츠 속성, 위치와 직교).
                // CACHED/PRESET/PROFILE 세션은 consensusAdoptedAspect 가 null 이라 자연 제외되고,
                // requireAgreement=false 롤백 세션은 confirm 자체가 안 돌아 역시 null — 단일 프레임 값은
                // 어떤 경로로도 캐시에 들어올 수 없다.
                val adoptedAspect = s?.consensusAdoptedAspect
                if (pkg != null && state.verified && adoptedAspect != null && (s?.cacheAspectEnabled ?: true)) {
                    Log.i(TAG, "aspect cache save: pkg=$pkg aspect=$adoptedAspect (합치∧verified)")
                    scope.launch { store.saveMeasuredAspect(pkg, adoptedAspect) }
                }
            }

            is ArrangeState.Failed -> {
                Log.i(TAG, "arrange failed: reason=${state.reason} trigger=$triggerSource")
                if (!triggerSource.isAuto) {
                    toast("배치 실패: ${failureReasonKo(state.reason)}")
                    return
                }

                // [#30 D3/D15 보정] CANCELLED 는 자동화의 실패가 아니라 "누군가 의도적으로 멈췄다"는
                // 뜻이므로 아래 실패 처리(스트릭 +1, BACK 복구)에서 제외한다. 프로덕션에서 이 사유를
                // 만드는 유일한 경로는 같은 파일 933행 근처의 posture-exit 취소 — FLEX_AUTO 세션이
                // 도는 중 사용자가 자세를 바꾸면 "의도 번복"으로 보고 cancelArrange() 하는 기존
                // 로직이다(ArrangeTriggerReceiver 의 취소는 debug 전용 매니페스트라 릴리스에 없다).
                // 실패로 취급하면 두 가지 신규 부작용이 FLEX 경로에 생긴다:
                //   ① recoverAfterAutoFailure 의 GLOBAL_ACTION_BACK 주입 — 사용자가 방금 스스로
                //      멈춘 화면을 한 번 더 뒤로 민다. 세션이 Recents 도달 **전에** 취소됐다면 BACK 이
                //      대상 앱 자체를 뒤로 보낸다. 요청하지 않은 입력 주입이다.
                //   ② autoLedger.onAutoResult(success=false) 의 실패 스트릭 +1 — 기기를 두 번
                //      접었다 펴는 것만으로 maxFailStreak(2)에 도달해, 게이트 11 이 이 부팅 세션 동안
                //      해당 패키지의 자동 트리거를 영구 비활성화한다.
                // 실패 취급을 빼도 재발화 루프(P-1) 위험은 없다 — 래치는 이미 발화 **직전**
                // (autoLedger.onAutoFired, 1371행)에 걸렸고 결과와 무관하게 유지되므로, 취소된
                // 세션이 스스로 재무장 엣지를 만들 수 없다.
                if (state.reason == FailureReason.CANCELLED) {
                    // 무고지는 안 된다(화면이 실제로 바뀌었다 되돌아가는 중이다) — "실패"가 아닌
                    // 사실만 짧게 알린다.
                    Toast.makeText(this, "자동 배치를 취소했습니다", Toast.LENGTH_SHORT).show()
                    return
                }

                // [#30 D19] 자동 세션은 사용자가 요청한 적이 없으므로 진단 문구 대신 짧은 사실
                // 통보만 한다 — 그래도 무고지는 안 된다(화면이 실제로 바뀌었다 되돌아가는 중이다).
                Toast.makeText(this, "자동 배치에 실패했습니다", Toast.LENGTH_SHORT).show()

                val pkg = s?.targetPackage ?: return
                // [#30 D3] 연속 실패 서킷브레이커 — maxFailStreak 도달 시 게이트 11 이 이 부팅
                // 세션 동안 이 패키지의 자동 트리거를 막는다.
                autoLedger.onAutoResult(pkg, success = false)
                // [#30 D15] 실패한 자동 세션이 사용자를 Recents/분할 피커에 유기하지 않도록
                // 복구를 시도한다. reportTerminal 본문에는 중단점이 없어야 하므로(Session 읽기 규약
                // 의 유일한 예외 조건) 별도 코루틴으로 내보낸다.
                //
                // [#31] 플래그는 launch **밖에서** 세운다 — 안에서 세우면 코루틴 본문이 실제로
                // 시작되기 전에 도착한 이벤트가 이 구간을 "한가함"으로 오인할 수 있다.
                // 해제는 반드시 finally 로 — 예외/취소로 이탈해도 플래그가 영구히 남으면
                // 이후 모든 래치 해제가 조용히 막힌다.
                autoRecoveryInFlight = true
                scope.launch {
                    try {
                        recoverAfterAutoFailure(pkg)
                    } finally {
                        autoRecoveryInFlight = false
                    }
                }
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
        // [M7, minSdk=30=R] SDK_INT < R 가드는 항상 거짓인 죽은 분기였다(ObsoleteSdkInt) — 제거.
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
                        // [F7] 콜백은 코루틴 취소와 경합할 수 있다 — cont 가 이미 취소됐는데 bmp 를
                        // 그냥 버리면 전면 스크린샷(≈17MB) 1장이 GC 전까지 붙잡힌다. resume 이
                        // 불가능하면 즉시 recycle 한다.
                        if (cont.isActive) cont.resume(bmp) else bmp?.recycle()
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

        /**
         * ScreenshotSampler.toPillarboxScan 에 전달하는 값과 동일해야 band px → 실제 px 환산이
         * 맞는다(DESIGN_12 §3.4 confirm 열축 스캔 + verify residualCols 보고 공용).
         */
        private const val COL_STRIDE = 2

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

        /**
         * P3-5 플렉스 각도 안정성 조건 폴링 간격([awaitFlexTrigger]). 각도가 멎은 뒤
         * [FlexModePolicy.ANGLE_QUIET_MS](600ms) 를 채워야 발화하므로, 그보다 충분히 촘촘하면서
         * (체감 지연 ≤ 이 값) 메인 스레드 부담이 없는 값이다 — 매 틱의 작업은 산술 비교 몇 번뿐이다.
         * 파일 내 다른 조건 폴링(150~200ms 관례)과 같은 부류이며 고정 지연이 아니다(ADR-2).
         */
        private const val FLEX_ANGLE_POLL_INTERVAL_MS = 250L

        /**
         * [#30] [awaitFullscreenTrigger] 조건 폴링 간격. [FLEX_ANGLE_POLL_INTERVAL_MS] 와 동일한
         * 관례다. 틱당 작업 = 바인더 1회(`windows`) + 산술 + (준비된 경우) 미디어 질의 1회.
         * ADR-2 의 조건 폴링이지 타이밍을 맞추기 위한 고정 지연이 아니다.
         */
        private const val FULLSCREEN_POLL_INTERVAL_MS = 250L

        /**
         * [#30] [awaitFullscreenTrigger] 의 **절대 데드라인**. **[미검증]** — 힌지 각도(센서)와
         * 달리 창 이벤트는 화면이 정적이면 아예 오지 않으므로, 전체화면 이탈이 영영 관측되지 않아
         * 무장이 풀리지 않을 수 있다. 이 데드라인이 폴링의 무조건 종료를 보장한다.
         * 진입 디바운스(3000ms)가 지난 시점부터 재는 값이라 여유는 충분하다.
         */
        private const val FULLSCREEN_TRIGGER_POLL_TIMEOUT_MS = 5_000L

        /** [#30] [recoverAfterAutoFailure] 조건 폴링 간격. 파일 내 다른 150ms 폴링 관례와 동일. */
        private const val AUTO_RECOVERY_POLL_INTERVAL_MS = 150L

        /**
         * [#30] [recoverAfterAutoFailure] 데드라인. **[미검증]** — BACK 주입 후 대상 앱이 전면으로
         * 복귀할 때까지의 대기. 실측 E2E 전환 시간(세션 전체 4.2~4.7s 중 1스텝) 대비 여유값이다.
         * 타임아웃돼도 추가 주입은 없다(위 KDoc 계약).
         */
        private const val AUTO_RECOVERY_TIMEOUT_MS = 2_500L

        /** [P4-4] [startArrangeWhenForeground] 조건 폴링 간격. 파일 내 다른 150ms 폴링 관례와 동일. */
        private const val SHORTCUT_FOREGROUND_POLL_INTERVAL_MS = 150L

        /**
         * [P4-4] [startArrangeWhenForeground] 최대 대기. 바로가기가 대상 앱을 막 실행한 시점부터
         * 콜드 스타트(프로세스 생성 포함)까지 흡수할 여유값이다.
         */
        private const val SHORTCUT_FOREGROUND_TIMEOUT_MS = 5_000L

        /**
         * [P4-1] [ShizukuShell.exec] 개별 셸 명령(am start/am stack list/am task resize) 타임아웃.
         * F3+F4+S2+S3 리팩터 이후 이 값은 **원격**([ShellExecUserService.run])의
         * `Process.waitFor(timeoutMs, ...)` 데드라인으로 그대로 전달된다 — 실효 타임아웃은
         * 원격에서만 걸린다(클라이언트 쪽 `withTimeoutOrNull` 은 블로킹 바인더 호출을 취소하지
         * 못하기 때문). 클라이언트 쪽 예산은 이 값에 여유분이 더해져 더 크게 잡히므로 원격이
         * 먼저 포기한다 — 자세한 근거는 [ShizukuShell.exec] KDoc 참고.
         */
        private const val SHELL_EXEC_TIMEOUT_MS = 5_000L

        /** [P4-1] [startPopup] 조건 폴링 간격. 파일 내 다른 150ms 폴링 관례와 동일. */
        private const val POPUP_POLL_INTERVAL_MS = 150L

        /** [P4-1] `am start --windowingMode 5` 실행 후 대상 APPLICATION 창 출현 대기 최대 시간. */
        private const val POPUP_WINDOW_POLL_TIMEOUT_MS = 5_000L

        /** [P4-1] `am task resize` 후 bounds 일치 확인 최대 시간. */
        private const val POPUP_VERIFY_TIMEOUT_MS = 3_000L

        /** [P4-1] 팝업 창 bounds 검증 허용 오차(px). F3(DEVICE_FACTS)에서 resize 는 오차 0으로 실측됐으나, a11y bounds 보고 지연을 감안한 여유값. */
        private const val POPUP_BOUNDS_TOLERANCE_PX = 8

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
