package dev.dj.foldwindow.platform

import android.graphics.Rect
import android.view.accessibility.AccessibilityWindowInfo
import dev.dj.foldwindow.domain.FullscreenSignal
import dev.dj.foldwindow.domain.FullscreenWindowJudge
import dev.dj.foldwindow.domain.IntRect
import dev.dj.foldwindow.domain.PaneGeometry
import dev.dj.foldwindow.domain.WindowBox

/**
 * [FullscreenWindowJudge] 의 판정 근거 개수. **진단 전용**이다(설계서 §9 Advisor 추가 요구 1) —
 * 실제 판정은 언제나 [FullscreenSignalSampler.sample] → [FullscreenWindowJudge.judge] 가 한다.
 *
 * W0-1~W0-6(실기기 창 목록 측정)을 **별도 프로브 빌드 없이 logcat 만으로** 재구성할 수 있게 하는
 * 것이 존재 이유다. `judge` 는 3값 하나만 돌려주므로, 그 값이 "전체 덮는 앱 창이 없어서" 나온
 * NOT_FULLSCREEN 인지 "상단 전폭 시스템 바가 있어서" 나온 것인지 로그만으로 구분할 수 없다.
 */
data class FullscreenEvidence(val appFullCount: Int, val topBarCount: Int)

/**
 * `AccessibilityWindowInfo` 목록 → [FullscreenSignal] 매핑. #30 설계서 §2.2.
 *
 * [DividerLocator.applicationPaneRects] 와 동일한 구조다 — 이 클래스는 SDK 타입에서 bounds/type 을
 * 뽑아 [WindowBox] 로 사상하기만 하고, 기하 판정은 전부 domain([FullscreenWindowJudge])에 위임한다.
 * **IPC 를 지는 유일한 지점이며 파일 소속을 명시적으로 고정한다**(설계서 D21 — 매퍼가 소속 없이
 * 떠다니면 2000행대 서비스로 흡수되고, 그러면 a11y 타입 상수의 2차 SSOT 가 생긴다).
 *
 * **`root?.packageName` 을 조회하지 않는다.** 판정 규칙이 긍정 술어(전체 덮는 APPLICATION 창 존재
 * ∧ 상단 전폭 비-APPLICATION 창 부재)라 패키지명이 아예 필요 없다 — 그 덕에 평가 1회당 바인더
 * 왕복이 `windows` 조회 1회로 묶인다(설계서 D12: 메인 스레드 N+1 IPC 가 세션 예산을 잠식하는
 * 문제). 이 클래스에 패키지 조회를 되살리면 그 설계 전제가 무너지므로 추가 금지.
 *
 * 창 필드 접근(`type`/`getBoundsInScreen`)은 probe/ProbeAccessibilityService·[DividerLocator] 의
 * 관례대로 `runCatching` 으로 방어한다 — 스테일 창 핸들은 예외를 던질 수 있다.
 */
class FullscreenSignalSampler {

    /**
     * 창 목록을 3값 신호로 압축한다. 빈 목록(또는 전 창 매핑 실패)은
     * [FullscreenWindowJudge.judge] 계약에 따라 [FullscreenSignal.UNKNOWN] 이 되고, 정책은 그것을
     * 무시 샘플로 다룬다(설계서 D6).
     */
    fun sample(windows: List<AccessibilityWindowInfo>, screen: IntRect): FullscreenSignal =
        FullscreenWindowJudge.judge(toWindowBoxes(windows), screen)

    /**
     * [sample] 과 **같은 규칙**으로 판정 근거 개수만 센다(설계서 §9 전이 로깅용).
     *
     * 서비스가 신호 전이를 관측했을 때만 호출한다 — 매 샘플마다 부르면 최대 10Hz 로그 스팸이 된다.
     * 임계값은 전부 [FullscreenWindowJudge] 의 상수를 그대로 재사용하므로 상수의 2차 SSOT 는
     * 생기지 않는다. 다만 술어의 **형태**는 여기서 한 번 더 쓰이므로, `judge` 의 규칙을 고치면
     * 이 함수도 함께 고쳐야 한다(진단값이 판정과 어긋나면 W0 측정이 거짓말을 한다).
     */
    fun evidence(windows: List<AccessibilityWindowInfo>, screen: IntRect): FullscreenEvidence {
        val boxes = toWindowBoxes(windows)
        val minCoverWidth = screen.width * FullscreenWindowJudge.FULL_COVER_MIN_FRACTION
        val minCoverHeight = screen.height * FullscreenWindowJudge.FULL_COVER_MIN_FRACTION
        val appFullCount = boxes.count { box ->
            if (!box.isApplication) return@count false
            val visible = PaneGeometry.visibleRect(box.bounds, screen) ?: return@count false
            visible.width >= minCoverWidth && visible.height >= minCoverHeight
        }

        val topStripBottom = screen.height * FullscreenWindowJudge.TOP_STRIP_FRACTION
        val minTopBarWidth = screen.width * FullscreenWindowJudge.TOP_BAR_MIN_WIDTH_FRACTION
        val topBarCount = boxes.count { box ->
            !box.isApplication &&
                box.bounds.top <= topStripBottom &&
                box.bounds.width >= minTopBarWidth
        }
        return FullscreenEvidence(appFullCount = appFullCount, topBarCount = topBarCount)
    }

    /**
     * a11y 창 → [WindowBox]. `type`/bounds 조회에 실패한 창은 **목록에서 버린다**.
     *
     * 버리는 방향이 보수적인 이유: 버려진 창이 마침 "전체를 덮는 앱 창" 이었다면 판정 규칙 (a) 가
     * 거짓이 되어 자연히 [FullscreenSignal.NOT_FULLSCREEN](= 발화 안 함)으로 접힌다. 반대로 버려진
     * 창이 상단 시스템 바였다면 (b) 가 참이 되어 FULLSCREEN 쪽으로 기울 수 있지만, 그 경우에도
     * (a) 를 만족하는 전체 덮음 앱 창이 함께 관측돼야만 발화하므로 단독으로 오발화를 만들지 못한다.
     *
     * [IntRect] 는 `right >= left ∧ bottom >= top` 을 생성자에서 강제하므로, 뒤집힌 bounds 를
     * 보고하는 병리적 창도 같은 `runCatching` 에서 걸러진다.
     */
    private fun toWindowBoxes(windows: List<AccessibilityWindowInfo>): List<WindowBox> =
        runCatching {
            windows.mapNotNull { w ->
                runCatching {
                    val isApplication = w.type == AccessibilityWindowInfo.TYPE_APPLICATION
                    val bounds = Rect().also { w.getBoundsInScreen(it) }
                    WindowBox(
                        bounds = IntRect(bounds.left, bounds.top, bounds.right, bounds.bottom),
                        isApplication = isApplication,
                    )
                }.getOrNull()
            }
        }.getOrDefault(emptyList())
}
