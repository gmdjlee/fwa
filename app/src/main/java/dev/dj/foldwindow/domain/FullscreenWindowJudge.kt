package dev.dj.foldwindow.domain

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙).
 *
 * #30 (docs/DESIGN_30_FULLSCREEN_AUTO.md §2.1): 창 서술자 목록 → [FullscreenSignal] 판정.
 * platform/FullscreenSignalSampler 가 AccessibilityWindowInfo 를 [WindowBox] 로 사상해 넘긴다
 * (platform/DividerLocator 가 PaneGeometry 에 기하를 위임하는 것과 동일 구조).
 *
 * `WindowKind` 같은 a11y 타입 열거형을 domain 에 들이지 않는다 — AccessibilityWindowInfo.TYPE_*
 * 의 2차 SSOT 가 생기는 것을 막기 위해, 판정에 실제로 필요한 유일한 구분(APPLICATION 인가)만
 * Boolean 하나로 압축해서 받는다(설계서 D21).
 */

/**
 * 판정에 필요한 최소 창 서술자.
 *
 * @param bounds 화면 좌표계 bounds(`getBoundsInScreen`). 화면 밖으로 슬라이드된 창은 음수 좌표를
 *   가질 수 있다(PaneGeometry KDoc 의 실측 함정) — [FullscreenWindowJudge] 가 가시 교집합으로 접는다.
 * @param isApplication `type == AccessibilityWindowInfo.TYPE_APPLICATION` 인가. 이 한 비트가
 *   domain 이 a11y 타입에 대해 아는 전부다.
 */
data class WindowBox(val bounds: IntRect, val isApplication: Boolean)

/**
 * 창 목록만으로 "지금 전체화면(몰입) 재생 형상인가"를 판정하는 순수 술어.
 *
 * **긍정 술어**다 — "상태바가 없다"는 부정형이 아니라 "화면을 통째로 덮는 앱 창이 있고, 상단
 * 전폭 시스템 바가 없다"는 형태로 쓴다. 부수 효과로 판정에 `packageName` 이 전혀 필요 없어져
 * `root` 노드 조회(IPC)가 0회가 되고, 평가 1회당 바인더 왕복이 `windows` 1회로 묶인다(설계서 D12).
 *
 * 실측 3표본에서 이 규칙이 내는 값(설계서 §2.1 표, 전부 검증 완료. 원본 덤프 =
 * docs/DEVICE_FACTS.md 「Phase 0 프로브 원본 측정」):
 * - 가로 몰입 재생 → FULLSCREEN (APP `0,0,2184,1968` = 화면 100%, 상단 전폭 바 없음)
 * - 세로 비몰입(엣지투엣지) → NOT_FULLSCREEN (전폭 상태바 `0,0,1968,89` 존재)
 * - 분할 활성 → NOT_FULLSCREEN (최대 페인 폭 977 = 49.6%)
 */
object FullscreenWindowJudge {

    /**
     * 상단 스트립 높이 = 화면 높이 × 이 비율. 이 스트립과 교차하는 창만 "상단 시스템 바 후보"다.
     *
     * 근거(설계서 §4): 세로 비몰입 프로브 실측 상태바 `0,0,1968,89` = 높이 89px
     * (docs/DEVICE_FACTS.md 「Phase 0 프로브 원본 측정」 런 ① B절) ÷ 화면 높이는
     * 가로에서 89/1968 = 4.52%, 세로에서 89/2184 = 4.07%. 여유를 포함해 6%.
     * 픽셀 상수가 아니라 비율인 이유는 CLAUDE.md 함정 #2(좌표 하드코딩 금지) — 방향/DPI 가 바뀌면
     * 화면 높이가 바뀐다.
     */
    const val TOP_STRIP_FRACTION = 0.06f

    /**
     * "화면을 통째로 덮는다" 판정 하한(가로·세로 각 축 비율).
     *
     * 근거(설계서 §4): 가로 몰입 프로브 실측 전체화면 창 `0,0,2184,1968` = 정확히 100%.
     * 반대편 군집인 분할 페인은 최대 977/1968 = 49.6%(분할 활성 프로브)라 두 군집 간격이
     * 절반 이상 벌어져 있다(docs/DEVICE_FACTS.md 「Phase 0 프로브 원본 측정」). 0.99 는 기존
     * `WindowGeometry.matchesScreen` 의 ±1% 허용오차와 같은 눈금이다.
     */
    const val FULL_COVER_MIN_FRACTION = 0.99f

    /**
     * 상단 시스템 바로 인정할 최소 폭 비율. 토스트·볼륨패널·자기 버블처럼 상단 스트립과 우연히
     * 교차하는 소형 창을 **구조적으로** 배제한다(설계서 D10).
     *
     * 근거(설계서 §4): 실측 상태바 폭 = 화면 폭 100%(세로 비몰입 프로브 `0,0,1968,89`).
     * 배제해야 할 최대 폭은 systemui 소형 창 214px/1968 = 10.9%(분할 활성 프로브 `381,89,595,145`)
     * 와 런처 우측 창 53px = 2.7%(세로 비몰입 프로브 `1915,235,1968,564`). 원본 덤프는 전부
     * docs/DEVICE_FACTS.md 「Phase 0 프로브 원본 측정」.
     * 두 군집 사이 어디에 두어도 결과가 같으므로 여유를 크게 잡아 80%.
     * 자기 버블(TYPE_SYSTEM, ~126px — DEVICE_FACTS.md P3-1)도 같은 필터에서 죽는다.
     */
    const val TOP_BAR_MIN_WIDTH_FRACTION = 0.80f

    /**
     * 창 목록을 3값 신호로 압축한다.
     *
     * - [windows] 가 비어 있으면 [FullscreenSignal.UNKNOWN]. **UNKNOWN 을 내는 경우는 이것 하나뿐**
     *   이다 — 정책이 UNKNOWN 을 무시 샘플로 다루므로(설계서 D6), 그 외의 판정 불확실은 전부
     *   보수적으로 [FullscreenSignal.NOT_FULLSCREEN] 쪽으로 접는다.
     * - 아래 둘을 **모두** 만족하면 [FullscreenSignal.FULLSCREEN]:
     *   (a) [WindowBox.isApplication] 이면서 [screen] 을 각 축 [FULL_COVER_MIN_FRACTION] 이상 덮는
     *       창이 1개 이상 존재
     *   (b) 비-APPLICATION 이면서 상단 스트립과 교차하고 폭이 [TOP_BAR_MIN_WIDTH_FRACTION] 이상인
     *       창이 0개
     * - 그 외는 [FullscreenSignal.NOT_FULLSCREEN].
     *
     * (a) 의 "덮는다"는 [PaneGeometry.visibleRect] 로 구한 **가시 교집합** 기준이다 — 실측상
     * 창이 리사이즈 대신 화면 밖으로 슬라이드되는 경우가 있어(PaneGeometry KDoc) 원 bounds 의
     * 폭·높이만 보면 화면을 절반만 덮는 창이 "전체 덮음"으로 오판정될 수 있다. 반대로 (b) 는 원
     * bounds 의 폭을 그대로 쓴다 — 상단 바를 더 많이 세는 쪽이 보수적(= 발화 안 함)이다.
     */
    fun judge(windows: List<WindowBox>, screen: IntRect): FullscreenSignal {
        if (windows.isEmpty()) return FullscreenSignal.UNKNOWN

        val minCoverWidth = screen.width * FULL_COVER_MIN_FRACTION
        val minCoverHeight = screen.height * FULL_COVER_MIN_FRACTION
        val hasFullCoverApp = windows.any { box ->
            if (!box.isApplication) return@any false
            val visible = PaneGeometry.visibleRect(box.bounds, screen) ?: return@any false
            visible.width >= minCoverWidth && visible.height >= minCoverHeight
        }
        if (!hasFullCoverApp) return FullscreenSignal.NOT_FULLSCREEN

        val topStripBottom = screen.height * TOP_STRIP_FRACTION
        val minTopBarWidth = screen.width * TOP_BAR_MIN_WIDTH_FRACTION
        val hasTopSystemBar = windows.any { box ->
            !box.isApplication &&
                box.bounds.top <= topStripBottom &&
                box.bounds.width >= minTopBarWidth
        }
        return if (hasTopSystemBar) FullscreenSignal.NOT_FULLSCREEN else FullscreenSignal.FULLSCREEN
    }
}
