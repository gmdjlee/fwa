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
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
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
import android.widget.ImageView
import android.widget.Toast
import dev.dj.foldwindow.R
import dev.dj.foldwindow.ui.OnboardingActivity
import kotlin.math.abs

/**
 * P3-1: 오버레이 플로팅 버블을 띄우고 원터치로 액추에이터([ArrangerAccessibilityService])를 실행하는
 * 포그라운드 서비스. Phase 2 의 adb 트리거([ArrangeTriggerReceiver])를 사용자용으로 대체한다.
 *
 * 동작:
 * - 탭: 접근성 서비스 인스턴스가 있으면 `startArrange(null, null)` (프로파일/기본값 적용).
 *   없으면 토스트 안내 후 [OnboardingActivity] 로 유도.
 * - 드래그: 버블 이동. 손을 떼면 가까운 좌/우 가장자리로 스냅.
 * - 롱프레스(드래그 없이 유지): [OnboardingActivity] 진입.
 *
 * ADR-2 참고: 이 서비스가 쓰는 지연(Handler.postDelayed 롱프레스 감지, ValueAnimator 스냅 애니메이션)은
 * 전부 표준 UI 제스처 인식/화면 전환 애니메이션이며 액추에이터 상태 대기가 아니다. 실제 배치 진행은
 * 전적으로 [ArrangerAccessibilityService] 의 상태 머신이 담당하고, 이 서비스는 트리거만 한다.
 *
 * 좌표 하드코딩 금지(CLAUDE.md 함정 #2, #5): 버블 위치 클램프·스냅은 매번
 * `windowManager.currentWindowMetrics` 를 재조회해 계산한다 — 화면 크기를 상수로 가정하지 않는다.
 *
 * [실측 2026-07-25, A/B 실험] 버블 오버레이 창이 떠 있는 동안 배치를 실행하면 SplitEntry step3
 * 피커發 PanelActivity 가 분할 페인이 아니라 **전체화면**으로 낙착해 자가 가드가 즉시 종료 →
 * 분할 쌍 미수렴 → ENTRY_STEP_FAILED 로 귀결됨이 재현됐다(버블 ON 2회 실패, 동일 빌드·경로에서
 * 버블 OFF 는 즉시 성공). 버블 창은 접근성 창 목록에 TYPE_SYSTEM 으로 보고돼 기하 판정 오염은
 * 아니며, One UI WM 라우팅이 "런칭 패키지에 가시 오버레이 창 존재" 상황에서 페인 배치를 하지
 * 않는 것으로 추정된다(메커니즘 불명 — 경험 법칙으로 대응). [setBubbleHiddenForArrange] 가
 * [ArrangerAccessibilityService] 의 세션 시작/종료에 맞춰 이 창을 완전히 제거/재부착한다
 * (INVISIBLE 로는 부족할 수 있음 — 원인이 "가시 창의 존재" 자체이므로 창 자체를 없앤다).
 */
class FloatingLauncherService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private var bubbleView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var bubbleAttached = false
    private var snapAnimator: ValueAnimator? = null

    /** [setBubbleHiddenForArrange] 안전 타이머 — 복원 신호가 안 오면 자동 재표시한다. UI 안전망 취소용. */
    private var hideSafetyRunnable: Runnable? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        createNotificationChannel()
        instance = this
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
        prefs.edit().putBoolean(PREF_BUBBLE_ENABLED, true).apply()
        addBubbleIfNeeded()
        isRunning = true
        return START_STICKY
    }

    override fun onDestroy() {
        removeBubble()
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

    private fun createBubbleViewIfNeeded() {
        if (bubbleView != null) return

        val sizePx = (BUBBLE_SIZE_DP * resources.displayMetrics.density).toInt()
        val bounds = windowManager.currentWindowMetrics.bounds
        val maxX = (bounds.width() - sizePx).coerceAtLeast(0)
        val maxY = (bounds.height() - sizePx).coerceAtLeast(0)
        val savedX = prefs.getInt(PREF_BUBBLE_X, maxX)
        val savedY = prefs.getInt(PREF_BUBBLE_Y, bounds.height() / 3)

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

        val view = ImageView(this).apply {
            setImageResource(R.drawable.ic_bubble)
            setBackgroundResource(R.drawable.bubble_background)
            contentDescription = getString(R.string.bubble_content_description)
            val pad = (14 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
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
    // 터치 제스처: 탭 = 배치 트리거, 드래그 = 이동+스냅, 롱프레스 = 온보딩
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

        private val longPressRunnable = Runnable {
            longPressFired = true
            Log.i(TAG, "bubble long-press — 온보딩 진입")
            launchOnboarding()
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
                        else -> onBubbleTap()
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

    private fun savePosition(x: Int, y: Int) {
        prefs.edit().putInt(PREF_BUBBLE_X, x).putInt(PREF_BUBBLE_Y, y).apply()
    }

    companion object {
        private const val TAG = "FWFloatingLauncher"
        private const val CHANNEL_ID = "floating_launcher_channel"
        private const val NOTIFICATION_ID = 1001
        private const val BUBBLE_SIZE_DP = 56
        private const val SNAP_ANIM_DURATION_MS = 200L

        /**
         * setBubbleHiddenForArrange(true) 후 복원 신호가 오지 않을 때 자동 재표시까지의 유예.
         * ArrangerAccessibilityService 의 세션 타임아웃(가장 긴 경로도 수십 초 내)보다 넉넉히
         * 길게 잡아, 정상 세션 도중에는 절대 발동하지 않게 한다. UI 안전망일 뿐 ADR-2 대상 아님.
         */
        private const val HIDE_SAFETY_TIMEOUT_MS = 30_000L

        // [미해결] P3-3 에서 DataStore 로 이관 예정. 지금은 SharedPreferences 로 최소 구현한다
        // (프로젝트 전반은 DataStore 를 쓰지만, 이 값들은 OnboardingActivity/BootReceiver 도 동기적으로
        // 읽어야 해서 P3-1 범위에서는 SharedPreferences 가 더 단순하다).
        const val PREFS_NAME = "bubble_prefs"
        const val PREF_BUBBLE_ENABLED = "bubble_enabled"
        private const val PREF_BUBBLE_X = "bubble_x"
        private const val PREF_BUBBLE_Y = "bubble_y"

        /** [OnboardingActivity] 가 버블 실행 여부를 표시하기 위한 토글 상태. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** ArrangerAccessibilityService.instance 와 동일한 패턴 — 브릿지용 정적 참조. */
        @Volatile
        var instance: FloatingLauncherService? = null
            private set
    }
}
