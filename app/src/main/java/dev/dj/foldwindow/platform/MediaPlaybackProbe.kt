package dev.dj.foldwindow.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager

/**
 * 무권한 미디어 재생 확증. #30 설계서 §2.2.
 *
 * **게이트 의미 격하(명시)**: 이 프로브가 대답하는 질문은 "대상 앱이 재생 중인가"가 **아니라**
 * "이 기기에서 미디어 용도의 오디오가 나고 있는가"다. `AudioPlaybackConfiguration` 은 비특권
 * 앱에게 익명화된 사본으로 전달돼 어느 패키지의 재생인지 알 수 없다. 따라서 이 게이트는 발화
 * 조건의 **약한 필요조건**일 뿐이며, "재생 중이 아닌데 발화" 를 막아 줄 뿐 "다른 앱이 내는 소리로
 * 인한 발화" 는 막지 못한다. 그 나머지는 게이트 5(기하)·8(포그라운드)·9(자동 대상)가 담당한다.
 *
 * **금지 사항(설계서 §2.2 명시)**
 * - `AudioPlaybackConfiguration.getPlayerState()` / `isActive()` / `getClientUid()` 를 리플렉션으로
 *   부르지 말 것. 전부 `@hide @SystemApi` 이고, 비특권 앱이 받는 사본에서는 uid·player id 가
 *   익명화되어 있어 값 자체가 의미를 갖지 않는다.
 * - `isPackagePlaying(pkg)` 류 API 를 신설하지 말 것. 위와 같은 이유로 패키지 귀속이 구조적으로
 *   불가능하다 — 그럴듯한 시그니처를 만들면 호출부가 있지도 않은 보장을 믿게 된다.
 * - `registerAudioPlaybackCallback` 을 등록하지 말 것. 이 프로브의 호출 시점은 창 이벤트가 이미
 *   결정하고 있고(창 이벤트가 곧 wake 신호), 콜백 등록은 상시 구독 비용과 해제 누락 위험만 더한다.
 *
 * **동기 함수 · 무상태 · 결과 캐싱 금지**: [isMediaPlaying] 이 `suspend` 이면 게이트 체인의
 * `busy`~`startArrange` 원자 구간이 깨진다(설계서 D7). 결과를 캐싱하면 광고 전환·seek·버퍼
 * 언더런으로 요동치는 상태를 스테일하게 읽게 되므로 매 호출마다 실측한다 — 호출 빈도는
 * 트리거 대기 루프의 폴링 간격(250ms)이 상한이라 부담이 없다.
 *
 * 전 경로를 `runCatching` 으로 감싼다: One UI 의 `AudioService` 변형 가능성(설계서 R5)이
 * 사실검증에서 확정되지 않았다. 실패는 "재생 중 아님"(= 발화 안 함) 으로 접히므로 fail-safe 다.
 */
class MediaPlaybackProbe(context: Context) {

    /**
     * 시스템 서비스 **핸들**이다(결과 캐시가 아니다). 조회 자체가 실패하면 null 로 남고 이후 모든
     * 질의가 안전 기본값으로 접힌다. DI 프레임워크를 쓰지 않는 프로젝트 관례대로 호출부가 직접
     * 이 클래스를 생성한다(`ArrangerAccessibilityService` 의 `by lazy`).
     */
    private val audioManager: AudioManager? =
        runCatching { context.applicationContext.getSystemService(AudioManager::class.java) }.getOrNull()

    /**
     * `USAGE_MEDIA` 활성 재생이 하나라도 있는가.
     *
     * `contentType` 은 게이트 조건에서 **제외한다** — ExoPlayer 는 기본적으로
     * `CONTENT_TYPE_UNKNOWN` 으로 트랙을 열기 때문에 `CONTENT_TYPE_MOVIE` 를 요구하면 정작
     * 대상 앱들이 전부 탈락한다.
     */
    fun isMediaPlaying(): Boolean = runCatching {
        audioManager?.activePlaybackConfigurations
            ?.any { it.audioAttributes.usage == AudioAttributes.USAGE_MEDIA }
            ?: false
    }.getOrDefault(false)

    /**
     * 관측된 활성 재생의 `AudioAttributes.usage` 목록. **로깅 전용**(설계서 §9 Advisor 추가 요구 2 —
     * W0-7 을 별도 프로브 빌드 없이 logcat 으로 수행하기 위한 것)이며 게이트 판정에 쓰지 않는다.
     * 실패 시 빈 목록 — "관측 실패"와 "재생 없음"이 같은 값이 되지만, 호출부가 이 값으로 판정하지
     * 않으므로 무해하다.
     */
    fun activeUsages(): List<Int> = runCatching {
        audioManager?.activePlaybackConfigurations?.map { it.audioAttributes.usage } ?: emptyList()
    }.getOrDefault(emptyList())
}
