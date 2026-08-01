package dev.dj.foldwindow.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.DateFormat as AndroidDateFormat
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dev.dj.foldwindow.R
import dev.dj.foldwindow.data.ProfileStore
import dev.dj.foldwindow.data.ProfileStoreMapping
import dev.dj.foldwindow.service.ArrangerAccessibilityService
import dev.dj.foldwindow.ui.theme.FoldWindowTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * P2-5 / ADR-3: 파트너(비영상) 창. 도메인 `PartnerMode`(Profiles.kt, JSON 스키마 소속)는 BLACK 으로
 * 고정이며, 그 위에 P4-2 부터 사용자가 직접 고르는 UI 표시 위젯 3종([PanelWidgetMode]: 시계/메모/
 * 검정)을 얹는다 — 위젯 전환은 순수 표시 선호일 뿐 도메인 스키마와 무관하다.
 *
 * ⚠ @string/panel_title 값은 반드시 "FW Panel" 이어야 한다. platform/SplitEntry.kt 의 step4
 * 폴백(findPanelPickerNode)이 Recents 파트너 피커에서 이 라벨 문자열로 우리 앱을 찾는다.
 *
 * 고정 크기 가정 금지 (CLAUDE.md 함정 #5): Android 16 적응형 동작 대상 기기(sw≥600dp)에서
 * 이 액티비티는 임의 크기로 리사이즈될 수 있다. 레이아웃은 항상 fillMaxSize 기준으로 반응한다.
 */
class PanelActivity : ComponentActivity() {

    private var fullscreenGuardJob: Job? = null

    // [P4-2] 기존 파일 전반에 Hilt 등 DI 없음 — OnboardingActivity/FloatingLauncherService 와
    // 동일하게 액티비티가 직접 생성하는 패턴을 그대로 따른다.
    private val store by lazy { ProfileStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        if (intent.consumesFinishRequest()) {
            // 결함 #24① 수정: dismissSplit() 이 이 액티비티를 finish 시키는 것이 분할 해제
            // 트리거다(아래 companion object KDoc의 실측 근거 참고). UI를 전혀 구성하지 않고
            // 즉시 종료해 깜빡임을 없앤다.
            // [#27/A1, 18차 G1] removeTask 는 step3 소환원인 카드까지 지우는 초과 동작 —
            // finish 만으로 분할이 해소됨이 18차 G1 로 실증됐다.
            finish()
            return
        }
        // 앱 공용 디자인 시스템([FoldWindowTheme]) 을 적용한다 — 타입 스케일/셰이프/세이지·클레이
        // 액센트를 다른 화면과 공유하기 위함이다. 다만 **표면 색만은 예외**로 항상 순검정을 강제한다:
        // 이 창은 영상 옆 레터박스를 메우는 필러이므로 OLED 에서 검정이 아닌 표면은 회색 띠로 보인다.
        // (Surface 를 검정으로 못 박아 창 배경이 잠깐이라도 밝게 비치지 않게 한다.)
        setContent {
            FoldWindowTheme {
                Surface(color = Color.Black, contentColor = PaneInk) {
                    PanelScreen(store = store)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // dismissSplit() 폴백 경로([ArrangerAccessibilityService.performDismissSplit] 참고):
        // instance 가 이미 null(액티비티 인스턴스는 죽었지만 프로세스는 살아 있는 희귀 경로)일 때
        // FLAG_ACTIVITY_SINGLE_TOP 으로 기존 태스크를 재사용하며 여기로 들어온다.
        if (intent.consumesFinishRequest()) {
            // [#27/A1, 18차 G1] removeTask 는 step3 소환원인 카드까지 지우는 초과 동작 —
            // finish 만으로 분할이 해소됨이 18차 G1 로 실증됐다.
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        // 자기 방어: 이 액티비티는 분할 파트너 전용이다. 전체화면으로 떠 있으면
        // (예: 오래된 태스크가 전면으로 재사용된 경우) 스스로 제거해 화면을 뺏지 않는다.
        // 600ms 유예: 분할 배치 전환 중 일시적으로 멀티윈도우 아님으로 보고될 수 있어
        // 즉시 종료하면 정상 배치를 죽인다. 이 대기는 라이프사이클 전환 유예이지
        // 상태 전이 대체가 아님 (ADR-2 취지 유지).
        // 한계: 프로세스가 강제 종료(예: 앱 재설치)되면 이 가드 자체가 실행될 기회를 얻지
        // 못해 태스크 레코드만 잔존할 수 있다 — [#27, 18차 G3] 그 잔존 카드는 더 이상 위협이
        // 아니다(죽은 카드를 탭해도 정상 낙착함이 실증됐다). 다만 패널 태스크가 여러 개로
        // 쌓이는 것 자체는 무의미하므로, ArrangerAccessibilityService.beginSession 의
        // pruneExtraPanelTasks 가 세션 시작 시 MRU 1개만 남기고 축소한다.
        scheduleFullscreenCheck(600)
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (!isInMultiWindowMode) {
            // 분할 해제 = 파트너 창의 존재 이유 소멸. 잔존 태스크가 다음 배치에서 전체화면으로
            // 재사용되는 것을 원천 차단한다 ([측정] 2026-07-25 깜빡임 루프).
            // 전환 블립 오탐 방지로 400ms 뒤 재확인 후 종료.
            scheduleFullscreenCheck(400)
        }
    }

    private fun scheduleFullscreenCheck(graceMs: Long) {
        fullscreenGuardJob?.cancel()
        fullscreenGuardJob = lifecycleScope.launch {
            delay(graceMs)
            if (!isInMultiWindowMode) {
                Log.i(TAG, "fullscreen 상태 감지 — 파트너 전용 액티비티이므로 종료")
                // [#27/A1, 18차 G1] removeTask 는 step3 소환원인 카드까지 지우는 초과 동작 —
                // finish 만으로 분할이 해소됨이 18차 G1 로 실증됐다.
                finish()
            }
        }
    }

    override fun onPause() {
        // 백그라운드 전환 중 가드가 발화하지 않도록 취소한다.
        fullscreenGuardJob?.cancel()
        fullscreenGuardJob = null
        super.onPause()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        // [#30, DESIGN_30_FULLSCREEN_AUTO.md §2.3] 우리 분할이 해제됐다는 신호. 이 지점이
        // (a) 메뉴 "분할 해제" (b) 커버 화면 자동 해제 (c) 위 자가 가드 finish (d) 사용자의 BACK/
        // 디바이더 드래그 네 경로를 **모두** 포착하는 유일한 지점이다 — 해제 직후 대상 앱이
        // 전체화면으로 자동 복귀하며 만드는 진입 엣지가 곧바로 재발화로 이어지는 P-1 루프를
        // 자동 트리거 장부(AutoTriggerLedger)의 재래치로 끊는다.
        ArrangerAccessibilityService.instance?.onPanelDestroyed()
        super.onDestroy()
    }

    /** ⚠ 부작용 있음 — 토큰을 **소비**한다(1회용). 같은 인텐트로 두 번 호출하면 두 번째는 항상 false. */
    private fun Intent?.consumesFinishRequest(): Boolean =
        consumeFinishToken(this?.getStringExtra(EXTRA_FINISH_TOKEN))

    companion object {
        private const val TAG = "FWPanelActivity"

        /**
         * [실측 2026-07-25, 결함 #24①] dispatchGesture 로 디바이더를 가장자리까지 SINGLE_STROKE
         * 드래그하면 One UI 가 스냅백한다(재현 2회, onCompleted 콜백은 옴). 완전히 동일한 기하·
         * 시간을 `adb input swipe` 로 주입하면 분할 해제 성공(3/3) — 접근성 주입 제스처만 dismiss
         * 깊이에서 거부되는 것으로 추정된다(원인 불명, 경험 법칙). 반면 이 액티비티를 BACK 으로
         * finish 하면 분할이 즉시 해소되고 상대 앱(유튜브)이 전체화면으로 자동 복귀함이 실측
         * 확인됐다 — [ArrangerAccessibilityService.performDismissSplit] 이 이 인텐트 extra 로
         * finish() 를 원격 트리거한다. [#27/A1, 18차 G1] 예전에는 finishAndRemoveTask() 를
         * 트리거했으나, removeTask 부분이 step3 소환원인 카드까지 지우는 초과 동작임이 밝혀져
         * finish() 로 격하했다 — 분할 해소 자체는 finish 만으로 충분함이 18차 G1 로 실증됐다.
         *
         * **[S4] boolean extra 를 1회용 토큰으로 교체한 이유**: 이 액티비티는 파트너 피커 노출을
         * 위해 `exported="true"` + MAIN/LAUNCHER 여야 한다. 예전 `EXTRA_FINISH_PANEL: Boolean`
         * 은 발신자 확인 없이 그대로 신뢰됐다 — 임의 앱이 이 extra 를 실어 우리 액티비티를 즉시
         * finish 시키는 자체 DoS 를 유발할 수 있었다. `callingActivity` 는 `startActivityForResult`
         * 전용이라 여기선 null 이고 `referrer` 도 Service 발 `startActivity` 에서는 신뢰할 수
         * 없으므로(발신자 신원 검증 불가), 대신 **비밀값**으로 검증한다: 서비스가
         * [issueFinishToken] 으로 1회용 토큰을 발급해 인텐트에 싣고, `onCreate`/`onNewIntent`
         * 가 [consumeFinishToken] 으로 소비한다 — 불일치·부재는 전부 무시(finish 하지 않음).
         * `PanelActivity` 와 `ArrangerAccessibilityService` 는 동일 프로세스이므로 static 필드로
         * 충분하다: `dismissSplit()` 은 항상 서비스 인스턴스 위에서 호출되므로 프로세스 생존이
         * 전제이고, 따라서 토큰 발급과 소비가 항상 같은 프로세스에서 일어난다(정상 경로 회귀 없음).
         *
         * **계약 [#28, AOSP 확정] — 토큰 도입으로 구조적으로 소멸한 결함 클래스**: 과거
         * `EXTRA_FINISH_PANEL` 은 **이미 존재하는 패널 태스크**를 향해서만 실을 수 있었다.
         * 태스크를 새로 만들 수 있는 인텐트(`FLAG_ACTIVITY_NEW_TASK` 등)에 실으면, 그 인텐트가
         * 실제로 새 태스크를 만들 때 base intent 에 이 extra 가 그대로 보존되어(AOSP
         * `Task#setIntent` 은 extras 를 유지) 이후 그 태스크 카드를 최근 앱/분할 파트너 피커에서
         * 탭할 때마다(`startActivityFromRecents` → base intent 재실행) `onCreate` 가 즉시
         * finish() 되어 분할 쌍이 성립하지 않는 영구 실패 루프가 됐다. 토큰 체계에서는 base
         * intent 에 토큰 문자열이 박혀 있어도, 그 시점의 [finishToken] 이 이미 다른 값이거나
         * null 이면 **불일치로 무시된다** — "영구 실패 루프" 라는 결함 클래스 자체가 구조적으로
         * 소멸한다. **다만 이는 부수 효과일 뿐, [ArrangerAccessibilityService.performDismissSplit]
         * 의 `hasPanelTask()` 사전 확인은 계속 유지한다** — 그쪽은 불필요한 태스크 생성을 막는
         * 별개 목적이다(#28).
         */
        const val EXTRA_FINISH_TOKEN = "dev.dj.foldwindow.EXTRA_FINISH_TOKEN"

        @Volatile
        private var finishToken: String? = null

        /** 서비스가 finish 인텐트를 만들기 직전 1회 호출한다. 새로 발급하면 이전에 발급되고도
         *  아직 소비되지 않은 토큰은 자동으로 무효화된다(항상 최신 토큰 1개만 유효). */
        fun issueFinishToken(): String = UUID.randomUUID().toString().also { finishToken = it }

        /** 일치 시 소비(1회용). 불일치·부재는 전부 false — 호출부는 이 경우 finish 하지 않는다. */
        private fun consumeFinishToken(raw: String?): Boolean {
            val cur = finishToken ?: return false
            if (raw == null || raw != cur) return false
            finishToken = null
            return true
        }

        /** ArrangerAccessibilityService.instance 와 동일한 패턴 — dismissSplit() 이 이 인스턴스를 직접 finish 시킨다. */
        var instance: PanelActivity? = null
            private set
    }
}

// ── 패널 전용 저휘도 팔레트 ─────────────────────────────────────────────────────────
// 이 창의 배경은 항상 순검정이다(영상 옆 레터박스 필러 — 회색 띠가 보이면 존재 이유가 무너진다).
// 그래서 시스템 라이트/다크와 무관하게 ui/theme/Theme.kt 의 **다크** 세이지/클레이 톤에서만 색을
// 파생한다. 라이트 스킴의 짙은 세이지(#4E6E5D)는 검정 위에서 거의 보이지 않기 때문이다.
//
// **휘도 서열(검정 배경 기준 WCAG 대비, 재계산 2026-08-01 / 전 행 재검증 2026-08-01)**.
// 합성은 순검정 배경이므로 채널 = alpha × 원본채널(sRGB 에서 곱한 뒤 선형화)이며, 아래 12행은
// 전부 그 공식으로 실제 계산해 코드의 alpha 와 한 줄씩 대조했다. 계약은 두 줄이다:
//   (1) 어느 모드에서든 **그 모드의 주역 콘텐츠 > 크롬 상한**(선택된 칩 2.63:1)이 성립한다.
//       보조 콘텐츠(날짜 1.83, 플레이스홀더 1.75)는 크롬보다 어둡다 — 의도된 것이다.
//       읽으라고 둔 글자가 아니라 곁눈질할 글자이므로 크롬과 밝기를 겨루게 두지 않는다.
//   (2) 최상위(가장 밝은 것)는 모드가 정한다 —
//       **MEMO 편집 중에는 본문 잉크가 최상위**(읽는 것이 목적이므로 가독성 우선),
//       **CLOCK 에서는 시계가 최상위**(영상 옆 앰비언트이므로 발광 억제 우선),
//       **BLACK 에는 콘텐츠가 아예 없다** — 3초만 떠 있는 칩(2.63:1)이 유일한 발광체이고
//       그 뒤에는 순검정만 남는다(예전 주석은 여기서도 "시계가 최상위"라 했으나 BLACK 은
//       시계를 그리지 않는다).
//   캐럿(PaneSage 0.80)            7.41:1  ← 예외①. 폭 2dp 의 깜빡이는 선, 편집 중에만 존재
//   메모 본문 포커스(PaneInk 0.74)  6.60:1  ← MEMO 최상위
//   메모 본문 비포커스(PaneInk 0.62) 4.82:1
//   저장 실패 줄(PaneClay 0.60)     3.76:1  ← 예외②. 드물고 사용자 조치가 필요한 상태만 더 밝다
//   시각(PaneSage 0.46)            2.96:1  ← CLOCK 최상위. 저장 확정 줄도 같은 값
//   글자 수 상한 줄(PaneClay 0.50)  2.88:1
//   선택된 칩(PaneSage 0.42)        2.63:1  ← 크롬 상한. 어느 모드의 주역 콘텐츠보다도 어둡다
//   선택 안 된 칩(PaneSage 0.34)    2.06:1
//   날짜(PaneSage 0.30)             1.83:1  ← 앰비언트 보조 정보
//   메모 플레이스홀더(PaneInk 0.28)  1.75:1
//   선택 칩 채움면(PaneSage 0.10)    1.13:1 / 메모 입력면·칩 트레이(PaneSage 0.05) 1.06:1 (면)
private val PaneSage = Color(0xFFA9C7B5) // = Theme.kt DarkPrimary
private val PaneClay = Color(0xFFD9A183) // = Theme.kt DarkSecondary
private val PaneInk = Color(0xFFC7C3B6) // = Theme.kt DarkOnSurfaceVariant (본문 잉크)

/**
 * Material3 는 선택 핸들/하이라이트 색을 `colorScheme.primary` 에서 파생한다. 시스템이 라이트일 때
 * 그 값은 짙은 세이지(#4E6E5D)이고, 이 창의 순검정 배경 위 대비는 핸들 **3.71:1**(PaneSage 는
 * 11.52:1) / 알파 0.4 하이라이트 면 **1.45:1**(PaneSage 는 2.47:1)이다 — 특히 하이라이트가 검정과
 * 사실상 구분되지 않아 "어디가 선택됐는지"가 보이지 않는다. (예전 주석의 "1.9:1" 은 오기였다.
 * 재계산 2026-08-01, 파일 상단 휘도 서열과 같은 공식.) 이 창만 다크 팔레트로 못 박혀 있으므로
 * 선택 색도 함께 못 박는다.
 */
private val PaneSelectionColors = TextSelectionColors(
    handleColor = PaneSage,
    backgroundColor = PaneSage.copy(alpha = 0.4f),
)

/** 컨트롤 자동 숨김까지의 시간. 표시 타이밍이지 상태 전이 대기가 아니다(ADR-2 무관 — 아래 KDoc 참고) */
private const val CONTROLS_AUTO_HIDE_MS = 3_000L

/** 메모 입력 디바운스(입력 IO 절약). 이 값이 바뀌어도 어떤 조건 판정에도 영향을 주지 않는다 */
private const val MEMO_DEBOUNCE_MS = 500L

/** 저장 확정 표시가 스스로 사라지기까지의 시간. "아무것도 안 보임 = 저장됨"이 안정 상태다 */
private const val MEMO_SAVED_LINGER_MS = 1_000L

/** 시각 글자 크기 밴드(dp). 레터박스가 위로 몰리든 아래로 몰리든 시계 크기가 2배씩 출렁이지 않게 한다 */
private const val CLOCK_BAND_MIN_DP = 64f
private const val CLOCK_BAND_MAX_DP = 96f

/** 이 높이(dp) 미만의 **창**에서는 날짜 줄을 아예 그리지 않는다 — 작은 창엔 시각만 있으면 된다 */
private const val CLOCK_DATE_MIN_PANE_DP = 170f

/**
 * 시각 문자열 폭 실측에 쓰는 기준 글자 크기(dp). 글자 폭은 글자 크기에 선형이므로
 * "기준 크기에서 잰 폭"만 있으면 원하는 폭에 맞는 크기를 역산할 수 있다. 100 은 반올림
 * 오차를 무시할 만큼 크고 측정 비용은 문자열당 1회다.
 */
private const val CLOCK_MEASURE_REF_DP = 100f

/**
 * MEMO 에서 IME 회피 패딩이 남겨 두는 창 높이 하한(dp). 이 아래로는 패딩을 더 주지 않는다 —
 * [MemoWidget] KDoc 참고.
 */
private const val MEMO_MIN_VISIBLE_DP = 120f

/**
 * ON_PAUSE 메모 플러시 **전용** 프로세스 스코프.
 *
 * 왜 `rememberCoroutineScope()` 가 아닌가: 이 앱에서 가장 흔한 종료 경로는 분할 해제
 * (`dismissSplit()` → [PanelActivity.finish])다. 이때 `onPause` 와 `onDestroy` 는 **한 트랜잭션
 * 안에서 연달아** 도착하고, 컴포지션 스코프는 `onDestroy`(정확히는 컴포지션 폐기) 시점에 취소된다.
 * 컴포지션 스코프의 디스패처(`AndroidUiDispatcher`)는 인라인 실행을 하지 않고 다음 프레임/핸들러
 * 콜백으로 **큐잉**하므로, `onPause` 에서 `scope.launch { ... }` 한 블록은 **시작조차 하기 전에**
 * 스코프가 취소되어 그대로 사라진다(= 마지막 타이핑이 조용히 유실). `Dispatchers.IO` + 프로세스
 * 수명 스코프로 옮기면 즉시 워커 스레드로 디스패치되고, 이후 액티비티가 죽어도 취소되지 않는다.
 * [dev.dj.foldwindow.data.ProfileStore] 의 `safeWrite` 가 내부적으로 `NonCancellable` 이라
 * 시작된 쓰기의 완료 보장은 그쪽이 이미 책임진다.
 *
 * 디바운스(500ms) 쪽 launch 는 계속 컴포지션 스코프에 둔다 — 디바운스가 파기 시 취소되는 것이
 * 정상이고(그 유실을 막는 것이 바로 이 ON_PAUSE 플러시다), 그래야 창이 사라진 뒤 유령 쓰기가 남지 않는다.
 */
private val panelSaveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/** 메모 저장 표시 상태. IDLE = 디스크와 동일(아무것도 표시하지 않음)이 **안정 상태**다 */
private enum class MemoSaveState { IDLE, SAVING, SAVED, FAILED }

private fun MemoSaveState.labelResOrNull(): Int? = when (this) {
    MemoSaveState.IDLE -> null
    MemoSaveState.SAVING -> R.string.panel_memo_saving
    MemoSaveState.SAVED -> R.string.panel_memo_saved
    MemoSaveState.FAILED -> R.string.panel_memo_save_failed
}

/**
 * [P4-2] 파트너 창 위젯 화면. CLOCK(기존 v1 시계) / MEMO / BLACK 3종을 전환한다.
 *
 * **레이아웃은 Box 오버레이다** — 콘텐츠(Crossfade)가 항상 창 전체를 차지하고, 모드 전환 컨트롤은
 * 그 **위에** 떠 있다. 형제(Column)로 두면 컨트롤이 접힐 때마다 그 자리(약 99dp)가 콘텐츠로
 * 되돌아가 시계가 미끄러지고 크기까지 바뀐다 — 영상 옆에서 3초마다 반복되면 안 되는 움직임이다.
 *
 * **모드 전환 컨트롤은 자동으로 숨는다**(≈3초). 자리를 아끼기 위해서가 아니라(이제 자리를 먹지
 * 않는다) 이 창이 영상 시청 세션 내내 떠 있기 때문이다 — 상시 표시하면 크롬이 화면에서 가장
 * 오래 빛나는 물체가 된다. 대신 **창 전체가 되살리기 버튼**이다.
 *
 * **복귀 불가 상태 0개 증명** (모드 × 컨트롤 표시 × IME):
 * | 모드 | IME | 컨트롤 | 탈출 경로 |
 * |---|---|---|---|
 * | CLOCK/BLACK | 항상 닫힘(포커스 대상 없음) | 표시 | 칩 탭 |
 * | CLOCK/BLACK | 〃 | 숨김 | 창 아무 데나 탭 → 되살아남 |
 * | MEMO | 닫힘 | 표시 | 칩 탭(컨트롤이 필드 위에 떠 있어도 칩이 포인터를 먼저 소비한다) |
 * | MEMO | 닫힘 | 숨김 | 필드 **밖**(좌우 16dp·상하 12dp 여백, 상단 상태줄) 탭 → 되살아남 |
 * | MEMO | 열림 | 강제 숨김(어차피 IME 에 가려짐) | IME 닫기(뒤로) → `imeVisible` 하강에서 자동으로 되살아남 |
 * 어느 칸도 "되살릴 방법 없음"이 아니다. 창 전체 탭이 막히는 유일한 구간은 MEMO 의 필드 영역인데,
 * 그 경우의 탈출(IME 닫기)이 곧 되살리기 트리거다.
 *
 * `store` 하나만 인자로 받는다(기존 파일에 DI 없음 — 이 안에서 필요한 모든 Flow 구독/디바운스/
 * 생명주기 관찰을 자체 완결적으로 처리한다).
 */
@Composable
private fun PanelScreen(store: ProfileStore) {
    val scope = rememberCoroutineScope()

    // 위젯 모드는 DataStore 값을 지속 구독한다 — 모드 전환 버튼을 탭해 저장하면 이 Flow 가
    // 재방출되어 화면이 즉시 갱신된다. remember(store) 로 감싸 동일 Flow 인스턴스를 유지한다
    // (매 리컴포지션마다 새 Flow 를 만들면 collectAsState 가 매번 재구독하게 된다).
    val mode by remember(store) { store.panelWidgetMode.map(PanelWidgetMode::fromStorage) }
        .collectAsState(initial = PanelWidgetMode.CLOCK)

    var memoText by remember { mutableStateOf("") }
    var memoSaveJob by remember { mutableStateOf<Job?>(null) }
    // "지금 디스크와 같은 상태인가"를 **정직하게** 표시하기 위한 상태. 쓰기가 실제로 성공했다고
    // 보고된 뒤에만 SAVED 로 간다(예전 구현은 IOException 을 삼킨 경우에도 "자동 저장됨"이라
    // 거짓말을 했다). IDLE(=아무 표시 없음)이 안정 상태다.
    var saveState by remember { mutableStateOf(MemoSaveState.IDLE) }

    // 저장된 메모를 최초 1회만 읽어 로컬 편집 상태의 시작값으로 삼는다. 이후에는 로컬 상태가
    // 진실 소스다 — store.panelMemo 를 계속 구독하면 (다른 키 변경으로 인한) Flow 재방출이
    // 디바운스 저장 대기 중인 타이핑 값을 되돌릴 위험이 있다(레이스).
    LaunchedEffect(store) {
        memoText = store.panelMemo.first()
    }

    // 저장 확정 표시는 스스로 사라진다 — "아무것도 안 보임 = 저장됨". FAILED 는 여기 걸리지 않으므로
    // 다음 저장 시도(=타이핑)까지 계속 남는다(조용한 실패 금지).
    LaunchedEffect(saveState) {
        if (saveState == MemoSaveState.SAVED) {
            delay(MEMO_SAVED_LINGER_MS)
            saveState = MemoSaveState.IDLE
        }
    }

    fun onMemoTextChange(raw: String) {
        // 상한 초과분은 저장 계층(sanitizePanelMemo)이 조용히 잘라내던 것을 여기서 먼저 잘라
        // **화면에 드러낸다** — 편집 버퍼와 디스크 내용이 항상 일치하고, 상한 도달은 아래
        // MemoWidget 의 안내 줄로 보인다.
        val text = if (raw.length > ProfileStoreMapping.PANEL_MEMO_MAX_CHARS) {
            raw.take(ProfileStoreMapping.PANEL_MEMO_MAX_CHARS)
        } else {
            raw
        }
        memoText = text
        saveState = MemoSaveState.SAVING
        // 500ms 디바운스: ADR-2 가 금지하는 "오케스트레이션 상태 전이를 맞추기 위한 고정 지연"이
        // 아니라 매 키 입력마다 디스크에 쓰지 않기 위한 입력 IO 절약용 디바운스다 — ClockWidget 의
        // 분 경계 틱과 같은 종류로, 상태 전이 대기가 아니라 주기적 절약/갱신 목적이다.
        memoSaveJob?.cancel()
        memoSaveJob = scope.launch {
            delay(MEMO_DEBOUNCE_MS)
            saveState = if (store.savePanelMemo(text)) MemoSaveState.SAVED else MemoSaveState.FAILED
        }
    }

    // onPause 시 즉시 저장: 디바운스 창(500ms)이 끝나기 전에 백그라운드로 전환되면(홈 버튼, 분할
    // 해제 등) 대기 중이던 변경이 유실될 수 있다. PanelActivity.onPause() 는 전체화면 자가 가드
    // 전용으로 무변경 유지해야 하므로(브리프 계약), 여기서는 호스트 액티비티의 Lifecycle 을 별도로
    // 구독해 ON_PAUSE 시 부수적으로 즉시 저장한다. LocalContext.current 를 ComponentActivity 로
    // 그대로 캐스팅한다 — 이 컴포저블은 PanelActivity.setContent 안에서만 호출되므로 항상 안전하다.
    //
    // 이 플러시만은 [panelSaveScope](프로세스 수명 + Dispatchers.IO)에서 돌린다 — 컴포지션 스코프는
    // finish() 경로에서 블록이 시작되기도 전에 취소된다(그 KDoc 참고).
    val activity = LocalContext.current as ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && saveState != MemoSaveState.IDLE) {
                val pending = memoText
                memoSaveJob?.cancel()
                memoSaveJob = null
                panelSaveScope.launch {
                    saveState =
                        if (store.savePanelMemo(pending)) MemoSaveState.SAVED else MemoSaveState.FAILED
                }
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    // ── 컨트롤 자동 숨김 ──────────────────────────────────────────────────────────
    var controlsVisible by remember { mutableStateOf(true) }
    // 같은 값(true)을 다시 써도 타이머를 재시작시키기 위한 카운터. 상호작용마다 증가한다.
    var revealTick by remember { mutableIntStateOf(0) }
    fun revealControls() {
        controlsVisible = true
        revealTick++
    }

    // IME 가 떠 있으면 컨트롤은 어차피 가려진다(targetSdk 36 = 강제 edge-to-edge → 창이 IME 에
    // 맞춰 리사이즈되지 않는다). 가려진 채로 자리만 차지하지 않도록 표시 자체를 접는다.
    // isImeVisible 은 아직 실험 API 라 안정 API 인 인셋 높이로 판정한다.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val switcherShown = controlsVisible && !imeVisible

    // IME 가 닫히면(뒤로 제스처 등) 컨트롤을 되살린다 — MEMO 에서의 탈출 경로 보장.
    // 최초 컴포지션에서도 1회 발화해 시작 시 컨트롤이 보이도록 한다.
    LaunchedEffect(imeVisible) {
        if (!imeVisible) revealControls()
    }
    // 표시 타이밍이지 상태 전이 대기가 아니다(ADR-2 무관): 이 delay 가 얼마이든 어떤 조건 판정도
    // 달라지지 않고, 만료되지 않아도 기능이 실패 상태로 떨어지지 않는다(그냥 계속 보일 뿐).
    LaunchedEffect(switcherShown, revealTick, mode) {
        if (switcherShown) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 창 전체가 되살리기 어포던스다. 안쪽에서 포인터를 소비하는 노드(칩, 메모 필드)는
            // 여기까지 이벤트를 흘리지 않으므로 "필드 밖 탭"만 골라 받게 된다.
            // indication = null: 앰비언트 창 전면에 리플이 번지면 그 자체가 영상 옆 발광이 된다.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = stringResource(R.string.panel_reveal_controls),
                onClick = { revealControls() },
            ),
    ) {
        // 창(=콘텐츠 슬롯) 높이. 아래 Box 오버레이 구조 덕분에 이 둘은 **항상 같다**.
        val paneHeight = maxHeight

        // 콘텐츠는 언제나 창 전체를 차지한다. 크롬(모드 전환 컨트롤)은 형제가 아니라 **오버레이**다.
        // 예전에는 Column 형제였는데, 크롬이 접힐 때마다 약 99dp 를 콘텐츠에 되돌려 주는 바람에
        // (a) 가운데 정렬된 시계가 매 3초 표시 주기마다 약 50dp 씩 미끄러지고 (b) 147dp 짜리 창에서는
        // 높이 상한이 39.7dp↔64dp(61%)로 튀어 시계 크기 자체가 바뀌었다. 재생 중인 영상 바로 옆에서
        // 절대 있어서는 안 되는 움직임이다. 콘텐츠가 크롬 밑으로 지나가는 것은 허용한다 — 크롬은
        // 3초만 떠 있는 투명 오버레이이고, 콘텐츠는 창 기준으로 가운데 정렬되어 있다.
        Crossfade(
            targetState = mode,
            modifier = Modifier.fillMaxSize(),
            // 200ms 페이드. ADR-2 의 "상태 전이를 맞추기 위한 고정 지연"이 아니라 순수 표시
            // 지속 시간이다 — 이 값이 커져도/작아져도 어떤 조건 판정에도 영향을 주지 않는다.
            animationSpec = tween(durationMillis = 200),
            label = "panelWidgetMode",
        ) { shown ->
            when (shown) {
                PanelWidgetMode.CLOCK -> ClockWidget(paneHeight = paneHeight)
                PanelWidgetMode.MEMO -> MemoWidget(
                    paneHeight = paneHeight,
                    text = memoText,
                    saveState = saveState,
                    onTextChange = ::onMemoTextChange,
                )
                // BLACK 은 의도적으로 아무것도 그리지 않는다 — 워터마크 한 줄도 OLED 에서
                // 영상 옆 글로우/번인이 된다. 배경(부모의 순검정)만 남는다.
                PanelWidgetMode.BLACK -> Unit
            }
        }

        // **페이드만** 쓴다(expand/shrink 금지). 크기 애니메이션이 붙으면 이 노드가 매 프레임
        // 다른 크기로 측정되는데, 그래도 형제인 Crossfade 는 fillMaxSize 라 영향이 없지만
        // 오버레이 자신이 아래에서 솟아오르는 움직임을 만든다 — 그것 역시 영상 옆에서는 소음이다.
        AnimatedVisibility(
            visible = switcherShown,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            enter = fadeIn(animationSpec = tween(durationMillis = 200)),
            exit = fadeOut(animationSpec = tween(durationMillis = 200)),
            label = "panelModeSwitcher",
        ) {
            ModeSwitcher(
                selected = mode,
                onSelect = { newMode ->
                    // 어떤 상호작용이든 자동 숨김 타이머를 재시작시킨다.
                    revealControls()
                    scope.launch { store.savePanelWidgetMode(newMode.name) }
                },
                modifier = Modifier.padding(bottom = 20.dp),
            )
        }
    }
}

/**
 * CLOCK 모드 — 앰비언트 시계. 시각(주역) + 날짜/요일(보조) 두 줄로만 구성한다.
 *
 * 크기 규칙 (고정 크기 가정 금지, CLAUDE.md 함정 #5). 이 창은 소스 종횡비에 따라 147dp(4:3),
 * 329dp(16:9), 469dp(2.39:1) 등 어떤 높이로도 태어난다. 세 가지 제약을 **동시에** 만족시킨다:
 *
 * 1. **밴드** `창높이 × 0.26` 을 [CLOCK_BAND_MIN_DP]~[CLOCK_BAND_MAX_DP] 로 좁게 조인다. 순수
 *    비례식이면 329dp 창과 469dp 창에서 크기가 2배 가까이 벌어져, "레터박스가 어느 쪽으로
 *    몰렸는가"라는 사용자와 무관한 사정이 시계 크기를 흔든다. 앰비언트 시계는 크기를 지킨다.
 * 2. 기준은 [paneHeight](**창** 높이)다. [PanelScreen] 이 Box 오버레이 구조라 콘텐츠 슬롯 높이는
 *    이제 창 높이와 항상 같지만, 이 파라미터는 그 불변식을 **명시적으로** 못 박아 둔다 — 누군가
 *    다시 Column 형제로 되돌려 크롬이 슬롯을 먹더라도 시계 크기는 크롬을 추종하지 않는다.
 * 3. 그래도 **넘치면 안 되므로** 높이/폭 기준 상한 두 개로 다시 내린다. 폭 상한은 포맷된 문자열을
 *    [rememberTextMeasurer] 로 **실측**해서 얻는다. 예전의 `0.62em × 글자수` 휴리스틱은 CJK 를
 *    과소평가했다("오후 3:45" 실측 ≈5.1em vs 예산 4.34em) — 커버 화면 크기의 좁은 창에서
 *    조용히 잘렸다(maxLines=1 의 기본 overflow 는 Clip).
 *
 * 모든 계산은 **dp(실제 렌더 크기)** 로 하고 마지막에만 sp 로 환산한다. sp 로 계산하면 시스템
 * 글꼴 크기 배율이 큰 사용자에게서 위 3번 상한이 그대로 무너진다.
 */
@Composable
private fun ClockWidget(paneHeight: Dp, modifier: Modifier = Modifier) {
    val activity = LocalContext.current as ComponentActivity

    // 표준 시간대/시각 변경 즉시 반영. 분 경계 루프만 있으면 ACTION_TIMEZONE_CHANGED 뒤 최대 60초
    // 동안 떠난 지역의 시각을 보여준다(비행기에서 내리자마자 보게 되는 화면이다). 이 카운터가
    // 오르면 (a) 포매터가 새 기본 TimeZone/12·24시간제로 다시 만들어지고 (b) 아래 틱 루프가
    // 재시작하면서 선두의 `now = Date()` 로 즉시 갱신 + 분 경계를 다시 맞춘다.
    // ACTION_LOCALE_CHANGED 는 넣지 않는다 — 매니페스트의 configChanges 에 locale 이 없어
    // 로케일 변경은 액티비티 재생성으로 처리되고, 재생성이 포매터도 새로 만든다.
    var timeInvalidation by remember { mutableIntStateOf(0) }
    DisposableEffect(activity) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                timeInvalidation++
            }
        }
        // 둘 다 보호된 시스템 브로드캐스트다 — NOT_EXPORTED 로도 시스템 발신분은 그대로 온다.
        // ContextCompat 을 쓰는 이유: 플래그를 받는 registerReceiver 오버로드는 API 33+ 인데
        // 이 앱의 minSdk 는 30 이다.
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_TIME_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { activity.unregisterReceiver(receiver) }
    }

    // 포매터는 창 수명 동안(그리고 시간대/시각 변경 사이) 1회만 만든다(매 틱 생성 금지).
    // 12/24시간제와 날짜 표기는 전부 로케일이 결정한다 — 한국어 밖에서도 올바른 표기가 나와야 한다.
    val timeFormat = remember(activity, timeInvalidation) { paneTimeFormat(activity) }
    val dateFormat = remember(activity, timeInvalidation) { paneDateFormat() }
    var now by remember { mutableStateOf(Date()) }

    // UI 시계 갱신 틱. ADR-2가 금지하는 "타이밍을 맞추기 위한 고정 지연"이 아니라 단순 화면 갱신
    // 루프다(오케스트레이션 상태 전이와 무관). 표시 단위가 분이므로 1초마다 깨울 이유가 없다 —
    // 다음 **분 경계**까지만 잔다. STARTED 밖(창이 가려짐/정지)에서는 아예 돌지 않고, 다시
    // STARTED 가 되는 순간 루프 선두에서 즉시 현재 시각을 반영한다.
    LaunchedEffect(activity, timeInvalidation) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                now = Date()
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        val density = LocalDensity.current
        val fontScale = density.fontScale
        val formatted = timeFormat.format(now)
        // 이 슬롯은 창 그 자체다(Box 오버레이 구조). `maxHeight == paneHeight` 가 성립하지만,
        // 크롬 유무와 무관함을 코드에서도 보이도록 창 높이를 직접 쓴다.
        val paneDp = paneHeight.value

        // (1)(2) 창 높이에 묶인 좁은 밴드
        val band = (paneDp * 0.26f).coerceIn(CLOCK_BAND_MIN_DP, CLOCK_BAND_MAX_DP)

        // (3-a) 폭 적합 — 실측. 기준 크기에서 한 번 재고 선형 역산한다(글자 폭 ∝ 글자 크기,
        // 자간도 em 단위라 같이 비례한다). 렌더에 쓰는 스타일에서 fontSize 만 바꿔 재므로
        // "잰 것과 그린 것이 다른" 종류의 회귀가 구조적으로 불가능하다.
        val refStyle = MaterialTheme.typography.displayLarge.copy(
            fontSize = (CLOCK_MEASURE_REF_DP / fontScale).sp,
            lineHeight = (CLOCK_MEASURE_REF_DP * 1.04f / fontScale).sp,
            fontWeight = FontWeight.Light,
            letterSpacing = (-0.02).em,
            fontFeatureSettings = "tnum",
        )
        val measurer = rememberTextMeasurer()
        val refWidthDp = remember(formatted, refStyle, measurer) {
            measurer.measure(
                text = formatted,
                style = refStyle,
                softWrap = false,
                maxLines = 1,
            ).size.width / density.density
        }
        val widthFit = if (refWidthDp > 0f) {
            CLOCK_MEASURE_REF_DP * maxWidth.value / refWidthDp
        } else {
            band // 측정 실패(빈 문자열 등)는 밴드에 맡긴다 — 폭 상한만 포기하고 조용히 넘어가지 않는다
        }

        // (3-b) 높이 적합. 작은 창(4:3 소스 ⇒ 147dp)에서는 날짜 줄을 통째로 버린다 — 좁은 창에
        // 필요한 것은 시각뿐이고, 버티면 스택이 상자를 넘친다.
        val showDate = paneDp >= CLOCK_DATE_MIN_PANE_DP
        // 스택 높이 = 시각줄(1.04) + 간격(0.08) + 날짜줄(0.19 × 1.4 = 0.266). 0.86 은 위아래 숨통.
        val stackFactor = if (showDate) 1.386f else 1.04f
        val heightFit = paneDp * 0.86f / stackFactor

        val timeDp = minOf(band, widthFit, heightFit).coerceAtLeast(16f)
        // 날짜가 하한(11dp)으로 올라붙어 stackFactor 가정보다 커질 수 있으나, showDate 게이트
        // (170dp 이상) + 0.86 여유가 그 오차를 흡수한다.
        val dateDp = (timeDp * 0.19f).coerceIn(11f, 20f)

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = formatted,
                // 위 refStyle 과 **같은** 스타일에서 크기만 내린다(앰비언트 표기: 가는 굵기 + 음의
                // 자간으로 큰 숫자를 조여 준다. tnum(tabular figures) 이 없으면 One UI Sans 의
                // 비례 숫자 때문에 분이 바뀔 때마다 폭이 변해 가운데 정렬 시계가 좌우로 흔들린다).
                style = refStyle.copy(
                    fontSize = (timeDp / fontScale).sp,
                    lineHeight = (timeDp * 1.04f / fontScale).sp,
                ),
                color = PaneSage.copy(alpha = 0.46f),
                maxLines = 1,
            )
            if (showDate) {
                Text(
                    text = dateFormat.format(now),
                    // 위계는 **크기와 알파로만** 준다. 색을 바꾸면(예전 클레이) 두 줄이 서로 다른
                    // 브랜드 색처럼 읽힌다 — 한 화면 두 줄에 두 색은 위계가 아니라 소음이다.
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontSize = (dateDp / fontScale).sp,
                        lineHeight = (dateDp * 1.4f / fontScale).sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.08.em,
                    ),
                    // 0.30 = 검정 위 **1.83:1** — 시계(0.46 = 2.96:1)보다 한 단 아래의 보조 위계.
                    // (예전 주석의 "≈2.0:1" 은 오기였다. 재계산 2026-08-01, 파일 상단 휘도 서열 참고.)
                    // 비평 처방(0.20)은 1.39:1 로 원 지적 대상보다도 어두워지는 자충수라 채택하지 않았다.
                    color = PaneSage.copy(alpha = 0.30f),
                    maxLines = 1,
                    modifier = Modifier.padding(top = (timeDp * 0.08f).dp),
                )
            }
        }
    }
}

/**
 * MEMO 모드 — 검정 배경 위 자유 입력. 어둡게 유지하되 (a) 입력 영역을 아주 낮은 대비의 면으로
 * 드러내고 (b) 읽히는 글자색·보이는 캐럿·보이는 선택 색을 주고 (c) 저장 상태를 **정직하게**
 * 한 줄 표시한다. 저장 버튼은 두지 않는다 — 디바운스 + ON_PAUSE 플러시가 이미 저장을 책임진다.
 * 다중행 필드이므로 내용이 넘치면 TextField 기본 내부 스크롤로 자연히 스크롤된다(캐럿 추적 포함).
 *
 * **IME 회피는 [Modifier.imePadding] 이 아니라 겹침 인지 패딩이다.** targetSdk 36 은 edge-to-edge
 * 가 강제라 IME 가 떠도 창이 리사이즈되지 않으므로 회피 자체는 필요하다. 다만 `imePadding()` 은
 * 인셋 전체를 무조건 빼서, 329dp 짜리 **아래쪽** 창 + ~300dp IME 조합에서 편집기를 0dp 로 접어
 * 버렸다(필드가 사라진 것처럼 보인다). 그래서 [MEMO_MIN_VISIBLE_DP] 만큼의 창 높이는 남기고
 * `min(IME 겹침, 창높이 − 120dp)` 만 뺀다.
 *
 * 두 기하의 결과가 다르다:
 * - 패널이 **위쪽** 페인이면 IME(화면 하단)가 이 창과 아예 겹치지 않아 `WindowInsets.ime` 이 0 →
 *   패딩 0 → 인라인 편집이 온전하다. 이것이 이 수정의 실질적 이득이다.
 * - 패널이 **아래쪽** 329dp 페인이면 IME 가 창의 300dp 를 덮어 **플랫폼이 남기는 가시 영역 자체가
 *   ~29dp** 다. 어떤 패딩도 없는 공간을 만들어내지 못한다. 이때는 편집기를 접어 버리는 대신
 *   120dp 를 유지하고, 그 안에서 위쪽(=가시 스트립)에 [MemoStatusLine] 과 본문 첫 줄이 오도록
 *   상태 줄을 **필드 위**로 올렸다. 캐럿은 `BasicTextField` 의 bringIntoView 가 필드 뷰포트
 *   안으로만 끌어오므로, 이 기하에서는 캐럿이 IME 밑에 깔릴 수 있다 — 플랫폼 한계이며
 *   실제 편집은 패널이 위쪽 페인일 때 하는 것이 정상 사용이다.
 */
@Composable
private fun MemoWidget(
    paneHeight: Dp,
    text: String,
    saveState: MemoSaveState,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // IME 가 **이 창**을 덮는 높이(위쪽 페인이면 0).
    val imeOverlapPx = WindowInsets.ime.getBottom(density)
    // 창 높이에서 최소 가시 높이를 남기는 선까지만 뺀다.
    val imeAvoidance = with(density) {
        val floorPx = (paneHeight.roundToPx() - MEMO_MIN_VISIBLE_DP.dp.roundToPx()).coerceAtLeast(0)
        imeOverlapPx.coerceAtMost(floorPx).toDp()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = imeAvoidance)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        // 상태 줄이 **위**에 있는 이유: (a) IME 가 창을 대부분 덮어도 남는 가시 스트립 안에 들어온다
        // (b) 화면 하단 제스처 핸들/내비게이션 인셋과 겹치지 않는다 (c) 3초간 떠 있는 모드 전환
        // 오버레이(하단 중앙)에 가려지지 않는다.
        MemoStatusLine(text = text, saveState = saveState)
        CompositionLocalProvider(LocalTextSelectionColors provides PaneSelectionColors) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(PaneSage.copy(alpha = 0.05f)),
                textStyle = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.panel_memo_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedTextColor = PaneInk.copy(alpha = 0.74f),
                    unfocusedTextColor = PaneInk.copy(alpha = 0.62f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    cursorColor = PaneSage.copy(alpha = 0.8f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedPlaceholderColor = PaneInk.copy(alpha = 0.28f),
                    unfocusedPlaceholderColor = PaneInk.copy(alpha = 0.28f),
                ),
            )
        }
    }
}

/**
 * 메모 상태 줄: (왼쪽) 글자 수 상한 안내 · (오른쪽) 저장 표시. 메모 칼럼 **맨 위**에 놓인다
 * (이유는 [MemoWidget] KDoc).
 *
 * 저장 표시의 **안정 상태는 "아무것도 보이지 않음"** 이다 — 저장이 확정되면 약 1초 뒤 사라진다.
 * 상시 "자동 저장됨" 배지는 (a) 영상 옆에서 계속 빛나고 (b) 쓰기 실패까지 성공으로 위장했다.
 * 실패([MemoSaveState.FAILED])만은 사라지지 않고 남는다(조용한 실패 금지).
 *
 * "보이지 않음"은 **접근성 트리에서도** 보이지 않아야 한다. `Modifier.alpha(0f)` 는 시각적으로만
 * 지우기 때문에 TalkBack 은 아무것도 저장한 적 없는 상태에서도 "자동 저장됨"을 읽었다. 안정 API
 * 인 `hideFromAccessibility()` 는 이 버전(Compose UI 1.7.6)에 없고 `invisibleToUser()` 는 실험
 * API 라 opt-in 을 새로 들이지 않기 위해, **페이드가 끝난 뒤 컴포지션에서 빼는** 쪽을 택했다:
 * 알파가 0 보다 크면(=사라지는 중) 노드가 살아 있어 페이드가 온전히 보이고, 완전 투명이 되는
 * 순간 노드가 사라져 접근성 트리에서도 빠진다.
 *
 * 이 처방이 양 끝을 실제로 닫는 근거 두 가지(2026-08-01 확인):
 * - **시작**: `animateFloatAsState` 는 내부 `Animatable` 을 `targetValue` 로 **생성**한다(첫
 *   컴포지션에서 0 → 1 로 달려오지 않는다). 저장한 적 없는 최초 상태는 `labelRes == null` 이므로
 *   알파가 0f 로 태어나고, 아래 `labelRes != null || indicatorAlpha > 0f` 가 첫 프레임부터 거짓 —
 *   즉 원 결함(한 번도 저장 안 했는데 TalkBack 이 "자동 저장됨"을 읽음)은 발생 자체가 불가능하다.
 * - **끝**: `tween` 은 마지막 프레임에서 끝값을 **정확히** 낸다(보간 계수가 1.0 에 도달하므로
 *   0.0001 같은 잔량이 남지 않는다). 따라서 페이드 아웃이 끝나면 `indicatorAlpha == 0f` 가 되고
 *   그 리컴포지션에서 노드가 빠진다 — 근사값에 걸려 영원히 남는 경로가 없다.
 *
 * 상한은 [ProfileStoreMapping.sanitizePanelMemo] 가 조용히 잘라내던 것을 입력 시점에 잘라 여기서
 * 드러낸다 — 상한 자체(4000자)는 그대로다.
 */
@Composable
private fun MemoStatusLine(text: String, saveState: MemoSaveState) {
    val labelRes = saveState.labelResOrNull()
    // 사라지는 동안에도 문자열이 유지되도록 마지막으로 보였던 라벨을 붙들어 둔다
    // (labelRes 가 null 이 되는 순간 텍스트가 ""로 튀면 페이드가 아니라 깜빡임이 된다).
    var shownLabel by remember { mutableIntStateOf(R.string.panel_memo_saved) }
    LaunchedEffect(labelRes) { if (labelRes != null) shownLabel = labelRes }
    val indicatorAlpha by animateFloatAsState(
        targetValue = if (labelRes == null) 0f else 1f,
        // 순수 표시 지속 시간(ADR-2 무관).
        animationSpec = tween(durationMillis = 400),
        label = "panelMemoSaveIndicator",
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 빈 문자열이어도 항상 컴포즈한다 — 줄 높이를 고정해 저장 표시가 나타나고 사라질 때
        // 아래 필드가 위아래로 밀리지 않게 한다.
        // 다만 **접근성 트리에는 남기지 않는다**: Compose 의 `Text` 는 문자열이 비어 있어도
        // `SemanticsProperties.Text` 키를 항상 실으므로, 접근성 델리게이트의 판정(키의 *존재*만
        // 본다)에서 "말하는 노드"로 분류되어 TalkBack 이 아무 말도 없는 빈 항목에 한 번 멈춘다.
        // 오른쪽 저장 표시와 같은 처방(안 보이면 트리에도 없다)을 여기에도 적용하되, 이쪽은
        // 노드를 빼면 줄 높이가 무너지므로 **레이아웃은 남기고 시맨틱만 지운다** —
        // `clearAndSetSemantics {}` 는 측정/배치에 전혀 관여하지 않아 회귀가 없다(안정 API).
        val capReached = text.length >= ProfileStoreMapping.PANEL_MEMO_MAX_CHARS
        Text(
            text = if (capReached) stringResource(R.string.panel_memo_cap_reached) else "",
            style = MaterialTheme.typography.labelSmall,
            color = PaneClay.copy(alpha = 0.50f),
            maxLines = 1,
            modifier = if (capReached) Modifier else Modifier.clearAndSetSemantics {},
        )
        if (labelRes != null || indicatorAlpha > 0f) {
            Text(
                text = stringResource(shownLabel),
                style = MaterialTheme.typography.labelSmall,
                // 실패만 더 밝은 클레이(3.76:1). 나머지는 시계와 같은 휘도(2.96:1)를 넘지 않는다.
                color = if (saveState == MemoSaveState.FAILED) {
                    PaneClay.copy(alpha = 0.60f)
                } else {
                    PaneSage.copy(alpha = 0.46f)
                },
                maxLines = 1,
                modifier = Modifier.alpha(indicatorAlpha).padding(end = 4.dp),
            )
        }
    }
}

/**
 * 하단 중앙 모드 전환 컨트롤(세그먼티드). 약 3초 뒤 자동으로 숨고, 창 아무 데나 탭하면 다시
 * 나타난다(복귀 불가 상태가 없다는 증명은 [PanelScreen] KDoc 의 표). 선택된 칸만 채움+높은 대비로
 * 표시해 "지금 어느 모드인가"가 한눈에 보이게 한다.
 */
@Composable
private fun ModeSwitcher(
    selected: PanelWidgetMode,
    onSelect: (PanelWidgetMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(PaneSage.copy(alpha = 0.05f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeChip(
            iconRes = R.drawable.ic_panel_clock,
            labelRes = R.string.panel_mode_clock,
            selected = selected == PanelWidgetMode.CLOCK,
            onClick = { onSelect(PanelWidgetMode.CLOCK) },
        )
        ModeChip(
            iconRes = R.drawable.ic_panel_memo,
            labelRes = R.string.panel_mode_memo,
            selected = selected == PanelWidgetMode.MEMO,
            onClick = { onSelect(PanelWidgetMode.MEMO) },
        )
        ModeChip(
            iconRes = R.drawable.ic_panel_black,
            labelRes = R.string.panel_mode_black,
            selected = selected == PanelWidgetMode.BLACK,
            onClick = { onSelect(PanelWidgetMode.BLACK) },
        )
    }
}

/**
 * 세그먼트 1칸: 아이콘 + 라벨, 최소 터치 타깃 64×56dp.
 *
 * 접근성: 아이콘은 `contentDescription = null`(장식)로 두고 의미는 옆의 라벨 텍스트가 전달한다 —
 * 둘 다 주면 TalkBack 이 같은 단어를 두 번 읽는다. 대신 [Modifier.selectable] 에 [Role.Tab] 과
 * `selected` 를 실어 "시계, 탭, 선택됨" 처럼 **선택 상태까지** 읽히게 한다(단순 clickable 로는
 * 선택 상태가 전달되지 않는다).
 */
@Composable
private fun ModeChip(
    @DrawableRes iconRes: Int,
    @StringRes labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // 선택 대비: 채움 면(PaneSage 0.10 = 1.13:1) + 콘텐츠 휘도 차. 크롬은 그 모드의 **주역**
    // 콘텐츠보다 어두워야 한다 — 선택된 칩(0.42)조차 시계(2.96:1)보다 낮은 2.63:1, 선택 안 된
    // 칩(0.34)은 2.06:1 이다. 예전 0.62(4.69:1)는 시계보다 1.58배 밝아 화면에서 가장 밝은 물체가
    // 크롬이라는 위계 역전을 만들었다. (세 수치 모두 재계산 2026-08-01 — 파일 상단 서열과 동일 공식.)
    val content = PaneSage.copy(alpha = if (selected) 0.42f else 0.34f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) PaneSage.copy(alpha = 0.10f) else Color.Transparent)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                // 기본 인디케이션은 검정 오버레이라 순검정 배경에서 눌린 티가 전혀 나지 않는다.
                // 세이지 리플로 바꾼다(RippleDefaults 알파 0.1 이 곱해져 여전히 매우 어둡다).
                indication = ripple(color = PaneSage),
                role = Role.Tab,
                onClick = onClick,
            )
            .defaultMinSize(minWidth = 64.dp, minHeight = 56.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

/** 로케일이 정한 시각 표기 + 시스템 12/24시간제 설정을 따른다(한국어 밖에서도 올바르게). */
private fun paneTimeFormat(context: Context): SimpleDateFormat {
    val locale = Locale.getDefault()
    val skeleton = if (AndroidDateFormat.is24HourFormat(context)) "Hm" else "hm"
    return SimpleDateFormat(AndroidDateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

/** 로케일이 정한 "월/일 + 요일" 표기 (ko: 8월 1일 토요일 / en: Saturday, Aug 1). */
private fun paneDateFormat(): SimpleDateFormat {
    val locale = Locale.getDefault()
    return SimpleDateFormat(AndroidDateFormat.getBestDateTimePattern(locale, "EEEEMMMd"), locale)
}
