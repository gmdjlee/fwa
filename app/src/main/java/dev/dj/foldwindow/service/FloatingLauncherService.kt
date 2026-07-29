package dev.dj.foldwindow.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import dev.dj.foldwindow.R
import dev.dj.foldwindow.data.ProfileStore
import dev.dj.foldwindow.data.ProfilesParseResult
import dev.dj.foldwindow.data.WindowProfilesParser
import dev.dj.foldwindow.domain.AspectPreset
import dev.dj.foldwindow.domain.Placement
import dev.dj.foldwindow.ui.OnboardingActivity
import dev.dj.foldwindow.ui.PairShortcutActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

/**
 * P3-1: 오버레이 플로팅 버블을 띄우고 원터치로 액추에이터([ArrangerAccessibilityService])를 실행하는
 * 포그라운드 서비스. Phase 2 의 adb 트리거([ArrangeTriggerReceiver])를 사용자용으로 대체한다.
 *
 * 동작:
 * - 탭: 접근성 서비스 인스턴스가 있을 때 `startArrange(null, null)` (프로파일/기본값 적용),
 *   없으면 토스트 안내 후 [OnboardingActivity] 로 유도. 메뉴가 열려 있는 동안은 버블이 풀스크린
 *   스크림 아래 깔려 터치를 아예 받지 못하므로(아래 [showMenu] KDoc 참고), 이 분기는 메뉴가
 *   닫혀 있을 때만 실행될 수 있다.
 * - 드래그: 버블 이동. 손을 떼면 가까운 좌/우 가장자리로 스냅. 드래그가 시작되면 열려 있던
 *   확장 메뉴는 닫는다(옛 위치에 뜬 메뉴가 무의미해지므로).
 * - 롱프레스(드래그 없이 유지): 확장 메뉴([showMenu]) 를 연다 — 위/아래 배치, 분할 해제, 종횡비
 *   프리셋, 설정(온보딩) 진입점을 제공한다(P3-2). 메뉴를 닫으려면 메뉴 바깥(스크림)을 탭한다.
 *
 * ADR-2 참고: 이 서비스가 쓰는 지연(Handler.postDelayed 롱프레스 감지, ValueAnimator 스냅 애니메이션)은
 * 전부 표준 UI 제스처 인식/화면 전환 애니메이션이며 액추에이터 상태 대기가 아니다. 실제 배치 진행은
 * 전적으로 [ArrangerAccessibilityService] 의 상태 머신이 담당하고, 이 서비스는 트리거만 한다.
 *
 * 좌표 하드코딩 금지(CLAUDE.md 함정 #2, #5): 버블 위치 클램프·스냅, 확장 메뉴 위치 클램프는
 * 매번 `windowManager.currentWindowMetrics` 를 재조회해 계산한다 — 화면 크기를 상수로 가정하지 않는다.
 *
 * [실측 2026-07-25, A/B 실험] 버블 오버레이 창이 떠 있는 동안 배치를 실행하면 SplitEntry step3
 * 피커發 PanelActivity 가 분할 페인이 아니라 **전체화면**으로 낙착해 자가 가드가 즉시 종료 →
 * 분할 쌍 미수렴 → ENTRY_STEP_FAILED 로 귀결됨이 재현됐다(버블 ON 2회 실패, 동일 빌드·경로에서
 * 버블 OFF 는 즉시 성공). 버블 창은 접근성 창 목록에 TYPE_SYSTEM 으로 보고돼 기하 판정 오염은
 * 아니며, One UI WM 라우팅이 "런칭 패키지에 가시 오버레이 창 존재" 상황에서 페인 배치를 하지
 * 않는 것으로 추정된다(메커니즘 불명 — 경험 법칙으로 대응). [setBubbleHiddenForArrange] 가
 * [ArrangerAccessibilityService] 의 세션 시작/종료에 맞춰 이 창을 완전히 제거/재부착한다
 * (INVISIBLE 로는 부족할 수 있음 — 원인이 "가시 창의 존재" 자체이므로 창 자체를 없앤다).
 *
 * P3-2: 같은 이유로 확장 메뉴 오버레이 창도 배치/해제 트리거 전 반드시 제거해야 한다 — 메뉴
 * 항목 클릭 경로는 [dismissMenu] 를 동기 호출한 뒤에만 `startArrange`/`dismissSplit` 을 부르고
 * ([dismissMenuThenArrange], [dismissMenuThenDismissSplit]), adb 트리거 등 메뉴를 거치지 않는
 * 경로에 대비해 [setBubbleHiddenForArrange] 도 hidden=true 일 때 메뉴를 함께 제거한다.
 *
 * [실측 2026-07-25, 결함 #24③ 수정] 예전에는 메뉴 창에 FLAG_WATCH_OUTSIDE_TOUCH 를 걸고
 * ACTION_OUTSIDE 로 바깥 탭을 감지해 닫았는데, 이 ACTION_OUTSIDE 가 버블 창의 ACTION_DOWN 보다
 * *먼저* 디스패치됨이 실측됐다 — 그 결과 재탭 시 `menuWasOpenAtDown` 스냅샷이 이미 null 을 읽어
 * "닫기만" 이 아니라 "닫기 + startArrange 오발화" 로, 재롱프레스는 "닫힘+재열림" 으로 오동작했다.
 * 지금은 메뉴를 화면 전체를 덮는 투명 스크림 창 하나로 재구성해 이 경합 클래스 자체를 구조적으로
 * 없앴다 — 스크림이 모든 터치를 먼저 받으므로 메뉴가 열려 있는 동안 버블 창은 어떤 터치 이벤트도
 * 받을 수 없다(자세한 내용은 [showMenu] KDoc 참고).
 */
class FloatingLauncherService : Service() {

    private lateinit var windowManager: WindowManager
    private val store by lazy { ProfileStore(this) }
    private val handler = Handler(Looper.getMainLooper())

    /** P3-2 프리셋 asset 백그라운드 로드 전용. UI 상태 변경은 이 서비스도 메인 스레드로 단일화한다. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * [ProfileStore.bubblePosition] 의 콜드 스타트 1회 스냅샷 캐시(P3-3). onCreate 에서 한 번만
     * runBlocking 으로 읽고, 이후 createBubbleView/savePosition 은 전부 이 캐시만 쓴다 — 매 위치
     * 조회마다 DataStore I/O 를 걸지 않기 위함이다. null 이면 아직 저장된 위치가 없다는 뜻이다.
     */
    private var cachedBubbleX: Int? = null
    private var cachedBubbleY: Int? = null

    private var bubbleView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    /**
     * [#20] 메인 스레드에서만 변이되지만, [hasAttachedOverlayWindow] 가 a11y 서비스의 코루틴
     * 스코프(다른 스레드일 수 있음)에서 읽는다 — 스테일 읽기를 막기 위해 `@Volatile` 을 붙인다.
     */
    @Volatile
    private var bubbleAttached = false
    private var snapAnimator: ValueAnimator? = null

    /** [setBubbleHiddenForArrange] 안전 타이머 — 복원 신호가 안 오면 자동 재표시한다. UI 안전망 취소용. */
    private var hideSafetyRunnable: Runnable? = null

    /**
     * P3-2 확장 메뉴 오버레이. null 이면 닫혀 있음 — 그 자체가 열림/닫힘 상태다(별도 플래그 불필요).
     * [#20] [bubbleAttached] 와 동일한 이유로 `@Volatile` — [hasAttachedOverlayWindow] 가 다른
     * 스레드에서 읽는다.
     */
    @Volatile
    private var menuView: View? = null

    /** window_profiles.json presets 캐시. null 이면 아직 로드 전이거나 파싱 실패(메뉴에서 프리셋 섹션 생략). */
    private var cachedPresets: List<AspectPreset>? = null
    private var presetsLoadStarted = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        instance = this
        preloadPresetsIfNeeded()
        // 콜드 스타트 1회 한정 동기 스냅샷 읽기(위 cachedBubbleX/Y KDoc 참고). 최초 접근 시
        // SharedPreferencesMigration(레거시 "bubble_prefs" 이관)도 함께 실행되므로 이 1회는
        // 약간의 I/O 비용이 있으나, 버블 뷰 생성 전에 위치가 확정돼야 하므로 동기 대기가 맞다.
        //
        // [실기기 검증 리뷰 지적 반영, 2026-07-25] ProfileStore.bubblePosition() 은 내부적으로
        // 이미 읽기 실패를 잡아 null 로 폴백하지만(safeRead), 이 onCreate 경로는 BootReceiver ->
        // startForegroundService 직후 즉시 실행돼 부팅 크래시 루프로 이어질 수 있는 가장 민감한
        // 지점이라 runCatching 으로 한 번 더 방어한다(조용한 실패 금지 — 실패 시 Log.w 로 드러내고
        // 기본 위치로 폴백. cachedBubbleX/Y == null 이면 createBubbleView 가 화면 비율 기반 기본
        // 위치를 계산한다).
        val position = runCatching { runBlocking { store.bubblePosition() } }
            .onFailure { e -> Log.w(TAG, "onCreate: 저장된 버블 위치 읽기 실패 — 기본 위치로 폴백", e) }
            .getOrNull()
        cachedBubbleX = position?.first
        cachedBubbleY = position?.second
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            // 조용한 실패 금지 + 크래시 금지: startForeground 를 호출하지 않고 즉시 stopSelf.
            // startForegroundService 로 기동됐더라도 타임아웃 전에 stopSelf 하면 ANR 없이 종료된다.
            Log.w(TAG, "overlay 권한 없음 — 버블을 띄울 수 없음")
            Toast.makeText(this, getString(R.string.toast_overlay_permission_required), Toast.LENGTH_LONG).show()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat()
        // 서비스가 살아있다 = 사용자가 버블을 켠 상태. BootReceiver 가 이 값을 읽어 부팅 시 복귀한다.
        // fire-and-forget — ProfileStore.setBubbleEnabled 가 내부에서 IOException 을 잡아
        // Log.w 로 드러내므로(safeWrite) 여기서 별도 예외 처리가 필요 없다.
        serviceScope.launch { store.setBubbleEnabled(true) }
        addBubbleIfNeeded()
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        dismissMenu()
        removeBubble()
        serviceScope.cancel()
        isRunning = false
        if (instance == this) instance = null
        super.onDestroy()
    }

    // ══════════════════════════════════════════════════════════
    // 공개 API — ArrangerAccessibilityService 세션 연동
    // ══════════════════════════════════════════════════════════

    /**
     * 배치 세션 시작/종료에 맞춰 버블 오버레이 창을 완전히 제거/재부착한다 (KDoc 상단 실측 참조).
     * 항상 메인 스레드에서 실행되도록 post 한다 — 호출자([ArrangerAccessibilityService])는
     * 자신의 코루틴 스코프(Dispatchers.Main.immediate)에서 부르지만, 다른 스레드에서 호출돼도
     * 안전하도록 방어한다.
     *
     * hidden=true: 안전 타이머([HIDE_SAFETY_TIMEOUT_MS])를 건다 — 액추에이터가 예외로 죽어
     * false 복원 호출이 영영 안 오는 경우 버블이 영구 실종되는 것을 막는다. 이 타이머는
     * UI 안전망이지 액추에이터 상태를 기다리는 용도가 아니므로 ADR-2(고정 지연 금지) 위반이
     * 아니다 — 실제 배치 진행 대기는 전적으로 ArrangeStateMachine 의 조건 폴링이 담당한다.
     */
    fun setBubbleHiddenForArrange(hidden: Boolean) {
        handler.post {
            hideSafetyRunnable?.let { handler.removeCallbacks(it) }
            hideSafetyRunnable = null

            if (hidden) {
                dismissMenu() // 함정 #22: 배치 트리거가 어떤 경로로 오든(메뉴 클릭 외 adb 등) 메뉴 창도 반드시 제거
                detachBubbleView()
                val runnable = Runnable {
                    Log.w(
                        TAG,
                        "setBubbleHiddenForArrange: 안전 타이머 발동 — " +
                            "${HIDE_SAFETY_TIMEOUT_MS}ms 안에 복원 신호 없음. 자동 재표시",
                    )
                    hideSafetyRunnable = null
                    showBubbleIfPermitted()
                }
                hideSafetyRunnable = runnable
                handler.postDelayed(runnable, HIDE_SAFETY_TIMEOUT_MS)
            } else {
                showBubbleIfPermitted()
            }
        }
    }

    private fun showBubbleIfPermitted() {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "setBubbleHiddenForArrange: overlay 권한 없음 — 재표시 생략")
            return
        }
        createBubbleViewIfNeeded()
        attachBubbleView()
    }

    // ══════════════════════════════════════════════════════════
    // 알림 / 포그라운드
    // ══════════════════════════════════════════════════════════

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.floating_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun startForegroundCompat() {
        val onboardingIntent = Intent(this, OnboardingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            onboardingIntent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.floating_notification_title))
            .setContentText(getString(R.string.floating_notification_text))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // 매니페스트 foregroundServiceType="specialUse" + PROPERTY_SPECIAL_USE_FGS_SUBTYPE 와 짝을 이룬다.
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ══════════════════════════════════════════════════════════
    // 버블 뷰 생성/부착/분리
    //
    // create(뷰·LayoutParams 객체 생성)와 attach/detach(WindowManager 에 실제로 얹기/떼기)를
    // 분리한다 — setBubbleHiddenForArrange 는 뷰 객체를 파괴하지 않고 WindowManager 에서만
    // 떼어냈다 다시 붙여서 위치를 보존한다.
    // ══════════════════════════════════════════════════════════

    private fun addBubbleIfNeeded() {
        createBubbleViewIfNeeded()
        attachBubbleView()
    }

    /**
     * lint `ClickableViewAccessibility` 대응(setOnTouchListener 호출부 검사): 정적 검사가 "터치
     * 리스너를 단 뷰의 실제 클래스가 `performClick()` 을 오버라이드하는지" 를 보므로, 프레임워크
     * `ImageView` 를 그대로 쓰면 상속만으로는 통과하지 못한다. `super.performClick()` 만 호출하는
     * 최소 오버라이드라 동작은 `View.performClick()` 기본 구현과 완전히 동일하다(행동 변경 없음).
     */
    private inner class BubbleImageView : ImageView(this@FloatingLauncherService) {
        override fun performClick(): Boolean = super.performClick()
    }

    private fun createBubbleViewIfNeeded() {
        if (bubbleView != null) return

        val sizePx = (BUBBLE_SIZE_DP * resources.displayMetrics.density).toInt()
        val bounds = windowManager.currentWindowMetrics.bounds
        val maxX = (bounds.width() - sizePx).coerceAtLeast(0)
        val maxY = (bounds.height() - sizePx).coerceAtLeast(0)
        val savedX = cachedBubbleX ?: maxX
        val savedY = cachedBubbleY ?: (bounds.height() / 3)

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX.coerceIn(0, maxX)
            y = savedY.coerceIn(0, maxY)
        }

        val view = BubbleImageView().apply {
            setImageResource(R.drawable.ic_bubble)
            setBackgroundResource(R.drawable.bubble_background)
            contentDescription = getString(R.string.bubble_content_description)
            val pad = (BUBBLE_ICON_PADDING_DP * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            // lint ClickableViewAccessibility 대응: 탭 실행은 performClick() 경로로 통일한다 —
            // 스크린리더의 ACTION_CLICK(예: TalkBack "두 번 탭하여 활성화")도 이 리스너를 거쳐
            // onBubbleTap() 을 실행하게 하기 위함이다. 실제 탭 판정(BubbleTouchListener)은
            // 여전히 터치 이벤트로 하되, 확정된 탭은 view.performClick() 을 호출해 여기로 수렴한다.
            setOnClickListener { onBubbleTap() }
        }
        view.setOnTouchListener(BubbleTouchListener(view, params))

        bubbleView = view
        layoutParams = params
    }

    /** 이미 생성된 [bubbleView]/[layoutParams] 를 WindowManager 에 얹는다. 중복 attach 는 무시한다. */
    private fun attachBubbleView() {
        if (bubbleAttached) return
        val view = bubbleView ?: return
        val params = layoutParams ?: return
        runCatching { windowManager.addView(view, params) }
            .onFailure { e -> Log.e(TAG, "버블 뷰 attach 실패", e) }
            .onSuccess { bubbleAttached = true }
    }

    /** 뷰 객체는 유지한 채 WindowManager 에서만 창을 뗀다. 중복 detach 는 무시한다. */
    private fun detachBubbleView() {
        if (!bubbleAttached) return
        val view = bubbleView ?: return
        runCatching { windowManager.removeView(view) }
            .onFailure { e -> Log.w(TAG, "버블 뷰 detach 실패", e) }
        bubbleAttached = false
    }

    private fun removeBubble() {
        snapAnimator?.cancel()
        snapAnimator = null
        handler.removeCallbacksAndMessages(null)
        hideSafetyRunnable = null
        detachBubbleView()
        bubbleView = null
        layoutParams = null
    }

    // ══════════════════════════════════════════════════════════
    // 터치 제스처: 탭 = 배치 트리거, 드래그 = 이동+스냅, 롱프레스 = 확장 메뉴 토글(P3-2)
    // ══════════════════════════════════════════════════════════

    private inner class BubbleTouchListener(
        private val view: View,
        private val params: WindowManager.LayoutParams,
    ) : View.OnTouchListener {

        private val touchSlop = ViewConfiguration.get(this@FloatingLauncherService).scaledTouchSlop
        private val longPressTimeoutMs = ViewConfiguration.getLongPressTimeout().toLong()

        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var dragging = false
        private var longPressFired = false

        // [결함 #24③ 수정] 메뉴가 열려 있으면 풀스크린 스크림이 모든 터치를 가로채므로 이
        // 리스너는 그 동안 어떤 이벤트도 받지 않는다 — "메뉴 열림 스냅샷"을 따로 둘 필요가 없다.
        private val longPressRunnable = Runnable {
            longPressFired = true
            Log.i(TAG, "bubble long-press — 확장 메뉴 열기")
            showMenu()
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dragging = false
                    longPressFired = false
                    handler.postDelayed(longPressRunnable, longPressTimeoutMs)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        handler.removeCallbacks(longPressRunnable)
                        dismissMenu() // 드래그로 버블이 움직이면 옛 위치에 뜬 메뉴는 무의미하므로 닫는다
                    }
                    if (dragging) {
                        val bounds = windowManager.currentWindowMetrics.bounds
                        val maxX = (bounds.width() - view.width).coerceAtLeast(0)
                        val maxY = (bounds.height() - view.height).coerceAtLeast(0)
                        params.x = (initialX + dx.toInt()).coerceIn(0, maxX)
                        params.y = (initialY + dy.toInt()).coerceIn(0, maxY)
                        runCatching { windowManager.updateViewLayout(view, params) }
                    }
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    when {
                        longPressFired -> Unit // 이미 롱프레스 콜백에서 처리됨
                        dragging -> snapToEdge(view, params)
                        // lint ClickableViewAccessibility 대응: 탭 확정 시 performClick() 을 거친다
                        // (view 의 OnClickListener 가 onBubbleTap() 을 호출). 탭/드래그/롱프레스
                        // 판정 임계값·타이밍은 변경 없음 — 실행 경로만 표준 클릭 경로로 통일했다.
                        else -> view.performClick()
                    }
                    return true
                }

                else -> return false
            }
        }
    }

    private fun snapToEdge(view: View, params: WindowManager.LayoutParams) {
        val bounds = windowManager.currentWindowMetrics.bounds
        val screenWidth = bounds.width()
        val bubbleCenterX = params.x + view.width / 2
        val targetX = if (bubbleCenterX < screenWidth / 2) 0 else (screenWidth - view.width).coerceAtLeast(0)

        snapAnimator?.cancel()
        snapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = SNAP_ANIM_DURATION_MS
            addUpdateListener { anim ->
                params.x = anim.animatedValue as Int
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    savePosition(params.x, params.y)
                }
            })
            start()
        }
    }

    private fun onBubbleTap() {
        val service = ArrangerAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, getString(R.string.toast_accessibility_off), Toast.LENGTH_LONG).show()
            launchOnboarding()
            return
        }
        service.startArrange(null, null)
    }

    private fun launchOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    // ══════════════════════════════════════════════════════════
    // P3-2 확장 메뉴: 롱프레스로 열리는 위/아래 배치·분할 해제·종횡비 프리셋·설정 팝업.
    //
    // 클래식 View + TYPE_APPLICATION_OVERLAY 로 만든다(오버레이 창에 Compose 금지 — 프로젝트
    // 결정, lifecycle owner 함정 회피). bubbleView 와는 별개의 독립 창이라 버블 드래그/스냅과
    // 서로 간섭하지 않는다.
    // ══════════════════════════════════════════════════════════

    /**
     * 메뉴 오버레이 창을 연다. 이미 열려 있으면 아무것도 하지 않는다(열기/닫기 토글은 호출자
     * 쪽에서 판단한다 — [BubbleTouchListener] 참고).
     *
     * 위치: 버블 근처에 붙이되 화면 경계를 넘지 않게 클램프한다. 화면 크기는 매번
     * `windowManager.currentWindowMetrics` 를 재조회한다(좌표 하드코딩 금지, CLAUDE.md 함정 #2).
     *
     * [실측 2026-07-25, 결함 #24③ 수정] 예전에는 [buildMenuContent] 결과(LinearLayout)를 그
     * 자체로 WRAP_CONTENT 창에 얹고 FLAG_WATCH_OUTSIDE_TOUCH 의 ACTION_OUTSIDE 로 바깥 탭을
     * 감지했다. 그런데 이 ACTION_OUTSIDE 가 버블 창의 ACTION_DOWN 보다 *먼저* 디스패치됨이
     * 실측돼, 재탭/재롱프레스 스냅샷 방어(`menuWasOpenAtDown`)가 경합에서 졌다(재탭 → 닫기 +
     * startArrange 오발화, 재롱프레스 → 닫힘+재열림). 지금은 화면 전체(MATCH_PARENT)를 덮는
     * 투명 스크림 [FrameLayout] 을 루트 창으로 쓰고, 실제 메뉴 내용([buildMenuContent])은 그
     * 안에 `leftMargin`/`topMargin` 으로 배치한다(계산 로직은 그대로 재사용). 스크림이 모든
     * 터치를 먼저 받으므로 버블 창은 메뉴가 열려 있는 동안 어떤 터치도 받지 못한다 — "메뉴 열린
     * 채 버블 재탭/재롱프레스" 라는 경합 클래스 자체가 구조적으로 사라진다. 메뉴 항목(TextView)은
     * `isClickable = true` 라 자기 터치를 소비하므로 그대로 동작하고, 스크림 자체를 탭하면
     * [dismissMenu] 가 불린다.
     */
    private fun showMenu() {
        if (menuView != null) return
        val bubble = bubbleView ?: return
        val bubbleParams = layoutParams ?: return

        val content = buildMenuContent()
        // WindowManager 에 얹기 전 예상 크기를 재서 클램프 계산에 쓴다. 실제 최종 렌더 크기와
        // 완전히 같지 않을 수 있지만, 화면 밖으로 크게 벗어나지 않게 하는 용도로는 충분하다.
        content.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val menuWidth = content.measuredWidth
        val menuHeight = content.measuredHeight

        val bounds = windowManager.currentWindowMetrics.bounds
        val bubbleSize = bubble.width.takeIf { it > 0 }
            ?: (BUBBLE_SIZE_DP * resources.displayMetrics.density).toInt()

        // 버블이 화면 좌/우 어느 쪽에 가까운지에 따라 메뉴 정렬을 바꿔 화면 밖으로 나가는 경우를 줄인다.
        val screenCenterX = bounds.width() / 2
        val bubbleCenterX = bubbleParams.x + bubbleSize / 2
        var menuX = if (bubbleCenterX < screenCenterX) bubbleParams.x else bubbleParams.x + bubbleSize - menuWidth
        var menuY = bubbleParams.y + bubbleSize
        if (menuY + menuHeight > bounds.height()) {
            menuY = bubbleParams.y - menuHeight // 아래쪽 공간이 부족하면 버블 위로 띄운다
        }
        menuX = menuX.coerceIn(0, (bounds.width() - menuWidth).coerceAtLeast(0))
        menuY = menuY.coerceIn(0, (bounds.height() - menuHeight).coerceAtLeast(0))

        // 스크림(루트) 자체를 탭하면 메뉴를 닫는다. 메뉴 항목(TextView, isClickable=true)은
        // 자기 터치를 먼저 소비하므로 이 클릭 리스너까지 전파되지 않는다.
        val scrim = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            setOnClickListener { dismissMenu() }
        }
        scrim.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = menuX
                topMargin = menuY
            },
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        runCatching { windowManager.addView(scrim, params) }
            .onSuccess { menuView = scrim }
            .onFailure { e -> Log.e(TAG, "메뉴 뷰 attach 실패", e) }
    }

    /**
     * 메뉴 오버레이 창(풀스크린 스크림 루트)을 제거한다. 이미 닫혀 있으면 무시(멱등).
     * 배치/분할 해제 트리거 직전에는 반드시 이 함수를 거쳐야 한다 — CLAUDE.md 함정 #22:
     * 오버레이 창이 떠 있는 채로 배치가 돌면 피커發 파트너 창이 전체화면으로 낙착해 세션이
     * 실패한다(실측).
     */
    private fun dismissMenu() {
        val view = menuView ?: return
        menuView = null
        runCatching { windowManager.removeView(view) }
            .onFailure { e -> Log.w(TAG, "메뉴 뷰 제거 실패", e) }
    }

    private fun buildMenuContent(): LinearLayout {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.menu_background)
            val pad = (MENU_CONTAINER_PADDING_DP * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        container.addMenuItem(getString(R.string.bubble_menu_place_top)) {
            dismissMenuThenArrange(Placement.TOP, null)
        }
        container.addMenuItem(getString(R.string.bubble_menu_place_bottom)) {
            dismissMenuThenArrange(Placement.BOTTOM, null)
        }
        container.addMenuItem(getString(R.string.bubble_menu_dismiss_split)) {
            dismissMenuThenDismissSplit()
        }
        container.addMenuItem(getString(R.string.bubble_menu_export_pair)) {
            exportAppPair()
        }
        // P4-1: Shizuku 가용할 때만 노출한다(설계 확정 — 미가용 시 항목 자체를 숨긴다,
        // DESIGN_P41_FREEFORM.md §4 "Shizuku 없이 동작하는 폴백 경로 없음").
        if (ShizukuShell.isReady()) {
            container.addMenuItem(getString(R.string.bubble_menu_open_popup)) {
                dismissMenuThenPopup()
            }
        }

        // window_profiles.json presets 은 자산 파싱 성공 시에만 채워진다. 실패/미로드 시 섹션 자체를
        // 생략한다(크래시 금지, 조용한 실패는 preloadPresetsIfNeeded 의 Log.w 로 이미 드러남).
        val presets = cachedPresets
        if (!presets.isNullOrEmpty()) {
            container.addMenuDivider()
            presets.forEach { preset ->
                container.addMenuItem(preset.label) {
                    // preset.aspect == null("자동 감지")이면 startArrange(null, null) 과 동일 의미가 된다.
                    dismissMenuThenArrange(null, preset.aspect)
                }
            }
        }

        container.addMenuDivider()
        container.addMenuItem(getString(R.string.bubble_menu_settings)) {
            dismissMenu()
            launchOnboarding()
        }

        return container
    }

    private fun LinearLayout.addMenuItem(label: String, onClick: () -> Unit) {
        val density = resources.displayMetrics.density
        val item = TextView(context).apply {
            text = label
            setTextColor(MENU_ITEM_TEXT_COLOR)
            textSize = MENU_ITEM_TEXT_SIZE_SP
            setBackgroundResource(R.drawable.menu_item_background)
            val horizontal = (MENU_ITEM_PADDING_H_DP * density).toInt()
            val vertical = (MENU_ITEM_PADDING_V_DP * density).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
            isClickable = true
            setOnClickListener { onClick() }
        }
        addView(
            item,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
    }

    private fun LinearLayout.addMenuDivider() {
        val density = resources.displayMetrics.density
        val divider = View(context).apply { setBackgroundColor(MENU_DIVIDER_COLOR) }
        val margin = (4 * density).toInt()
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt())
        params.setMargins(margin, margin, margin, margin)
        addView(divider, params)
    }

    /**
     * 메뉴를 먼저 제거한 뒤(함정 #22) 배치를 트리거한다. 접근성 서비스 인스턴스가 없으면
     * 기존 탭 동작과 동일하게 토스트 + 온보딩으로 유도한다.
     */
    private fun dismissMenuThenArrange(placement: Placement?, aspect: Float?) {
        dismissMenu()
        val service = ArrangerAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, getString(R.string.toast_accessibility_off), Toast.LENGTH_LONG).show()
            launchOnboarding()
            return
        }
        service.startArrange(placement, aspect)
    }

    /** 메뉴를 먼저 제거한 뒤(함정 #22) 분할 해제를 트리거한다. */
    private fun dismissMenuThenDismissSplit() {
        dismissMenu()
        val service = ArrangerAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, getString(R.string.toast_accessibility_off), Toast.LENGTH_LONG).show()
            launchOnboarding()
            return
        }
        service.dismissSplit()
    }

    /**
     * [P4-1] 메뉴를 먼저 제거한 뒤(함정 #22) 팝업(freeform) 배치를 트리거한다. 버블 자체는
     * freeform 낙착에 무해함이 프로브에서 확인됐으므로([DESIGN_P41_FREEFORM.md] §4), 배치
     * 세션처럼 버블 창을 숨길 필요는 없다 — 스크림(메뉴)만 제거하면 된다.
     */
    private fun dismissMenuThenPopup() {
        dismissMenu()
        val service = ArrangerAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, getString(R.string.toast_accessibility_off), Toast.LENGTH_LONG).show()
            launchOnboarding()
            return
        }
        service.startPopup()
    }

    // ══════════════════════════════════════════════════════════
    // P4-4: 홈 화면 고정 바로가기(앱 페어 export)
    // ══════════════════════════════════════════════════════════

    /**
     * 메뉴 "앱 페어 바로가기 만들기" 트리거. 현재 전면 앱을 식별해 홈 화면에 고정 가능한
     * 바로가기([PairShortcutActivity] 트램펄린 인텐트)를 시스템에 요청한다.
     *
     * 1) [dismissMenu] 를 먼저 호출한다 — 다른 메뉴 트리거 항목([dismissMenuThenArrange] 등)과
     * 동일한 이유다: 스크림이 떠 있으면 접근성 창 목록의 활성 창 판독이 우리 창으로 오염된다
     * (함정 #22 계열).
     * 2) 스크림 제거 직후에는 접근성 창 목록 재구축이 비원자적이라([PROGRESS #25],
     * [ArrangerAccessibilityService] 의 dismissSplit/awaitWindowsSettled 와 동일 실측 근거) 고정
     * 지연 대신 [ArrangerAccessibilityService.foregroundPackageForExport] 가 non-null 값을 낼 때까지
     * 조건 폴링한다(ADR-2).
     */
    private fun exportAppPair() {
        dismissMenu()

        val service = ArrangerAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, getString(R.string.toast_export_accessibility_off), Toast.LENGTH_LONG).show()
            return
        }

        serviceScope.launch {
            val pkg = withTimeoutOrNull(EXPORT_FOREGROUND_TIMEOUT_MS) {
                var found = service.foregroundPackageForExport()
                while (found == null) {
                    delay(EXPORT_FOREGROUND_POLL_INTERVAL_MS)
                    found = service.foregroundPackageForExport()
                }
                found
            }

            if (pkg == null) {
                Log.w(TAG, "exportAppPair: ${EXPORT_FOREGROUND_TIMEOUT_MS}ms 내 대상 앱 식별 실패")
                Toast.makeText(
                    this@FloatingLauncherService,
                    getString(R.string.toast_export_target_not_found),
                    Toast.LENGTH_LONG,
                ).show()
                return@launch
            }

            requestPinShortcut(pkg)
        }
    }

    /**
     * [pkg] 를 대상으로 하는 홈 화면 고정 바로가기를 시스템에 요청한다. 인텐트는
     * [PairShortcutActivity] 를 트램펄린으로 거쳐 `startArrangeWhenForeground` 를 호출한다.
     * 라벨/아이콘 조회가 실패해도(예: 조회 시점과 고정 시점 사이 제거된 앱) 바로가기 생성 자체는
     * 계속한다 — 패키지명/우리 앱 아이콘으로 폴백한다(조용한 실패 금지: 원인은 로그로 남긴다).
     */
    private fun requestPinShortcut(pkg: String) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            Log.w(TAG, "requestPinShortcut: 이 런처는 바로가기 고정을 지원하지 않음")
            Toast.makeText(this, getString(R.string.toast_pin_shortcut_unsupported), Toast.LENGTH_LONG).show()
            return
        }

        val label = runCatching {
            val appInfo = packageManager.getApplicationInfo(pkg, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(pkg)

        val icon = runCatching {
            IconCompat.createWithBitmap(drawableToBitmap(packageManager.getApplicationIcon(pkg)))
        }.getOrElse { e ->
            Log.w(TAG, "requestPinShortcut: 대상 앱 아이콘 조회 실패 — 우리 앱 아이콘으로 폴백", e)
            IconCompat.createWithResource(this, R.drawable.ic_bubble)
        }

        val shortcutIntent = Intent(Intent.ACTION_VIEW)
            .setClass(this, PairShortcutActivity::class.java)
            .putExtra(PairShortcutActivity.EXTRA_TARGET_PACKAGE, pkg)

        val shortcutInfo = ShortcutInfoCompat.Builder(this, "pair_$pkg")
            .setShortLabel(label)
            .setIcon(icon)
            .setIntent(shortcutIntent)
            .build()

        runCatching { ShortcutManagerCompat.requestPinShortcut(this, shortcutInfo, null) }
            .onFailure { e -> Log.e(TAG, "requestPinShortcut: 바로가기 고정 요청 실패", e) }
    }

    /**
     * Drawable → Bitmap 변환 유틸(P4-4, [IconCompat.createWithBitmap] 입력용). 이미
     * [BitmapDrawable] 이면 내부 비트맵을 그대로 재사용하고, 그렇지 않으면(벡터 등) intrinsic
     * 크기로 캔버스에 그려 비트맵화한다. intrinsic 크기가 0 이하인 방어적 경우 1x1 로
     * 폴백한다(크래시 금지 — 호출부는 어차피 runCatching 으로 감싸져 있지만 이 함수 자체도
     * 안전해야 한다).
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        (drawable as? BitmapDrawable)?.bitmap?.let { return it }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
        val bitmap = createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    /**
     * window_profiles.json 의 presets 를 백그라운드에서 1회 로드해 캐싱한다. 실패해도 재시도하지
     * 않는다(무한 재시도 금지) — 메뉴는 그냥 프리셋 섹션 없이 뜬다. 서비스 생성 시점(onCreate)에
     * 미리 불러 두므로 사용자가 실제로 메뉴를 열 때는 대개 이미 준비돼 있다.
     */
    private fun preloadPresetsIfNeeded() {
        if (presetsLoadStarted) return
        presetsLoadStarted = true
        serviceScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    assets.open(WindowProfilesParser.PROFILES_ASSET_NAME).bufferedReader().use { it.readText() }
                }.getOrNull()
            }
            if (text == null) {
                Log.w(TAG, "preloadPresetsIfNeeded: profiles asset 읽기 실패 — 메뉴에서 프리셋 섹션 생략")
                return@launch
            }
            when (val result = WindowProfilesParser.parse(text)) {
                is ProfilesParseResult.Success -> cachedPresets = result.config.presets
                is ProfilesParseResult.Failure -> {
                    result.errors.forEach { Log.w(TAG, "preloadPresetsIfNeeded: 파싱 오류: $it") }
                }
            }
        }
    }

    private fun savePosition(x: Int, y: Int) {
        cachedBubbleX = x
        cachedBubbleY = y
        // fire-and-forget — ProfileStore.saveBubblePosition 이 내부에서 예외를 잡는다(safeWrite).
        serviceScope.launch { store.saveBubblePosition(x, y) }
    }

    companion object {
        private const val TAG = "FWFloatingLauncher"
        private const val CHANNEL_ID = "floating_launcher_channel"
        private const val NOTIFICATION_ID = 1001
        private const val BUBBLE_SIZE_DP = 56
        private const val SNAP_ANIM_DURATION_MS = 200L

        /** [createBubbleViewIfNeeded] 버블 아이콘(ImageView) 내부 패딩. */
        private const val BUBBLE_ICON_PADDING_DP = 14

        // P3-2 확장 메뉴 스타일. 과한 꾸밈 없이 bubble_background 와 톤만 맞춘다.
        private val MENU_ITEM_TEXT_COLOR = Color.WHITE
        private const val MENU_ITEM_TEXT_SIZE_SP = 15f
        private const val MENU_DIVIDER_COLOR = 0x33FFFFFF

        /** [buildMenuContent] 스크림 컨테이너(LinearLayout)의 사방 패딩. */
        private const val MENU_CONTAINER_PADDING_DP = 4

        /** [addMenuItem] 메뉴 항목 TextView 의 좌우/상하 패딩. */
        private const val MENU_ITEM_PADDING_H_DP = 14
        private const val MENU_ITEM_PADDING_V_DP = 10

        /**
         * setBubbleHiddenForArrange(true) 후 복원 신호가 오지 않을 때 자동 재표시까지의 유예.
         * 이론 최악 세션 = MENU 5스텝 × 3시도 × 3s + 디바이더 4s + 드래그 12s(세션 오버라이드)
         * + verify ≈ 70s > 종전 30s. 조기 복원은 세션 중 오버레이 재출현 = 함정 #22(피커發
         * 파트너 전체화면 낙착) 자충수 — 워치독은 액추에이터 사망 시 최후 복구만 담당하므로
         * 큰 값이 안전. 실측 최장 세션 12s (2026-07-28, 15차까지 관측).
         * UI 안전망일 뿐 ADR-2 대상 아님.
         */
        private const val HIDE_SAFETY_TIMEOUT_MS = 90_000L

        /**
         * [P4-4] exportAppPair() 의 대상 앱 식별 조건 폴링 간격/타임아웃. dismissMenu() 직후
         * 접근성 창 목록 재구축이 비원자적이라는 실측 근거(dismissSplit 의 동일 값 선례,
         * [ArrangerAccessibilityService] KDoc 참고)를 그대로 재사용한다.
         */
        private const val EXPORT_FOREGROUND_POLL_INTERVAL_MS = 150L
        private const val EXPORT_FOREGROUND_TIMEOUT_MS = 2_000L

        /** [OnboardingActivity] 가 버블 실행 여부를 표시하기 위한 토글 상태. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** ArrangerAccessibilityService.instance 와 동일한 패턴 — 브릿지용 정적 참조. */
        @Volatile
        var instance: FloatingLauncherService? = null
            private set

        /**
         * [#20] 오버레이 가드: `SplitEntry.clickUntilCondition` 의 GESTURE_TAP 디스패치 전 판정원.
         * 제스처 탭은 히트테스트 기반이라 자기 터치 가능 오버레이(버블/확장 메뉴)가 화면 위에
         * 떠 있으면 그 오버레이가 탭을 가로채 대상 노드까지 도달하지 못한다(함정 #22 계열을
         * 클릭-사이클 에스컬레이션에 맞춰 강제화). a11y 창 목록이 아니라 in-process 상태를 직접
         * 본다 — a11y 창 목록은 touchable 여부를 노출하지 않고 자기 오버레이 자체가 그 목록을
         * 오염시킬 수 있다(함정 #25).
         */
        fun hasAttachedOverlayWindow(): Boolean =
            instance?.let { it.bubbleAttached || it.menuView != null } ?: false
    }
}
