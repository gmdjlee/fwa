package dev.dj.foldwindow.domain

/*
 * 순수 Kotlin. android.* import 금지 (CLAUDE.md 아키텍처 규칙).
 *
 * #30 (docs/DESIGN_30_FULLSCREEN_AUTO.md §2.1): 자동 트리거의 재발화를 억제하는 장부.
 * [TriggerSource] 를 같은 파일에 둔다 — FlexModePolicy.kt 가 FoldPosture 를 동거시키는 선례.
 *
 * 스레딩: FlexModePolicy·FullscreenPlaybackPolicy 와 동일하게 모든 public 멤버는 서비스 메인
 * 스레드(Dispatchers.Main.immediate)에서만 접근된다고 가정한다 — 내부 상태 변이에 동기화가 없다.
 */

/**
 * 배치 세션이 무엇 때문에 시작됐는가. 세션의 진단 표면(토스트/로그)과 취소 조건이 기원별로
 * 달라야 하기 때문에 필요하다(설계서 D4·D19).
 *
 * - 배치 근거(`placementSource`)와 혼동하지 말 것. `placementSource == "FLEX"` 는 "어느 쪽에
 *   붙일지를 자세로 정했다"는 뜻이지 "자세가 세션을 시작했다"는 뜻이 아니다 — 이 둘을 프록시로
 *   재사용한 것이 D4(수동 세션의 오취소 + 잘못된 토스트)의 원인이었다.
 * - 자동 세션은 사용자가 요청한 적이 없으므로 진단 문구를 토스트로 띄우지 않는다(로그만).
 */
enum class TriggerSource {
    MANUAL, SHORTCUT, FLEX_AUTO, FULLSCREEN_AUTO;

    /** 사용자의 명시적 조작 없이 시작된 세션인가 */
    val isAuto: Boolean get() = this == FLEX_AUTO || this == FULLSCREEN_AUTO
}

/**
 * 자동 트리거의 **패키지 단위 에피소드 래치** + 연속 실패 서킷브레이커.
 *
 * 설계서 D1·D2 의 유일한 해법이다. 초안이 쓰던 "자가유발 재진입 억제창"(시간 기반)은 두 가지
 * 이유로 원리상 성립하지 않았다: ① 셰이드 개폐·잠금해제·일시정지 같은 사용자 조작이 새 진입
 * 엣지를 만들어 사용자의 명시적 해제를 무효화한다(D1) ② 해제→재진입 간격이 사용자 페이스라
 * 억제창 크기를 정할 근거가 존재하지 않는다(D2). 그래서 **해제 조건에서 시간을 완전히 제거**하고
 * 사건 기반으로 바꾼다.
 *
 * **래치 계약**
 * - **세팅**: ① 자동 발화 직전([onAutoFired]) — 성공·실패와 무관하다(D3) ② 우리 분할이 해제된 것을
 *   관측했을 때([onSplitDismissed]) — 해제 직후 앱이 전체화면으로 자동 복귀하며 만드는 진입 엣지가
 *   곧바로 재발화로 이어지는 P-1 루프를 여기서 끊는다.
 * - **해제**: ① 포그라운드가 래치 패키지를 떠남([onForeground]) ② 사용자가 **수동으로** 배치를
 *   트리거([onManualTrigger]) — 자동화에 대한 재신뢰 신호로 해석한다.
 * - **시간은 해제 조건이 아니다.**
 *
 * 트레이드오프(명시): 같은 앱에서 다음 영상으로 넘어가도 자동 재발화하지 않는다. 탈출구는 버블
 * 탭(수동)이며, 그것이 곧 재신뢰 신호로 래치를 푼다(설계서 R7).
 *
 * ## 래치 두 종류 — sticky / non-sticky (#31 2차, 22차 실측 회귀 대응)
 *
 * [onForeground] 의 "떠남" 판정에는 예외가 하나 있다. **분할 해제 전환 중에 One UI 가 홈 런처를
 * 잠깐 노출**하는데, 그 런처 이벤트가 [onSplitDismissed] 가 방금 건 재래치를 0.3초 만에 풀어버려
 * P-1 루프가 그대로 되살아났다(22차 실측):
 *
 * ```
 * 07:02:56.698 fullscreen signal: FULLSCREEN -> NOT_FULLSCREEN appFull=1 topBars=1
 * 07:02:57.004 auto latch released: foreground=com.sec.android.app.launcher  ← 재래치가 즉시 풀림
 * 07:02:59.662 fullscreen signal: NOT_FULLSCREEN -> FULLSCREEN appFull=1 topBars=0
 * 07:03:02.675 fullscreen auto-arrange trigger: target=com.google.android.youtube ← 재발화
 * ```
 *
 * 시간창(grace period)으로 덮는 것은 **금지**다 — 해제→재진입 간격이 사용자 페이스라 창 크기를
 * 정할 근거가 원리상 없다는 것이 D2 의 결론이고, ADR-2 위반이기도 하다. 대신 **래치의 기원**으로
 * 가른다: [onSplitDismissed] 가 건 래치는 **sticky** 라 홈 런처로는 풀리지 않고, [onAutoFired] 가
 * 건 래치는 non-sticky 라 홈으로 풀린다.
 *
 * | 경로 | 래치 종류 | 홈 이벤트 | 결과 |
 * |---|---|---|---|
 * | 자동 배치 → 홈 → 복귀 | non-sticky | 해제함 | **재발화 1회** (#31 수정, 설계서 §6 W1-3 명세) |
 * | 자동 배치 → 분할 해제 → (전환 중 런처 블립) | sticky | 무시 | **재발화 0** (P-1 차단) |
 * | 자동 배치 → 해제 → 홈 → **다른 앱** → 복귀 | sticky | 다른 앱이 해제 | 재발화 1회 |
 * | 자동 배치 → 해제 → 홈 → **같은 앱** 복귀 | sticky | 무시 | 재발화 0 — 탈출구는 **버블 탭(수동)** |
 *
 * 마지막 행은 **의도된 비대칭**이다. 분할 해제는 "이 배치를 원하지 않는다"는 사용자의 명시적
 * 신호이므로, 같은 앱으로 그냥 돌아오는 것만으로 자동화를 재신뢰하지 않는다(D2·R7 의도 그대로).
 * 1행과 4행이 갈리는 이유가 바로 이것이다 — 1행에는 그런 명시적 거부 신호가 없다.
 *
 * @param maxFailStreak 이 횟수만큼 자동 세션이 **연속** 실패하면 해당 패키지의 자동 트리거를 이
 *   부팅 세션 동안 비활성화한다([isDisabled]). 기본값 [DEFAULT_MAX_FAIL_STREAK](2).
 *   **[미검증]** — 실기기 실패 클래스(#27 17차 4연속 전멸, #29 MENU menuStep4 3attempt 전멸)가
 *   재현성 있음을 근거로 보수적으로 2회. 3회 이상 시도할 근거가 없다.
 */
class AutoTriggerLedger(private val maxFailStreak: Int = DEFAULT_MAX_FAIL_STREAK) {

    companion object {
        const val DEFAULT_MAX_FAIL_STREAK = 2
    }

    /** 현재 래치가 걸린 패키지. 동시에 두 앱이 포그라운드일 수 없으므로 1개면 충분하다 */
    private var latchedPackage: String? = null

    /**
     * 마지막으로 자동 발화한 대상 패키지. 래치가 이미 풀린 뒤에도 기억해야 [onSplitDismissed] 가
     * 재래치할 대상을 알 수 있다(D1·D2 핵심 — 분할 해제는 래치가 풀린 상태에서 일어날 수 있다).
     */
    private var lastAutoPackage: String? = null

    /**
     * 현재 [latchedPackage] 가 [onSplitDismissed] 로 걸린 래치인가(= sticky). sticky 래치는 홈
     * 런처 포그라운드로 **풀리지 않는다** — 분할 해제 전환 중 One UI 가 잠깐 노출하는 런처 블립이
     * 재래치를 즉시 무효화하는 것을 막는다(클래스 KDoc 의 22차 실측 로그 참고).
     *
     * `latchedPackage == null` 이면 의미 없는 값이라 항상 false 로 함께 되돌린다.
     */
    private var latchSticky: Boolean = false

    /** 패키지별 연속 실패 횟수. 성공 1회 또는 수동 트리거가 0으로 되돌린다 */
    private val failStreaks: MutableMap<String, Int> = mutableMapOf()

    /** 이 패키지에 대해 자동 트리거가 이미 소진됐는가(재발화 금지) */
    fun isLatched(pkg: String): Boolean = latchedPackage == pkg

    /** 연속 실패로 이 부팅 세션 동안 자동 트리거가 영구 비활성인가 */
    fun isDisabled(pkg: String): Boolean = failStreak(pkg) >= maxFailStreak

    /**
     * 현재 연속 실패 횟수. 설계서 §3.2 게이트 11 이 실패 사유 로그에 스트릭을 함께 남기도록
     * 규정하고 있어(`log(pkg, streak)`) 서비스가 읽을 수 있어야 한다.
     */
    fun failStreak(pkg: String): Int = failStreaks[pkg] ?: 0

    /**
     * 자동 발화 **직전** 호출한다. 성공·실패와 무관하게 래치한다 — 실패한 자동 세션이 스스로
     * 재무장 엣지를 생산해 같은 실패를 반복하는 것을 막기 위함이다(D3).
     *
     * 여기서 거는 래치는 **non-sticky** 다 — 사용자가 홈으로 나갔다 돌아오면 풀린다(#31, W1-3).
     * 이 경로에는 "이 배치를 원하지 않는다"는 사용자의 명시적 거부 신호가 없기 때문이다.
     */
    fun onAutoFired(pkg: String) {
        latchedPackage = pkg
        lastAutoPackage = pkg
        latchSticky = false
    }

    /**
     * 자동 세션의 터미널 보고. 실패는 스트릭을 1 올리고, 성공은 0으로 리셋한다.
     * [maxFailStreak] 에 도달하면 [isDisabled] 가 true 가 되어 게이트 11 이 이후 발화를 막는다.
     */
    fun onAutoResult(pkg: String, success: Boolean) {
        if (success) {
            failStreaks.remove(pkg)
        } else {
            failStreaks[pkg] = failStreak(pkg) + 1
        }
    }

    /**
     * 사용자가 **수동으로** 배치를 트리거했다 = 자동화 재신뢰 신호. 해당 패키지의 래치와 실패
     * 스트릭을 함께 푼다. 다른 패키지에 걸린 래치는 건드리지 않는다 — 그 래치는 포그라운드가
     * 이미 떠났거나 곧 떠나면서([onForeground]) 자연히 풀린다.
     */
    fun onManualTrigger(pkg: String) {
        if (latchedPackage == pkg) {
            latchedPackage = null
            // sticky 도 함께 지운다 — 수동 트리거는 분할 해제(거부 신호)보다 **나중에** 온 더
            // 강한 신호이며, 이것이 sticky 래치의 유일하고 명시적인 탈출구다(R7).
            latchSticky = false
        }
        failStreaks.remove(pkg)
    }

    /**
     * 포그라운드 전환 관측. 래치 패키지를 **떠났을 때만** 래치를 푼다(시간과 무관한 유일한 해제 조건).
     *
     * [pkg] 가 null 이면 아무것도 하지 않는다 — 창 전환 도중의 순간적인 "포그라운드 미상" 블립이
     * 래치를 조기 소멸시키면 P-1 루프가 되살아난다. 모르는 것은 해제 근거가 되지 못한다.
     *
     * **어떤 신호를 여기까지 흘릴지는 이 장부가 정하지 않는다** — 호출부가
     * [ForegroundSignalPolicy.releasesLatch] 로 먼저 거른다(#31: 홈 런처 이벤트가 서비스의
     * 추적용 제외 목록에 걸려 여기까지 도달하지 못하던 결함). **어느 신호가 실제로 래치를
     * 푸는지**(= sticky 판정)는 반대로 이 장부만이 안다 — 클래스 KDoc 의 동작 표 참고.
     *
     * @param isHome 이 포그라운드가 홈 런처인가. sticky 래치는 이 값이 true 인 이벤트로는 풀리지
     *   않는다(분할 해제 전환 중의 런처 블립 방어, 22차 실측). **기본값을 두지 않는다** — 호출부가
     *   매번 명시하게 해서 누락이 컴파일 에러로 드러나게 한다(조용한 실패 금지).
     * @return 이 호출이 **실제로 래치를 푼 경우에만** true. 서비스는 이 값으로 해제 사실을
     *   logcat 에 남긴다(CLAUDE.md "조용한 실패 금지" — 21차에서 래치 해제가 로그에 전혀 안 남아
     *   #31 원인 규명에 실기기 분리 실험이 필요했다).
     */
    fun onForeground(pkg: String?, isHome: Boolean): Boolean {
        if (pkg == null) return false
        // 래치가 없거나(풀 것이 없음) 아직 래치 패키지에 머무는 중이면 해제 대상이 아니다.
        if (latchedPackage == null || latchedPackage == pkg) return false
        // sticky 래치 + 홈 = 우리 분할 해제 전환이 만든 런처 블립. 해제 근거로 삼지 않는다.
        if (isHome && latchSticky) return false
        latchedPackage = null
        latchSticky = false
        return true
    }

    /**
     * 우리 분할이 해제된 것을 관측했다 → 마지막 자동 대상 패키지를 **재래치**한다.
     *
     * D1·D2 의 핵심 지점이다. 사용자가 분할을 해제하면 대상 앱은 대개 전체화면 재생으로 자동
     * 복귀하는데, 그 복귀가 그대로 새 진입 엣지가 되어 방금 사용자가 되돌린 배치를 다시 실행해
     * 버린다(P-1 루프). 재래치는 그 엣지를 게이트 10 에서 조용히 죽인다.
     *
     * 자동 발화 이력이 없으면([lastAutoPackage] == null) 무해한 no-op 이다 — 수동 배치의 해제까지
     * 억제 대상으로 삼지 않는다. 그 경우 [latchSticky] 도 false 로 남는다(래치가 없으면 sticky 는
     * 의미가 없다).
     *
     * 여기서 거는 래치는 **sticky** 다 — 분할 해제는 "이 배치를 원하지 않는다"는 사용자의 명시적
     * 신호이므로, 그 직후의 홈 런처 노출(One UI 전환 애니메이션)이 재래치를 무효화하면 안 된다.
     */
    fun onSplitDismissed() {
        latchedPackage = lastAutoPackage
        latchSticky = latchedPackage != null
    }

    /** 상태를 완전히 초기화한다(접근성 서비스 재연결 등). 실패 스트릭도 함께 지워진다 */
    fun reset() {
        latchedPackage = null
        lastAutoPackage = null
        latchSticky = false
        failStreaks.clear()
    }
}
