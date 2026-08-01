package dev.dj.foldwindow.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
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
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
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
 * P3-2: 같은 이유로 확장 메뉴 오버레이 창도 배치/해제 트리거 전 반드시 제거해야 한다.
 * [P5 폴리시] 메뉴에 퇴장 애니메이션이 붙으면서 이 계약이 "dismissMenu 동기 호출 후 트리거" 에서
 * **"창이 실제로 헐린 뒤 콜백에서 트리거"** 로 바뀌었다 — 메뉴 항목 클릭 경로는
 * `dismissMenu { ... }` 의 람다 안에서만 `startArrange`/`dismissSplit` 을 부른다
 * ([dismissMenuThenArrange], [dismissMenuThenDismissSplit]). 애니메이션 *시작* 시점에 트리거하면
 * 스크림이 아직 화면에 남은 채로 세션이 돌아 함정 #22 를 그대로 밟는다. adb 트리거 등 메뉴를
 * 거치지 않는 경로에 대비해 [setBubbleHiddenForArrange] 는 애니메이션을 건너뛰는 즉시 제거
 * ([removeMenuNow])를 쓴다 — 이미 시작된 세션 앞에서 애니메이션을 기다릴 여유는 없다.
 *
 * **[D20] 제거 프리미티브는 `removeViewImmediate` 다** — 우리 오버레이 창을 떼는 세 경로
 * ([removeMenuNow], [detachBubbleView], 그 둘을 거치는 [onDestroy])가 모두 이것만 쓴다.
 * `removeView` 는 `ViewRootImpl` 에 `MSG_DIE` 를 **포스트만** 하므로 반환했다고 창이 사라진 것이
 * 아니다 — 예전 KDoc 이 주장하던 "removeView 가 실행된 뒤" 는 실제로는 "다음 메인 루프 순회
 * 전까지 스크림이 아직 합성돼 있는 상태" 였다. `removeViewImmediate` 는 같은 스레드에서
 * `dispatchDetachedFromWindow()` → `WindowSession.remove()`(동기 바인더)까지 마치고 돌아온다.
 * 유일한 예외는 `ViewRootImpl.mIsTraversal` 인 동안의 호출인데, 우리 호출 지점은 전부
 * 입력 콜백/애니메이션 콜백/Handler.post 라 traversal 내부가 아니다.
 *
 * [실측 2026-07-25, 결함 #24③ 수정] 예전에는 메뉴 창에 FLAG_WATCH_OUTSIDE_TOUCH 를 걸고
 * ACTION_OUTSIDE 로 바깥 탭을 감지해 닫았는데, 이 ACTION_OUTSIDE 가 버블 창의 ACTION_DOWN 보다
 * *먼저* 디스패치됨이 실측됐다 — 그 결과 재탭 시 `menuWasOpenAtDown` 스냅샷이 이미 null 을 읽어
 * "닫기만" 이 아니라 "닫기 + startArrange 오발화" 로, 재롱프레스는 "닫힘+재열림" 으로 오동작했다.
 * 지금은 메뉴를 화면 전체를 덮는 투명 스크림 창 하나로 재구성해 이 경합 클래스 자체를 구조적으로
 * 없앴다 — 스크림이 모든 터치를 먼저 받으므로 메뉴가 열려 있는 동안 버블 창은 어떤 터치 이벤트도
 * 받을 수 없다(자세한 내용은 [showMenu] KDoc 참고).
 *
 * [W7-B/P2] 저장된 버블 위치 복원은 **비동기**다([restoreBubblePositionAsync]). 예전에는
 * `onCreate` 에서 코루틴 블로킹 브리지로 DataStore 를 메인 스레드 동기 대기했는데, 이 경로는
 * `BootReceiver → startForegroundService` 직후 — ANR 판정이 가장 가혹한 구간 — 에서 실행되고
 * 최초 실행 시에는 `SharedPreferencesMigration`(레거시 "bubble_prefs" 이관)까지 동기로 돈다.
 * **의도된 사용자 체감 변화**: 부팅 직후 한두 프레임 동안 버블이 기본 위치(우측 가장자리,
 * 화면 높이 1/3)에 떴다가 저장 위치로 이동한다. 이 트레이드오프는 계획서가 명시적으로 수용했다.
 */
class FloatingLauncherService : Service() {

    private lateinit var windowManager: WindowManager
    private val store by lazy { ProfileStore(this) }
    private val handler = Handler(Looper.getMainLooper())

    /** P3-2 프리셋 asset 백그라운드 로드 전용. UI 상태 변경은 이 서비스도 메인 스레드로 단일화한다. */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * [ProfileStore.bubblePosition] 의 콜드 스타트 1회 스냅샷 캐시(P3-3). onCreate 에서 한 번만
     * 읽고([restoreBubblePositionAsync] — W7-B/P2 이전에는 메인 스레드 동기 읽기였다), 이후
     * createBubbleView/savePosition 은 전부 이 캐시만 쓴다 — 매 위치 조회마다 DataStore I/O 를
     * 걸지 않기 위함이다. null 이면 아직 읽기가 도착하지 않았거나 저장된 위치가 없다는 뜻이며,
     * 그때는 [createBubbleViewIfNeeded] 가 화면 크기 기반 기본 위치를 쓴다.
     */
    private var cachedBubbleX: Int? = null
    private var cachedBubbleY: Int? = null

    /**
     * [W7-B/P2] 사용자가 이미 버블 위치를 손댔는지. 이 플래그가 서 있으면
     * [restoreBubblePositionAsync] 는 캐시를 덮어쓰지 않는다.
     *
     * 복원이 비동기가 되면서 생긴 **신규 실패 모드**를 막는다: DataStore 읽기가 도착하기 전에
     * 사용자가 버블을 드래그해 놓으면, 뒤늦게 도착한 **옛 저장값이 방금 옮긴 위치를 되돌린다**.
     * 창은 밀리초 단위로 좁지만 부팅 직후 오동작은 재현이 어려워 더 나쁘다.
     *
     * [W7-C] 실제로 막는 지점은 **세 곳**이다:
     *  1. **드래그 확정** — [BubbleTouchListener] 의 `ACTION_MOVE` 가 touchSlop 을 넘겨
     *     `params.x/y` 를 실제로 바꾸기 시작하는 그 지점에서 곧바로 잠근다.
     *  2. **스냅 종료 저장** — [savePosition] (드래그 끝 → [snapToEdge] 애니메이션 완료).
     *  3. **스냅 애니메이션 진행 중** — [applyCachedBubblePosition] 이 `snapAnimator.isRunning`
     *     이면 조용히 반환한다(플래그와 무관한 별도 가드).
     *
     * W7-C 이전에는 2번뿐이었다 — 즉 **스냅 애니메이션이 끝나야** 플래그가 섰다. 그 사이에
     * 복원이 도착하면 버블이 손가락 밑에서 저장 위치로 튀고, 이어 `onAnimationEnd` 가 그 오염된
     * 좌표를 저장했다.
     *
     * **남는 창(정직하게):** 터치 `ACTION_DOWN` ~ 드래그 확정(touchSlop 초과) 사이 수 ms 는
     * 여전히 열려 있다. 다만 `initialX/initialY` 를 `ACTION_DOWN` 시점에 스냅샷해 두므로, 그 창에
     * 복원이 도착해도 **다음 `ACTION_MOVE` 가 드래그 기준 좌표로 다시 덮어쓴다** — 남는 증상은
     * 한 프레임짜리 시각적 튐이고 저장값이 오염되지는 않는다. 그 창에서 제스처가 탭/롱프레스로
     * 끝나면 복원값이 그대로 남는데, 그건 사용자가 위치를 안 옮긴 경우라 올바른 동작이다.
     *
     * 메인 스레드([serviceScope] = `Dispatchers.Main.immediate`, 터치 리스너/애니메이터도 전부
     * 메인)에서만 읽고 쓰므로 `@Volatile` 은 불필요하다 — [bubbleAttached]/[menuView] 와 달리
     * 다른 스레드에서 접근하는 경로가 없다.
     */
    private var bubblePositionUserAdjusted = false

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
     * P3-2 확장 메뉴 오버레이(풀스크린 스크림 루트). null 이면 닫혀 있음 — 그 자체가 열림/닫힘
     * 상태다. [#20] [bubbleAttached] 와 동일한 이유로 `@Volatile` — [hasAttachedOverlayWindow] 가
     * 다른 스레드에서 읽는다.
     *
     * [P5 폴리시] **퇴장 애니메이션이 도는 동안에도 non-null 로 유지된다.** 창이 아직
     * WindowManager 에 붙어 터치를 가로채고 있는 구간이므로, [hasAttachedOverlayWindow] 가
     * 그 구간 내내 true 여야 한다는 계약(#20)을 그대로 지키기 위함이다. 실제 null 대입은
     * `windowManager.removeViewImmediate` 를 부르는 [removeMenuNow] 에서만 일어난다(D20 —
     * 이 파일의 제거 프리미티브는 세 경로 모두 `removeViewImmediate` 다).
     */
    @Volatile
    private var menuView: View? = null

    /**
     * 메뉴 창이 **WindowManager 에 실제로 얹혀 있는가**. [menuView] 와 미묘하게 다르며, 그 차이가
     * 함정 #22 가드의 정확성을 좌우한다.
     *
     * [menuView] 는 "논리적으로 열려 있는가"(열림/닫는 중 판정, 재진입 가드)에 쓰이므로
     * [removeMenuNow] 가 **재진입을 막기 위해 창을 헐기 전에 미리** null 로 만든다. 그런데 그
     * 사이에는 `removeViewImmediate` 의 동기 바인더 왕복(WMS 호출)이 끼어 있다 — 즉 [menuView]
     * 만 보면 **풀스크린 스크림이 아직 합성돼 있는데 "오버레이 없음"이라고 답하는 구간**이
     * 1ms 단위로 열린다. [hasAttachedOverlayWindow] 는 다른 스레드(a11y 서비스 코루틴)에서
     * 읽히므로 이 구간이 실제로 관측될 수 있고, 그때 제스처 탭이 디스패치되면 우리 스크림이
     * 그 탭을 가로챈다(#20/함정 #22).
     *
     * 그래서 [bubbleAttached] 와 완전히 대칭인 창 존재 플래그를 따로 둔다: `addView` 성공 **직후**
     * 세우고, `removeViewImmediate` 반환 **직후** 내린다. [hasAttachedOverlayWindow] 는 이 값만 본다.
     */
    @Volatile
    private var menuAttached = false

    /**
     * [menuView](스크림) 안의 실제 메뉴 내용 뷰와 딤 레이어. 진입/퇴장 애니메이션 대상이라
     * 참조를 들고 있어야 한다(스크림 자체에 alpha 를 걸면 딤과 내용이 서로 곱해진다).
     * 메인 스레드에서만 접근한다.
     */
    private var menuContent: LinearLayout? = null
    private var menuDim: View? = null

    /**
     * 퇴장 애니메이션 진행 중인가. [dismissMenu] 의 멱등성을 지키는 플래그다 — 이 플래그가 서 있는
     * 동안 [menuView] 는 여전히 non-null 이므로(위 KDoc) `menuView != null` 만으로는 "닫는 중" 과
     * "열려 있음" 을 구분할 수 없고, 두 번째 호출이 `removeViewImmediate` 를 이중 실행하게 된다.
     */
    private var menuDismissing = false

    /**
     * [D1] **커밋된 메뉴 액션**. [dismissMenu] 가 호출된 그 순간(= 사용자가 항목을 눌러 액션이
     * 확정된 순간) 여기에 담기고, 메뉴 창이 실제로 헐린 직후 [removeMenuNow] 가 **정확히 한 번**
     * 꺼내 실행한다.
     *
     * **[F4] 결과는 셋 중 하나이며, 어느 쪽이든 "정확히 1회" 는 유지된다:**
     *  1. **즉시 실행** — 평상시. [removeMenuNow] 가 창을 헐어 낸 직후 그 자리에서 실행한다.
     *  2. **이연 실행** — 자동 배치 세션이 시작돼 버블이 숨겨진 상태
     *     ([bubbleHiddenForArrange] == true)에서 창이 헐린 경우. 지금 실행하면 세션 한복판이라
     *     `startArrange`/`dismissSplit` 의 busy 가드에 걸려 "무시 + busy 토스트" 로 증발한다
     *     (함정 #22 계열). 그래서 버리지도 즉시 실행하지도 않고 [deferredMenuAction] 으로 옮겨
     *     **복원 시점**([restoreBubbleAfterArrange])까지 미룬다.
     *  3. **폐기** — [onDestroy] 가 먼저 온 경우. 실행할 스코프 자체가 사라지므로 버리되,
     *     조용히 버리지 않고 `Log.w` 로 드러낸다(두 필드 모두).
     *
     * 왜 필드인가(= 왜 `withEndAction` 캡처로는 안 되는가): 종전에는 액션이 퇴장
     * [android.view.ViewPropertyAnimator] 의 `withEndAction` 안에만 살아 있었다. 그런데
     * [removeMenuNow] 는 그 애니메이터를 `cancel()` 하고, 취소된 ViewPropertyAnimator 의
     * end-action 은 **실행되지 않는다**(플랫폼 계약). 즉 [MENU_EXIT_ANIM_MS] 창 안에 배치 세션이
     * 시작되면([setBubbleHiddenForArrange] → [removeMenuNow]) 사용자가 이미 확정한 액션이
     * 조용히 증발했다. 최악의 사례가 대칭적으로 나쁘다: **「전체화면 자동 배치 끄기」 탭이,
     * 그것이 막으려던 바로 그 자동 세션에 의해 취소된다** — 사용자는 껐다고 믿는데 켜져 있다.
     *
     * 폐기가 허용되는 유일한 지점은 [onDestroy] 이며, 그때도 조용히 버리지 않고 `Log.w` 로
     * 드러낸다(조용한 실패 금지).
     */
    private var pendingMenuAction: (() -> Unit)? = null

    /**
     * [F4] 자동 배치 세션 때문에 실행이 **미뤄진** 메뉴 액션([pendingMenuAction] KDoc 결과 2).
     * [restoreBubbleAfterArrange] 가 꺼내 정확히 한 번 실행하고, 그전에 [onDestroy] 가 오면
     * 폐기한다. 메인 스레드에서만 접근한다([setBubbleHiddenForArrange] 는 handler.post 로 수렴).
     */
    private var deferredMenuAction: (() -> Unit)? = null

    /**
     * [F4] 자동 배치 세션 때문에 버블/메뉴 창이 걷힌 상태인가. [setBubbleHiddenForArrange] 의
     * hidden 인자를 그대로 반영하며, [removeMenuNow] 가 "지금 실행 vs 복원까지 이연" 을 이 값
     * 하나로 판정한다 — 세션 진행 여부를 액추에이터에 되묻지 않고 우리 창 상태로만 판단한다.
     */
    private var bubbleHiddenForArrange = false

    /**
     * [#30] 메뉴 열기 시퀀스가 진행 중인가. 토글 항목의 표시 상태를 **메뉴 표시 직전**
     * DataStore 스냅샷으로 읽어야 해서([showMenu] KDoc, 설계서 §2.3) 창 부착 전에 suspend 읽기가
     * 한 번 끼는데, 그 사이 롱프레스가 한 번 더 들어오면 메뉴 창이 두 개 붙는다 —
     * `menuView != null` 가드만으로는 그 창을 막지 못한다.
     * 메인 스레드([serviceScope] = `Dispatchers.Main.immediate`, 터치 리스너도 메인)에서만
     * 읽고 쓰므로 [bubblePositionUserAdjusted] 와 같은 이유로 `@Volatile` 이 불필요하다.
     */
    private var menuOpenInFlight = false

    /** window_profiles.json presets 캐시. null 이면 아직 로드 전이거나 파싱 실패(메뉴에서 프리셋 섹션 생략). */
    private var cachedPresets: List<AspectPreset>? = null
    private var presetsLoadStarted = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        instance = this
        preloadPresetsIfNeeded()
        restoreBubblePositionAsync()
    }

    /**
     * 콜드 스타트 1회 한정 위치 스냅샷 읽기(위 [cachedBubbleX]/[cachedBubbleY] KDoc 참고).
     *
     * [W7-B/P2] **비동기**다. 예전에는 코루틴 블로킹 브리지로 메인 스레드에서 동기 대기했는데, 이
     * onCreate 경로는 `BootReceiver → startForegroundService` 직후 — ANR 판정이 가장 가혹한 구간 —
     * 에 실행되고 최초 접근 시에는 `SharedPreferencesMigration`(레거시 "bubble_prefs" 이관)까지
     * 동기로 돈다. 대신 버블은 기본 위치로 먼저 뜨고, 저장 위치는 도착하는 대로 반영된다
     * ([applyCachedBubblePosition] — 뷰가 아직 없으면 캐시만 갱신하고 조용히 반환하며,
     * 나중에 [createBubbleViewIfNeeded] 가 그 캐시를 읽는다).
     *
     * [실기기 검증 리뷰 지적 반영, 2026-07-25 / W7-B 로 갱신] ProfileStore.bubblePosition() 은
     * 내부적으로 이미 읽기 실패를 잡아 null 로 폴백하지만(safeRead), 이 경로는 부팅 직후 실행돼
     * 크래시 루프로 이어질 수 있는 가장 민감한 지점이라 runCatching 으로 한 번 더 방어한다
     * (조용한 실패 금지 — 실패 시 Log.w 로 드러내고 기본 위치로 폴백). 메인 스레드 블로킹이
     * 사라져 "블로킹 대기 중 예외" 라는 최악 경우는 이제 성립하지 않지만, 방어 자체는 유지한다
     * (DataStore 파일 손상 시 부팅 크래시 루프 — [ProfileStore] KDoc 참고 — 은 여전히 유효한 위협).
     */
    private fun restoreBubblePositionAsync() {
        serviceScope.launch {
            val position = runCatching { store.bubblePosition() }
                .onFailure { e -> Log.w(TAG, "onCreate: 저장된 버블 위치 읽기 실패 — 기본 위치로 폴백", e) }
                .getOrNull() ?: return@launch
            // 경합 가드: 읽기가 도착하기 전에 사용자가 이미 버블을 옮겼다면 옛 값으로 되돌리지 않는다.
            if (bubblePositionUserAdjusted) {
                Log.i(TAG, "restoreBubblePositionAsync: 복원 전 사용자가 버블을 이동 — 저장값 적용 생략")
                return@launch
            }
            cachedBubbleX = position.first
            cachedBubbleY = position.second
            applyCachedBubblePosition()
        }
    }

    /**
     * 캐시된 버블 위치([cachedBubbleX]/[cachedBubbleY])를 실제 창에 반영한다(W7-B/P2).
     *
     * 항상 메인 스레드에서만 불린다([serviceScope] = `Dispatchers.Main.immediate`).
     * 뷰/LayoutParams 가 아직 없으면(= `onStartCommand → addBubbleIfNeeded` 이전) 캐시만 갱신된
     * 채 조용히 반환한다 — 나중에 [createBubbleViewIfNeeded] 가 같은 캐시를 읽어 반영하므로
     * 뷰 유무 양쪽에서 안전하다.
     *
     * 클램프에 `view.width`/`view.height` 를 **쓰지 않는다**: `addView` 직후에는 아직 레이아웃
     * 전이라 둘 다 0 이라서 maxX/maxY 가 화면 크기 그대로가 돼 버린다. [createBubbleViewIfNeeded]
     * 와 동일하게 `layoutParams.width`/`.height`(= sizePx) 를 기준으로 삼아야 두 경로의 클램프가
     * 어긋나지 않는다. 화면 크기는 매번 재조회한다(좌표 하드코딩 금지, CLAUDE.md 함정 #2).
     */
    private fun applyCachedBubblePosition() {
        // [W7-C] 스냅 애니메이션 진행 중에는 좌표를 덮어쓰지 않는다 — 애니메이터가 매 프레임
        // params.x 를 다시 쓰므로 여기서 끼어들어도 무의미하고, onAnimationEnd 가 그 사이에
        // 섞인 y 좌표를 저장해 버린다.
        if (snapAnimator?.isRunning == true) return
        val view = bubbleView ?: return
        val params = layoutParams ?: return
        val bounds = windowManager.currentWindowMetrics.bounds
        val maxX = (bounds.width() - params.width).coerceAtLeast(0)
        val maxY = (bounds.height() - params.height).coerceAtLeast(0)
        params.x = (cachedBubbleX ?: params.x).coerceIn(0, maxX)
        params.y = (cachedBubbleY ?: params.y).coerceIn(0, maxY)
        if (!bubbleAttached) return
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { e -> Log.w(TAG, "applyCachedBubblePosition: 버블 위치 반영 실패", e) }
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
        // [D1] 커밋된 메뉴 액션을 폐기해도 되는 **유일한** 지점. 서비스가 죽는 중이므로 실행할 수
        // 없고(액추에이터 트리거·토스트·Activity 시작 전부 이 서비스의 스코프에 얹혀 있다),
        // 조용히 버리지도 않는다 — 무엇을 버렸는지 로그로 남긴다.
        pendingMenuAction?.let {
            Log.w(TAG, "onDestroy: 커밋됐지만 아직 실행되지 않은 메뉴 액션이 남아 있음 — 서비스 종료로 폐기")
            pendingMenuAction = null
        }
        // [F4] 배치 세션 복원까지 미뤄 둔 액션(결과 2)도 같은 이유로 여기서만 폐기된다.
        deferredMenuAction?.let {
            Log.w(TAG, "onDestroy: 배치 세션 복원까지 이연돼 있던 메뉴 액션이 남아 있음 — 서비스 종료로 폐기")
            deferredMenuAction = null
        }
        // 애니메이션 콜백이 서비스가 죽은 뒤에 돌아 창을 남기는 일이 없도록, 두 함수 모두
        // 진행 중인 애니메이션을 취소하고 창을 **동기적으로**(removeViewImmediate) 제거한다.
        removeMenuNow()
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
                // [F4] 창을 헐기 **전에** 세운다 — [removeMenuNow] 가 이 값을 보고 커밋된 액션을
                // 지금 실행할지(평상시) 복원까지 미룰지(세션 중) 판정한다.
                bubbleHiddenForArrange = true
                // 함정 #22: 배치 트리거가 어떤 경로로 오든(메뉴 클릭 외 adb 등) 메뉴 창도 반드시 제거.
                // 여기서는 퇴장 애니메이션을 기다리지 않고 즉시 제거한다 — 세션이 이미 시작된
                // 뒤라 MENU_EXIT_ANIM_MS 동안이라도 오버레이가 남으면 그대로 함정 #22 다.
                //
                // [D1] **순서 주의**: 버블을 먼저 뗀다. 액션이 실제로 도는 순간 우리 창이 하나도
                // 남아 있으면 안 되기 때문이다(hasAttachedOverlayWindow() == false 여야 한다).
                // [F4] 이 경로에서 [removeMenuNow] 는 커밋된 액션을 실행하지 않고 복원까지
                // 이연하지만, 순서 자체는 그대로 둔다 — 이연 실행도 결국 창이 없는 상태에서
                // 도는 것이 계약이고([restoreBubbleAfterArrange]), 세션 중 오버레이 잔존은
                // 액션 유무와 무관하게 함정 #22 다.
                detachBubbleView()
                removeMenuNow()
                val runnable = Runnable {
                    Log.w(
                        TAG,
                        "setBubbleHiddenForArrange: 안전 타이머 발동 — " +
                            "${HIDE_SAFETY_TIMEOUT_MS}ms 안에 복원 신호 없음. 자동 재표시",
                    )
                    hideSafetyRunnable = null
                    restoreBubbleAfterArrange()
                }
                hideSafetyRunnable = runnable
                handler.postDelayed(runnable, HIDE_SAFETY_TIMEOUT_MS)
            } else {
                restoreBubbleAfterArrange()
            }
        }
    }

    /**
     * [F4] 숨김 해제 공통 경로. 정상 복원 신호(hidden=false)와 안전 타이머 발동이 모두 여기로
     * 수렴하므로, 이연된 메뉴 액션이 어느 쪽 경로에서도 잊히지 않는다.
     *
     * **순서**: 액션을 버블 재부착 **앞에서** 실행한다. D1 의 순서 계약("액션이 도는 순간 우리
     * 오버레이 창이 하나도 없어야 한다", 함정 #22)을 이연 경로에서도 그대로 지키기 위함이다.
     * 액션이 새 세션을 시작하면 그 세션의 `setBubbleHiddenForArrange(true)` 가 뒤이어 posted
     * 되므로, 방금 붙인 버블은 진입 애니메이션(알파 0 → 1) 첫 프레임 전에 다시 걷힌다.
     */
    private fun restoreBubbleAfterArrange() {
        bubbleHiddenForArrange = false
        deferredMenuAction?.let { action ->
            // 필드를 먼저 비운다 — 액션이 재진입해도 이중 실행이 구조적으로 불가능하다(D1 관례).
            deferredMenuAction = null
            Log.i(TAG, "restoreBubbleAfterArrange: 배치 세션 중 커밋됐던 메뉴 액션을 지금 실행(이연 실행)")
            action.invoke()
        }
        showBubbleIfPermitted()
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

    // [S1 부작용, 검증된 lint 오탐] `ForegroundServiceType` 검사기가 debug 변형(main+debug 매니페스트
    // 병합)에서 이 서비스가 아닌 다른 매니페스트 파일의 <service> 선언(예: probe.ProbeAccessibilityService
    // — foregroundServiceType 이 없는 게 정상인 AccessibilityService)을 이 startForeground() 호출과
    // 잘못 연결해 "manifest 에 foregroundServiceType 없음" 오류를 낸다. 실제로는 main/AndroidManifest.xml
    // 의 FloatingLauncherService 선언에 foregroundServiceType="specialUse" 가 있고, 병합된 매니페스트
    // (app/build/intermediates/merged_manifests/debug/.../AndroidManifest.xml) 에서도 확인됨 — lintRelease
    // (매니페스트 1개, debug 소스셋 없음)는 이 오류 없이 통과한다. ProbeAccessibilityService 를 다시
    // main 으로 되돌리면 S1 자체가 무효화되므로(릴리스에서 소멸해야 함), 여기서 억제한다.
    @SuppressLint("ForegroundServiceType")
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

        val density = resources.displayMetrics.density
        // [D4/D5] 창 = 버블 원 그 자체다. 종전에는 굽힌 드롭 섀도를 위해 사방 6dp 를 더해 창을
        // 68dp 로 키웠는데, 그 그림자가 보이는 곳에서 알파 ≈5% 라 지각 하한을 밑돌면서 정작
        // 반투명 원 밑은 탁하게 만들었다(레이어 구성상 둘을 분리할 수 없다). 그림자를 걷어내니
        // 창이 다시 원과 같은 크기가 되고, 스냅 타깃(0 / screenWidth-width)이 곧 **원이 화면
        // 가장자리에 딱 붙는 위치**가 된다 — D5 가 요구하던 pad 보정이 애초에 필요 없어졌다.
        val sizePx = (BUBBLE_SIZE_DP * density).toInt()
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
            // 그림자 여백이 사라져도 아이콘의 시각적 크기는 종전과 동일하다:
            // 옛 68dp 창 - 2×(6+14)dp = 28dp, 새 56dp 창 - 2×14dp = 28dp.
            val pad = (BUBBLE_ICON_PADDING_DP * density).toInt()
            setPadding(pad, pad, pad, pad)
            // lint ClickableViewAccessibility 대응: 탭 실행은 performClick() 경로로 통일한다 —
            // 스크린리더의 ACTION_CLICK(예: TalkBack "두 번 탭하여 활성화")도 이 리스너를 거쳐
            // onBubbleTap() 을 실행하게 하기 위함이다. 실제 탭 판정(BubbleTouchListener)은
            // 여전히 터치 이벤트로 하되, 확정된 탭은 view.performClick() 을 호출해 여기로 수렴한다.
            setOnClickListener { onBubbleTap() }

            // [F1] 스크린리더용 **명시적** 메뉴 도달 경로. 확장 메뉴는 원래 [BubbleTouchListener]
            // 의 자체 롱프레스 검출(ACTION_DOWN 후 longPressTimeoutMs 유지)로만 열렸고, 접근성
            // 서비스는 그 터치 시퀀스를 합성하지 못하고 ACTION_LONG_CLICK 을 디스패치할 뿐이라
            // "스크린리더에서는 도달 불가"로 판단해 이 리스너를 넣었다.
            //
            // ⚠ [정정 2026-08-01, 24차 실측] **그 전제는 성립하지 않았다.** 실기기에서
            // TalkBack 활성 시: (a) 버블 단일 탭은 TalkBack 이 정상 낭독하고 앱에는 터치가
            // 도달하지 않는다(= 이 오버레이는 터치 탐색 **대상 안**이다). (b) 그런데도 메뉴가
            // 열렸고 로그는 `bubble long-press`(터치 경로)였다 — `bubble long-click` 은 한 번도
            // 관측되지 않았다. 평범한 터치가 앱에 닿지 않는데 터치 롱프레스가 발화했다는 것은
            // TalkBack 이 어떤 제스처에서 터치를 **통과(pass-through)** 시킨다는 뜻이다(두 번 탭
            // 후 유지가 유력하나 제스처 종류는 미확정). 즉 **메뉴는 이 리스너 이전에도 도달
            // 가능했다.**
            //
            // 그래도 이 리스너는 남긴다: 몇 줄이고, 통과 동작은 TalkBack 구현 세부라 보장이
            // 아니며, 라벨이 붙은 ACTION_LONG_CLICK 은 읽기 메뉴에서도 노출되는 **명시적** 계약이기
            // 때문이다. 다만 "유일한 경로"가 아니라 "명시적 경로"다.
            //
            // 별건(더 큰 제약): **TalkBack 이 켜져 있으면 앱이 분할을 생성하지 못한다**
            // (`SplitEntry` step2 의 Recents 카드 드래그가 3/3 실패, 플랫폼 제약 추정). 그래서 v1 은
            // 스크린리더 지원을 범위 밖으로 둔다(TASK.md 「범위 밖」). 상세 = DEVICE_FACTS 24차.
            //
            // 터치 경로와 **이중 발화하지 않는다**(코드로 확인): 프레임워크의 롱클릭 검출
            // (`View.onTouchEvent` → `checkForLongClick`)은 터치 리스너가 이벤트를 소비하지
            // 않았을 때만 예약되는데, [BubbleTouchListener.onTouch] 는 ACTION_DOWN 에서 **항상**
            // true 를 돌려준다(다른 액션도 DOWN 이 소비된 이상 이 뷰로만 온다). 따라서 이
            // 리스너는 접근성 ACTION_LONG_CLICK 으로만 불리고 터치 동작은 종전과 동일하다.
            // `setOnLongClickListener` 는 내부적으로 `isLongClickable = true` 를 세운다.
            setOnLongClickListener {
                Log.i(TAG, "bubble long-click(접근성 경로) — 확장 메뉴 열기")
                showMenu()
                true
            }
            // [F1] TalkBack 이 "길게 누르기: 메뉴 열기" 로 읽도록 액션에 라벨을 붙인다.
            // 같은 id 로 다시 넣으면 기본 라벨을 대체한다(토글 행 D12 와 동일한 관례).
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.addAction(
                        AccessibilityNodeInfo.AccessibilityAction(
                            AccessibilityNodeInfo.ACTION_LONG_CLICK,
                            getString(R.string.bubble_menu_long_click_label),
                        ),
                    )
                }
            }
        }
        view.setOnTouchListener(BubbleTouchListener(view, params))

        bubbleView = view
        layoutParams = params
    }

    /**
     * 이미 생성된 [bubbleView]/[layoutParams] 를 WindowManager 에 얹는다. 중복 attach 는 무시한다.
     *
     * [D3] 부착은 **연출을 갖는다**: 배치 세션이 끝나 버블이 돌아오는 순간은 이 앱에서 가장
     * 통행량이 많은 지점인데 종전에는 아무 전이 없이 툭 튀어나온 뒤 3초 후에야 페이드가 돌았다
     * (연출 순서가 거꾸로였다). 창은 [windowManager] 에 이미 붙은 뒤에 알파/배율만 만지므로
     * [hasAttachedOverlayWindow] 계약(#20)에는 영향이 없다 — 창 자체는 부착 즉시 존재한다.
     * 숨김 쪽은 여전히 **즉시**다([setBubbleHiddenForArrange]): 세션이 시작됐으니 지금 사라져야 한다.
     */
    private fun attachBubbleView() {
        if (bubbleAttached) return
        val view = bubbleView ?: return
        val params = layoutParams ?: return
        runCatching { windowManager.addView(view, params) }
            .onFailure { e -> Log.e(TAG, "버블 뷰 attach 실패", e) }
            .onSuccess {
                bubbleAttached = true
                // 배치 세션 동안 detach 돼 있던 사이에 남은 유휴 페이드/눌림 상태를 물고 오지 않게 한다.
                view.animate().cancel()
                view.alpha = 0f
                view.scaleX = BUBBLE_RESTORE_START_SCALE
                view.scaleY = BUBBLE_RESTORE_START_SCALE
                view.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    // 표현용 지속시간(presentation duration). 어떤 상태 전이도 여기 걸려 있지 않다(ADR-2 무관).
                    .setDuration(BUBBLE_RESTORE_ANIM_MS)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()
                scheduleIdleFade()
            }
    }

    /**
     * 뷰 객체는 유지한 채 WindowManager 에서만 창을 뗀다. 중복 detach 는 무시한다.
     *
     * [D20] `removeViewImmediate` 를 쓴다. `removeView` 는 `MSG_DIE` 를 포스트만 하므로, 그 뒤에
     * [bubbleAttached] 를 false 로 내리면 **창이 아직 떠 있는데 [hasAttachedOverlayWindow] 가
     * false 를 답하는 창**(false negative)이 한 프레임 열린다 — 함정 #22 가드가 그 틈에 제스처
     * 탭을 허용하면 우리 버블이 그 탭을 가로챈다. immediate 는 동기적으로 창을 헐고 돌아오므로
     * 반환 직후의 플래그 하강이 실제 상태와 일치한다.
     */
    private fun detachBubbleView() {
        if (!bubbleAttached) return
        val view = bubbleView ?: return
        // 창을 떼기 전에 진행 중인 표현용 애니메이션을 끊는다 — 떼어진 뒤 콜백이 도는 것을 막는다.
        cancelIdleFade()
        view.animate().cancel()
        runCatching { windowManager.removeViewImmediate(view) }
            .onFailure { e -> Log.w(TAG, "버블 뷰 detach 실패", e) }
        bubbleAttached = false
    }

    private fun removeBubble() {
        snapAnimator?.cancel()
        snapAnimator = null
        bubbleView?.animate()?.cancel()
        // 유휴 페이드/롱프레스 등 이 서비스가 건 모든 지연 작업을 제거한다.
        handler.removeCallbacksAndMessages(null)
        hideSafetyRunnable = null
        detachBubbleView()
        bubbleView = null
        layoutParams = null
    }

    // ══════════════════════════════════════════════════════════
    // P5 폴리시: 버블 눌림 피드백 + 유휴 페이드
    //
    // 여기 쓰인 지속시간/지연은 전부 **표현용(presentation)** 이다 — 어떤 상태 전이도 이 시간에
    // 걸려 있지 않다(ADR-2 는 "상태 전이를 고정 지연으로 기다리는 것" 을 금지하며, 애니메이션
    // 길이와 유휴 타이머는 그 대상이 아니다).
    // ══════════════════════════════════════════════════════════

    /**
     * 유휴 페이드. 몇 초 동안 손대지 않으면 버블을 반투명으로 낮춰 아래 영상과 경쟁하지 않게 한다.
     * **알파만 바꾼다** — 창 크기/위치는 그대로라 탭 영역은 변하지 않는다.
     *
     * [D16] 뷰 전체 알파를 낮춘다(드로어블을 레이어로 쪼개 채움만 흐리는 쪽은 택하지 않았다).
     * 근거: 레이어 분리는 `LayerDrawable.findDrawableByLayerId` + 레이어별 알파 애니메이션이
     * 필요해 코드가 세 배가 되는데, 얻는 것은 "링만 또렷" 뿐이다. 대신 바닥값을
     * [BUBBLE_IDLE_ALPHA] 로 올려 어두운 영상 위에서도 링과 원이 함께 읽히게 했다 — 더 단순한
     * 쪽이 검은 화면 위에서도 보이면 그쪽이 맞다.
     */
    private val idleFadeRunnable = Runnable {
        bubbleView?.takeIf { bubbleAttached }?.animate()
            ?.alpha(BUBBLE_IDLE_ALPHA)
            ?.setDuration(BUBBLE_IDLE_FADE_MS)
            // [D16] 퇴장 모션이므로 가속(accelerate) 이징이다 — 감속은 "들어오는" 것의 문법이다.
            ?.setInterpolator(AccelerateInterpolator())
            ?.start()
    }

    private fun cancelIdleFade() {
        handler.removeCallbacks(idleFadeRunnable)
    }

    /**
     * 유휴 페이드 타이머 재예약. **가드가 여기 한곳에 모여 있다**(호출부가 아니라).
     *
     * [D15] 메뉴가 열려 있는 동안에는 예약하지 않는다. 종전에는 롱프레스로 메뉴를 연 직후
     * `ACTION_UP` 이 무조건 재예약해서, 메뉴를 보고 있는 내내 그 밑의 버블이 흐려졌다(그리고
     * 메뉴를 닫아도 흐린 채로 남았다). 메뉴가 닫히면 [removeMenuNow] 가 다시 건다.
     *
     * [F3] 가드에 [menuOpenInFlight] 를 함께 본다. 메뉴 열기 시퀀스는 창을 얹기 전에 DataStore
     * 읽기가 한 번 끼므로([showMenu]), 그 사이 손을 떼면 `ACTION_UP` 이 `menuView == null` 을
     * 보고 유휴 페이드를 **무장**해 버렸다 — 잠시 뒤 스크림이 올라온 상태에서 타이머가 터져
     * 메뉴 밑의 버블이 흐려진다(D15 가 없앴다고 믿은 바로 그 증상이 비동기 창으로 되살아난 것).
     */
    private fun scheduleIdleFade() {
        handler.removeCallbacks(idleFadeRunnable)
        if (!bubbleAttached || menuView != null || menuOpenInFlight) return
        handler.postDelayed(idleFadeRunnable, BUBBLE_IDLE_DELAY_MS)
    }

    /**
     * 눌림 피드백. 누르면 살짝 줄고, 떼면 되돌아온다.
     * 터치 판정(탭/드래그/롱프레스 임계값)에는 전혀 관여하지 않는다 — 순수 시각 효과이고
     * 뷰의 변환(scale)만 바꾸므로 히트테스트 영역인 창 크기는 그대로다.
     *
     * @param overshoot [D21] 오버슈트(튕김)는 **탭 해제의 스프링백에만** 쓴다. 드래그를 끝낼
     *   때는 곧바로 [snapToEdge] 의 가로 이동이 이어지므로, 여기서까지 튕기면 "튕김 + 미끄러짐"
     *   이 겹쳐 손을 뗀 것뿐인데 과한 반응이 된다(종전에는 여기에 착지 햅틱까지 붙어 셋이었다).
     */
    private fun setBubblePressed(pressed: Boolean, overshoot: Boolean = false) {
        val view = bubbleView ?: return
        view.animate().cancel()
        val scale = if (pressed) BUBBLE_PRESS_SCALE else 1f
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .alpha(1f)
            .setDuration(if (pressed) BUBBLE_PRESS_ANIM_MS else BUBBLE_RELEASE_ANIM_MS)
            .setInterpolator(if (!pressed && overshoot) OvershootInterpolator(1.6f) else DecelerateInterpolator())
            .start()
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
            // [D1 스윕] 열 수 없는 조건을 **햅틱보다 먼저** 판정한다. 롱프레스 햅틱은 "메뉴를
            // 열어 주겠다"는 약속이라, 울린 뒤에 조용히 아무 일도 안 일어나면 그 약속이 깨진다.
            //  · menuOpenInFlight — 열기 시퀀스가 DataStore 읽기에서 대기 중인 재롱프레스
            //  · bubbleAttached == false — 배치 세션이 시작돼 이미 창이 헐린 상태
            //    (detach 는 이 runnable 을 취소하지 않으므로 여기까지 도달할 수 있다)
            if (menuView != null || menuOpenInFlight || !bubbleAttached) {
                Log.i(
                    TAG,
                    "bubble long-press 무시 — menuOpen=${menuView != null} " +
                        "inFlight=$menuOpenInFlight attached=$bubbleAttached",
                )
                return@Runnable
            }
            Log.i(TAG, "bubble long-press — 확장 메뉴 열기")
            // 롱프레스가 인식된 그 순간을 손끝으로 알린다(표준 롱프레스 햅틱).
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
                    // [P5 폴리시] 터치 즉시 반응한다: 유휴 페이드를 취소하고 불투명으로 되돌리며
                    // 살짝 눌린다. 아래 제스처 판정(임계값/타임아웃)에는 관여하지 않는다.
                    cancelIdleFade()
                    setBubblePressed(true)
                    handler.postDelayed(longPressRunnable, longPressTimeoutMs)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                        dragging = true
                        // [W7-C] 드래그가 확정되는 = params.x/y 를 실제로 바꾸기 시작하는 이 지점이
                        // "사용자가 위치를 손댄 순간" 이다. 종전엔 스냅 애니메이션이 끝난 뒤
                        // savePosition() 에서야 잠갔던 탓에, 그 사이 도착한 비동기 복원이 손가락
                        // 밑의 버블을 저장 위치로 되돌렸다([bubblePositionUserAdjusted] KDoc 참고).
                        // 판정 조건·임계값은 기존 그대로이며 추가된 것은 이 대입 한 줄뿐이다.
                        bubblePositionUserAdjusted = true
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
                    // 눌림 상태는 어떤 분기로 끝나든 반드시 해제한다 — 접근성 서비스 OFF 라
                    // onBubbleTap() 이 토스트+온보딩으로 빠지는 경로에서도 마찬가지다.
                    // [D21] 드래그 종료에는 오버슈트를 쓰지 않는다(바로 이어질 스냅 이동과 겹친다).
                    setBubblePressed(false, overshoot = !dragging)
                    when {
                        // 이미 롱프레스 콜백에서 처리됨. [D15] 메뉴가 열렸다면 scheduleIdleFade()
                        // 자체가 no-op 이고(가드가 그 안에 있다), 메뉴가 닫힐 때 다시 걸린다.
                        // 메뉴 열기에 실패한 경우에만 여기서 실제로 예약된다.
                        longPressFired -> scheduleIdleFade()
                        // 스냅 애니메이션 종료 콜백이 유휴 타이머를 다시 건다.
                        dragging -> snapToEdge(view, params)
                        // lint ClickableViewAccessibility 대응: 탭 확정 시 performClick() 을 거친다
                        // (view 의 OnClickListener 가 onBubbleTap() 을 호출). 탭/드래그/롱프레스
                        // 판정 임계값·타이밍은 변경 없음 — 실행 경로만 표준 클릭 경로로 통일했다.
                        else -> {
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            view.performClick()
                            scheduleIdleFade()
                        }
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
            // [P5 폴리시] 표현용 지속시간/이징. 가장자리에 "탁" 멈추지 않고 감속하며 안착한다.
            // 오버슈트는 쓰지 않는다 — 창 x 가 화면 밖으로 나갔다 돌아오면 WM 클램프에 걸려
            // 오히려 튀어 보인다.
            duration = SNAP_ANIM_DURATION_MS
            interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { anim ->
                params.x = anim.animatedValue as Int
                runCatching { windowManager.updateViewLayout(view, params) }
            }
            addListener(object : AnimatorListenerAdapter() {
                /**
                 * [D2] `ValueAnimator.cancel()` 은 `onAnimationCancel` **에 이어 `onAnimationEnd`
                 * 까지** 부른다(플랫폼 계약). 종전에는 그 구분이 없어서, 취소된 스냅이 화면
                 * 한복판의 **중간 좌표**를 "사용자가 확정한 위치"로 저장해 버렸다 — 다음 부팅에
                 * 버블이 엉뚱한 데서 뜬다. 취소 경로는 저장도 재예약도 하지 않는다.
                 */
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) return
                    savePosition(params.x, params.y)
                    // [D21] 착지 햅틱은 삭제했다 — 손을 뗀 것 하나에 튕김·미끄러짐·진동이
                    // 연달아 붙어 과했다. 탭/롱프레스 햅틱은 그대로 유지된다.
                    scheduleIdleFade()
                }
            })
            start()
        }
    }

    private fun onBubbleTap() {
        // 접근성 서비스가 없을 때의 토스트+온보딩 분기는 메뉴 항목들과 완전히 동일하다 —
        // 두 벌로 유지하다 문자열이 갈라졌던 것을 한곳으로 모은다([requireArrangerOrGuide]).
        val service = requireArrangerOrGuide() ?: return
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
     *
     * [#30] 전체화면 자동 배치 토글 항목의 표시 상태는 **메뉴 표시 직전** DataStore 스냅샷이어야
     * 한다(설계서 §2.3) — 캐시를 들고 있으면 다른 경로(온보딩 재설치, 값 초기화)로 바뀐 값을
     * 스테일하게 보여주고, 사용자가 그걸 탭하면 의도와 반대로 토글된다. 그래서 이 함수는 읽기만
     * 담당하고 실제 창 부착은 [attachMenu] 로 분리했다.
     */
    private fun showMenu() {
        if (menuView != null || menuOpenInFlight) return
        menuOpenInFlight = true
        // [F3] 이미 무장돼 있던 유휴 페이드를 여기서 끊는다. 터치 경로는 ACTION_DOWN 에서 이미
        // 취소하지만, 접근성 롱클릭 경로(F1)는 터치 리스너를 거치지 않아 타이머가 살아 있다 —
        // 그대로 두면 메뉴가 열린 뒤 스크림 밑에서 버블이 흐려진다. 가드를 열기 시퀀스의
        // **입구 한곳**에 두어 두 경로 모두를 덮는다.
        cancelIdleFade()
        serviceScope.launch {
            try {
                attachMenu(fullscreenAutoEnabled = store.isFullscreenAutoEnabled())
            } finally {
                menuOpenInFlight = false
            }
        }
    }

    /**
     * [showMenu] 의 실제 창 구성/부착부. 스냅샷 읽기가 끝난 뒤 메인 스레드에서 호출된다.
     *
     * [bubbleAttached] 를 다시 확인하는 이유(#30 으로 새로 생긴 창): [showMenu] 가 비동기가 되면서
     * "롱프레스 → (DataStore 읽기) → 그 사이 배치 세션이 시작돼 [setBubbleHiddenForArrange] 가
     * 버블/메뉴를 제거 → 뒤늦게 메뉴 부착" 순서가 가능해졌다. 세션 중 오버레이 창이 뜨면 함정 #22
     * (피커發 파트너 창이 전체화면으로 낙착 → ENTRY_STEP_FAILED)를 그대로 밟는다.
     * 종전 동기 구현에는 없던 실패 모드이므로 여기서 닫는다.
     */
    private fun attachMenu(fullscreenAutoEnabled: Boolean) {
        if (menuView != null) return
        if (!bubbleAttached) {
            // [D1 스윕] 여기까지 온 롱프레스는 이미 햅틱이 울린 뒤다. 그런데 이 지점에서
            // 토스트로 알리지는 **않는다**: 토스트도 우리 프로세스가 WindowManager 에 얹는
            // TYPE_TOAST 창이라, 방금 시작된 배치 세션 위에 띄우면 함정 #22(런칭 패키지의 가시
            // 오버레이 창 → 피커發 파트너 창이 전체화면으로 낙착)를 그대로 되살릴 수 있다.
            // 대신 (a) [BubbleTouchListener.longPressRunnable] 이 햅틱 **앞에서** 같은 조건을
            // 먼저 걸러 이 경로가 사실상 도달 불가능해졌고(남은 창은 "햅틱 ~ DataStore 읽기
            // 완료" 사이 수 ms), (b) 그 잔여 경합이 실제로 일어나면 화면은 이미 눈에 띄게
            // 재배치 중이라 사용자에게 무반응으로 보이지 않는다. 사유는 Log.w 로 남긴다.
            Log.w(TAG, "attachMenu: 버블이 이미 분리됨(배치 세션 진행 등) — 메뉴 부착 생략(롱프레스 1회 소실)")
            return
        }
        val bubble = bubbleView ?: return
        val bubbleParams = layoutParams ?: return

        val content = buildMenuContent(fullscreenAutoEnabled)
        // WindowManager 에 얹기 전 예상 크기를 재서 클램프 계산에 쓴다. 실제 최종 렌더 크기와
        // 완전히 같지 않을 수 있지만, 화면 밖으로 크게 벗어나지 않게 하는 용도로는 충분하다.
        content.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val menuWidth = content.measuredWidth
        val menuHeight = content.measuredHeight

        val density = resources.displayMetrics.density
        val bounds = windowManager.currentWindowMetrics.bounds
        val bubbleSize = bubble.width.takeIf { it > 0 }
            ?: (BUBBLE_SIZE_DP * density).toInt()

        // 버블이 화면 좌/우 어느 쪽에 가까운지에 따라 메뉴 정렬을 바꿔 화면 밖으로 나가는 경우를 줄인다.
        val screenCenterX = bounds.width() / 2
        val bubbleCenterX = bubbleParams.x + bubbleSize / 2
        val anchoredLeft = bubbleCenterX < screenCenterX
        var menuX = if (anchoredLeft) bubbleParams.x else bubbleParams.x + bubbleSize - menuWidth
        var menuY = bubbleParams.y + bubbleSize
        var anchoredBelow = true
        if (menuY + menuHeight > bounds.height()) {
            menuY = bubbleParams.y - menuHeight // 아래쪽 공간이 부족하면 버블 위로 띄운다
            anchoredBelow = false
        }
        // [D10] 가장자리에 딱 붙이지 않는다. 버블은 좌/우 끝으로 스냅하므로 종전 클램프는
        // menuX 를 거의 항상 0 또는 (width-menuWidth) 로 밀어붙였고, 그러면 창 경계 밖으로는
        // 그려지지 않는 elevation 그림자의 한쪽이 통째로 잘려 메뉴가 화면에 "박힌" 것처럼 보였다.
        val marginPx = (MENU_SCREEN_MARGIN_DP * density).toInt()
        menuX = clampWithMargin(menuX, menuWidth, bounds.width(), marginPx)
        menuY = clampWithMargin(menuY, menuHeight, bounds.height(), marginPx)

        // 스크림(루트) 자체를 탭하면 메뉴를 닫는다. 메뉴 항목(행 LinearLayout, isClickable=true)은
        // 자기 터치를 먼저 소비하므로 이 클릭 리스너까지 전파되지 않는다.
        val scrim = FrameLayout(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true
            // [D11] 스크림은 화면 전체를 덮는 클릭 가능 뷰라 TalkBack 순회의 **첫 노드**가 된다.
            // 라벨이 없으면 "레이블 없음, 두 번 탭하여 활성화" 로 읽혀 무엇인지 알 길이 없다.
            contentDescription = getString(R.string.bubble_menu_scrim_cd)
            setOnClickListener { dismissMenu() }
        }

        // [P5 폴리시] 딤 레이어. 스크림 **루트**에 색/알파를 주지 않고 별도 자식 뷰로 두는 이유:
        // 루트 alpha 를 애니메이션하면 자식인 메뉴 내용까지 곱해져 두 애니메이션이 서로 간섭한다.
        // 클릭은 받지 않으므로(isClickable 기본 false) 바깥 탭은 그대로 스크림에 떨어진다.
        val dim = View(this).apply {
            setBackgroundColor(MENU_SCRIM_COLOR)
            alpha = 0f
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        scrim.addView(
            dim,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
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

        // 메뉴 표면의 그림자. 내용 뷰는 스크림(풀스크린)의 자식이라 부모가 그림자를 그릴 공간이
        // 충분하다 — 루트 뷰인 버블과 달리 elevation 을 그대로 쓸 수 있다.
        content.elevation = MENU_ELEVATION_DP * density
        // [D10] 메뉴 표면은 90% 불투명(menu_background #E6…)이라 순수 검정 그림자가 표면을
        // 통해 비쳐 안쪽까지 탁해진다. 그림자 색을 낮춰 번짐만 덜어낸다(API 28+, minSdk 30).
        content.outlineAmbientShadowColor = MENU_SHADOW_COLOR
        content.outlineSpotShadowColor = MENU_SHADOW_COLOR
        // [D11] 메뉴가 뜬 사실 자체를 알린다. accessibilityPaneTitle 은 창 부착 시
        // TYPE_WINDOW_STATE_CHANGED 를 발생시켜 스크린리더가 제목을 읽게 한다(API 28+).
        content.accessibilityPaneTitle = getString(R.string.bubble_menu_pane_title)
        // 확대/축소 기준점을 버블에 가장 가까운 모서리로 둔다 — 메뉴가 버블에서 "자라나는" 것처럼
        // 보이게 하는 값이며, 위에서 이미 계산한 정렬 방향을 그대로 재사용한다.
        content.pivotX = if (anchoredLeft) 0f else menuWidth.toFloat()
        content.pivotY = if (anchoredBelow) 0f else menuHeight.toFloat()
        content.scaleX = MENU_ENTER_START_SCALE
        content.scaleY = MENU_ENTER_START_SCALE
        content.alpha = 0f

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
            .onSuccess {
                menuAttached = true
                menuView = scrim
                menuContent = content
                menuDim = dim
                menuDismissing = false
                // 진입 모션. 지속시간은 표현용이며 어떤 상태 전이도 여기 걸려 있지 않다(ADR-2 무관).
                dim.animate().alpha(1f).setDuration(MENU_ENTER_ANIM_MS).start()
                content.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(MENU_ENTER_ANIM_MS)
                    .setInterpolator(DecelerateInterpolator(1.6f))
                    .start()
                // [D11] 포커스를 코드로 강제하지 않는다 — 위에서 설정한 accessibilityPaneTitle 이
                // 창 부착 시 TYPE_WINDOW_STATE_CHANGED 를 발생시켜 스크린리더가 이미 메뉴 등장을
                // 알린다. 그 이후 포커스 이동은 TalkBack 자체가 관리하는 영역이라, 여기서
                // performAccessibilityAction(ACTION_ACCESSIBILITY_FOCUS) 로 가로채면 lint
                // AccessibilityFocus 규칙("Do not force accessibility focus")이 지적하는
                // 안티패턴이 된다.
            }
            .onFailure { e -> Log.e(TAG, "메뉴 뷰 attach 실패", e) }
    }

    /**
     * [D10] 화면 가장자리에 [margin] 만큼 여백을 남기며 클램프한다.
     *
     * 화면이 좁아 여백조차 확보할 수 없으면(작은 커버 디스플레이 등) 가운데 정렬로 폴백한다 —
     * 그 경우 클램프 하한이 상한보다 커져 `coerceIn` 이 `IllegalArgumentException` 을 던진다.
     */
    private fun clampWithMargin(value: Int, size: Int, extent: Int, margin: Int): Int {
        val max = extent - size - margin
        return if (max < margin) (extent - size) / 2 else value.coerceIn(margin, max)
    }

    /**
     * 메뉴 오버레이 창(풀스크린 스크림 루트)을 **퇴장 애니메이션과 함께** 닫는다. 이미 닫혀
     * 있으면 [after] 만 즉시 실행한다(멱등).
     *
     * ⚠ **순서 계약(CLAUDE.md 함정 #22)**: [after] 는 메뉴 창이 **실제로 헐린 뒤에만** 불린다.
     * 이 함수는 [after] 를 [pendingMenuAction] 에 **커밋**만 하고, 실행은 창을
     * `removeViewImmediate` 로 헐어 낸 [removeMenuNow] 가 책임진다(D1/D20). 애니메이션 *시작*
     * 시점에 배치/해제를 트리거하면 스크림이 아직 화면에 남은 채로 세션이 돌아, 피커發 파트너
     * 창이 분할 페인이 아니라 전체화면으로 낙착해 세션이 실패한다(실측).
     *
     * **[D1] 왜 커밋과 실행을 분리했나**: 종전에는 [after] 가 퇴장 애니메이터의 `withEndAction`
     * 안에만 있었다. [removeMenuNow] 가 그 애니메이터를 취소하면 end-action 은 실행되지 않으므로
     * ([pendingMenuAction] KDoc 참고), 130ms 퇴장 창 안에 자동 배치 세션이 끼어들면 사용자가 이미
     * 확정한 탭이 조용히 사라졌다. 이제는 두 경로 중 **먼저 도착한 쪽**이 반드시 실행한다.
     *
     * **[D17] 커밋 즉시 모든 행의 클릭을 끈다.** 퇴장 130ms 동안 행은 줄어드는 표면 위에서
     * 여전히 눌렸고, 그렇게 들어온 탭은 아래 `menuDismissing` 가드에서 Log.w 한 줄로 버려졌다
     * (사용자에겐 "눌렀는데 아무 일도 없음"). 이제 그 탭은 아무것도 받지 않는 스크림으로
     * 떨어져 조용한 no-op 이 된다 — **아무 약속도 하지 않은** no-op 이라는 점이 다르다.
     *
     * 같은 계약의 뒷면: 퇴장 애니메이션이 도는 동안 [menuView] 는 non-null 로 남는다. 창이 아직
     * 붙어 터치를 가로채는 구간이므로 [hasAttachedOverlayWindow] 도 그 동안 true 여야 한다(#20).
     *
     * **멱등/재진입 안전**: 이미 퇴장 중이면 두 번째 제거를 하지 않는다. D17 이후 행에서 두 번째
     * 액션이 들어올 경로는 없지만, 방어적으로 남기고 Log.w 로 드러낸다(조용한 실패 금지).
     */
    private fun dismissMenu(after: (() -> Unit)? = null) {
        if (menuView == null) {
            after?.invoke()
            return
        }
        if (menuDismissing) {
            if (after != null) Log.w(TAG, "dismissMenu: 퇴장 애니메이션 진행 중 추가 액션 요청 — 무시")
            return
        }
        menuDismissing = true
        // [D1] 액션이 확정되는 지점. 이제부터 이 액션은 창이 헐릴 때 반드시 실행된다.
        pendingMenuAction = after

        val content = menuContent
        // [D17] 확정 즉시 모든 행을 클릭 불가로 만든다. 지금부터의 탭은 스크림으로 떨어진다.
        content?.let { for (i in 0 until it.childCount) it.getChildAt(i).isClickable = false }

        if (content == null) {
            // 방어: 내용 뷰 참조를 잃었다면 애니메이션 없이 즉시 제거한다(순서 계약은 동일 —
            // removeMenuNow 가 창을 헐고 나서 pendingMenuAction 을 실행한다).
            Log.w(TAG, "dismissMenu: menuContent 가 null — 애니메이션 없이 즉시 제거")
            removeMenuNow()
            return
        }

        menuDim?.animate()?.alpha(0f)?.setDuration(MENU_EXIT_ANIM_MS)?.start()
        content.animate()
            .scaleX(MENU_ENTER_START_SCALE)
            .scaleY(MENU_ENTER_START_SCALE)
            .alpha(0f)
            // 퇴장은 진입보다 짧게. 표현용 지속시간이며 상태 전이 대기가 아니다(ADR-2 무관) —
            // 아래 액션은 이 시간이 아니라 창 제거 완료를 기다린다.
            .setDuration(MENU_EXIT_ANIM_MS)
            .setInterpolator(DecelerateInterpolator())
            // 창을 헐고 그 다음에야 배치/해제를 트리거한다(함정 #22). 두 일 모두 removeMenuNow 안에 있다.
            .withEndAction { removeMenuNow() }
            .start()
    }

    /**
     * 메뉴 오버레이 창을 **애니메이션 없이 즉시** 제거하고, 커밋된 [pendingMenuAction] 이 있으면
     * 창이 헐린 **직후** 실행한다(멱등, 재진입 안전).
     *
     * 호출 경로는 셋이다:
     *  1. [dismissMenu] 의 퇴장 애니메이션 완료(`withEndAction`) — 정상 경로
     *  2. 배치 세션 시작([setBubbleHiddenForArrange]) — 애니메이션을 기다릴 여유가 없는 경로.
     *     세션이 시작된 뒤에도 [MENU_EXIT_ANIM_MS] 동안 터치 가능 오버레이가 남으면 함정 #22 다.
     *  3. 서비스 종료([onDestroy]) — 이때는 [onDestroy] 가 **먼저** [pendingMenuAction] 을
     *     명시적으로 비우므로 여기서 실행되지 않는다.
     *
     * [D1] 1과 2 중 **먼저 도착한 쪽**이 액션을 처리하고, 둘째는 아무것도 찾지 못한다 —
     * 액션 참조를 로컬로 빼낸 뒤 필드를 즉시 비우기 때문에 이중 실행이 구조적으로 불가능하다.
     *
     * [F4] 단, 경로 2 에서는 **여기서 실행하지 않는다**. 세션이 이미 도는 중이라 그 자리에서
     * 실행하면 액추에이터의 busy 가드에 걸려 사라지므로, [deferredMenuAction] 으로 옮겨
     * [restoreBubbleAfterArrange] 까지 미룬다([pendingMenuAction] KDoc 의 3결과 모델).
     *
     * [D20] 제거는 `removeViewImmediate` 다. `removeView` 는 `MSG_DIE` 포스트일 뿐이라, 그 뒤에
     * 액션을 실행하면 스크림이 아직 합성된 채로 세션이 시작된다 — 고치려던 함정 #22 를 그대로
     * 밟는다. immediate 는 `dispatchDetachedFromWindow()` 까지 동기로 마치고 반환한다.
     */
    private fun removeMenuNow() {
        val view = menuView
        val content = menuContent
        val dim = menuDim
        val action = pendingMenuAction
        // 필드를 먼저 비운다 — cancel() 이 어떤 경로로든 재진입해도 두 번째 제거/이중 실행이 없다.
        menuView = null
        menuContent = null
        menuDim = null
        menuDismissing = false
        pendingMenuAction = null
        content?.animate()?.cancel()
        dim?.animate()?.cancel()
        if (view != null) {
            runCatching { windowManager.removeViewImmediate(view) }
                .onFailure { e -> Log.w(TAG, "메뉴 뷰 제거 실패", e) }
            // 창이 실제로 헐린 **뒤에야** 내린다([menuAttached] KDoc — menuView 와 시점이 다르다).
            menuAttached = false
        }

        // [D15] 메뉴가 닫혔으니 버블을 다시 또렷하게 되돌리고 유휴 페이드를 새로 건다.
        // 버블이 붙어 있지 않으면(세션 시작·서비스 종료 경로) 둘 다 no-op 이다.
        // cancel() 이 진행 중이던 눌림/해제 애니메이션을 중간 배율에서 끊을 수 있으므로
        // 알파와 함께 배율도 기준값으로 되돌린다 — 안 그러면 0.9 배로 굳는다.
        bubbleView?.takeIf { bubbleAttached }?.let {
            it.animate().cancel()
            it.alpha = 1f
            it.scaleX = 1f
            it.scaleY = 1f
        }
        scheduleIdleFade()

        // ⚠ 창이 실제로 헐린 **뒤**여야 한다(함정 #22). 이 시점에 우리 오버레이 창은
        //   hasAttachedOverlayWindow() 기준으로 메뉴 쪽이 확실히 없다.
        if (action == null) return
        if (bubbleHiddenForArrange) {
            // [F4] 결과 2 — 이연 실행. 자동 배치 세션이 이미 돌고 있어 지금 실행하면 액추에이터의
            // busy 가드에 걸려 "무시 + busy 토스트" 로 사라진다(사용자는 눌렀는데 아무 일도 안 남).
            // 폐기도 즉시 실행도 아닌 세 번째 결과: 복원 시점까지 미룬다.
            // 이연 대기 중 또 하나가 커밋되는 경로는 실질적으로 없지만(창이 전부 걷힌 상태라
            // 메뉴를 열 수 없다), 도착하면 잃지 않고 순서대로 이어 붙인다.
            val prior = deferredMenuAction
            deferredMenuAction = if (prior == null) action else ({ prior(); action() })
            Log.i(TAG, "removeMenuNow: 배치 세션 진행 중 — 커밋된 메뉴 액션을 복원 시점까지 이연")
            return
        }
        action.invoke()
    }

    /**
     * @param fullscreenAutoEnabled [#30] 토글 항목의 **현재 상태** 스냅샷([showMenu] 가 메뉴 표시
     *   직전에 읽어 넘긴다). 항목 라벨은 현재 상태를 보여주고, 탭하면 반대 값으로 뒤집는다.
     */
    private fun buildMenuContent(fullscreenAutoEnabled: Boolean): LinearLayout {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.menu_background)
            val pad = (MENU_CONTAINER_PADDING_DP * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        // ── 그룹 1: 지금 이 창에 하는 일(창 동작) ──────────────────────────
        // [D19] 종전에는 「앱 페어 바로가기 만들기」가 분할 해제와 팝업 사이에 끼어 있어서,
        // 창 동작 넷 중 셋만 이웃하고 하나는 남의 자리에 앉은 꼴이었다. 그룹이 우연처럼 보이면
        // 구분선도 라벨도 신호가 아니라 장식이 된다.
        container.addMenuItem(getString(R.string.bubble_menu_place_top), R.drawable.ic_menu_place_top) {
            dismissMenuThenArrange(Placement.TOP, null)
        }
        container.addMenuItem(getString(R.string.bubble_menu_place_bottom), R.drawable.ic_menu_place_bottom) {
            dismissMenuThenArrange(Placement.BOTTOM, null)
        }
        container.addMenuItem(getString(R.string.bubble_menu_dismiss_split), R.drawable.ic_menu_dismiss_split) {
            dismissMenuThenDismissSplit()
        }
        // P4-1: Shizuku 가용할 때만 노출한다(설계 확정 — 미가용 시 항목 자체를 숨긴다,
        // DESIGN_P41_FREEFORM.md §4 "Shizuku 없이 동작하는 폴백 경로 없음").
        if (ShizukuShell.isReady()) {
            container.addMenuItem(getString(R.string.bubble_menu_open_popup), R.drawable.ic_menu_popup) {
                dismissMenuThenPopup()
            }
        }

        // ── 그룹 2: 종횡비 프리셋 ─────────────────────────────────────────
        // window_profiles.json presets 은 자산 파싱 성공 시에만 채워진다. 실패/미로드 시 섹션 자체를
        // 생략한다(크래시 금지, 조용한 실패는 preloadPresetsIfNeeded 의 Log.w 로 이미 드러남).
        // 구분선도 이 안에서 붙인다 — 프리셋이 없을 때 구분선 두 개가 붙어 버리는 일이 없게.
        //
        // [D19] **머리말은 이 그룹에만 붙인다**(전부 붙이거나 여기만, 중 후자). 근거: 나머지 두
        // 그룹의 항목은 전부 동사("위로 배치", "설정")라 스스로를 설명하지만, 이 그룹만은 항목이
        // **데이터**("16:9", "21:9")다 — 머리말이 없으면 그 숫자가 무엇의 비율인지 알 수 없다.
        // 「동작」·「기타」 같은 머리말을 나머지에 붙이는 것은 정보가 아니라 소음이다.
        val presets = cachedPresets
        if (!presets.isNullOrEmpty()) {
            container.addMenuDivider()
            container.addMenuSectionLabel(getString(R.string.menu_section_aspect))
            presets.forEach { preset ->
                container.addMenuItem(preset.label, R.drawable.ic_menu_aspect, iconTint = MENU_ACCENT_COLOR) {
                    // preset.aspect == null("자동 감지")이면 startArrange(null, null) 과 동일 의미가 된다.
                    dismissMenuThenArrange(null, preset.aspect)
                }
            }
        }

        // ── 그룹 3: 이 앱 자체의 설정/도구 ────────────────────────────────
        container.addMenuDivider()
        // [#30, 설계서 §2.3 / D14·D16·D22] 전체화면 자동 배치 사용자 토글. 기본 OFF 인 이 값이
        // 자동 트리거의 실질 방어선이라(R9), 켜는 표면과 **끄는 표면**이 반드시 같은 자리에
        // 있어야 한다 — 종전에는 되돌릴 표면이 없어 앱 삭제/접근성 OFF 가 유일한 탈출구였다.
        // [P5 폴리시] 라벨 의미(현재 상태 표시 + 탭하면 반전, D16)는 그대로 두고 상태 표현만
        // 보강한다: 켜짐 = 채운 아이콘 + 세이지 액센트, 꺼짐 = 윤곽 아이콘 + 흐린 흰색.
        container.addMenuItem(
            label = getString(
                if (fullscreenAutoEnabled) {
                    R.string.bubble_menu_fullscreen_auto_on
                } else {
                    R.string.bubble_menu_fullscreen_auto_off
                },
            ),
            iconRes = if (fullscreenAutoEnabled) {
                R.drawable.ic_menu_fullscreen_auto_on
            } else {
                R.drawable.ic_menu_fullscreen_auto_off
            },
            textColor = if (fullscreenAutoEnabled) MENU_ITEM_TEXT_COLOR else MENU_ITEM_MUTED_COLOR,
            iconTint = if (fullscreenAutoEnabled) MENU_ACCENT_COLOR else MENU_ITEM_MUTED_COLOR,
            accessibilityLabel = getString(
                if (fullscreenAutoEnabled) {
                    R.string.bubble_menu_fullscreen_auto_cd_on
                } else {
                    R.string.bubble_menu_fullscreen_auto_cd_off
                },
            ),
            // [D12] 이 행만 "체크 가능" 으로 노출한다 — 아래 addMenuItem KDoc 참고.
            toggleState = fullscreenAutoEnabled,
        ) {
            dismissMenuThenToggleFullscreenAuto(fullscreenAutoEnabled)
        }
        container.addMenuItem(getString(R.string.bubble_menu_export_pair), R.drawable.ic_menu_export_pair) {
            exportAppPair()
        }
        container.addMenuItem(getString(R.string.bubble_menu_settings), R.drawable.ic_menu_settings) {
            // 함정 #22 와 무관한 항목이지만 순서 계약은 동일하게 지킨다 — 창이 제거된 뒤 화면 전환.
            dismissMenu { launchOnboarding() }
        }

        return container
    }

    /**
     * 메뉴 항목 한 줄. 선행 아이콘 + 라벨 + 리플로 구성하고 최소 48dp 높이를 보장한다.
     *
     * 접근성: 행 전체가 하나의 탭 타깃이자 하나의 접근성 노드다 — 아이콘/텍스트는
     * `IMPORTANT_FOR_ACCESSIBILITY_NO` 로 내려 두고 행의 [accessibilityLabel] 하나만 읽히게 한다
     * (토글 항목은 상태까지 읽어 주는 별도 문자열을 넘긴다).
     *
     * lint `ClickableViewAccessibility` 대응이 필요 없는 이유: 여기서는 `setOnTouchListener` 가
     * 아니라 표준 `setOnClickListener` 를 쓴다(버블과 달리 드래그/롱프레스 판정이 없다).
     *
     * @param toggleState non-null 이면 이 행을 **체크 가능한 토글**로 접근성에 노출한다(D12).
     *   그냥 클릭 가능 뷰로 두면 TalkBack 이 설명 뒤에 자기 기본 안내("두 번 탭하여 활성화")를
     *   덧붙여, 설명 안에 이미 있던 "두 번 탭하면 끕니다" 와 겹쳐 두 번 안내가 나갔다. 이제
     *   상태는 `isCheckable`/`isChecked` 가, 결과는 ACTION_CLICK 의 **라벨**("끄기"/"켜기")이
     *   맡는다 — 그래서 두 `_cd_` 문자열에서는 수동 안내 문구를 걷어냈다.
     */
    private fun LinearLayout.addMenuItem(
        label: String,
        iconRes: Int,
        textColor: Int = MENU_ITEM_TEXT_COLOR,
        iconTint: Int = textColor,
        accessibilityLabel: String = label,
        toggleState: Boolean? = null,
        onClick: () -> Unit,
    ) {
        val density = resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (MENU_ITEM_MIN_HEIGHT_DP * density).toInt()
            setBackgroundResource(R.drawable.menu_item_background)
            val horizontal = (MENU_ITEM_PADDING_H_DP * density).toInt()
            val vertical = (MENU_ITEM_PADDING_V_DP * density).toInt()
            setPadding(horizontal, vertical, horizontal, vertical)
            isClickable = true
            isFocusable = true
            contentDescription = accessibilityLabel
            setOnClickListener { clicked ->
                // 커밋된 액션마다 가벼운 햅틱 — 창이 애니메이션으로 사라지기 **전에** 눌림을 알린다.
                clicked.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onClick()
            }
        }

        if (toggleState != null) {
            val clickLabel = getString(
                if (toggleState) R.string.bubble_menu_action_turn_off else R.string.bubble_menu_action_turn_on,
            )
            row.accessibilityDelegate = object : View.AccessibilityDelegate() {
                @Suppress("DEPRECATION") // isChecked= 는 API 36 에서 setChecked(Int) 로 대체됐지만
                // minSdk 30 에는 tri-state API 가 없어 분기 없인 대체 불가능하다.
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.isCheckable = true
                    if (Build.VERSION.SDK_INT >= 36) {
                        info.setChecked(
                            if (toggleState) {
                                AccessibilityNodeInfo.CHECKED_STATE_TRUE
                            } else {
                                AccessibilityNodeInfo.CHECKED_STATE_FALSE
                            },
                        )
                    } else {
                        info.isChecked = toggleState
                    }
                    // 같은 id 로 다시 넣으면 기본 ACTION_CLICK 라벨을 대체한다.
                    info.addAction(
                        AccessibilityNodeInfo.AccessibilityAction(
                            AccessibilityNodeInfo.ACTION_CLICK,
                            clickLabel,
                        ),
                    )
                }
            }
        }

        val iconSize = (MENU_ITEM_ICON_SIZE_DP * density).toInt()
        val icon = ImageView(context).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(iconTint)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.addView(
            icon,
            LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = (MENU_ITEM_ICON_GAP_DP * density).toInt()
            },
        )

        val labelView = TextView(context).apply {
            text = label
            setTextColor(textColor)
            textSize = MENU_ITEM_TEXT_SIZE_SP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        row.addView(
            labelView,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ),
        )

        addView(
            row,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
    }

    /**
     * 그룹 머리말. 항목이 있는 그룹 위에만 붙인다(호출부에서 판단). 작은 크기 + 액센트 색으로
     * 항목과 계층이 구분되며, 스크린리더에는 제목(heading)으로 노출된다.
     *
     * [D13] 색은 **알파를 뺀** 원색이다(종전 `0x99A9C7B5` = 60% 알파는 메뉴 표면 위에서 대비가
     * AA 의 절반 수준이었다 — 11sp 라 더 나빴다). 계층 구분은 크기(11sp vs 15sp)와 색상(세이지
     * 계열 vs 흰색)이 맡는다.
     *
     * [F2] 다만 액센트 원색(`MENU_ACCENT_COLOR` #A9C7B5)은 메뉴 표면(#3E5A4B) 위에서 **4.16:1**
     * 이라 여전히 AA(4.5:1) 미달이다 — D13 이 적어 둔 "≈4.56:1" 은 계산 오류였다. 그래서 머리말
     * 전용 색 [MENU_SECTION_LABEL_COLOR] 를 따로 두고 아이콘 틴트용 액센트와 용도를 분리했다.
     *
     * [D14] `letterSpacing` 은 삭제했다. 자간 넓히기는 라틴 대문자 조판의 관습이고, 한글은
     * 글자 하나가 이미 완결된 네모틀이라 자간을 벌리면 낱글자가 흩어져 오히려 덜 읽힌다.
     */
    private fun LinearLayout.addMenuSectionLabel(label: String) {
        val density = resources.displayMetrics.density
        val view = TextView(context).apply {
            text = label
            setTextColor(MENU_SECTION_LABEL_COLOR)
            textSize = MENU_SECTION_LABEL_TEXT_SIZE_SP
            isAccessibilityHeading = true
            setPadding(
                (MENU_ITEM_PADDING_H_DP * density).toInt(),
                (10 * density).toInt(),
                (MENU_ITEM_PADDING_H_DP * density).toInt(),
                (4 * density).toInt(),
            )
        }
        addView(
            view,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )
    }

    /** 그룹 구분선. 섹션 라벨보다 약한 신호라 대비를 낮추고 좌우 여백을 항목 패딩에 맞춘다. */
    private fun LinearLayout.addMenuDivider() {
        val density = resources.displayMetrics.density
        val divider = View(context).apply {
            setBackgroundColor(MENU_DIVIDER_COLOR)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val marginH = (MENU_ITEM_PADDING_H_DP * density).toInt()
        val marginV = (6 * density).toInt()
        val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt())
        params.setMargins(marginH, marginV, marginH, marginV)
        addView(divider, params)
    }

    /**
     * 메뉴 창이 **실제로 제거된 뒤** 배치를 트리거한다(함정 #22 — 순서 계약은 [dismissMenu] KDoc).
     * 접근성 서비스 인스턴스가 없으면 기존 탭 동작과 동일하게 토스트 + 온보딩으로 유도한다.
     */
    private fun dismissMenuThenArrange(placement: Placement?, aspect: Float?) {
        dismissMenu {
            val service = requireArrangerOrGuide() ?: return@dismissMenu
            service.startArrange(placement, aspect)
            announceToScreenReader(R.string.bubble_announce_arrange_started)
        }
    }

    /** 메뉴 창이 실제로 제거된 뒤(함정 #22) 분할 해제를 트리거한다. */
    private fun dismissMenuThenDismissSplit() {
        dismissMenu {
            val service = requireArrangerOrGuide() ?: return@dismissMenu
            service.dismissSplit()
            announceToScreenReader(R.string.bubble_announce_split_dismissing)
        }
    }

    /**
     * [D12] 메뉴 액션으로 실제 세션이 **시작됐음**을 스크린리더에 알린다. 시각 사용자에게는
     * 화면이 재배치되는 것 자체가 피드백이지만, 그것이 보이지 않는 사용자에게는 메뉴가 닫히고
     * 아무 말도 없는 것으로 끝난다.
     *
     * 발화 주체가 버블인 이유: 이 시점에 우리 뷰 중 창에 붙어 있는 것은 버블뿐이다(메뉴는 방금
     * 헐렸다). `announceForAccessibility` 는 창에 붙지 않은 뷰에서는 아무 일도 하지 않으므로
     * [bubbleAttached] 를 확인한다. 붙어 있지 않은 경우 = 이미 배치 세션이 도는 중이고, 그때는
     * 액추에이터 자신의 결과 토스트가 뒤이어 읽힌다.
     */
    @Suppress("DEPRECATION") // announceForAccessibility 는 API 36 에서 라이브 리전/paneTitle/
    // stateDescription 으로 대체 권장되지만, 그 대안들은 발화 시점에 살아 있는 뷰가 필요하다.
    // 여기서는 메뉴 창이 이미 헐린 직후(위 문서 주석 참고)라 대체할 수 있는 지속 뷰가 없다.
    private fun announceToScreenReader(messageRes: Int) {
        bubbleView?.takeIf { bubbleAttached }?.announceForAccessibility(getString(messageRes))
    }

    /**
     * 접근성 서비스 인스턴스를 얻거나, 없으면 토스트 + 온보딩으로 유도하고 null 을 돌려준다.
     * 메뉴 트리거 항목들이 똑같이 반복하던 분기를 한곳으로 모은 것 — 동작은 종전과 동일하다.
     *
     * 문구는 [R.string.arrange_accessibility_off] 하나로 통일했다(구 `toast_accessibility_off`
     * 는 같은 사실을 "설정에서 켜 주세요" 없이 말하던 중복 문자열이라 삭제). 이 경로는 토스트
     * 직후 실제로 온보딩을 여니, 할 일까지 말해 주는 쪽이 맞다.
     */
    private fun requireArrangerOrGuide(): ArrangerAccessibilityService? {
        val service = ArrangerAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, getString(R.string.arrange_accessibility_off), Toast.LENGTH_LONG).show()
            launchOnboarding()
        }
        return service
    }

    /**
     * [#30] 전체화면 자동 배치 토글을 뒤집는다.
     *
     * @param current 메뉴 표시 직전에 읽은 스냅샷([showMenu]). 저장 시점에 DataStore 를 다시 읽어
     *   토글하지 않는 이유: 사용자가 화면에서 본 상태와 실제 결과가 어긋나면 "껐는데 켜졌다" 가
     *   되기 때문이다. 메뉴가 떠 있는 짧은 구간에 다른 경로가 이 값을 바꿀 가능성은 없다
     *   (이 토글의 유일한 쓰기 지점이 여기다).
     *
     * 저장 후 [ArrangerAccessibilityService.refreshFullscreenAutoSnapshot] 을 반드시 호출한다 —
     * 접근성 서비스는 이벤트 최전방 선차단을 위해 이 값을 `@Volatile` 스냅샷으로 들고 있어서,
     * 갱신을 빠뜨리면 "메뉴에서 켰는데 아무 일도 일어나지 않는" 상태가 된다(다음 서비스 재연결
     * 까지 지속되므로 사용자가 원인을 찾을 방법이 없다).
     */
    private fun dismissMenuThenToggleFullscreenAuto(current: Boolean) {
        dismissMenu {
            val next = !current
            serviceScope.launch {
                store.setFullscreenAutoEnabled(next)
                ArrangerAccessibilityService.instance?.refreshFullscreenAutoSnapshot()
                Log.i(TAG, "fullscreen auto toggle: $current -> $next")
                Toast.makeText(
                    this@FloatingLauncherService,
                    getString(
                        if (next) {
                            R.string.toast_fullscreen_auto_enabled
                        } else {
                            R.string.toast_fullscreen_auto_disabled
                        },
                    ),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /**
     * [P4-1] 메뉴를 먼저 제거한 뒤(함정 #22) 팝업(freeform) 배치를 트리거한다. 버블 자체는
     * freeform 낙착에 무해함이 프로브에서 확인됐으므로([DESIGN_P41_FREEFORM.md] §4), 배치
     * 세션처럼 버블 창을 숨길 필요는 없다 — 스크림(메뉴)만 제거하면 된다.
     */
    private fun dismissMenuThenPopup() {
        dismissMenu {
            val service = requireArrangerOrGuide() ?: return@dismissMenu
            service.startPopup()
            announceToScreenReader(R.string.bubble_announce_arrange_started)
        }
    }

    // ══════════════════════════════════════════════════════════
    // P4-4: 홈 화면 고정 바로가기(앱 페어 export)
    // ══════════════════════════════════════════════════════════

    /**
     * 메뉴 "앱 페어 바로가기 만들기" 트리거. 현재 전면 앱을 식별해 홈 화면에 고정 가능한
     * 바로가기([PairShortcutActivity] 트램펄린 인텐트)를 시스템에 요청한다.
     *
     * 1) [dismissMenu] 의 **창 제거 완료 콜백** 안에서만 이어서 진행한다 — 다른 메뉴 트리거
     * 항목([dismissMenuThenArrange] 등)과 동일한 이유다: 스크림이 떠 있으면 접근성 창 목록의
     * 활성 창 판독이 우리 창으로 오염된다(함정 #22 계열).
     * 2) 스크림 제거 직후에는 접근성 창 목록 재구축이 비원자적이라([PROGRESS #25],
     * [ArrangerAccessibilityService] 의 dismissSplit/awaitWindowsSettled 와 동일 실측 근거) 고정
     * 지연 대신 [ArrangerAccessibilityService.foregroundPackageForExport] 가 non-null 값을 낼 때까지
     * 조건 폴링한다(ADR-2).
     */
    private fun exportAppPair() {
        dismissMenu { exportAppPairAfterMenuRemoved() }
    }

    /** [exportAppPair] 의 실제 본문. 메뉴 창이 실제로 제거된 뒤에만 호출된다(함정 #22). */
    private fun exportAppPairAfterMenuRemoved() {
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
        // [W7-B/P2] 사용자가 위치를 확정한 순간 — 뒤늦게 도착할 수 있는 비동기 복원이 이 값을
        // 되돌리지 못하게 잠근다([bubblePositionUserAdjusted] KDoc 참고).
        bubblePositionUserAdjusted = true
        cachedBubbleX = x
        cachedBubbleY = y
        // fire-and-forget — ProfileStore.saveBubblePosition 이 내부에서 예외를 잡는다(safeWrite).
        serviceScope.launch { store.saveBubblePosition(x, y) }
    }

    companion object {
        private const val TAG = "FWFloatingLauncher"
        private const val CHANNEL_ID = "floating_launcher_channel"
        private const val NOTIFICATION_ID = 1001

        /**
         * 버블 원의 지름 = 오버레이 창 크기. 튜닝된 탭 타깃 크기이므로 근거 없이 바꾸지 않는다.
         *
         * [D4/D5] 종전에는 굽힌 드롭 섀도용 여백 6dp 를 사방에 더해 창이 68dp 였고, 그 탓에
         * 가장자리 스냅 후 **원이 화면 끝에서 6dp 안쪽**에 떠 있었다. 그림자를 걷어내면서
         * 창과 원이 다시 같아졌고, 스냅 타깃 보정도 필요 없어졌다.
         */
        private const val BUBBLE_SIZE_DP = 56

        /** [createBubbleViewIfNeeded] 버블 아이콘(ImageView) 내부 패딩. */
        private const val BUBBLE_ICON_PADDING_DP = 14

        // ── P5 폴리시: 버블 모션/피드백 ─────────────────────────────
        // 아래 지속시간·지연은 전부 **표현용**이다. 어떤 상태 전이도 이 값에 걸려 있지 않으므로
        // ADR-2(상태 전이를 고정 지연으로 기다리지 말 것)의 대상이 아니다.

        /** 드래그 후 가장자리 스냅 애니메이션 길이. */
        private const val SNAP_ANIM_DURATION_MS = 240L

        /** 눌림/해제 스케일 애니메이션 길이와 눌림 배율. */
        private const val BUBBLE_PRESS_ANIM_MS = 90L
        private const val BUBBLE_RELEASE_ANIM_MS = 180L
        private const val BUBBLE_PRESS_SCALE = 0.90f

        /**
         * [D3] 버블 (재)부착 연출. 배치 세션이 끝나 버블이 돌아오는 순간에 쓴다 — 이 앱에서
         * 가장 자주 지나는 지점인데 종전에는 전이가 아예 없었다. **숨김 쪽은 여전히 즉시**이며
         * ([setBubbleHiddenForArrange]), 여기에만 연출이 붙는다.
         */
        private const val BUBBLE_RESTORE_ANIM_MS = 160L
        private const val BUBBLE_RESTORE_START_SCALE = 0.7f

        /**
         * 유휴 페이드: 마지막 조작 후 [BUBBLE_IDLE_DELAY_MS] 동안 손대지 않으면
         * [BUBBLE_IDLE_ALPHA] 까지 흐려져 아래 영상과 경쟁하지 않게 한다. 알파만 바꾸므로
         * 창 크기·위치(=탭 영역)는 변하지 않는다.
         *
         * [D16] 3s/0.55 → 5s/0.65. 0.55 는 어두운 영상 위에서 버블을 사실상 사라지게 만들고
         * (원도 링도 어두운 톤이다), 3s 는 어떤 참조 오버레이보다도 짧아 "잠깐 멈춘 것"까지
         * 유휴로 판정했다.
         */
        private const val BUBBLE_IDLE_DELAY_MS = 5_000L
        private const val BUBBLE_IDLE_ALPHA = 0.65f
        private const val BUBBLE_IDLE_FADE_MS = 320L

        // ── P3-2 확장 메뉴 스타일 (P5 폴리시로 아이콘/그룹/모션 추가) ──
        // 색은 bubble_background 계열의 따뜻한 세이지 톤을 그대로 유지한다.
        private val MENU_ITEM_TEXT_COLOR = Color.WHITE

        /**
         * 비활성/부가 정보용 흐린 흰색(꺼짐 상태 토글 등).
         * [D13] 0x99(60%) → 0xCC(80%). 60% 는 메뉴 표면 위에서 대비 ≈3.92:1 로 AA 미달이었고,
         * 하필 **끄는 방법을 찾는 사용자가 읽어야 할 행**(자동 배치 OFF)에 쓰이고 있었다.
         * 80% 는 ≈5.56:1.
         */
        private val MENU_ITEM_MUTED_COLOR = 0xCCFFFFFF.toInt()

        /**
         * 세이지 액센트 — 켜짐 상태 토글 아이콘, 프리셋 아이콘 **틴트 전용**.
         * [F2] 텍스트에는 쓰지 않는다: 메뉴 표면 위 대비가 4.16:1 로 AA(4.5:1) 미달이다.
         * 아이콘은 22dp 굵은 획이라 비텍스트 대비 기준(3:1)을 넘으므로 그대로 유지한다.
         */
        private val MENU_ACCENT_COLOR = 0xFFA9C7B5.toInt()

        /**
         * [F2] 그룹 머리말 전용 색. 11sp 소형 텍스트라 WCAG AA 는 4.5:1 을 요구하는데, 종전에
         * 쓰던 [MENU_ACCENT_COLOR] 는 메뉴 표면 위에서 4.16:1 로 미달이었다(D13 주석의 4.56:1 은
         * 계산 오류). 같은 세이지 계열을 밝은 쪽으로 옮긴 #DCEAE3 로 교체한다.
         *
         * 실측 계산(WCAG 2.x 상대 휘도, 배경 = menu_background 의 #E63E5A4B):
         *  · 불투명 표면(#3E5A4B) 기준 — 액센트 #A9C7B5 = **4.162:1**, #DCEAE3 = **6.111:1**
         *  · 0xE6 = 90.196% 표면 + 스크림 딤(0x38000000) 합성 후 최악(흰 배경 위) — #DCEAE3 = **5.143:1**
         *    (같은 조건에서 액센트는 3.503:1 로 무너진다)
         * 즉 배경이 무엇이든 AA 를 넘는다. 흰색(7.58:1)까지 올리지 않은 이유는 머리말이 항목
         * 라벨(흰색)보다 약한 신호여야 하기 때문이다 — 계층은 유지하고 하한만 넘긴다.
         */
        private val MENU_SECTION_LABEL_COLOR = 0xFFDCEAE3.toInt()

        private const val MENU_ITEM_TEXT_SIZE_SP = 15f
        private const val MENU_SECTION_LABEL_TEXT_SIZE_SP = 11f
        private const val MENU_DIVIDER_COLOR = 0x1FFFFFFF

        /** [buildMenuContent] 스크림 컨테이너(LinearLayout)의 사방 패딩. */
        private const val MENU_CONTAINER_PADDING_DP = 6

        /** [addMenuItem] 메뉴 항목 행의 좌우/상하 패딩과 접근성 최소 높이. */
        private const val MENU_ITEM_PADDING_H_DP = 14
        private const val MENU_ITEM_PADDING_V_DP = 10
        private const val MENU_ITEM_MIN_HEIGHT_DP = 48

        /** [addMenuItem] 선행 아이콘 크기와 라벨까지의 간격. */
        private const val MENU_ITEM_ICON_SIZE_DP = 22
        private const val MENU_ITEM_ICON_GAP_DP = 14

        /** 메뉴 표면 그림자 높이(내용 뷰는 스크림의 자식이라 elevation 을 그대로 쓸 수 있다). */
        private const val MENU_ELEVATION_DP = 12f

        /**
         * [D10] 메뉴가 화면 가장자리에서 최소한 이만큼 떨어지게 한다 — elevation 그림자가
         * 그려질 공간. 버블이 좌/우 끝으로 스냅하므로 이 여백이 없으면 종전처럼 거의 항상
         * 한쪽 그림자가 통째로 잘렸다. elevation(12dp)과 같은 크기로 잡는다.
         */
        private const val MENU_SCREEN_MARGIN_DP = 12

        /**
         * [D10] elevation 그림자 색(API 28+, minSdk 30). 메뉴 표면이 90% 불투명이라 기본 순검정
         * 그림자가 표면을 통해 비쳐 안쪽까지 탁해진다 — 70% 로 낮춰 번짐만 덜어낸다.
         */
        private val MENU_SHADOW_COLOR = 0xB3000000.toInt()

        /** 메뉴가 떠 있는 동안 아래 화면을 살짝 어둡게 — 모달임을 알리고 가독성을 올린다. */
        private const val MENU_SCRIM_COLOR = 0x38000000

        /**
         * 메뉴 진입/퇴장 모션. 표현용 지속시간이며 상태 전이 대기가 아니다(ADR-2 무관) —
         * 배치 트리거는 이 시간이 아니라 `removeViewImmediate` 완료 뒤에 걸려 있다([dismissMenu]) —
         * 퇴장 애니메이션의 `withEndAction` 이 [removeMenuNow] 를 부르고, 창을 동기로 헐어 낸
         * 그 함수가 액션을 실행(또는 F4 이연)한다.
         */
        private const val MENU_ENTER_ANIM_MS = 180L
        private const val MENU_EXIT_ANIM_MS = 130L
        private const val MENU_ENTER_START_SCALE = 0.85f

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
         *
         * **[D20] 보증하는 것(정확히)**: 이 함수는 각 창의 `addView` 반환 직후부터
         * `removeViewImmediate` 반환 직후까지 true 다.
         *  · 버블 — [attachBubbleView] 가 `addView` **성공 후** [bubbleAttached] 를 세우고,
         *    [detachBubbleView] 는 `removeViewImmediate`(동기 teardown) 가 **끝난 뒤** 내린다.
         *    부착 직후의 진입 애니메이션(D3)은 알파/배율만 만지므로 창의 존재와 무관하다.
         *  · 메뉴 — `addView` 성공 **직후** [menuAttached] 가 서고, 퇴장 애니메이션 130ms 동안에도
         *    true 로 남으며(창이 아직 터치를 가로챈다), [removeMenuNow] 의 `removeViewImmediate`
         *    가 **반환한 뒤에야** 내려간다. 그 다음 [pendingMenuAction] 은 즉시 실행되거나(F4 결과 1)
         *    [deferredMenuAction] 으로 이연된다(F4 결과 2) — 어느 쪽이든 이 시점엔 이미 false 다.
         *
         * 여기서 [menuView] 가 아니라 [menuAttached] 를 보는 것이 핵심이다 — [menuView] 는
         * 재진입 방지를 위해 창을 헐기 **전에** 비워지므로, 그것으로 판정하면 동기 바인더 왕복
         * 동안 스크림이 떠 있는데 false 를 답한다([menuAttached] KDoc).
         *
         * 남는 오차는 `ViewRootImpl` 이 traversal 중일 때의 immediate 폴백뿐이다(우리 호출 지점은
         * 전부 traversal 밖 — 클래스 KDoc 참고). 종전 `removeView`(MSG_DIE 포스트) 하에서는
         * 이 오차가 버블·메뉴 양쪽에서 **한 프레임 전체**였다.
         */
        fun hasAttachedOverlayWindow(): Boolean =
            instance?.let { it.bubbleAttached || it.menuAttached } ?: false
    }
}
