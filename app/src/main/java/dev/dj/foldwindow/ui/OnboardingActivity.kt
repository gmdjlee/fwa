package dev.dj.foldwindow.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import dev.dj.foldwindow.R
import dev.dj.foldwindow.data.ProfileStore
import dev.dj.foldwindow.service.ArrangerAccessibilityService
import dev.dj.foldwindow.service.FloatingLauncherService
import dev.dj.foldwindow.service.ShizukuShell
import dev.dj.foldwindow.ui.theme.FoldWindowTheme
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * P3-4: 온보딩 — 오버레이/접근성/알림 권한 유도 + 버블 시작·중지 + 사용 안내.
 *
 * 앱의 MAIN/LAUNCHER 진입점(probe.ProbeActivity 는 Phase 0 도구로 별도 유지).
 * ViewModel 은 불필요할 만큼 단순한 상태(권한 4종 + 버블 실행 여부)라, 액티비티가 직접
 * `mutableStateOf` 를 들고 `onResume` 마다 재평가한다 (설정 화면 다녀온 직후 갱신 요구사항).
 */
class OnboardingActivity : ComponentActivity() {

    private var overlayGranted by mutableStateOf(false)
    private var accessibilityGranted by mutableStateOf(false)
    private var notificationGranted by mutableStateOf(false)
    /** P4-1: 팝업(freeform) 모드 선택 권한. 설치·실행·권한 허용 전부를 [ShizukuShell.isReady] 로 재평가한다. */
    private var shizukuReady by mutableStateOf(false)
    private var bubbleRunning by mutableStateOf(false)

    /**
     * 중지 시퀀스(DataStore 쓰기 -> stopService) 진행 중 재진입을 막는 플래그.
     * Compose 상태가 아니라 순수 필드다 — UI 를 다시 그리지 않고 [toggleBubble] 호출만 무시하면 된다.
     * [실기기 검증 리뷰 지적 반영, 2026-07-25] 이 플래그가 없으면: 사용자가 중지 버튼을 빠르게 두 번
     * 누르거나(중지 시퀀스가 아직 stopService 전인데 재호출) 중지 도중 재시작을 눌렀을 때, 두 번째
     * 호출이 시작한 새 서비스를 첫 번째 호출의 지연된 stopService 가 죽이는 레이스가 발생한다.
     */
    private var stopInProgress = false

    private val store by lazy { ProfileStore(this) }

    private lateinit var notificationPermissionLauncher: ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // targetSdk 36 은 edge-to-edge 를 강제한다 — 명시적으로 켜서 상태바 아이콘 명암을
        // 라이트/다크 배경에 맞게 시스템이 자동 조정하게 한다(상태바 밑에 콘텐츠가 깔리는
        // 사용자 제보 버그의 원인은 인셋 처리 누락이었다. 아래 windowInsetsPadding 참고).
        enableEdgeToEdge()

        notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                notificationGranted = granted
            }

        setContent {
            FoldWindowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    OnboardingScreen(
                        overlayGranted = overlayGranted,
                        accessibilityGranted = accessibilityGranted,
                        notificationGranted = notificationGranted,
                        shizukuReady = shizukuReady,
                        bubbleRunning = bubbleRunning,
                        onOverlayClick = ::requestOverlayPermission,
                        onAccessibilityClick = ::openAccessibilitySettings,
                        onNotificationClick = ::requestNotificationPermission,
                        onShizukuClick = ::requestShizuku,
                        onToggleBubble = ::toggleBubble,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 오버레이/접근성 설정 화면에서 돌아온 직후 상태를 즉시 반영한다.
        refreshState()
    }

    private fun refreshState() {
        overlayGranted = Settings.canDrawOverlays(this)
        accessibilityGranted = ArrangerAccessibilityService.instance != null
        notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true // API 33 미만은 런타임 알림 권한 자체가 없음 — 항상 충족으로 취급
        }
        shizukuReady = ShizukuShell.isReady()
        bubbleRunning = FloatingLauncherService.isRunning
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri(),
        )
        startActivity(intent)
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    /**
     * [P4-1] Shizuku 카드 탭 처리. 3단계:
     * 1) 바인더 자체가 없음(Shizuku 앱 미실행) → 앱 실행 시도, 미설치면 안내 토스트
     * 2) 바인더는 있으나 권한 미허용 → [ShizukuShell.requestPermission] 으로 권한 요청 다이얼로그
     * 3) 이미 준비됨 → 상태만 재평가(다른 분기와 동일하게 [refreshState] 로 수렴)
     */
    private fun requestShizuku() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            val launchIntent = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
            if (launchIntent != null) {
                startActivity(launchIntent)
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.onboarding_shizuku_not_installed),
                    Toast.LENGTH_LONG,
                ).show()
            }
            return
        }
        ShizukuShell.requestPermission {
            runOnUiThread { refreshState() }
        }
    }

    private fun toggleBubble() {
        // 중지 시퀀스 진행 중에는 재호출(빠른 재탭, 중지 도중 재시작 시도)을 전부 무시한다 —
        // 아래에서 stopInProgress 를 false 로 되돌리기 전까지는 안전하게 재진입할 수 없다.
        if (stopInProgress) return

        val intent = Intent(this, FloatingLauncherService::class.java)
        if (bubbleRunning) {
            // 사용자가 명시적으로 중지 — 부팅 자동 복귀 대상에서 빠지도록 DataStore 쓰기가
            // 완료된 뒤에 서비스를 멈춘다(쓰기 -> 중지 순서 유지, 기존 SharedPreferences 구현과
            // 동일한 의도: 부팅 자동 복귀 제외 확정이 서비스 중지보다 먼저 이뤄져야 한다).
            //
            // [실기기 검증 리뷰 지적 반영, 2026-07-25] 이 시퀀스 전체를 withContext(NonCancellable)
            // 로 감싼다. 폴드 접기 등 구성 변경으로 액티비티가 파괴돼 lifecycleScope 가 취소돼도,
            // 이미 시작된 "쓰기 -> stopService" 시퀀스는 끝까지 실행된다(ProfileStore.safeWrite 도
            // 자체적으로 NonCancellable 이지만, 그것만으로는 쓰기와 stopService 사이에서 취소되는
            // 것까지 막지 못한다 — 이 바깥쪽 NonCancellable 이 두 호출을 하나의 원자적 시퀀스로
            // 보장한다). 이게 없으면 서비스는 살아있는데 DataStore 는 enabled=false 인 불일치가
            // 생긴다. stopInProgress 리셋도 같은 NonCancellable 블록 안에서 수행해 액티비티가
            // 파괴돼도 반드시 정리되게 한다.
            stopInProgress = true
            lifecycleScope.launch {
                withContext(NonCancellable) {
                    store.setBubbleEnabled(false)
                    stopService(intent)
                    stopInProgress = false
                }
            }
        } else {
            startForegroundService(intent)
        }
        // 낙관적 갱신 — 서비스 onCreate/onDestroy 완료 시점과 미세한 시차가 있을 수 있으나
        // onResume 재진입 시 refreshState() 가 실제 상태로 다시 맞춘다.
        bubbleRunning = !bubbleRunning
    }

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// UI
//
// 아래 애니메이션의 durationMillis 는 전부 "연출용 지속시간"이다.
// ADR-2 가 금지하는 "상태 전이를 기다리는 고정 지연(postDelayed/delay)" 과는 무관하다 —
// 권한 상태 자체는 언제나 onResume -> refreshState() 로 동기 재평가되며, 애니메이션은
// 이미 확정된 값 사이를 시각적으로 잇기만 한다.
// ─────────────────────────────────────────────────────────────────────────────

/** 연출용 표준 지속시간 (ms). */
private const val ENTER_MS = 220
private const val EXIT_MS = 160
private const val STATE_MS = 260

@Composable
private fun OnboardingScreen(
    overlayGranted: Boolean,
    accessibilityGranted: Boolean,
    notificationGranted: Boolean,
    shizukuReady: Boolean,
    bubbleRunning: Boolean,
    onOverlayClick: () -> Unit,
    onAccessibilityClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onShizukuClick: () -> Unit,
    onToggleBubble: () -> Unit,
) {
    val requiredDone = listOf(overlayGranted, accessibilityGranted).count { it }
    val bubbleReady = overlayGranted && accessibilityGranted
    // [D7] 설정을 마친 사용자에게 필수 권한 카드 2장은 매 방문마다 지나가야 할 장벽이다.
    // 접힌 상태가 기본이고, 재구성(폴드 접기 등)을 넘겨도 펼침 여부는 유지한다.
    var requiredExpanded by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 상태바/내비게이션 바 밑으로 콘텐츠가 깔리지 않도록 안전 영역만큼 패딩을 준다
            // (enableEdgeToEdge() 로 인해 Surface 는 이미 전체 화면을 차지한다).
            // 이 화면에는 화면 가장자리에 고정되는 요소(하단 바 등)가 없으므로, 인셋은
            // 여전히 이 한 곳에서만 처리하면 된다.
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 이 앱이 무엇을 하는지 한 장으로 — 글보다 그림이 먼저 오도록 최상단에 둔다.
        HeroCard()

        // [F1] 시작 버튼의 호출 지점은 이 한 곳뿐이고, 자리는 언제나 히어로 바로 아래다.
        // 예전에는 bubbleReady 에 따라 "히어로 밑" / "선택 설정 아래" 두 곳으로 갈렸는데,
        // 마지막 필수 권한이 허용되는 순간(설정에서 복귀 -> onResume -> refreshState) 분기가
        // 뒤집히면서 버튼 위쪽 콘텐츠 ~340dp 가 통째로 사라졌다. 스크롤 오프셋은 그대로라
        // 사용자는 "버튼이 화면 위로 사라진" 안내 카드 앞에 남겨졌다. 위치는 고정하고
        // 활성 여부(enabled)만 바꾼다 — 차단 사유 줄은 버튼 바로 아래에 그대로 있다.
        Spacer(modifier = Modifier.height(4.dp))
        StartSection(
            bubbleReady = bubbleReady,
            bubbleRunning = bubbleRunning,
            overlayGranted = overlayGranted,
            accessibilityGranted = accessibilityGranted,
            onOverlayClick = onOverlayClick,
            onAccessibilityClick = onAccessibilityClick,
            onToggleBubble = onToggleBubble,
        )
        Spacer(modifier = Modifier.height(4.dp))

        if (bubbleReady) {
            // [F8] 접힌 AnimatedVisibility 는 높이가 0 이어도 spacedBy(12.dp) 의 자식으로
            // 세어져 「선택 설정」 과의 간격이 12dp 더 붙었다(첫 실행 20dp vs 재방문 32dp).
            // 한 겹 감싸 바깥 Column 의 자식 수를 1 로 고정하고, 펼쳤을 때의 간격은
            // AV 내부 패딩으로 준다 — 접히면 패딩도 함께 사라진다.
            Column {
                RequiredDoneRow(
                    expanded = requiredExpanded,
                    onToggle = { requiredExpanded = !requiredExpanded },
                )
                AnimatedVisibility(
                    visible = requiredExpanded,
                    enter = fadeIn(tween(ENTER_MS)) + expandVertically(tween(ENTER_MS)),
                    exit = fadeOut(tween(EXIT_MS)) + shrinkVertically(tween(EXIT_MS)),
                ) {
                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RequiredPermissionCards(
                            overlayGranted = overlayGranted,
                            accessibilityGranted = accessibilityGranted,
                            onOverlayClick = onOverlayClick,
                            onAccessibilityClick = onAccessibilityClick,
                        )
                    }
                }
            }
        } else {
            SectionHeader(
                text = stringResource(R.string.onboarding_section_required),
                color = MaterialTheme.colorScheme.primary,
            )
            RequiredProgress(done = requiredDone, total = 2)
            RequiredPermissionCards(
                overlayGranted = overlayGranted,
                accessibilityGranted = accessibilityGranted,
                onOverlayClick = onOverlayClick,
                onAccessibilityClick = onAccessibilityClick,
            )
        }

        // [D9] 필수/선택 헤더가 둘 다 primary 라 구분이 없었다 — 선택은 tertiary(올리브).
        SectionHeader(
            text = stringResource(R.string.onboarding_section_optional),
            color = MaterialTheme.colorScheme.tertiary,
        )
        PermissionCard(
            iconRes = R.drawable.ic_setup_notification,
            title = stringResource(R.string.onboarding_permission_notification_title),
            description = stringResource(R.string.onboarding_permission_notification_desc),
            actionLabel = stringResource(R.string.onboarding_action_allow_notification),
            granted = notificationGranted,
            onClick = onNotificationClick,
        )
        PermissionCard(
            iconRes = R.drawable.ic_setup_shizuku,
            title = stringResource(R.string.onboarding_permission_shizuku_title),
            description = stringResource(R.string.onboarding_permission_shizuku_desc),
            actionLabel = stringResource(R.string.onboarding_action_open_shizuku),
            granted = shizukuReady,
            onClick = onShizukuClick,
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_guide_title),
            style = MaterialTheme.typography.titleMedium,
        )
        StepCard(
            step = 1,
            title = stringResource(R.string.onboarding_guide_usage_title),
            body = stringResource(R.string.onboarding_guide_usage),
        )
        // [#30 D20] 롱프레스 메뉴 안내. 자동 배치를 켜면 사용자가 요청하지 않은 배치가 일어날 수
        // 있는데, 되돌리는 경로(분할 해제)와 끄는 경로(토글)가 둘 다 미공개 롱프레스 메뉴 안에만
        // 있었다 — 카드 1장으로 그 발견성을 메운다(설정 화면 신설은 명시적 비목표, §7).
        // 리디자인 후에도 이 카드는 강조 톤(tertiaryContainer)을 유지해 스텝 목록에 묻히지 않게 한다.
        StepCard(
            step = 2,
            title = stringResource(R.string.onboarding_guide_longpress_title),
            body = stringResource(R.string.onboarding_guide_longpress),
            emphasized = true,
        )

        // 주의(경고)는 팁과 같은 모양이면 안 된다 — 에러 컨테이너 톤 + 경고 글리프로 분리한다.
        CautionCard(
            title = stringResource(R.string.onboarding_guide_netflix_title),
            body = stringResource(R.string.onboarding_guide_netflix),
        )
        CautionCard(
            title = stringResource(R.string.onboarding_guide_reenable_title),
            body = stringResource(R.string.onboarding_guide_reenable),
        )
    }
}

// ── 히어로 ────────────────────────────────────────────────────────────────────

/** 다이어그램 가로:세로. 패널 2장 + 화살표가 여백 없이 들어가는 최소 비율(2.46)보다 살짝 넉넉하다. */
private const val DIAGRAM_ASPECT = 2.6f

/** 다이어그램 높이 상한. 폭이 계속 늘어나도(내부 화면 875dp — docs/DEVICE_FACTS.md 실측) 히어로가 화면을 삼키지 않게 한다. */
private val DIAGRAM_MAX_HEIGHT = 180.dp

/**
 * [F10] 다크 전용 영상 블록 색. primaryContainer(#35513F) 를 명도만 눌러 패널 외곽선
 * outline(#90947F) 과 3.15:1 을 확보한다(팔레트 슬롯이 아니라 이 다이어그램 한정 보정값).
 */
private val DIAGRAM_DARK_VIDEO = Color(0xFF304939)

/** 상단 히어로 — 변환 다이어그램 + 그림 설명 한 줄 + 원리 보조 한 줄. */
@Composable
private fun HeroCard() {
    val diagramDesc = stringResource(R.string.onboarding_hero_content_desc)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // [D5] 고정 112dp 는 이 앱의 홈그라운드인 내부 화면(875dp)에서 가장 초라해 보였다.
            // 폭에 비례해 커지되 상한을 둔다.
            // [F9] 흔한 처방인 `aspectRatio(2.6f).heightIn(max = 180.dp)` 은 여기서 상한이
            // 그냥 무시된다 — aspectRatio 가 (W, W/2.6) 으로 이미 확정된 제약을 내려주므로
            // 뒤따르는 heightIn 이 손댈 여지가 없다(높이 0 으로 무너지는 게 아니다).
            // 그래서 폭을 직접 읽어 계산한다.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                TransformDiagram(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((maxWidth / DIAGRAM_ASPECT).coerceAtMost(DIAGRAM_MAX_HEIGHT))
                        .semantics { contentDescription = diagramDesc },
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.onboarding_hero_caption),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.onboarding_hero_caption_secondary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * 앱이 하는 일을 한눈에 보여주는 다이어그램.
 *
 *   [ 띠 ]        [ 영상 ]
 *   [영상]   →    [      ]
 *   [ 띠 ]        [ 남는 공간 ]
 *
 * 오른쪽 패널의 영상 블록이 진입 시 1회만 위로 올라붙으면서 "남는 공간을 한쪽으로 몰아준다"
 * 는 동작을 그대로 재연한다. 루프하지 않는다 — 설정 화면이지 스플래시가 아니다.
 *
 * 좌표는 전부 캔버스 크기 비율로 계산한다(하드코딩 금지, CLAUDE.md 함정 #2 / #5).
 * 좁은 폭(커버 화면·분할 페인)에서도 호출부가 높이를 폭/[DIAGRAM_ASPECT] 로 묶어 주므로
 * 두 패널은 언제나 들어간다(아래 [F11] 주석 참고).
 */
@Composable
private fun TransformDiagram(modifier: Modifier = Modifier) {
    // [D6] 폴드 접기/펼치기와 다크 모드 전환은 이 기기의 일상 조작인데, 그때마다 액티비티가
    // 재생성돼 remember 가 초기화되고 950ms 짜리 연출이 처음부터 다시 돌았다.
    // rememberSaveable 은 이 Boolean 을 저장 상태(onSaveInstanceState 번들)에 넣었다가
    // 재생성 후 복원하므로, 이미 재생했다면 Animatable 을 종료값 1f 로 시작해 그대로 그린다.
    var played by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (played) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!played) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 700, delayMillis = 250, easing = FastOutSlowInEasing),
            )
            played = true
        }
    }

    // [D1] 카드(surfaceContainerLow) 대비 3:1 이상이어야 형태가 "의미를 전달"한다(WCAG 1.4.11).
    // 기존 alpha 0.22 는 라이트 1.39:1 로 사실상 보이지 않았다.
    //   띠  : 라이트 4.02:1 / 다크 6.27:1   (재계산값. 이전 주석의 6.26 은 반올림 오기)
    //   외곽: outlineVariant 1.46:1 → outline 라이트 3.79:1 / 다크 5.33:1
    //   영상: 띠와도 3:1 이 필요하다. primary 는 어두운 띠와 라이트 1.32:1 로 붙어버려
    //         primaryContainer 로 바꿨다(띠 대비 라이트 3.37:1).
    val barColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
    // [F10] 영상 블록은 패널 외곽선과도 맞닿는다(연출이 끝나면 위 모서리에 붙는다).
    // 다크의 primaryContainer(#35513F) 는 outline(#90947F) 과 2.80:1 로 3:1 미달이었다.
    // outline 이 더 밝으므로 "밝히는" 방향은 오히려 붙는다 — 살짝 어둡게 눌러 3.15:1
    // (띠 대비도 3.30 → 3.71:1 로 함께 오른다). 라이트는 outline 대비 3.18:1 로 이미 통과.
    // 위 수치는 전부 WCAG 상대휘도로 재계산해 확인했다(띠는 onSurfaceVariant@0.78 을 카드
    // surfaceContainerLow 위에 합성한 실제 색: 다크 #A29F94 / 라이트 #797B72 기준).
    val videoColor = if (isSystemInDarkTheme()) {
        DIAGRAM_DARK_VIDEO
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val outlineColor = MaterialTheme.colorScheme.outline
    val arrowColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier) {
        // 폴드 내부 화면(2184×1968) 에 가까운, 가로로 살짝 넓은 패널 비율.
        // [F11] 폭이 모자랄 때를 대비한 축소 분기가 있었지만 도달 불가능한 죽은 코드였다:
        // 호출부가 높이를 width/DIAGRAM_ASPECT(2.6) 이하로 클램프하므로 언제나
        // width >= 2.6*height 인데, 필요 폭은 2*1.11*height + 0.24*height = 2.46*height 다.
        // DIAGRAM_ASPECT 를 2.46 밑으로 내리면 이 전제가 깨져 오른쪽 패널이 잘린다
        // (그때는 축소 계수를 panelW/panelH 뿐 아니라 gap 에도 곱해야 한다 — [D4]).
        val gap = size.height * 0.24f
        val panelH = size.height
        val panelW = panelH * 1.11f
        val top = (size.height - panelH) / 2f
        val left0 = (size.width - (panelW * 2f + gap)) / 2f
        val left1 = left0 + panelW + gap

        val videoH = panelW * 9f / 16f
        val barH = ((panelH - videoH) / 2f).coerceAtLeast(0f)

        // 왼쪽: 지금 — 영상이 가운데, 남는 공간이 위/아래로 쪼개져 있다.
        drawPanel(left0, top, panelW, panelH, top + barH, videoH, barColor, videoColor, outlineColor)
        // 오른쪽: 배치 후 — 영상이 위로 붙고 남는 공간이 아래 한 덩어리로 모인다.
        drawPanel(
            left1, top, panelW, panelH,
            top + barH * (1f - progress.value), videoH, barColor, videoColor, outlineColor,
        )

        // 가운데 화살표
        val cy = top + panelH / 2f
        val ax = left0 + panelW + gap * 0.22f
        val aw = gap * 0.56f
        val head = aw * 0.42f
        val stroke = (panelH * 0.022f).coerceAtLeast(1f)
        drawLine(arrowColor, Offset(ax, cy), Offset(ax + aw, cy), stroke, StrokeCap.Round)
        drawLine(arrowColor, Offset(ax + aw - head, cy - head), Offset(ax + aw, cy), stroke, StrokeCap.Round)
        drawLine(arrowColor, Offset(ax + aw - head, cy + head), Offset(ax + aw, cy), stroke, StrokeCap.Round)
    }
}

/** 다이어그램의 화면 패널 1장. 배경 = 남는 공간(검은 띠), 그 위에 영상 블록. */
private fun DrawScope.drawPanel(
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    videoTop: Float,
    videoH: Float,
    barColor: Color,
    videoColor: Color,
    outlineColor: Color,
) {
    val radius = CornerRadius(h * 0.11f)
    val outline = Path().apply {
        addRoundRect(RoundRect(Rect(Offset(x, y), Size(w, h)), radius))
    }
    clipPath(outline) {
        drawRect(barColor, Offset(x, y), Size(w, h))
        drawRect(videoColor, Offset(x, videoTop), Size(w, videoH))
    }
    drawPath(outline, outlineColor, style = Stroke(width = (h * 0.018f).coerceAtLeast(1f)))
}

// ── 설정 단계 ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * 필수 권한 카드 2장. 첫 실행에서는 그대로, 재방문에서는 접힌 줄 안에서 같은 카드를 쓴다.
 *
 * [F7] 이 2장만 `actionWhenGranted` 를 켠다 — 허용된 뒤에도 같은 설정 화면을 다시 여는 것이
 * 실제 동선이기 때문이다(접근성 서비스는 앱을 업데이트할 때마다 꺼진다, CLAUDE.md 함정 #6).
 * 알림·Shizuku 는 이미 허용된 상태에서 버튼을 눌러도 아무 UI 도 뜨지 않으므로 켜지 않는다.
 */
@Composable
private fun RequiredPermissionCards(
    overlayGranted: Boolean,
    accessibilityGranted: Boolean,
    onOverlayClick: () -> Unit,
    onAccessibilityClick: () -> Unit,
) {
    PermissionCard(
        iconRes = R.drawable.ic_setup_overlay,
        title = stringResource(R.string.onboarding_permission_overlay_title),
        description = stringResource(R.string.onboarding_permission_overlay_desc),
        actionLabel = stringResource(R.string.onboarding_action_open_settings),
        granted = overlayGranted,
        actionWhenGranted = true,
        onClick = onOverlayClick,
    )
    PermissionCard(
        iconRes = R.drawable.ic_setup_accessibility,
        title = stringResource(R.string.onboarding_permission_accessibility_title),
        description = stringResource(R.string.onboarding_permission_accessibility_desc),
        actionLabel = stringResource(R.string.onboarding_action_open_settings),
        granted = accessibilityGranted,
        actionWhenGranted = true,
        onClick = onAccessibilityClick,
    )
}

/**
 * [D7] 필수 설정을 마친 사용자용 한 줄 요약. 탭하면 접힌 카드 2장이 펼쳐지고, 다시 탭하면 접힌다.
 * 권한을 다시 확인·수정할 경로를 지우지 않으면서 재방문 동선에서 4장을 2줄로 줄인다.
 */
@Composable
private fun RequiredDoneRow(expanded: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onToggle)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_setup_check),
                contentDescription = null, // 옆 문구가 "완료"임을 이미 말해준다
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.onboarding_required_done),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) {
                    stringResource(R.string.onboarding_required_collapse)
                } else {
                    stringResource(R.string.onboarding_required_expand)
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 필수 설정 진행도 — "몇 개 중 몇 개"를 숫자와 막대 둘 다로 보여준다. */
@Composable
private fun RequiredProgress(done: Int, total: Int) {
    val target = if (total <= 0) 1f else done.toFloat() / total
    val fraction by animateFloatAsState(
        targetValue = target.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 300),
        label = "requiredProgress",
    )
    val complete = done >= total
    // [D8] 미완료 색이 secondary(#A56340, 21°) 였는데 error(#A8503C, 11°) 와 사실상 같은 색으로
    // 읽혀 "진행 중"과 "경고"가 구분되지 않았다 — 올리브(tertiary)로 옮긴다.
    val barColor by animateColorAsState(
        targetValue = if (complete) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.tertiary
        },
        animationSpec = tween(durationMillis = STATE_MS),
        label = "requiredProgressBar",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            // fraction 이 0 이면 채움 블록 자체를 그리지 않는다(0 폭 레이아웃 회피).
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(barColor),
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.onboarding_progress_format, done, total),
            style = MaterialTheme.typography.labelLarge,
            color = if (complete) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/**
 * 권한 1종 카드.
 *
 * [actionLabel] — [D10] 이 카드의 버튼이 실제로 여는 것. 4장이 "권한 설정으로 이동"을 공유하면
 * 알림(시스템 다이얼로그)과 Shizuku(다른 앱) 2장에서 거짓말이 된다.
 *
 * [actionWhenGranted] — 허용된 뒤에도 같은 화면으로 가는 경로를 남길지. 켜면 낮은 강조의
 * 텍스트 버튼으로 바뀐다. 눌러도 아무 UI 가 뜨지 않는 카드(알림·Shizuku)에서는 끈다.
 */
@Composable
private fun PermissionCard(
    @DrawableRes iconRes: Int,
    title: String,
    description: String,
    actionLabel: String,
    granted: Boolean,
    actionWhenGranted: Boolean = false,
    onClick: () -> Unit,
) {
    // 허용됨 → 세이지 프라이머리 컨테이너를 옅게, 필요함 → 서피스 컨테이너 톤 — 두 상태 모두
    // 경고성이 아니라 은은한 오가닉 카드로 표현한다. 설정 화면에서 돌아왔을 때 값이 튀지 않도록
    // 색을 보간한다(연출용 지속시간, ADR-2 무관).
    val containerColor by animateColorAsState(
        targetValue = if (granted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(durationMillis = STATE_MS),
        label = "permissionCardContainer",
    )
    val iconBackground by animateColorAsState(
        targetValue = if (granted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(durationMillis = STATE_MS),
        label = "permissionCardIconBg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (granted) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = STATE_MS),
        label = "permissionCardIconTint",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(iconBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null, // 제목 텍스트가 이미 같은 내용을 읽어준다
                        tint = iconTint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(granted = granted)
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            AnimatedVisibility(
                visible = !granted,
                enter = fadeIn(tween(ENTER_MS)) + expandVertically(tween(ENTER_MS)),
                exit = fadeOut(tween(EXIT_MS)) + shrinkVertically(tween(EXIT_MS)),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(onClick = onClick) {
                        Text(actionLabel)
                    }
                }
            }
            // [F7] 펼친 「필수 설정 완료」 안의 카드에 아무 조작도 없으면 "확인·수정" 이라는
            // 그 줄의 약속이 거짓말이 된다. 같은 콜백을 낮은 강조로 남긴다.
            AnimatedVisibility(
                visible = granted && actionWhenGranted,
                enter = fadeIn(tween(ENTER_MS)) + expandVertically(tween(ENTER_MS)),
                exit = fadeOut(tween(EXIT_MS)) + shrinkVertically(tween(EXIT_MS)),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(onClick = onClick) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

/** 권한 카드 우측 상태 표시 — 완전히 둥근(50%) 알약. 상태가 바뀌면 교차 페이드된다. */
@Composable
private fun StatusPill(granted: Boolean) {
    Crossfade(
        targetState = granted,
        animationSpec = tween(durationMillis = STATE_MS),
        label = "statusPill",
    ) { isGranted ->
        Surface(
            // [D15] 「권한 필요」→「허용됨」 은 글자 폭이 달라 교차 페이드 중 알약이 줄었다 늘었다
            // 하면서 같은 줄의 카드 제목을 밀었다. 최소 폭을 잡아 폭 변화를 없앤다.
            modifier = Modifier.widthIn(min = 76.dp),
            shape = RoundedCornerShape(percent = 50),
            color = if (isGranted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (isGranted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isGranted) {
                    Icon(
                        painter = painterResource(R.drawable.ic_setup_check),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (isGranted) {
                        stringResource(R.string.onboarding_action_granted)
                    } else {
                        stringResource(R.string.onboarding_action_required)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ── 시작 버튼 ─────────────────────────────────────────────────────────────────

/**
 * 버블 시작/중지 + 비활성 사유.
 *
 * 예전에는 조건 미충족 시 버튼이 그냥 회색으로만 바뀌어서 "무엇이 모자란지" 알 수 없었다.
 * 여기서 부족한 권한을 문장으로 명시하고, [D11] 그 문장 자체를 해당 설정 화면으로 가는
 * 버튼으로 만든다(둘 다 없으면 먼저 켜야 하는 오버레이로 보낸다).
 * 차단 사유 줄은 차단 상태에서만 그려지므로 버튼이 활성일 때는 포커스 순서에도 등장하지 않는다.
 *
 * [F1] 이 컴포저블 자체는 [bubbleReady] 와 무관하게 항상 같은 자리에 그려진다 — 바뀌는 것은
 * 버튼의 enabled 와 그 아래 한 줄(차단 사유 <-> 실행 상태)뿐이라, 권한이 채워지는 순간에도
 * 버튼이 이동하지 않는다. 호출부는 한 곳이며 위치를 조건부로 바꾸지 말 것.
 */
@Composable
private fun StartSection(
    bubbleReady: Boolean,
    bubbleRunning: Boolean,
    overlayGranted: Boolean,
    accessibilityGranted: Boolean,
    onOverlayClick: () -> Unit,
    onAccessibilityClick: () -> Unit,
    onToggleBubble: () -> Unit,
) {
    val blockedRes = when {
        !overlayGranted && !accessibilityGranted -> R.string.onboarding_blocked_both
        !overlayGranted -> R.string.onboarding_blocked_overlay
        else -> R.string.onboarding_blocked_accessibility
    }
    val blockedAction = if (!overlayGranted) onOverlayClick else onAccessibilityClick
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = onToggleBubble,
            enabled = bubbleReady,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
        ) {
            Text(
                text = if (bubbleRunning) {
                    stringResource(R.string.onboarding_bubble_stop)
                } else {
                    stringResource(R.string.onboarding_bubble_start)
                },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        AnimatedVisibility(
            visible = !bubbleReady,
            enter = fadeIn(tween(ENTER_MS)) + expandVertically(tween(ENTER_MS)),
            exit = fadeOut(tween(EXIT_MS)) + shrinkVertically(tween(EXIT_MS)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // [F5] foundation 의 clickable 은 최소 터치 영역을 보장하지 않는다
                    // (Material3 버튼과 달리 minimumInteractiveComponentSize 가 없다).
                    // bodySmall 한 줄 + 상하 8dp 는 40dp 도 안 됐다 — 48dp 를 직접 잡는다.
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClick = blockedAction)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_setup_warning),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(blockedRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    // fill = false: 문구는 제 폭만 차지하고 화살표가 바로 뒤에 붙는다.
                    // weight 를 주는 이유는 큰 글꼴 배율에서 문구가 폭을 다 먹고 화살표를
                    // 0dp 로 밀어내지 않게 하기 위함이다(가중치 없는 화살표가 먼저 측정된다).
                    modifier = Modifier.weight(1f, fill = false),
                )
                // [F6] 화살표는 "누를 수 있다"는 시각 힌트일 뿐이다. 문자열에 넣어 뒀더니
                // TalkBack 이 문장마다 "오른쪽 화살표"를 덧붙여 읽었다 — 여기서 그리고
                // 접근성 트리에서는 지운다(Role.Button 이 이미 조작 가능함을 알려준다).
                // Icon 이 아니라 Text 글리프인 것은 의도다: 어포던스·틴트·접근성 제거는 이미
                // 동일하게 얻으면서 벡터 에셋을 하나 더 들이지 않고, 앞 문구와 같은 bodySmall
                // 로 글꼴 배율을 따라 커진다(고정 dp 아이콘은 큰 배율에서 홀로 작아진다).
                // 이 앱은 한국어 전용(LTR)이라 자동 미러링이 필요 없다.
                Text(
                    text = "→",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .clearAndSetSemantics { },
                )
            }
        }
        AnimatedVisibility(
            visible = bubbleReady,
            enter = fadeIn(tween(ENTER_MS)) + expandVertically(tween(ENTER_MS)),
            exit = fadeOut(tween(EXIT_MS)) + shrinkVertically(tween(EXIT_MS)),
        ) {
            Text(
                text = if (bubbleRunning) {
                    stringResource(R.string.onboarding_bubble_running)
                } else {
                    stringResource(R.string.onboarding_bubble_stopped)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── 사용 방법 ─────────────────────────────────────────────────────────────────

/** 번호가 붙은 사용 단계. [emphasized] 는 되돌리기 경로처럼 묻히면 안 되는 항목에 쓴다. */
@Composable
private fun StepCard(
    step: Int,
    title: String,
    body: String,
    emphasized: Boolean = false,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            // [D3] 강조 톤이 primaryContainer@0.55(#E5EDE5) 라 "허용됨" 카드(@0.6, #E4EDE4)와
            // 1.004:1 차이로 같은 색이었다 — 서로 다른 의미가 같은 색이면 안 된다.
            // 색상환이 다른 tertiaryContainer(올리브)로 옮긴다.
            containerColor = if (emphasized) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(modifier = Modifier.padding(18.dp)) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$step", // 숫자 그 자체 — 번역 대상 문구가 아니다
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 주의 사항. 팁(StepCard)과 절대 같은 모양이면 안 된다 — 톤·글리프를 모두 분리한다. */
@Composable
private fun CautionCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(modifier = Modifier.padding(18.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_setup_warning),
                contentDescription = null, // 제목이 "주의"임을 이미 말해준다
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}
