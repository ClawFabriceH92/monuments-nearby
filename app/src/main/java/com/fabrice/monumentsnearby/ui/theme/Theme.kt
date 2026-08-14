package com.fabrice.monumentsnearby.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Palette « patrimoine » : bleu nuit + or antique + touches chaleureuses
private val DeepBlue = Color(0xFF1B3A6B)
private val DeepBlueDark = Color(0xFF12294D)
private val Gold = Color(0xFFC9972B)
private val GoldSoft = Color(0xFFF3E5C2)
private val Terracotta = Color(0xFFB4613E)
private val Stone = Color(0xFF6B7280)
private val Cream = Color(0xFFFAF6EF)
private val SurfaceSoft = Color(0xFFFFFFFF)
private val GreenSoft = Color(0xFFE8F0E6)
private val PurpleSoft = Color(0xFFEDE6F2)

private val LightColors = lightColorScheme(
    primary = DeepBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F5),
    onPrimaryContainer = DeepBlueDark,
    secondary = Gold,
    onSecondary = Color.White,
    secondaryContainer = GoldSoft,
    onSecondaryContainer = Color(0xFF5C4308),
    tertiary = Terracotta,
    onTertiary = Color.White,
    background = Cream,
    onBackground = Color(0xFF1C1B1A),
    surface = SurfaceSoft,
    onSurface = Color(0xFF1C1B1A),
    surfaceVariant = Color(0xFFF0EBE1),
    onSurfaceVariant = Stone,
    error = Color(0xFFBA1A1A)
)

private val ShapesRound = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private val AppTypography = Typography().let { base ->
    base.copy(
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontSize = 12.sp)
    )
}

@Composable
fun MonumentsNearbyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        shapes = ShapesRound,
        typography = AppTypography,
        content = content
    )
}

// Couleurs utilitaires pour les badges de catégorie
object CategoryColors {
    val museum = Color(0xFFC62828)      // rouge
    val religious = Color(0xFF6A1B9A)   // violet
    val castle = Color(0xFFE65100)      // orange
    val ruins = Color(0xFF6D4C41)       // marron
    val monument = Color(0xFF1565C0)    // bleu
    val other = Color(0xFF2E7D32)       // vert

    fun forCategory(category: String): Color = when (category) {
        "musée" -> museum
        "religieux" -> religious
        "château" -> castle
        "ruines" -> ruins
        "monument" -> monument
        else -> other
    }
}
