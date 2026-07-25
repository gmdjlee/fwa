package dev.dj.foldwindow.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import dev.dj.foldwindow.domain.Placement

/**
 * Phase 2 디버그 트리거. Phase 3에서 플로팅 버블(FloatingLauncherService)이 이 자리를 대체한다.
 * probe/ProbeTriggerReceiver.kt 와 동일한 이유로 exported=true 를 허용한다: adb `am broadcast` 로
 * 접근성 서비스 액션을 외부에서 트리거하기 위한 디버그 도구다.
 *
 * 사용:
 *   adb shell am broadcast -a dev.dj.foldwindow.ARRANGE \
 *     -n dev.dj.foldwindow/.service.ArrangeTriggerReceiver \
 *     [--es placement top|bottom] [--ef aspect 1.7778] [--ez cancel true]
 *
 * ⚠ Phase 3 이후 제거 대상 코드. service/ 패키지에 격리되어 있다.
 */
class ArrangeTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ARRANGE) return

        val service = ArrangerAccessibilityService.instance
        if (service == null) {
            // 조용한 실패 금지: 서비스 미연결을 로그 + 토스트로 드러낸다.
            Log.w(TAG, "arranger service not connected — 접근성 서비스를 켠 뒤 다시 시도하세요")
            Toast.makeText(context, "접근성 서비스가 꺼져 있습니다", Toast.LENGTH_LONG).show()
            return
        }

        if (intent.getBooleanExtra(EXTRA_CANCEL, false)) {
            Log.i(TAG, "ARRANGE cancel 요청 수신")
            service.cancelArrange()
            return
        }

        val placement = when (intent.getStringExtra(EXTRA_PLACEMENT)?.lowercase()) {
            "top" -> Placement.TOP
            "bottom" -> Placement.BOTTOM
            null -> null
            else -> {
                Log.w(TAG, "알 수 없는 placement extra — 무시하고 프로파일/기본값 사용")
                null
            }
        }

        val aspect = if (intent.hasExtra(EXTRA_ASPECT)) {
            intent.getFloatExtra(EXTRA_ASPECT, -1f).takeIf { it > 0f }
        } else {
            null
        }

        Log.i(TAG, "ARRANGE 요청 수신: placement=$placement aspect=$aspect")
        service.startArrange(placement, aspect)
    }

    companion object {
        private const val TAG = "FWArrangeTrigger"

        const val ACTION_ARRANGE = "dev.dj.foldwindow.ARRANGE"
        const val EXTRA_PLACEMENT = "placement"
        const val EXTRA_ASPECT = "aspect"
        const val EXTRA_CANCEL = "cancel"
    }
}
