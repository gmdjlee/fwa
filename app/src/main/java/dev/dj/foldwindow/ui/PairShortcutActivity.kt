package dev.dj.foldwindow.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import dev.dj.foldwindow.R
import dev.dj.foldwindow.service.ArrangerAccessibilityService

/**
 * P4-4: 홈 화면 고정 바로가기(pinned shortcut) 트램펄린. [FloatingLauncherService] 의
 * `exportAppPair()` 가 `ShortcutManagerCompat.requestPinShortcut` 으로 생성을 요청하는 바로가기의
 * 인텐트 대상이 이 액티비티다.
 *
 * UI 를 전혀 그리지 않는다(`setContent` 호출 없음, 매니페스트 테마는
 * `@android:style/Theme.Translucent.NoTitleBar`) — [EXTRA_TARGET_PACKAGE] 로 받은 패키지를
 * `startActivity` 로 실행한 뒤 [ArrangerAccessibilityService.startArrangeWhenForeground] 로 넘기고
 * 즉시 finish 한다.
 *
 * 전 구간을 [run] 하나로 묶어 runCatching 으로 방어한다 — 트램펄린이 크래시하면 사용자가 홈 화면
 * 바로가기 자체를 잃는 것과 마찬가지라(바로가기는 재설치 전까지 남아있지만 탭할 때마다 크래시) 조용히
 * 넘어가지 않고 반드시 로그로 드러낸다(조용한 실패 금지).
 */
class PairShortcutActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { run() }
            .onFailure { e -> Log.e(TAG, "PairShortcutActivity 처리 중 예외", e) }
        finish()
    }

    private fun run() {
        val targetPkg = intent?.getStringExtra(EXTRA_TARGET_PACKAGE)
        if (targetPkg.isNullOrBlank()) {
            Log.w(TAG, "PairShortcutActivity: EXTRA_TARGET_PACKAGE 없음/빈 값 — 무시")
            return
        }

        val service = ArrangerAccessibilityService.instance
        if (service == null) {
            Toast.makeText(this, getString(R.string.toast_pair_accessibility_off), Toast.LENGTH_LONG).show()
            // FLAG_ACTIVITY_NEW_TASK: 이 트램펄린은 자기 전용 taskAffinity 로 떠 있다 — 플래그가
            // 없으면 OnboardingActivity 가 이 트램펄린의 태스크에 얹혀 태스크 위생이 깨진다
            // (FloatingLauncherService.launchOnboarding 과 동일 근거).
            startActivity(
                Intent(this, OnboardingActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            return
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(targetPkg)
        if (launchIntent == null) {
            Log.w(TAG, "PairShortcutActivity: 대상 앱($targetPkg) launch intent 없음 — 제거된 앱으로 추정")
            Toast.makeText(this, getString(R.string.toast_pair_app_not_found), Toast.LENGTH_LONG).show()
            return
        }

        // FLAG_ACTIVITY_NEW_TASK: 대상 앱은 이 트램펄린의 태스크(taskAffinity 격리됨)가 아니라
        // 자기 자신의 태스크에서 시작돼야 한다 — 없으면 대상 앱 액티비티가 이 트램펄린의
        // excludeFromRecents 태스크에 얹혀 Recents 에서 사라지는 부작용이 생긴다.
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        service.startArrangeWhenForeground(targetPkg)
    }

    companion object {
        private const val TAG = "FWPairShortcut"

        const val EXTRA_TARGET_PACKAGE = "dev.dj.foldwindow.EXTRA_TARGET_PACKAGE"
    }
}
