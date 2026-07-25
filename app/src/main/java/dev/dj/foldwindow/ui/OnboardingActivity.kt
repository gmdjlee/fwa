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
import dev.dj.foldwindow.service.ArrangerAccessibilityService
import dev.dj.foldwindow.service.FloatingLauncherService

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
        val intent = Intent(this, FloatingLauncherService::class.java)
        if (bubbleRunning) {
            // 사용자가 명시적으로 중지 — 부팅 자동 복귀 대상에서 빠지도록 서비스 시작 전에 prefs 를 내린다.
            getSharedPreferences(FloatingLauncherService.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(FloatingLauncherService.PREF_BUBBLE_ENABLED, false)
                .apply()
            stopService(intent)
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
