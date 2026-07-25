package dev.dj.foldwindow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import dev.dj.foldwindow.data.ProfileStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * P3-1 보조: 콜드 부팅 후 플로팅 버블을 자동 복귀시킨다 (Phase 3 완료 기준 ①).
 *
 * 조건: [ProfileStore.isBubbleEnabled] 가 true(사용자가 마지막으로 버블을 켜 둔 채 껐다) 이고
 * 오버레이 권한이 살아 있을 때만 [FloatingLauncherService] 를 시작한다. 조건 미충족 시
 * 조용히 무시한다 — 부팅 시점에는 크래시/토스트를 절대 띄우지 않는다 (사용자가 볼 화면이 없음).
 *
 * [실부팅 검증 통과, 2026-07-25] specialUse FGS 를 BOOT_COMPLETED 컨텍스트에서
 * startForegroundService 로 기동하는 경로 자체는 One UI 8 / Android 16 실기기에서 정상 동작함이
 * 확인됐다.
 *
 * [미검증] P3-3 에서 DataStore 읽기를 goAsync() + 코루틴으로 비동기화했다 — 이 새 코드 경로
 * (goAsync 수명 관리 포함)는 실부팅 재검증 전이다. 기존 동기 SharedPreferences 읽기와 의미론은
 * 동일하게 유지했다(조건/로그 문구 불변).
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // DataStore 읽기는 suspend 이므로 BroadcastReceiver 수명을 goAsync() 로 연장해야 한다.
        // pendingResult.finish() 전에 프로세스가 회수되면 이후 로직이 통째로 유실될 수 있다.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    val store = ProfileStore(appContext)
                    val enabled = store.isBubbleEnabled()
                    if (!enabled) {
                        Log.i(TAG, "boot: 버블 비활성 상태 — 자동 시작 생략")
                        return@runCatching
                    }
                    if (!Settings.canDrawOverlays(appContext)) {
                        Log.w(TAG, "boot: overlay 권한 없음 — 자동 시작 생략 (조용한 무시, 부팅 시점 크래시 금지)")
                        return@runCatching
                    }

                    Log.i(TAG, "boot: 버블 자동 복귀 시작")
                    appContext.startForegroundService(Intent(appContext, FloatingLauncherService::class.java))
                }.onFailure { e ->
                    // 조용한 실패 금지: 부팅 시점이라 토스트는 못 띄우지만 로그로 드러낸다.
                    Log.e(TAG, "boot: 버블 자동 시작 실패", e)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "FWBootReceiver"
    }
}
