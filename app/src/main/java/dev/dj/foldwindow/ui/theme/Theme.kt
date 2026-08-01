package dev.dj.foldwindow.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 오가닉(organic) 디자인 시스템 — 세이지/클레이 톤의 따뜻하고 자연스러운 팔레트.
 * 기본 Material.Light 테마 대신 [FoldWindowTheme] 로 감싸 통일된 라이트/다크 색상을 적용한다.
 *
 * 색 슬롯을 "필요한 것만" 채우면 나머지는 Material3 기본 baseline(보라 계열)로 폴백해
 * 팔레트와 충돌한다(카드 elevation tint, 에러 컨테이너, outlineVariant 구분선 등이 대표적).
 * 그래서 tertiary / surfaceContainer 계층 / errorContainer / outlineVariant / scrim / inverse*
 * 까지 전부 세이지·클레이 계열로 명시한다. 대비비는 아래 주석에 실측값을 남긴다.
 */

// ── Light 팔레트 ──
private val LightPrimary = Color(0xFF4E6E5D)
private val LightOnPrimary = Color(0xFFFFFFFF)          // 5.66:1 (AA)
private val LightPrimaryContainer = Color(0xFFD7E8DD)
private val LightOnPrimaryContainer = Color(0xFF24352C) // 10.17:1 (AAA)
private val LightInversePrimary = Color(0xFFA9C7B5)

// 클레이. 기존 #B0714F 는 흰 글자 대비가 3.95:1 로 AA(4.5:1) 미달이라 같은 색상환에서
// 약간만 낮춰 #A56340 로 조정했다(흰 글자 4.71:1). 색조 계열은 그대로 유지.
private val LightSecondary = Color(0xFFA56340)
private val LightOnSecondary = Color(0xFFFFFFFF)          // 4.71:1 (AA)
private val LightSecondaryContainer = Color(0xFFF3DED2)
private val LightOnSecondaryContainer = Color(0xFF3E2A1E) // 10.41:1 (AAA)

// 세이지와 클레이 사이를 메우는 올리브 — "선택 설정" 같은 3순위 강조에 쓴다.
// [F4] 기존 #6E7A52 는 이 색을 글자로 쓰는 「선택 설정」 헤더(labelLarge 14sp = 일반 텍스트)가
// background(#F7F4EE) 위에서 4.19:1 로 AA(4.5:1) 미달이었다. 같은 색상환에서 명도만 낮춰
// #636E4A 로 조정 — 헤더 4.96:1, 진행 막대 미완료 색 대 트랙(surfaceContainerHighest) 4.33:1,
// 흰 글자 5.44:1. 다크 팔레트는 이미 통과라 그대로 둔다.
private val LightTertiary = Color(0xFF636E4A)
private val LightOnTertiary = Color(0xFFFFFFFF)          // 5.44:1 (AA)
private val LightTertiaryContainer = Color(0xFFE3E8CF)
private val LightOnTertiaryContainer = Color(0xFF2C3220) // 10.63:1 (AAA)

private val LightBackground = Color(0xFFF7F4EE)
private val LightOnBackground = Color(0xFF22251F)
private val LightSurface = Color(0xFFFDFBF6)
private val LightOnSurface = Color(0xFF22251F)           // 15.0:1 (AAA)
private val LightSurfaceVariant = Color(0xFFE9E4D8)
private val LightOnSurfaceVariant = Color(0xFF55584E)    // 5.73:1 (AA)
private val LightSurfaceDim = Color(0xFFE0DDD3)
private val LightSurfaceBright = Color(0xFFFDFBF6)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFFAF7F1)
private val LightSurfaceContainer = Color(0xFFF4F1E9)
private val LightSurfaceContainerHigh = Color(0xFFEEEBE3)
private val LightSurfaceContainerHighest = Color(0xFFE9E5DC)
private val LightInverseSurface = Color(0xFF37392F)
private val LightInverseOnSurface = Color(0xFFF2EFE7)

private val LightOutline = Color(0xFF7C8072)
private val LightOutlineVariant = Color(0xFFD3CFC0)
private val LightScrim = Color(0xFF000000)

private val LightError = Color(0xFFA8503C)
private val LightOnError = Color(0xFFFFFFFF)
private val LightErrorContainer = Color(0xFFF7DDD5)
private val LightOnErrorContainer = Color(0xFF46180E)    // 11.64:1 (AAA)

// ── Dark 팔레트 ──
private val DarkPrimary = Color(0xFFA9C7B5)
private val DarkOnPrimary = Color(0xFF1E332A)
private val DarkPrimaryContainer = Color(0xFF35513F)
private val DarkOnPrimaryContainer = Color(0xFFD7E8DD)
private val DarkInversePrimary = Color(0xFF4E6E5D)

private val DarkSecondary = Color(0xFFD9A183)
private val DarkOnSecondary = Color(0xFF3C2415)
private val DarkSecondaryContainer = Color(0xFF573A2A)
private val DarkOnSecondaryContainer = Color(0xFFF3DED2)

private val DarkTertiary = Color(0xFFC3CB99)
private val DarkOnTertiary = Color(0xFF2C3220)
private val DarkTertiaryContainer = Color(0xFF4F5637)
private val DarkOnTertiaryContainer = Color(0xFFE3E8CF)

private val DarkBackground = Color(0xFF191B17)
private val DarkOnBackground = Color(0xFFE5E3DB)
private val DarkSurface = Color(0xFF21231E)
private val DarkOnSurface = Color(0xFFE5E3DB)            // 12.35:1 (AAA)
private val DarkSurfaceVariant = Color(0xFF3E4138)
private val DarkOnSurfaceVariant = Color(0xFFC7C3B6)     // 5.90:1 (AA)
private val DarkSurfaceDim = Color(0xFF141610)
private val DarkSurfaceBright = Color(0xFF3A3C35)
private val DarkSurfaceContainerLowest = Color(0xFF0F110D)
private val DarkSurfaceContainerLow = Color(0xFF1D1F1A)
private val DarkSurfaceContainer = Color(0xFF252721)
private val DarkSurfaceContainerHigh = Color(0xFF2F312A)
private val DarkSurfaceContainerHighest = Color(0xFF3A3D34)
private val DarkInverseSurface = Color(0xFFE5E3DB)
private val DarkInverseOnSurface = Color(0xFF2E312A)

private val DarkOutline = Color(0xFF90947F)
private val DarkOutlineVariant = Color(0xFF4A4D42)
private val DarkScrim = Color(0xFF000000)

private val DarkError = Color(0xFFE0937F)
private val DarkOnError = Color(0xFF33110A)
private val DarkErrorContainer = Color(0xFF6A2A1A)
private val DarkOnErrorContainer = Color(0xFFF7DDD5)     // 8.30:1 (AAA)

private val FoldWindowLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    inversePrimary = LightInversePrimary,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightPrimary,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurfaceBright,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    inverseSurface = LightInverseSurface,
    inverseOnSurface = LightInverseOnSurface,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    scrim = LightScrim,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
)

private val FoldWindowDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    inversePrimary = DarkInversePrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkPrimary,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    inverseSurface = DarkInverseSurface,
    inverseOnSurface = DarkInverseOnSurface,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    scrim = DarkScrim,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
)

private val FoldWindowShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * 타이포그래피 — 폰트 자산 없이 시스템 폰트를 쓰되, 스케일 전체에 의도를 준다.
 *
 * - 헤드라인/타이틀: SemiBold + 자간을 살짝 좁혀(-0.3~0) 덩어리감을 준다.
 * - 본문: 한글은 라틴 대비 글자 높이가 커서 Material3 기본 행간(20sp/14sp)이 답답하다.
 *   bodyLarge/Medium/Small 의 lineHeight 를 키워 가독성을 확보한다.
 * - 레이블: 알약/버튼용. 굵기는 기본(Medium) 그대로 두고 자간만 살짝 넓혀 작은 크기에서
 *   뭉치지 않게 한다(굵기를 올리면 이 슬롯을 쓰는 다른 화면까지 함께 무거워진다).
 *
 * 한글 본문에 음수 자간을 주면 쉽게 뭉치므로 본문 계열은 0 이상만 사용한다.
 */
private val base = Typography()
private val FoldWindowTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineMedium = base.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
        lineHeight = 38.sp,
    ),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = base.bodyLarge.copy(lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(lineHeight = 22.sp, letterSpacing = 0.15.sp),
    bodySmall = base.bodySmall.copy(lineHeight = 19.sp, letterSpacing = 0.2.sp),
    // [D13] 레이블 계열은 Material3 기본값과 같은 Medium 을 유지한다. 온보딩의 섹션 헤더는
    // 색(primary/tertiary)으로, 상태 알약은 컨테이너 톤으로 이미 구분되므로 SemiBold 가 주는
    // 이득이 없고, 이 슬롯을 굵히면 PanelActivity 등 다른 화면의 잉크 무게까지 함께 올라간다.
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun FoldWindowTheme(content: @Composable () -> Unit) {
    val colorScheme = if (isSystemInDarkTheme()) FoldWindowDarkColorScheme else FoldWindowLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        shapes = FoldWindowShapes,
        typography = FoldWindowTypography,
        content = content,
    )
}
