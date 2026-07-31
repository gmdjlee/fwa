package dev.dj.foldwindow.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ForegroundSignalPolicy] — 포그라운드 이벤트의 두 소비자(추적 / 래치 해제) 분기 계약 검증.
 *
 * 이 정책이 닫는 것은 결함 #31(21차 실기기, 2026-08-01)이다: 홈 경유 시 자동 트리거 래치가 풀리지
 * 않아 설계서 §6 W1-3(「홈 → 복귀 → 재발화 1회」)이 성립하지 않았다. 동시에 **회귀시키면 안 되는
 * 두 가지**를 함께 동결한다 — P-1 루프(셰이드 개폐 발 재발화)와 D3(실패한 자동 세션의 자기 재무장).
 *
 * **책임 경계**: 이 정책은 "이 이벤트를 래치 판정에 흘릴 것인가"만 정한다. 흘려보낸 이벤트가
 * 실제로 래치를 푸는지는 [AutoTriggerLedger] 의 sticky 판정 몫이다(#31 2차) — 그쪽 계약은
 * `AutoTriggerLedgerTest` 가 덮는다. 이 파일의 단언은 #31 2차에서 **하나도 바뀌지 않았다**
 * (정책 함수 동작 무변경).
 */
class ForegroundSignalPolicyTest {

    private val self = "dev.dj.foldwindow"
    private val youtube = "com.google.android.youtube"
    private val chrome = "com.android.chrome"
    private val launcher = "com.sec.android.app.launcher"
    private val systemUi = "com.android.systemui"

    /** 서비스의 `EXCLUDED_FOREGROUND_PACKAGES` 실제 값(2026-07-25 실측 근거). 이 목록은 수정 대상이 아니다. */
    private val excluded = setOf(
        launcher,
        systemUi,
        "com.samsung.android.app.cocktailbarservice",
        "com.samsung.android.sidegesturepad",
    )

    // ── tracksAsForeground: 구 동작 동결 ──────────────────────────

    @Test
    fun `tracksAsForeground rejects null self package and excluded packages`() {
        assertFalse("식별 실패(null)는 추적 근거가 아니다", ForegroundSignalPolicy.tracksAsForeground(null, self, excluded))
        assertFalse("우리 앱 창은 배치 대상이 아니다", ForegroundSignalPolicy.tracksAsForeground(self, self, excluded))
        for (pkg in excluded) {
            assertFalse(
                "제외 목록($pkg)은 세션 중 추적을 오염시킨다 — 구 동작 그대로 걸러야 한다",
                ForegroundSignalPolicy.tracksAsForeground(pkg, self, excluded),
            )
        }
    }

    @Test
    fun `tracksAsForeground accepts an ordinary application package`() {
        assertTrue(ForegroundSignalPolicy.tracksAsForeground(youtube, self, excluded))
        assertTrue(ForegroundSignalPolicy.tracksAsForeground(chrome, self, excluded))
    }

    // ── releasesLatch: #31 수정의 본체 ────────────────────────────

    /**
     * **#31 회귀 가드.** 홈 런처는 `EXCLUDED_FOREGROUND_PACKAGES` 에 들어 있지만(추적 오염원),
     * "대상 앱을 떠났다"의 증거로는 유효하다. 21차 실측에서 이 경로가 `reason=latched` 로
     * 2회 재현됐고, 같은 조건에서 Chrome 경유는 정상 재발화했다.
     *
     * 여기서 true 는 **"래치 판정에 흘려보내라"** 이지 "해제하라"가 아니다 — 홈 이벤트가 실제로
     * 래치를 푸는지는 [AutoTriggerLedger.onForeground] 의 sticky 판정이 정한다(#31 2차, 22차
     * 실측: 분할 해제로 걸린 sticky 래치는 홈으로 풀리지 않는다). 그 계약은
     * `AutoTriggerLedgerTest` 의 sticky 절이 덮는다.
     */
    @Test
    fun `releasesLatch accepts the home launcher even though it is excluded`() {
        assertTrue(
            "홈 이동은 대상 앱을 떠났다는 가장 흔한 증거다 (#31)",
            ForegroundSignalPolicy.releasesLatch(launcher, self, launcher, excluded, sessionActive = false),
        )
    }

    /**
     * **P-1 루프 회귀 가드.** 알림 셰이드 개방은 `com.android.systemui` 의
     * `TYPE_WINDOW_STATE_CHANGED` 를 낸다. 21차 실측: **셰이드 개폐만으로 전체화면 진입 엣지가
     * 재생성된다**(W1-2, W0-5 에서 셰이드 개방 시 술어가 NOT_FULLSCREEN → 닫으면 다시 FULLSCREEN).
     * 셰이드가 래치를 푼다면 「내림 → 올림 → 재발화」로 P-1 루프가 그대로 되살아나므로,
     * 홈만 예외 처리하고 systemui 는 제외 목록 규칙에 계속 맡긴다.
     */
    @Test
    fun `releasesLatch keeps rejecting systemui so the shade cannot re-arm the trigger`() {
        assertFalse(
            "셰이드 개폐가 래치를 풀면 P-1 루프가 되살아난다 (21차 실측)",
            ForegroundSignalPolicy.releasesLatch(systemUi, self, launcher, excluded, sessionActive = false),
        )
        // 홈이 아닌 나머지 제외 패키지도 동일하게 막혀야 한다(오버레이 발 이벤트).
        assertFalse(
            ForegroundSignalPolicy.releasesLatch(
                "com.samsung.android.sidegesturepad", self, launcher, excluded, sessionActive = false,
            ),
        )
    }

    /**
     * **D3 회귀 가드.** 우리 분할 진입 경로(step1~3)는 Recents = 런처를 지난다. 세션 진행 중에
     * 그 런처 이벤트가 래치를 풀면 **실패한 자동 세션이 스스로 재무장 엣지를 만들어** 같은 실패를
     * 반복한다. 자동 실패 후 BACK 복구 구간도 `sessionActive` 에 포함된다(그 구간은 상태 머신이
     * 이미 Idle 로 돌아간 뒤에 돌기 때문 — 서비스의 `autoRecoveryInFlight`).
     */
    @Test
    fun `releasesLatch rejects everything while a session is active`() {
        assertFalse(
            "세션 중 Recents(런처) 통과가 래치를 풀면 D3 가 무력화된다",
            ForegroundSignalPolicy.releasesLatch(launcher, self, launcher, excluded, sessionActive = true),
        )
        assertFalse(ForegroundSignalPolicy.releasesLatch(chrome, self, launcher, excluded, sessionActive = true))
        assertFalse(ForegroundSignalPolicy.releasesLatch(youtube, self, launcher, excluded, sessionActive = true))
    }

    /**
     * 런처 해석 실패(`homePackage == null`)는 **구 동작으로 폴백**한다 — 재허용 없음.
     * fail-safe 방향이다: 래치가 안 풀리면 미발화이고, 미발화는 오발화보다 언제나 안전하다.
     */
    @Test
    fun `releasesLatch falls back to the old behaviour when the home package is unknown`() {
        assertFalse(
            "홈 패키지를 모르면 런처는 그냥 제외 목록 항목일 뿐이다",
            ForegroundSignalPolicy.releasesLatch(launcher, self, null, excluded, sessionActive = false),
        )
        // 폴백은 "전부 거부"가 아니다 — 비-제외 패키지는 그대로 통과한다.
        assertTrue(ForegroundSignalPolicy.releasesLatch(chrome, self, null, excluded, sessionActive = false))
    }

    @Test
    fun `releasesLatch rejects null and our own package`() {
        assertFalse(
            "모르는 것은 해제 근거가 되지 못한다 (AutoTriggerLedger.onForeground 계약)",
            ForegroundSignalPolicy.releasesLatch(null, self, launcher, excluded, sessionActive = false),
        )
        assertFalse(
            "우리 PanelActivity 가 분할 페인으로 뜨는 것은 사용자가 앱을 떠난 것이 아니다",
            ForegroundSignalPolicy.releasesLatch(self, self, launcher, excluded, sessionActive = false),
        )
    }

    /**
     * 21차 분리 실험의 대조군을 동결한다: Chrome(비-제외 패키지) 경유는 **재발화 1회**가
     * 관측됐다(`arrange done … trigger=FULLSCREEN_AUTO`). 이 경로는 종전에도 동작했고,
     * #31 수정이 이를 깨뜨리지 않아야 한다.
     */
    @Test
    fun `releasesLatch accepts an ordinary non-excluded package`() {
        assertTrue(ForegroundSignalPolicy.releasesLatch(chrome, self, launcher, excluded, sessionActive = false))
        assertTrue(ForegroundSignalPolicy.releasesLatch(youtube, self, launcher, excluded, sessionActive = false))
    }
}
