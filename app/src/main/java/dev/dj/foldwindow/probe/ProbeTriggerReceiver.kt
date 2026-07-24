package dev.dj.foldwindow.probe

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.io.File

/**
 * Phase 0 전용. adb 브로드캐스트로 프로브를 트리거한다.
 *
 * 배경: 프로브 E(레터박스 실측)는 영상이 가로 전체화면인 상태에서 실행해야 하는데,
 * 실행 버튼이 ProbeActivity 안에 있어 전체화면을 벗어나야만 누를 수 있는 설계 공백이 있다.
 * adb 트리거로 전체화면을 유지한 채 진단을 실행할 수 있게 한다.
 *
 * 사용:
 *   adb shell am broadcast -a dev.dj.foldwindow.probe.RUN_PROBE \
 *     -n dev.dj.foldwindow/.probe.ProbeTriggerReceiver
 *
 * 완료 확인:
 *   adb logcat -d | grep PROBE_DONE
 *
 * ⚠ Phase 0 이후 제거 예정 코드. probe/ 패키지에 격리되어 있다.
 */
class ProbeTriggerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_RUN_PROBE) return

        val svc = ProbeAccessibilityService.instance
        if (svc == null) {
            // 조용한 실패 금지: 서비스 미연결을 로그로 드러낸다.
            Log.w(TAG, "probe service not connected — 접근성 서비스를 켠 뒤 다시 시도하세요")
            return
        }

        // runProbe 는 서비스 코루틴 스코프에서 돌고 콜백이 onReceive 반환 후 비동기로 실행된다.
        // goAsync() 로 브로드캐스트 수명을 연장하고, 파일 쓰기는 receiver context 대신
        // applicationContext 를 캡처해 수명 문제를 회피한다.
        val appContext = context.applicationContext
        val pendingResult = goAsync()

        Log.i(TAG, "PROBE_START — runProbe 호출")
        svc.runProbe { report ->
            try {
                val md = report.toMarkdown()
                // ProbeActivity 와 동일 경로를 사용한다.
                val file = File(appContext.getExternalFilesDir(null), "probe_report.md")
                file.writeText(md)
                Log.i(TAG, "PROBE_DONE -> ${file.absolutePath}")
            } catch (t: Throwable) {
                // 조용한 실패 금지: 저장 실패를 로그로 드러낸다.
                Log.e(TAG, "PROBE_FAILED — 리포트 저장 실패", t)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "FWProbe"
        const val ACTION_RUN_PROBE = "dev.dj.foldwindow.probe.RUN_PROBE"
    }
}
