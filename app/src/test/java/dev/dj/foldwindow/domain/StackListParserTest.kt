package dev.dj.foldwindow.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StackListParserTest {

    // DEVICE_FACTS.md 「P4-1 프로브 F1~F6」 실측 픽스처 형식 그대로.
    private val MESSAGING_LINE =
        "  taskId=4991: com.samsung.android.messaging/com.samsung.android.messaging.ui.view.main.WithActivity " +
            "bounds=[0,0][1968,2184] userId=0 visible=false " +
            "topActivity=ComponentInfo{com.samsung.android.messaging/com.samsung.android.messaging.ui.view.main.WithActivity}"

    @Test
    fun `parses real device fixture line`() {
        assertEquals(4991, StackListParser.taskIdFor(MESSAGING_LINE, "com.samsung.android.messaging"))
    }

    @Test
    fun `prefers visible task over non-visible for same package`() {
        val dollar = '$'
        val output = """
            $MESSAGING_LINE
              taskId=5002: com.google.android.youtube/.app.honeycomb.Shell${dollar}HomeActivity bounds=[0,0][2184,1968] userId=0 visible=true topActivity=ComponentInfo{com.google.android.youtube/.app.honeycomb.Shell${dollar}HomeActivity}
              taskId=5010: com.google.android.youtube/.app.honeycomb.Shell${dollar}HomeActivity bounds=[0,0][2184,1968] userId=0 visible=false topActivity=ComponentInfo{com.google.android.youtube/.app.honeycomb.Shell${dollar}HomeActivity}
        """.trimIndent()

        assertEquals(5002, StackListParser.taskIdFor(output, "com.google.android.youtube"))
    }

    @Test
    fun `falls back to first match when none visible`() {
        val dollar = '$'
        val output = """
              taskId=5010: com.google.android.youtube/.app.honeycomb.Shell${dollar}HomeActivity bounds=[0,0][2184,1968] userId=0 visible=false topActivity=ComponentInfo{com.google.android.youtube/.app.honeycomb.Shell${dollar}HomeActivity}
              taskId=5020: com.google.android.youtube/.app.honeycomb.Shell${dollar}HomeActivity bounds=[0,0][2184,1968] userId=0 visible=false topActivity=ComponentInfo{com.google.android.youtube/.app.honeycomb.Shell${dollar}HomeActivity}
        """.trimIndent()

        assertEquals(5010, StackListParser.taskIdFor(output, "com.google.android.youtube"))
    }

    @Test
    fun `does not match package as substring`() {
        val output =
            "  taskId=6001: com.google.android.youtubemusic/.MainActivity bounds=[0,0][2184,1968] userId=0 " +
                "visible=true topActivity=ComponentInfo{com.google.android.youtubemusic/.MainActivity}"

        assertNull(StackListParser.taskIdFor(output, "com.google.android.youtube"))
    }

    @Test
    fun `returns null for empty output`() {
        assertNull(StackListParser.taskIdFor("", "com.google.android.youtube"))
    }

    @Test
    fun `returns null when package not present`() {
        assertNull(StackListParser.taskIdFor(MESSAGING_LINE, "com.netflix.mediaclient"))
    }

    // D2: 실기기 `am stack list` 원문 46행 대조 픽스처. RootTask 헤더 행과
    // ` configuration={...}` 잡음 행이 태스크 행 사이에 실제 개행으로 끼어드는 다행 구조를
    // 재현한다 — 각 태스크 행 자체는 (헤더/노이즈 행과 달리) 단일 물리 행이다.
    @Test
    fun `parses real multi-line am stack list dump with RootTask header and configuration noise`() {
        val dollar = '$'
        val rawDump = listOf(
            "RootTask id=1 bounds=[0,0][1968,2184] displayId=0 userId=0",
            "  taskId=4575: com.sec.android.app.launcher/com.sec.android.app.launcher.activities.LauncherActivity bounds=[0,0][1968,2184] userId=0 visible=true topActivity=ComponentInfo{com.sec.android.app.launcher/com.sec.android.app.launcher.activities.LauncherActivity}",
            "  configuration={1.0 450mcc6mnc [ko_KR] ldltr sw875dp ... winConfig={ mBounds=Rect(0, 0 - 1968, 2184) }}",
            "  taskId=4971: com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell${dollar}HomeActivity bounds=[200,300][1200,2100] userId=0 visible=true topActivity=ComponentInfo{com.google.android.youtube/com.google.android.youtube.app.honeycomb.Shell${dollar}HomeActivity}",
            "  taskId=4991: com.samsung.android.messaging/com.samsung.android.messaging.ui.view.main.WithActivity bounds=[0,0][1968,2184] userId=0 visible=false topActivity=ComponentInfo{com.samsung.android.messaging/com.samsung.android.messaging.ui.view.main.WithActivity}",
        ).joinToString("\n")

        assertEquals(4971, StackListParser.taskIdFor(rawDump, "com.google.android.youtube"))
        assertEquals(4575, StackListParser.taskIdFor(rawDump, "com.sec.android.app.launcher"))
        assertEquals(4991, StackListParser.taskIdFor(rawDump, "com.samsung.android.messaging"))
    }
}
