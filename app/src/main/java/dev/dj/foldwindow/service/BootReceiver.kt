package dev.dj.foldwindow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * P3-1 보조: 콜드 부팅 후 플로팅 버블을 자동 복귀시킨다 (Phase 3 완료 기준 ①).
 *
 * 조건: `bubble_enabled` prefs 가 true(사용자가 마지막으로 버블을 켜 둔 채 껐다) 이고
 * 오버레이 권한이 살아 있을 때만 [FloatingLauncherService] 를 시작한다. 조건 미충족 시
 * 조용히 무시한다 — 부팅 시점에는 크래시/토스트를 절대 띄우지 않는다 (사용자가 볼 화면이 없음).
 *
 * [미검증] specialUse FGS 를 BOOT_COMPLETED 컨텍스트에서 startForegroundService 로 기동하는 것이
 * One UI 8 / Android 16 백그라운드 시작 제약에 걸리지 않는지 실기기 미검증. BOOT_COMPLETED 는
 * 통상 배경 시작 제한의 예외 사유로 알려져 있으나(문서 근거), 이 기기에서의 실동작은 확인 전이다.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val prefs = context.getSharedPreferences(FloatingLauncherService.PREFS_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(FloatingLauncherService.PREF_BUBBLE_ENABLED, false)
        if (!enabled) {
            Log.i(TAG, "boot: 버블 비활성 상태 — 자동 시작 생략")
            return
        }
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "boot: overlay 권한 없음 — 자동 시작 생략 (조용한 무시, 부팅 시점 크래시 금지)")
            return
        }

        Log.i(TAG, "boot: 버블 자동 복귀 시작")
        val serviceIntent = Intent(context, FloatingLauncherService::class.java)
        runCatching {
            context.startForegroundService(serviceIntent)
        }.onFailure { e ->
            // 조용한 실패 금지: 부팅 시점이라 토스트는 못 띄우지만 로그로 드러낸다.
            Log.e(TAG, "boot: 버블 자동 시작 실패", e)
        }
    }

    private companion object {
        const val TAG = "FWBootReceiver"
    }
}
