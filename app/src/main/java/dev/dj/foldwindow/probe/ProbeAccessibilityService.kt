package dev.dj.foldwindow.probe

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import dev.dj.foldwindow.domain.LetterboxDetector
import dev.dj.foldwindow.platform.toLetterboxScan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Phase 0 전용. 미확인 항목 5·6·7을 코드로 확인하고 Fold 7 실측값을 수집한다.
 *
 * 이 서비스는 Phase 2의 실제 액추에이터가 아니다. 검증이 끝나면 격리된 채로 남겨두거나 제거한다.
 *
 * 사용법:
 *   1. 설정 → 접근성 → 설치된 앱 → FoldWindow Probe 켜기
 *   2. 대상 앱(유튜브 등)에서 영상을 가로 전체화면으로 재생
 *   3. ProbeActivity 또는 알림에서 "진단 실행"
 *   4. 리포트가 앱 외부 저장소에 저장됨
 */
class ProbeAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    private var lastForegroundPkg: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "probe service connected")
    }

    override fun onDestroy() {
        instance = null
        screenshotExecutor.shutdown()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return
            if (pkg != packageName && pkg !in SYSTEM_PACKAGES) lastForegroundPkg = pkg
        }
    }

    override fun onInterrupt() = Unit

    // ══════════════════════════════════════════════════════════
    // 진단 실행
    // ══════════════════════════════════════════════════════════

    fun runProbe(onDone: (ProbeReport) -> Unit) {
        scope.launch {
            val report = ProbeReport(
                device = probeDevice(),
                windows = probeWindows(),
                splitAction = probeSplitAction(),
                metrics = probeMetrics(),
                letterbox = probeLetterbox(),
                foregroundPackage = lastForegroundPkg,
            )
            onDone(report)
        }
    }

    // ── A. 기기 기능 (미지수 #5) ───────────────────────────────

    private fun probeDevice(): DeviceProbe {
        val pm = packageManager
        fun globalInt(key: String): Int? =
            runCatching { Settings.Global.getInt(contentResolver, key) }.getOrNull()

        return DeviceProbe(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            sdkInt = Build.VERSION.SDK_INT,
            release = Build.VERSION.RELEASE,
            oneUiVersion = runCatching {
                Settings.Global.getString(contentResolver, "one_ui_version")
            }.getOrNull(),
            hasFreeformFeature = pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT),
            hasPipFeature = pm.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE),
            enableFreeformSupport = globalInt("enable_freeform_support"),
            enableNonResizableMultiWindow = globalInt("enable_non_resizable_multi_window"),
            forceResizable = globalInt("force_resizable_activities"),
        )
    }

    // ── B. 창 덤프 (미지수 #7) ─────────────────────────────────

    private fun probeWindows(): WindowsProbe {
        val list = runCatching { windows }.getOrNull().orEmpty()
        val entries = list.map { w ->
            val b = Rect().also { w.getBoundsInScreen(it) }
            WindowEntry(
                type = w.type,
                typeName = windowTypeName(w.type),
                layer = w.layer,
                bounds = "${b.left},${b.top},${b.right},${b.bottom}",
                packageName = runCatching { w.root?.packageName?.toString() }.getOrNull(),
                isActive = w.isActive,
            )
        }
        val divider = list.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER }
        val dividerBounds = divider?.let { w -> Rect().also { w.getBoundsInScreen(it) } }

        return WindowsProbe(
            count = entries.size,
            entries = entries,
            dividerExposed = divider != null,
            dividerBounds = dividerBounds?.let { "${it.left},${it.top},${it.right},${it.bottom}" },
            dividerThicknessPx = dividerBounds?.height(),
        )
    }

    // ── C. 분할 진입 (미지수 #6) ───────────────────────────────

    /**
     * ⚠ ADR-2: 고정 지연 금지. 호출 후 디바이더 창 출현을 폴링한다.
     */
    private suspend fun probeSplitAction(): SplitActionProbe {
        val before = hasDivider()
        val accepted = runCatching { performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN) }
            .getOrElse { false }

        val startedAt = System.currentTimeMillis()
        val appeared = withTimeoutOrNull(SPLIT_POLL_TIMEOUT_MS) {
            while (hasDivider() == before) delay(SPLIT_POLL_INTERVAL_MS)
            true
        } ?: false

        return SplitActionProbe(
            dividerPresentBefore = before,
            globalActionReturnedTrue = accepted,
            dividerAppeared = appeared,
            elapsedMs = System.currentTimeMillis() - startedAt,
            verdict = when {
                appeared -> "WORKS — performGlobalAction 경로 사용 가능"
                accepted -> "AMBIGUOUS — true를 반환했지만 디바이더가 나타나지 않음. Recents 폴백 필요"
                else -> "FAILS — Recents 폴백 전략으로 전환 필요"
            },
        )
    }

    private fun hasDivider(): Boolean =
        runCatching {
            windows.any { it.type == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER }
        }.getOrDefault(false)

    // ── D. 창 메트릭 ───────────────────────────────────────────

    private fun probeMetrics(): MetricsProbe {
        val dm = resources.displayMetrics
        val cfg = resources.configuration
        val rootBounds = runCatching {
            Rect().also { rootInActiveWindow?.getBoundsInScreen(it) }
        }.getOrNull()

        return MetricsProbe(
            widthPx = dm.widthPixels,
            heightPx = dm.heightPixels,
            density = dm.density,
            densityDpi = dm.densityDpi,
            smallestScreenWidthDp = cfg.smallestScreenWidthDp,
            screenWidthDp = cfg.screenWidthDp,
            screenHeightDp = cfg.screenHeightDp,
            orientation = if (cfg.orientation == 2) "LANDSCAPE" else "PORTRAIT",
            rootWindowBounds = rootBounds?.let { "${it.left},${it.top},${it.right},${it.bottom}" },
        )
    }

    // ── E. 검은 띠 실측 (ADR-1 ② 실증) ─────────────────────────

    private suspend fun probeLetterbox(): LetterboxProbe {
        val bitmap = captureScreen()
            ?: return LetterboxProbe(captured = false, note = "takeScreenshot 실패 (레이트 리밋 또는 미지원)")

        return try {
            val scan = bitmap.toLetterboxScan()
            val measurement = LetterboxDetector.resolveAspect(scan)
            LetterboxProbe(
                captured = true,
                frameWidth = scan.width,
                frameHeight = scan.height,
                topBarPx = measurement?.band?.topBarPx,
                bottomBarPx = measurement?.band?.bottomBarPx,
                contentHeightPx = measurement?.band?.height,
                rawAspect = measurement?.raw,
                snappedAspect = measurement?.snapped,
                confidence = measurement?.confidence,
                note = when {
                    measurement == null -> "검은 띠를 찾지 못함. 영상을 가로 전체화면으로 재생한 뒤 다시 실행할 것"
                    measurement.isSnapped -> "프리셋 스냅 성공"
                    else -> "프리셋에 맞지 않는 비율. raw 값 사용"
                },
            )
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * ⚠ CLAUDE.md 함정 #3: takeScreenshot 은 약 1초당 1회 레이트 리밋이 있다.
     * ⚠ 함정 #4: HardwareBuffer 는 반드시 close 한다.
     */
    private suspend fun captureScreen(): Bitmap? = suspendCancellableCoroutine { cont ->
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cont.resume(null); return@suspendCancellableCoroutine
        }
        runCatching {
            takeScreenshot(
                android.view.Display.DEFAULT_DISPLAY,
                screenshotExecutor,
                object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val bmp = try {
                            Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)
                        } finally {
                            buffer.close()
                        }
                        if (cont.isActive) cont.resume(bmp)
                    }

                    override fun onFailure(errorCode: Int) {
                        Log.w(TAG, "takeScreenshot failed: $errorCode")
                        if (cont.isActive) cont.resume(null)
                    }
                },
            )
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    private fun windowTypeName(type: Int): String = when (type) {
        AccessibilityWindowInfo.TYPE_APPLICATION -> "APPLICATION"
        AccessibilityWindowInfo.TYPE_INPUT_METHOD -> "INPUT_METHOD"
        AccessibilityWindowInfo.TYPE_SYSTEM -> "SYSTEM"
        AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> "ACCESSIBILITY_OVERLAY"
        AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> "SPLIT_SCREEN_DIVIDER"
        AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY -> "MAGNIFICATION_OVERLAY"
        else -> "UNKNOWN($type)"
    }

    companion object {
        private const val TAG = "FWProbe"
        private const val SPLIT_POLL_TIMEOUT_MS = 3_000L
        private const val SPLIT_POLL_INTERVAL_MS = 100L

        private val SYSTEM_PACKAGES = setOf(
            "com.android.systemui",
            "com.samsung.android.app.cocktailbarservice",
        )

        @Volatile
        var instance: ProbeAccessibilityService? = null
            private set
    }
}
