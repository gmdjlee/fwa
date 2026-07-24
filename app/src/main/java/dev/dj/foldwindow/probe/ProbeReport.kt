package dev.dj.foldwindow.probe

/**
 * Phase 0 진단 결과. docs/DEVICE_FACTS.md 로 병합할 마크다운을 생성한다.
 */
data class ProbeReport(
    val device: DeviceProbe,
    val windows: WindowsProbe,
    val splitAction: SplitActionProbe,
    val metrics: MetricsProbe,
    val letterbox: LetterboxProbe,
    val foregroundPackage: String?,
    val timestamp: Long = System.currentTimeMillis(),
) {
    fun toMarkdown(): String {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.KOREA)
            .format(java.util.Date(timestamp))
        return buildString {
        appendLine("# DEVICE_FACTS — Phase 0 프로브 결과")
        appendLine()
        appendLine("- 생성: $stamp")
        appendLine("- 측정 시점 포그라운드 앱: `${foregroundPackage ?: "(없음)"}`")
        appendLine()

        appendLine("## 미지수 해소")
        appendLine()
        appendLine("| # | 항목 | 결과 |")
        appendLine("|---|---|---|")
        appendLine("| 5 | 팝업 화면이 AOSP freeform 기반인가 | ${verdict5()} |")
        appendLine("| 6 | GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN 동작하는가 | ${splitAction.verdict} |")
        appendLine("| 7 | TYPE_SPLIT_SCREEN_DIVIDER 노출되는가 | ${if (windows.dividerExposed) "✅ 노출됨" else "❌ 미노출 — 휴리스틱 폴백 필요"} |")
        appendLine()

        appendLine("## SplitPlanner 에 반영할 값")
        appendLine()
        appendLine("```kotlin")
        appendLine("WindowGeometry(")
        appendLine("    usableLeft = 0,")
        appendLine("    usableTop = 0,   // ← rootWindowBounds 로 보정할 것")
        appendLine("    usableWidth = ${metrics.widthPx},")
        appendLine("    usableHeight = ${metrics.heightPx},")
        appendLine("    dividerThickness = ${windows.dividerThicknessPx ?: "TODO"},")
        appendLine("    minPaneHeight = 0,   // ← 디바이더를 위/아래 끝까지 밀어보고 실측")
        appendLine(")")
        appendLine("```")
        appendLine()

        appendLine("## A. 기기")
        appendLine()
        appendLine("| 항목 | 값 |")
        appendLine("|---|---|")
        appendLine("| 제조사/모델 | ${device.manufacturer} ${device.model} |")
        appendLine("| Android | ${device.release} (API ${device.sdkInt}) |")
        appendLine("| One UI | ${device.oneUiVersion ?: "-"} |")
        appendLine("| FEATURE_FREEFORM_WINDOW_MANAGEMENT | ${device.hasFreeformFeature} |")
        appendLine("| FEATURE_PICTURE_IN_PICTURE | ${device.hasPipFeature} |")
        appendLine("| enable_freeform_support | ${device.enableFreeformSupport ?: "-"} |")
        appendLine("| enable_non_resizable_multi_window | ${device.enableNonResizableMultiWindow ?: "-"} |")
        appendLine("| force_resizable_activities | ${device.forceResizable ?: "-"} |")
        appendLine()

        appendLine("## B. 창 (${windows.count}개)")
        appendLine()
        appendLine("| type | layer | bounds | package | active |")
        appendLine("|---|---|---|---|---|")
        windows.entries.forEach {
            appendLine("| ${it.typeName} | ${it.layer} | ${it.bounds} | ${it.packageName ?: "-"} | ${it.isActive} |")
        }
        appendLine()
        appendLine("디바이더 bounds: `${windows.dividerBounds ?: "(없음)"}`")
        appendLine()

        appendLine("## C. 분할 진입")
        appendLine()
        appendLine("- 호출 전 디바이더 존재: ${splitAction.dividerPresentBefore}")
        appendLine("- performGlobalAction 반환값: ${splitAction.globalActionReturnedTrue}")
        appendLine("- 디바이더 상태 변화 감지: ${splitAction.dividerAppeared} (${splitAction.elapsedMs}ms)")
        appendLine("- **판정: ${splitAction.verdict}**")
        appendLine()

        appendLine("## D. 메트릭")
        appendLine()
        appendLine("| 항목 | 값 |")
        appendLine("|---|---|")
        appendLine("| 해상도 | ${metrics.widthPx} × ${metrics.heightPx} px |")
        appendLine("| density | ${metrics.density} (${metrics.densityDpi} dpi) |")
        appendLine("| dp 크기 | ${metrics.screenWidthDp} × ${metrics.screenHeightDp} dp |")
        appendLine("| smallestScreenWidthDp | ${metrics.smallestScreenWidthDp} |")
        appendLine("| 방향 | ${metrics.orientation} |")
        appendLine("| rootWindowBounds | ${metrics.rootWindowBounds ?: "-"} |")
        appendLine()

        appendLine("## E. 검은 띠 실측")
        appendLine()
        if (!letterbox.captured) {
            appendLine("캡처 실패: ${letterbox.note}")
        } else {
            appendLine("| 항목 | 값 |")
            appendLine("|---|---|")
            appendLine("| 프레임 | ${letterbox.frameWidth} × ${letterbox.frameHeight} px |")
            appendLine("| 상단 띠 | ${letterbox.topBarPx ?: "-"} px |")
            appendLine("| 하단 띠 | ${letterbox.bottomBarPx ?: "-"} px |")
            appendLine("| 콘텐츠 높이 | ${letterbox.contentHeightPx ?: "-"} px |")
            appendLine("| 역산 종횡비 | ${letterbox.rawAspect ?: "-"} |")
            appendLine("| 스냅 결과 | ${letterbox.snappedAspect ?: "(스냅 안 됨)"} |")
            appendLine("| 신뢰도 | ${letterbox.confidence ?: "-"} |")
            appendLine()
            appendLine("비고: ${letterbox.note}")
        }
        }
    }

    private fun verdict5(): String = when {
        device.hasFreeformFeature -> "✅ freeform 지원 — Phase 4 Shizuku 경로 가능"
        device.enableFreeformSupport == 1 -> "△ 기능 플래그는 켜져 있으나 시스템 feature 없음"
        else -> "❌ freeform 미지원 — Tier 1 분할 화면 경로만 사용"
    }
}

data class DeviceProbe(
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
    val release: String,
    val oneUiVersion: String?,
    val hasFreeformFeature: Boolean,
    val hasPipFeature: Boolean,
    val enableFreeformSupport: Int?,
    val enableNonResizableMultiWindow: Int?,
    val forceResizable: Int?,
)

data class WindowEntry(
    val type: Int,
    val typeName: String,
    val layer: Int,
    val bounds: String,
    val packageName: String?,
    val isActive: Boolean,
)

data class WindowsProbe(
    val count: Int,
    val entries: List<WindowEntry>,
    val dividerExposed: Boolean,
    val dividerBounds: String?,
    val dividerThicknessPx: Int?,
)

data class SplitActionProbe(
    val dividerPresentBefore: Boolean,
    val globalActionReturnedTrue: Boolean,
    val dividerAppeared: Boolean,
    val elapsedMs: Long,
    val verdict: String,
)

data class MetricsProbe(
    val widthPx: Int,
    val heightPx: Int,
    val density: Float,
    val densityDpi: Int,
    val smallestScreenWidthDp: Int,
    val screenWidthDp: Int,
    val screenHeightDp: Int,
    val orientation: String,
    val rootWindowBounds: String?,
)

data class LetterboxProbe(
    val captured: Boolean,
    val frameWidth: Int? = null,
    val frameHeight: Int? = null,
    val topBarPx: Int? = null,
    val bottomBarPx: Int? = null,
    val contentHeightPx: Int? = null,
    val rawAspect: Float? = null,
    val snappedAspect: Float? = null,
    val confidence: Float? = null,
    val note: String = "",
)
