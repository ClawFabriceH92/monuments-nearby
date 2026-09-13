package com.fabrice.monumentsnearby.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fabrice.monumentsnearby.R

/*
 * Thème « Aurora » : interface sombre et futuriste — bleu nuit profond,
 * cyan électrique, or et violet en accents, surfaces vitrées, halos lumineux.
 * Un mode clair moderne reste disponible (Réglages → Apparence).
 */

// Accents communs
private val Cyan = Color(0xFF22D3EE)
private val CyanDeep = Color(0xFF0E7490)
private val Gold = Color(0xFFF2C14E)
private val Violet = Color(0xFFA78BFA)

// Sombre
private val Space = Color(0xFF070B18)
private val Nebula = Color(0xFF0F1830)
private val Panel = Color(0xFF16213F)
private val PanelHigh = Color(0xFF1D2A4D)
private val Starlight = Color(0xFFE6EDF7)
private val Mist = Color(0xFF9AA8C4)

// Clair
private val Snow = Color(0xFFF3F6FB)
private val Ice = Color(0xFFFFFFFF)
private val IceVariant = Color(0xFFE6ECF5)
private val Ink = Color(0xFF0E1526)
private val Slate = Color(0xFF55627A)

private val DarkColors = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF03161C),
    primaryContainer = Color(0xFF0B3A48),
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = Gold,
    onSecondary = Color(0xFF2A1D00),
    secondaryContainer = Color(0xFF3D2F0A),
    onSecondaryContainer = Color(0xFFFFE8A8),
    tertiary = Violet,
    onTertiary = Color(0xFF1B1038),
    tertiaryContainer = Color(0xFF2E2358),
    onTertiaryContainer = Color(0xFFE4DBFF),
    background = Space,
    onBackground = Starlight,
    surface = Nebula,
    onSurface = Starlight,
    surfaceVariant = Panel,
    onSurfaceVariant = Mist,
    surfaceContainer = Panel,
    surfaceContainerHigh = PanelHigh,
    surfaceContainerHighest = PanelHigh,
    surfaceContainerLow = Nebula,
    surfaceContainerLowest = Space,
    outline = Color(0xFF34436A),
    outlineVariant = Color(0xFF243158),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2B0000),
    inverseSurface = Starlight,
    inverseOnSurface = Space
)

private val LightColors = lightColorScheme(
    primary = CyanDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF6FC),
    onPrimaryContainer = Color(0xFF00363F),
    secondary = Color(0xFFB8860B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEFC2),
    onSecondaryContainer = Color(0xFF4A3600),
    tertiary = Color(0xFF6D4AFF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE8E1FF),
    onTertiaryContainer = Color(0xFF22135A),
    background = Snow,
    onBackground = Ink,
    surface = Ice,
    onSurface = Ink,
    surfaceVariant = IceVariant,
    onSurfaceVariant = Slate,
    surfaceContainer = Color(0xFFEDF1F8),
    surfaceContainerHigh = IceVariant,
    surfaceContainerHighest = Color(0xFFDDE4F0),
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainerLowest = Ice,
    outline = Color(0xFFB7C2D6),
    outlineVariant = Color(0xFFD6DEEB),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val ShapesRound = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

/** Exo 2 (OFL) : police géométrique pour les titres et libellés. */
private val Exo2 = FontFamily(
    Font(R.font.exo2, FontWeight.Normal),
    Font(
        R.font.exo2, FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        R.font.exo2, FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600))
    ),
    Font(
        R.font.exo2, FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontFamily = Exo2, fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.copy(fontFamily = Exo2, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(
            fontFamily = Exo2, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp
        ),
        titleMedium = base.titleMedium.copy(fontFamily = Exo2, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(
            fontFamily = Exo2, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = Exo2, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = Exo2, fontWeight = FontWeight.Medium, fontSize = 12.sp
        ),
        labelSmall = base.labelSmall.copy(fontFamily = Exo2, fontWeight = FontWeight.Medium)
    )
}

/** Mode d'apparence choisi dans les réglages (persisté). */
enum class ThemeMode(val key: String, val label: String) {
    DARK("dark", "Sombre"),
    LIGHT("light", "Clair"),
    SYSTEM("system", "Système");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: DARK
    }
}

@Composable
fun MonumentsNearbyTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = ShapesRound,
        typography = AppTypography,
        content = content
    )
}

/**
 * Fond « aurora » : dégradé vertical du thème + deux halos (cyan en haut à
 * droite, violet en bas à gauche). À appliquer à un Scaffold dont le
 * containerColor est transparent.
 */
@Composable
fun Modifier.auroraBackground(): Modifier {
    val scheme = MaterialTheme.colorScheme
    val top = scheme.background
    val bottom = scheme.surfaceVariant
    val glowA = scheme.primary
    val glowB = scheme.tertiary
    return this
        .background(Brush.verticalGradient(listOf(top, top, bottom)))
        .drawBehind {
            val w = size.width
            val h = size.height
            val centerA = Offset(w * 0.95f, h * 0.02f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowA.copy(alpha = 0.28f), Color.Transparent),
                    center = centerA,
                    radius = w * 0.75f
                ),
                radius = w * 0.75f,
                center = centerA
            )
            val centerB = Offset(w * 0.05f, h * 0.98f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowB.copy(alpha = 0.22f), Color.Transparent),
                    center = centerB,
                    radius = w * 0.7f
                ),
                radius = w * 0.7f,
                center = centerB
            )
        }
}

/** Variante conteneur du fond aurora (dialogues plein écran). */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().auroraBackground()) { content() }
}

// Couleurs utilitaires pour les badges de catégorie (vives, lisibles sur sombre)
object CategoryColors {
    val museum = Color(0xFFFF5C7A)      // corail
    val religious = Color(0xFFB388FF)   // violet
    val castle = Color(0xFFFFA040)      // ambre
    val ruins = Color(0xFFC9A27E)       // sable
    val monument = Color(0xFF38BDF8)    // bleu ciel
    val other = Color(0xFF4ADE80)       // vert

    fun forCategory(category: String): Color = when (category) {
        "musée" -> museum
        "religieux" -> religious
        "château" -> castle
        "ruines" -> ruins
        "monument" -> monument
        else -> other
    }
}
