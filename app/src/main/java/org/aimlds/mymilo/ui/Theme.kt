package org.aimlds.mymilo.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.aimlds.mymilo.R

/**
 * Skins + typography (v0.3.0). A skin is a full Material color scheme;
 * the user picks one in the drawer and it persists on-device.
 * Type is Inter (bundled, SIL OFL) so the app looks identical on
 * every phone instead of inheriting each vendor's system font.
 */
enum class Skin(val label: String) {
    MIDNIGHT("Midnight"),
    DAYLIGHT("Daylight"),
    OCEAN("Ocean"),
    EMBER("Ember");

    fun scheme(): ColorScheme = when (this) {
        MIDNIGHT -> darkColorScheme(
            primary = Color(0xFF6EA8FF),
            onPrimary = Color(0xFF0B1B33),
            background = Color(0xFF0E1116),
            onBackground = Color(0xFFE8EDF4),
            surface = Color(0xFF171C24),
            onSurface = Color(0xFFE8EDF4),
            surfaceVariant = Color(0xFF232B38),
            onSurfaceVariant = Color(0xFFB9C4D4),
            error = Color(0xFFFF6B6B),
        )
        DAYLIGHT -> lightColorScheme(
            primary = Color(0xFF2456A6),
            onPrimary = Color(0xFFFFFFFF),
            background = Color(0xFFF7F8FA),
            onBackground = Color(0xFF14181F),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF14181F),
            surfaceVariant = Color(0xFFE8ECF2),
            onSurfaceVariant = Color(0xFF3C4656),
            error = Color(0xFFC62828),
        )
        OCEAN -> darkColorScheme(
            primary = Color(0xFF38BDF8),
            onPrimary = Color(0xFF06222F),
            background = Color(0xFF0A1620),
            onBackground = Color(0xFFE2F0F9),
            surface = Color(0xFF10222F),
            onSurface = Color(0xFFE2F0F9),
            surfaceVariant = Color(0xFF17303F),
            onSurfaceVariant = Color(0xFFA9C6D8),
            error = Color(0xFFFF6B6B),
        )
        EMBER -> darkColorScheme(
            primary = Color(0xFFFF9E64),
            onPrimary = Color(0xFF331A06),
            background = Color(0xFF171110),
            onBackground = Color(0xFFF5E9E2),
            surface = Color(0xFF221A17),
            onSurface = Color(0xFFF5E9E2),
            surfaceVariant = Color(0xFF32251F),
            onSurfaceVariant = Color(0xFFD8BFA9),
            error = Color(0xFFFF6B6B),
        )
    }

    companion object {
        fun fromName(name: String?): Skin =
            entries.firstOrNull { it.name == name } ?: MIDNIGHT
    }
}

val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val MiloTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 34.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp, lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 21.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 19.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFamily, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 15.sp,
    ),
)
