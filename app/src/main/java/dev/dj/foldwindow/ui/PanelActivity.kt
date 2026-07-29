package dev.dj.foldwindow.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import dev.dj.foldwindow.data.ProfileStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        if (intent.requestsFinish()) {
            // 결함 #24① 수정: dismissSplit() 이 이 액티비티를 finish 시키는 것이 분할 해제
            // 트리거다(아래 companion object KDoc의 실측 근거 참고). UI를 전혀 구성하지 않고
            // 즉시 종료해 깜빡임을 없앤다.
            // [#27/A1, 18차 G1] removeTask 는 step3 소환원인 카드까지 지우는 초과 동작 —
            // finish 만으로 분할이 해소됨이 18차 G1 로 실증됐다.
            finish()
            return
        }
        setContent { MaterialTheme { Surface { PanelScreen(store = store) } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // dismissSplit() 폴백 경로([ArrangerAccessibilityService.performDismissSplit] 참고):
        // instance 가 이미 null(액티비티 인스턴스는 죽었지만 프로세스는 살아 있는 희귀 경로)일 때
        // FLAG_ACTIVITY_SINGLE_TOP 으로 기존 태스크를 재사용하며 여기로 들어온다.
        if (intent.requestsFinish()) {
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
        super.onDestroy()
    }

    private fun Intent?.requestsFinish(): Boolean =
        this?.getBooleanExtra(EXTRA_FINISH_PANEL, false) == true

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
         * **계약 [#28, AOSP 확정]**: 이 extra 는 **이미 존재하는 패널 태스크**를 향해서만 실을 수
         * 있다. 태스크를 새로 만들 수 있는 인텐트(`FLAG_ACTIVITY_NEW_TASK` 등)에 실으면, 그
         * 인텐트가 실제로 새 태스크를 만들 때 base intent 에 이 extra 가 그대로 보존된다(AOSP
         * `Task#setIntent` 은 extras 를 유지). 그 결과 이후 그 태스크 카드를 최근 앱/분할 파트너
         * 피커에서 탭할 때마다(`startActivityFromRecents` → base intent 재실행) `onCreate` 가
         * 이 extra 를 읽고 즉시 finish() 되어 분할 쌍이 성립하지 않는 영구 실패 루프가 된다
         * ([ArrangerAccessibilityService.performDismissSplit] 의 `hasPanelTask()` 사전 확인이
         * 이를 막는다). **태스크를 새로 만들 수 있는 인텐트에는 이 extra 를 절대 실으면 안
         * 된다** — 위 오염 경로가 그대로 재현된다.
         */
        const val EXTRA_FINISH_PANEL = "dev.dj.foldwindow.EXTRA_FINISH_PANEL"

        /** ArrangerAccessibilityService.instance 와 동일한 패턴 — dismissSplit() 이 이 인스턴스를 직접 finish 시킨다. */
        var instance: PanelActivity? = null
            private set
    }
}

/**
 * [P4-2] 파트너 창 위젯 화면. CLOCK(기존 v1 시계) / MEMO / BLACK 3종을 전환한다.
 * 모드 전환 버튼(하단 중앙)은 세 모드 어디서든 항상 보인다 — BLACK 에서 숨기면 복귀할 방법이
 * 없어지기 때문이다. `store` 하나만 인자로 받는다(기존 파일에 DI 없음 — 이 안에서 필요한 모든
 * Flow 구독/디바운스/생명주기 관찰을 자체 완결적으로 처리한다).
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

    // 저장된 메모를 최초 1회만 읽어 로컬 편집 상태의 시작값으로 삼는다. 이후에는 로컬 상태가
    // 진실 소스다 — store.panelMemo 를 계속 구독하면 (다른 키 변경으로 인한) Flow 재방출이
    // 디바운스 저장 대기 중인 타이핑 값을 되돌릴 위험이 있다(레이스).
    LaunchedEffect(store) {
        memoText = store.panelMemo.first()
    }

    fun flushMemoNow(text: String) {
        memoSaveJob?.cancel()
        memoSaveJob = null
        scope.launch { store.savePanelMemo(text) }
    }

    fun onMemoTextChange(text: String) {
        memoText = text
        // 500ms 디바운스: ADR-2 가 금지하는 "오케스트레이션 상태 전이를 맞추기 위한 고정 지연"이
        // 아니라 매 키 입력마다 디스크에 쓰지 않기 위한 입력 IO 절약용 디바운스다 — 아래 ClockWidget
        // 의 1초 틱(delay(1_000))과 같은 종류로, 상태 전이 대기가 아니라 주기적 절약/갱신 목적이다.
        memoSaveJob?.cancel()
        memoSaveJob = scope.launch {
            delay(500)
            store.savePanelMemo(text)
        }
    }

    // onPause 시 즉시 저장: 디바운스 창(500ms)이 끝나기 전에 백그라운드로 전환되면(홈 버튼 등)
    // 대기 중이던 변경이 유실될 수 있다. PanelActivity.onPause() 는 전체화면 자가 가드 전용으로
    // 무변경 유지해야 하므로(브리프 계약), 여기서는 호스트 액티비티의 Lifecycle 을 별도로 구독해
    // ON_PAUSE 시 부수적으로 즉시 저장한다. LocalContext.current 를 ComponentActivity 로 그대로
    // 캐스팅한다 — 이 컴포저블은 PanelActivity.setContent 안에서만 호출되므로 항상 안전하다.
    val activity = LocalContext.current as ComponentActivity
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                flushMemoNow(memoText)
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        when (mode) {
            PanelWidgetMode.CLOCK -> ClockWidget()
            PanelWidgetMode.BLACK -> BlackWidget()
            PanelWidgetMode.MEMO -> MemoWidget(text = memoText, onTextChange = ::onMemoTextChange)
        }

        ModeSwitcherRow(
            onSelect = { newMode -> scope.launch { store.savePanelWidgetMode(newMode.name) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
        )
    }
}

/** CLOCK 모드 — 기존 v1 구현 그대로(1초 틱으로 HH:mm 갱신) */
@Composable
private fun ClockWidget(modifier: Modifier = Modifier) {
    var timeText by remember { mutableStateOf(currentTimeText()) }

    // UI 시계 갱신용 1초 틱. ADR-2가 금지하는 "타이밍을 맞추기 위한 고정 지연"이 아니라
    // 단순 화면 갱신 루프다 (오케스트레이션 상태 전이와 무관).
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            timeText = currentTimeText()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = timeText,
            color = Color.Gray.copy(alpha = 0.4f),
            fontSize = 48.sp,
        )
        Text(
            text = "FoldWindow",
            color = Color.Gray.copy(alpha = 0.4f),
            fontSize = 14.sp,
        )
    }
}

/** BLACK 모드 — 순검정 배경(부모 Box 가 이미 깔아 둠) 위에 워터마크만 남긴다 */
@Composable
private fun BlackWidget(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "FoldWindow",
            color = Color.Gray.copy(alpha = 0.4f),
            fontSize = 14.sp,
        )
    }
}

/**
 * MEMO 모드 — 검정 배경 위 자유 입력 텍스트. 컨테이너/밑줄 색을 전부 투명으로 지워 필드 티가
 * 나지 않게 하고(요구사항 "배경 투명"), fillMaxSize 로 화면 전체를 차지하는 다중행 필드라 내용이
 * 넘치면 Compose TextField 가 기본 제공하는 내부 스크롤로 자연히 스크롤 가능해진다.
 */
@Composable
private fun MemoWidget(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier.fillMaxSize(),
        placeholder = { Text("메모", color = Color.Gray.copy(alpha = 0.4f)) },
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.Gray.copy(alpha = 0.6f),
            unfocusedTextColor = Color.Gray.copy(alpha = 0.6f),
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            cursorColor = Color.Gray,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedPlaceholderColor = Color.Gray.copy(alpha = 0.4f),
            unfocusedPlaceholderColor = Color.Gray.copy(alpha = 0.4f),
        ),
    )
}

/** 하단 중앙 모드 전환 버튼 3개. BLACK 을 포함해 모든 모드에서 항상 표시한다(숨기면 복귀 불가) */
@Composable
private fun ModeSwitcherRow(onSelect: (PanelWidgetMode) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        TextButton(onClick = { onSelect(PanelWidgetMode.CLOCK) }) {
            Text("시계", color = Color.Gray.copy(alpha = 0.4f))
        }
        TextButton(onClick = { onSelect(PanelWidgetMode.MEMO) }) {
            Text("메모", color = Color.Gray.copy(alpha = 0.4f))
        }
        TextButton(onClick = { onSelect(PanelWidgetMode.BLACK) }) {
            Text("검정", color = Color.Gray.copy(alpha = 0.4f))
        }
    }
}

private fun currentTimeText(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
