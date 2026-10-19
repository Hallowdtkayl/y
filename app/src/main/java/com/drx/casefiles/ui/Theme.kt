package com.drx.casefiles.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.drx.casefiles.data.Lang
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF0B6E8A), onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EEF7), onPrimaryContainer = Color(0xFF03303D),
    secondary = Color(0xFF4C6A74), onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE9EE), onSecondaryContainer = Color(0xFF0E2128),
    tertiary = Color(0xFFB4533A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6DC), onTertiaryContainer = Color(0xFF4A1706),
    background = Color(0xFFF4F8FA), onBackground = Color(0xFF14212A),
    surface = Color.White, onSurface = Color(0xFF14212A),
    surfaceVariant = Color(0xFFE8EFF2), onSurfaceVariant = Color(0xFF51626B),
    outline = Color(0xFFA6B4BB), outlineVariant = Color(0xFFD5DFE4),
    error = Color(0xFFC0392B), onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7CD0E8), onPrimary = Color(0xFF003543),
    primaryContainer = Color(0xFF0A4F63), onPrimaryContainer = Color(0xFFCDEFFB),
    secondary = Color(0xFFB4CBD4), onSecondary = Color(0xFF1E333B),
    secondaryContainer = Color(0xFF354B53), onSecondaryContainer = Color(0xFFD5E8EF),
    tertiary = Color(0xFFFFB59F), onTertiary = Color(0xFF5A1F0B),
    tertiaryContainer = Color(0xFF6B2B17), onTertiaryContainer = Color(0xFFFFDBCE),
    background = Color(0xFF0E161B), onBackground = Color(0xFFE1E9ED),
    surface = Color(0xFF16222A), onSurface = Color(0xFFE1E9ED),
    surfaceVariant = Color(0xFF26343B), onSurfaceVariant = Color(0xFFA9BBC3),
    outline = Color(0xFF6B7D86), outlineVariant = Color(0xFF34444C),
    error = Color(0xFFFFB4AB), onError = Color(0xFF561E19)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

private fun appTypography(): Typography {
    val b = Typography()
    return b.copy(
        titleLarge = b.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
        titleMedium = b.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        titleSmall = b.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
        bodyMedium = b.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
        labelLarge = b.labelLarge.copy(fontWeight = FontWeight.SemiBold)
    )
}

/** The look of the whole app: a calm clinical teal, soft rounded cards, clear type. */
@Composable
fun CaseTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides if (Lang.ar) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(
            colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
            shapes = AppShapes,
            typography = appTypography(),
            content = content
        )
    }
}

/**
 * One folding card per section: an icon, the title, an optional badge (pending items, counts) and a chevron.
 * Closed cards keep the screen short; the badge tells what is inside without opening it.
 * [accent] tints the card for the section that matters most.
 */
@Composable
fun FoldCard(
    icon: String,
    title: String,
    startOpen: Boolean = false,
    badge: String? = null,
    badgeAlert: Boolean = false,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    var open by rememberSaveable(title) { mutableStateOf(startOpen) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (accent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f) else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 1.dp,
        border = BorderStroke(1.dp, if (accent) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Text(icon, fontSize = 18.sp) }
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (badge != null) {
                    Tag(badge, bg = if (badgeAlert) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (open) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) {
                Column(
                    Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}
