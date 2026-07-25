package dev.dj.foldwindow.ui

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * P2-5 / ADR-3: 파트너(비영상) 창. v1 = PartnerMode.BLACK — 검정 배경 + 시계뿐인 최소 구현.
 * Phase 3에서 PartnerMode 확장(메모/임의 앱 지정)에 맞춰 교체될 자리다.
 *
 * ⚠ @string/panel_title 값은 반드시 "FW Panel" 이어야 한다. platform/SplitEntry.kt 의 step4
 * 폴백(findPanelPickerNode)이 Recents 파트너 피커에서 이 라벨 문자열로 우리 앱을 찾는다.
 *
 * 고정 크기 가정 금지 (CLAUDE.md 함정 #5): Android 16 적응형 동작 대상 기기(sw≥600dp)에서
 * 이 액티비티는 임의 크기로 리사이즈될 수 있다. 레이아웃은 항상 fillMaxSize 기준으로 반응한다.
 */
class PanelActivity : ComponentActivity() {

    private var fullscreenGuardJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { PanelScreen() } } }
    }

    override fun onResume() {
        super.onResume()
        // 자기 방어: 이 액티비티는 분할 파트너 전용이다. 전체화면으로 떠 있으면
        // (예: 오래된 태스크가 전면으로 재사용된 경우) 스스로 제거해 화면을 뺏지 않는다.
        // 600ms 유예: 분할 배치 전환 중 일시적으로 멀티윈도우 아님으로 보고될 수 있어
        // 즉시 종료하면 정상 배치를 죽인다. 이 대기는 라이프사이클 전환 유예이지
        // 상태 전이 대체가 아님 (ADR-2 취지 유지).
        scheduleFullscreenCheck(600)
    }

    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        if (!isInMultiWindowMode) {
            // 분할 해제 = 파트너 창의 존재 이유 소멸. 잔존 태스크가 다음 배치에서 전체화면으로
            // 재사용되는 것을 원천 차단한다 ([측정] 2026-07-25 깜빡임 루프).
            // 전환 블립 오탐 방지로 400ms 뒤 재확인 후 종료.
            scheduleFullscreenCheck(400)
        }
    }

    private fun scheduleFullscreenCheck(graceMs: Long) {
        fullscreenGuardJob?.cancel()
        fullscreenGuardJob = lifecycleScope.launch {
            delay(graceMs)
            if (!isInMultiWindowMode) {
                Log.i(TAG, "fullscreen 상태 감지 — 파트너 전용 액티비티이므로 종료")
                finishAndRemoveTask()
            }
        }
    }

    override fun onPause() {
        // 백그라운드 전환 중 가드가 발화하지 않도록 취소한다.
        fullscreenGuardJob?.cancel()
        fullscreenGuardJob = null
        super.onPause()
    }

    private companion object {
        const val TAG = "PanelActivity"
    }
}

@Composable
private fun PanelScreen() {
    var timeText by remember { mutableStateOf(currentTimeText()) }

    // UI 시계 갱신용 1초 틱. ADR-2가 금지하는 "타이밍을 맞추기 위한 고정 지연"이 아니라
    // 단순 화면 갱신 루프다 (오케스트레이션 상태 전이와 무관).
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            timeText = currentTimeText()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = timeText,
            color = Color.Gray.copy(alpha = 0.4f),
            fontSize = 48.sp,
        )
        Text(
            text = "FoldWindow",
            color = Color.Gray.copy(alpha = 0.4f),
            fontSize = 14.sp,
        )
    }
}

private fun currentTimeText(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
