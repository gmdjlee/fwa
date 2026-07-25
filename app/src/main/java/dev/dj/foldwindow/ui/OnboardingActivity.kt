package dev.dj.foldwindow.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
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
import dev.dj.foldwindow.R
import dev.dj.foldwindow.data.ProfileStore
import dev.dj.foldwindow.service.ArrangerAccessibilityService
import dev.dj.foldwindow.service.FloatingLauncherService
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        notificationPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                notificationGranted = granted
            }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    OnboardingScreen(
                        overlayGranted = overlayGranted,
                        accessibilityGranted = accessibilityGranted,
                        notificationGranted = notificationGranted,
                        bubbleRunning = bubbleRunning,
                        onOverlayClick = ::requestOverlayPermission,
                        onAccessibilityClick = ::openAccessibilitySettings,
                        onNotificationClick = ::requestNotificationPermission,
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
        bubbleRunning = FloatingLauncherService.isRunning
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
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
}

@Composable
private fun OnboardingScreen(
    overlayGranted: Boolean,
    accessibilityGranted: Boolean,
    notificationGranted: Boolean,
    bubbleRunning: Boolean,
    onOverlayClick: () -> Unit,
    onAccessibilityClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onToggleBubble: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall)

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

        val bubbleReady = overlayGranted && accessibilityGranted
        Button(onClick = onToggleBubble, enabled = bubbleReady, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = if (bubbleRunning) {
                    stringResource(R.string.onboarding_bubble_stop)
                } else {
                    stringResource(R.string.onboarding_bubble_start)
                },
            )
        }
        Text(
            text = if (bubbleRunning) {
                stringResource(R.string.onboarding_bubble_running)
            } else {
                stringResource(R.string.onboarding_bubble_stopped)
            },
            style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider()

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
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (granted) {
                        stringResource(R.string.onboarding_action_granted)
                    } else {
                        stringResource(R.string.onboarding_action_required)
                    },
                    color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            if (!granted) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onClick) { Text(stringResource(R.string.onboarding_action_grant)) }
            }
        }
    }
}

@Composable
private fun GuideCard(title: String? = null, text: String) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
