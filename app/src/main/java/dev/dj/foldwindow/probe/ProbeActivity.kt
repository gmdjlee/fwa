package dev.dj.foldwindow.probe

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/**
 * Phase 0 진단 실행 UI.
 *
 * 절차
 *   1. "접근성 설정 열기" → FoldWindow Probe 켜기
 *   2. 유튜브 등에서 영상을 가로 전체화면으로 재생 (E 프로브에 필요)
 *   3. 이 화면으로 돌아와 "진단 실행"
 *   4. "리포트 공유" → docs/DEVICE_FACTS.md 로 저장
 */
class ProbeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface { ProbeScreen() } } }
    }
}

@Composable
private fun ProbeScreen() {
    val context = LocalContext.current
    var report by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("대기 중") }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("FoldWindow Phase 0 프로브", style = MaterialTheme.typography.headlineSmall)
        Text(
            "1) 접근성 서비스를 켠다\n" +
                "2) 유튜브에서 영상을 가로 전체화면으로 재생한다\n" +
                "3) 여기로 돌아와 진단을 실행한다",
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }) { Text("접근성 설정 열기") }

        Button(onClick = {
            val svc = ProbeAccessibilityService.instance
            if (svc == null) {
                status = "접근성 서비스가 꺼져 있습니다. 켠 뒤 다시 시도하세요."
                return@Button
            }
            status = "실행 중…"
            svc.runProbe { r ->
                val md = r.toMarkdown()
                val file = File(context.getExternalFilesDir(null), "probe_report.md")
                file.writeText(md)
                report = md
                status = "완료 → ${file.absolutePath}"
            }
        }) { Text("진단 실행") }

        report?.let { md ->
            OutlinedButton(onClick = {
                val file = File(context.getExternalFilesDir(null), "probe_report.md")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/markdown"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        },
                        "리포트 공유",
                    )
                )
            }) { Text("리포트 공유") }

            Text(md, style = MaterialTheme.typography.bodySmall)
        }

        Text(status, style = MaterialTheme.typography.labelMedium)
    }
}
