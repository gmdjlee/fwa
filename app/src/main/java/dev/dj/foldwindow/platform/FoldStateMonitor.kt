package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.WindowManager
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import dev.dj.foldwindow.domain.FoldPosture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * P3-5: androidx.window `WindowInfoTracker` 를 접근성 서비스 컨텍스트에서 구독해
 * [dev.dj.foldwindow.domain.FoldPosture] 로 사상하는 래퍼. domain 은 androidx.window 를 몰라야
 * 하므로(CLAUDE.md 아키텍처 규칙) 사상 로직은 여기(platform/) 에만 존재한다.
 *
 * [실기기 판정, One UI 8 / API 36, 2026-07-27 — docs/DEVICE_FACTS.md "P3-5 FoldingFeature" 절 참고]
 * `WindowInfoTracker.windowLayoutInfo(Context)` 가 요구하는 `@UiContext` 조건에 대해 기존에 시도하던
 * 두 컨텍스트 후보가 실기기에서 **전부 실패**했다:
 * - 서비스(`AccessibilityService`) 자신: androidx.window 의 `assertUiContext` 가 즉시 거부한다 —
 *   `IllegalArgumentException: Context must be a UI Context with display association, which
 *   should be an Activity, WindowContext or InputMethodService`
 *   (`WindowLayoutComponentImpl.assertUiContext`).
 * - (구) 2-인자 `service.createWindowContext(TYPE_ACCESSIBILITY_OVERLAY, null)`: 구독 이전에
 *   컨텍스트 **생성 자체**가 실패한다 — `UnsupportedOperationException: Tried to obtain display
 *   from a Context not associated with one. Only visual Contexts (such as Activity or one created
 *   with Context#createWindowContext) or ones created with Context#createDisplayContext are
 *   associated with displays.` (`ContextImpl.getDisplay` — 2-인자 버전은 베이스 컨텍스트의 display 를
 *   요구하는데 서비스 컨텍스트엔 display 연결이 없다). 이 후보는 죽은 코드로 확정되어 제거했다.
 *
 * 두 에러 모두 "display 를 명시적으로 연결하라"는 동일한 원인을 가리킨다 — 그래서 display 를 직접
 * 넘기는 3-인자 `createWindowContext(Display, TYPE_ACCESSIBILITY_OVERLAY, null)` 방식을 채택했고,
 * 그마저 막힐 경우를 대비해 `createDisplayContext(display).createWindowContext(...)` 를 벨트-앤-
 * 서스펜더로 추가했다(순서·API 레벨 가드는 [candidateContexts] 참고). **실기기 판정 (2026-07-27,
 * Fold 7/One UI 8): 후보 ②(3-인자 display WindowContext)가 `assertUiContext` 통과·방출 수신 확인.
 * 후보 ①(서비스 자신)은 거부됨 — 타 OS 대비로만 유지.** 각 후보는 구독 시작 후에도 예외로
 * 죽을 수 있어 후보별 try/catch 로 다음 후보에 폴백하고, 전부 실패하면 기능을 조용히 끄되(크래시
 * 절대 금지) 로그로 원인을 남긴다.
 *
 * [실기기 판정 완료 2026-07-27] `@UiContext` 는 androidx.window 1.3.0 소스 기준 `@Retention(SOURCE)`
 * 수준의 문서화/린트 마커일 뿐 Kotlin `@RequiresOptIn` 마커가 아니다(1.3.0 sources jar 확인 완료) —
 * 그래서 `@OptIn` 없이 컴파일된다. 이 확인은 컴파일타임 API 형태에 한정되지만, 런타임에 실제로 폴드
 * 이벤트를 방출하는 컨텍스트는 후보 ②로 실기기 판정이 끝났다.
 *
 * v1 은 수집 도중 예외/완료 시 자동 재시작하지 않는다 — [start] 를 다시 호출해야 재시도한다.
 */
class FoldStateMonitor(private val service: AccessibilityService) {

    private var job: Job? = null

    /** 마지막으로 로그를 남긴 자세. "변화 시에만 로그" 규칙을 이 필드로 구현한다(콜백 자체는 매 방출마다 호출됨) */
    private var lastLoggedPosture: FoldPosture? = null

    /**
     * 폴드 상태 수집을 시작한다. [onPosture] 는 [scope] 의 디스패처에서 호출된다 — 서비스가 넘기는
     * scope 가 이미 Main.immediate 이므로 여기서 별도 디스패처 전환을 하지 않는다(호출측 계약).
     */
    fun start(scope: CoroutineScope, onPosture: (FoldPosture) -> Unit) {
        stop()
        job = scope.launch {
            val candidates = candidateContexts()
            for ((index, context) in candidates.withIndex()) {
                val finishedCleanly = collectFrom(context, onPosture)
                if (finishedCleanly) return@launch
                Log.w(TAG, "FoldStateMonitor: 컨텍스트 후보 #$index 실패 — 다음 후보로 폴백")
            }
            Log.w(TAG, "FoldStateMonitor: 모든 컨텍스트 후보 실패 — 폴드 감지 기능 비활성(기능만 꺼짐, 크래시 아님)")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * 후보 컨텍스트 하나로 수집을 시도한다.
     * @return 예외(취소 제외) 발생 시 false — 호출자가 다음 후보로 넘어간다. Flow 가 예외 없이
     *   완료되는 경우(v1 범위에서는 비정상 상황 — 원래 서비스 수명과 함께 무한히 방출돼야 한다)도
     *   true 로 취급해 재시도하지 않는다(자동 재시작은 v1 범위 밖).
     */
    private suspend fun collectFrom(context: Context, onPosture: (FoldPosture) -> Unit): Boolean {
        return try {
            WindowInfoTracker.getOrCreate(context).windowLayoutInfo(context).collect { info ->
                val posture = mapPosture(info)
                if (posture != lastLoggedPosture) {
                    val hingeBounds = info.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()?.bounds
                    Log.i(TAG, "fold posture changed: $lastLoggedPosture -> $posture hingeBounds=$hingeBounds")
                    lastLoggedPosture = posture
                }
                onPosture(posture)
            }
            Log.w(TAG, "FoldStateMonitor: windowLayoutInfo Flow 가 예기치 않게 완료됨 — 종료(v1 자동 재시작 없음)")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "FoldStateMonitor: windowLayoutInfo 수집 중 예외 — 이 후보 포기", e)
            false
        }
    }

    /**
     * 시도 순서(실기기 판정 반영 — 클래스 KDoc 참고): ① 서비스 자신 ② `createWindowContext(display,
     * TYPE_ACCESSIBILITY_OVERLAY, null)`(3-인자, API 31+) ③ `createDisplayContext(display)
     * .createWindowContext(TYPE_ACCESSIBILITY_OVERLAY, null)`(②가 막힐 경우의 벨트-앤-서스펜더).
     * (구) 2-인자 `createWindowContext(TYPE_ACCESSIBILITY_OVERLAY, null)` 단독 후보는 실기기에서
     * display 미연결로 생성 자체가 불가능함이 판명되어 제거했다.
     *
     * display 는 [DisplayManager] 로 조회한다 — 조회 실패/널이면 ②·③ 은 애초에 시도하지 않고 후보
     * ①만 반환한다(둘 다 display 필수 파라미터). ②는 API 31+ 전용 API 라 minSdk(30)에서는
     * `Build.VERSION.SDK_INT` 로 가드한다 — ③은 `createDisplayContext`(API 17)·2-인자
     * `createWindowContext`(API 30) 모두 minSdk 이하라 별도 가드가 필요 없다. 각 신규 후보 생성
     * 실패(runCatching)는 나머지 후보 시도를 막지 않는다.
     */
    private fun candidateContexts(): List<Context> {
        val candidates = mutableListOf<Context>(service)

        val display = runCatching {
            service.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        }.onFailure { Log.w(TAG, "FoldStateMonitor: DisplayManager.getDisplay 조회 실패", it) }
            .getOrNull()

        if (display == null) {
            Log.w(TAG, "FoldStateMonitor: display 를 얻지 못해 후보 ②·③ 생략 — 후보 ①(서비스 자신)만 시도")
            return candidates
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                service.createWindowContext(display, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
            }.onSuccess { candidates.add(it) }
                .onFailure { Log.w(TAG, "FoldStateMonitor: 3-인자 createWindowContext(display) 후보 생성 실패", it) }
        } else {
            Log.w(TAG, "FoldStateMonitor: API ${Build.VERSION.SDK_INT} < 31 — 3-인자 createWindowContext 후보 생략")
        }

        runCatching {
            service.createDisplayContext(display)
                .createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        }.onSuccess { candidates.add(it) }
            .onFailure { Log.w(TAG, "FoldStateMonitor: createDisplayContext().createWindowContext() 후보 생성 실패", it) }

        return candidates
    }

    /**
     * 첫 [FoldingFeature] 를 [FoldPosture] 로 사상한다. FoldingFeature 가 없으면(폴더블이 아니거나
     * 현재 창이 힌지와 겹치지 않음) UNKNOWN. `FoldingFeature.State`/`Orientation` 은 enum 이 아니라
     * private 생성자를 가진 일반 클래스라 `when` 이 exhaustive 하지 않으므로 `else` 분기가 필요하다.
     */
    private fun mapPosture(layoutInfo: WindowLayoutInfo): FoldPosture {
        val feature = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
            ?: return FoldPosture.UNKNOWN
        return when {
            feature.state == FoldingFeature.State.HALF_OPENED &&
                feature.orientation == FoldingFeature.Orientation.HORIZONTAL -> FoldPosture.HALF_OPENED_HORIZONTAL

            feature.state == FoldingFeature.State.HALF_OPENED &&
                feature.orientation == FoldingFeature.Orientation.VERTICAL -> FoldPosture.HALF_OPENED_VERTICAL

            feature.state == FoldingFeature.State.FLAT -> FoldPosture.FLAT

            else -> FoldPosture.UNKNOWN
        }
    }

    companion object {
        private const val TAG = "FWFoldStateMonitor"
    }
}
