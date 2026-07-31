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
 */

// ── Light 팔레트 ──
private val LightPrimary = Color(0xFF4E6E5D)
private val LightOnPrimary = Color(0xFFFFFFFF)
private val LightPrimaryContainer = Color(0xFFD7E8DD)
private val LightOnPrimaryContainer = Color(0xFF24352C)
private val LightSecondary = Color(0xFFB0714F)
private val LightSecondaryContainer = Color(0xFFF3DED2)
private val LightOnSecondaryContainer = Color(0xFF3E2A1E)
private val LightBackground = Color(0xFFF7F4EE)
private val LightOnBackground = Color(0xFF22251F)
private val LightSurface = Color(0xFFFDFBF6)
private val LightOnSurface = Color(0xFF22251F)
private val LightSurfaceVariant = Color(0xFFE9E4D8)
private val LightOnSurfaceVariant = Color(0xFF55584E)
private val LightOutline = Color(0xFF7C8072)
private val LightError = Color(0xFFA8503C)
private val LightOnError = Color(0xFFFFFFFF)

// ── Dark 팔레트 ──
private val DarkPrimary = Color(0xFFA9C7B5)
private val DarkOnPrimary = Color(0xFF1E332A)
private val DarkPrimaryContainer = Color(0xFF35513F)
private val DarkOnPrimaryContainer = Color(0xFFD7E8DD)
private val DarkSecondary = Color(0xFFD9A183)
private val DarkSecondaryContainer = Color(0xFF573A2A)
private val DarkOnSecondaryContainer = Color(0xFFF3DED2)
private val DarkBackground = Color(0xFF191B17)
private val DarkOnBackground = Color(0xFFE5E3DB)
private val DarkSurface = Color(0xFF21231E)
private val DarkOnSurface = Color(0xFFE5E3DB)
private val DarkSurfaceVariant = Color(0xFF3E4138)
private val DarkOnSurfaceVariant = Color(0xFFC7C3B6)
private val DarkOutline = Color(0xFF90947F)
private val DarkError = Color(0xFFE0937F)
private val DarkOnError = Color(0xFF33110A)

private val FoldWindowLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    error = LightError,
    onError = LightOnError,
)

private val FoldWindowDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    error = DarkError,
    onError = DarkOnError,
)

private val FoldWindowShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

// Material3 기본 Typography 를 기반으로 헤드라인 굵기·자간만 살짝 다듬는다(폰트 자산 없이 유지).
private val baseTypography = Typography()
private val FoldWindowTypography = baseTypography.copy(
    headlineMedium = baseTypography.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp,
    ),
    titleMedium = baseTypography.titleMedium.copy(fontWeight = FontWeight.Medium),
    titleSmall = baseTypography.titleSmall.copy(fontWeight = FontWeight.Medium),
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
