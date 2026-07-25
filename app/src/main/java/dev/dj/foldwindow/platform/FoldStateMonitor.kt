package dev.dj.foldwindow.platform

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
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
 * [실기기 검증 대상, docs/DEVICE_FACTS.md "P3-5 FoldingFeature (미검증)" 절]
 * `WindowInfoTracker.windowLayoutInfo(Context)` 는 `@UiContext` 컨텍스트(Activity 이거나
 * `Context.createWindowContext()` 로 만든 컨텍스트)를 요구한다(androidx.window 1.3.0 소스 확인).
 * `AccessibilityService` 자신이 이 조건을 만족하는지는 문서화되어 있지 않아 실기기 확인 전까지는
 * 미지수다 — 그래서 후보 컨텍스트를 순서대로 시도한다: ① 서비스 자신 ② `createWindowContext`
 * (`TYPE_ACCESSIBILITY_OVERLAY`). 각 후보는 구독 시작 후에도 예외로 죽을 수 있어 후보별
 * try/catch 로 다음 후보에 폴백하고, 전부 실패하면 기능을 조용히 끄되(크래시 절대 금지) 로그로
 * 원인을 남긴다.
 *
 * [실기기 검증 대상] `@UiContext` 는 androidx.window 1.3.0 소스 기준 `@Retention(SOURCE)` 수준의
 * 문서화/린트 마커일 뿐 Kotlin `@RequiresOptIn` 마커가 아니다(1.3.0 sources jar 확인 완료) — 그래서
 * `@OptIn` 없이 컴파일된다. 다만 이 확인은 컴파일타임 API 형태에 한정되며, 런타임에 어떤 컨텍스트가
 * 실제로 폴드 이벤트를 방출하는지는 여전히 실기기 확인 대상이다.
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
     * 시도 순서: ① 서비스 자신 ② `createWindowContext(TYPE_ACCESSIBILITY_OVERLAY)`.
     * `createWindowContext` 자체가 실패해도(runCatching) 최소 후보 ①은 항상 시도한다.
     */
    private fun candidateContexts(): List<Context> {
        val candidates = mutableListOf<Context>(service)
        runCatching {
            service.createWindowContext(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
        }.onSuccess { candidates.add(it) }
            .onFailure { Log.w(TAG, "FoldStateMonitor: createWindowContext 후보 생성 실패", it) }
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
