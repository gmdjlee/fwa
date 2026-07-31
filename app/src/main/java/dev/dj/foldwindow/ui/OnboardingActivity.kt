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
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
                Toast.makeText(this, "Shizuku 앱을 설치하세요", Toast.LENGTH_LONG).show()
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            // 상태바/내비게이션 바 밑으로 콘텐츠가 깔리지 않도록 안전 영역만큼 패딩을 준다
            // (enableEdgeToEdge() 로 인해 Surface 는 이미 전체 화면을 차지한다).
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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

        PermissionCard(
            title = stringResource(R.string.onboarding_permission_overlay_title),
            description = stringResource(R.string.onboarding_permission_overlay_desc),
            granted = overlayGranted,
            onClick = onOverlayClick,
        )
        PermissionCard(
            title = stringResource(R.string.onboarding_permission_accessibility_title),
            description = stringResource(R.string.onboarding_permission_accessibility_desc),
            granted = accessibilityGranted,
            onClick = onAccessibilityClick,
        )
        PermissionCard(
            title = stringResource(R.string.onboarding_permission_notification_title),
            description = stringResource(R.string.onboarding_permission_notification_desc),
            granted = notificationGranted,
            onClick = onNotificationClick,
        )
        PermissionCard(
            title = stringResource(R.string.onboarding_permission_shizuku_title),
            description = stringResource(R.string.onboarding_permission_shizuku_desc),
            granted = shizukuReady,
            onClick = onShizukuClick,
        )

        val bubbleReady = overlayGranted && accessibilityGranted
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
        Text(
            text = if (bubbleRunning) {
                stringResource(R.string.onboarding_bubble_running)
            } else {
                stringResource(R.string.onboarding_bubble_stopped)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(text = stringResource(R.string.onboarding_guide_title), style = MaterialTheme.typography.titleMedium)
        GuideCard(text = stringResource(R.string.onboarding_guide_usage))
        GuideCard(
            title = stringResource(R.string.onboarding_guide_netflix_title),
            text = stringResource(R.string.onboarding_guide_netflix),
        )
        GuideCard(text = stringResource(R.string.onboarding_guide_reenable))
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    // 허용됨 → 세이지 프라이머리 컨테이너를 옅게, 필요함 → 서프리스 변형 톤 — 두 상태 모두
    // 경고성이 아니라 은은한 오가닉 카드로 표현한다.
    val containerColor = if (granted) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(granted = granted)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                Spacer(modifier = Modifier.height(12.dp))
                FilledTonalButton(onClick = onClick) { Text(stringResource(R.string.onboarding_action_grant)) }
            }
        }
    }
}

/** 권한 카드 우측 상단 상태 표시 — 완전히 둥근(50%) 알약 모양 칩. */
@Composable
private fun StatusPill(granted: Boolean) {
    val containerColor = if (granted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (granted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Text(
            text = if (granted) {
                stringResource(R.string.onboarding_action_granted)
            } else {
                stringResource(R.string.onboarding_action_required)
            },
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun GuideCard(title: String? = null, text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
