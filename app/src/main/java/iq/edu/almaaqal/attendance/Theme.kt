package iq.edu.almaaqal.attendance

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** الألوان الكلاسيكية لجامعة المعقل (كحلي / ذهبي / ورقي) للفاتح والداكن. */
@Immutable
data class BT(
    val card: Color, val page: Color, val card2: Color, val line: Color, val line2: Color,
    val tx: Color, val tx2: Color, val tx3: Color,
    val plum: Color, val plumText: Color, val primary: Color, val band: Color,
    val gold: Color, val goldIcon: Color, val goldText: Color, val goldLight: Color, val onGold: Color,
    val ok: Color, val warn: Color, val err: Color,
    val okBg: Color, val warnBg: Color, val errBg: Color, val plumBg: Color, val bar: Color,
)

val BtLight = BT(
    card = Color(0xFFFFFDF9), page = Color(0xFFF4EFE4), card2 = Color(0xFFEFE8D8), line = Color(0xFFDDD3BC),
    line2 = Color(0xFFC7BA9C), tx = Color(0xFF1A1A22), tx2 = Color(0xFF4A4A55), tx3 = Color(0xFF66666F),
    plum = Color(0xFF12294B), plumText = Color(0xFF12294B), primary = Color(0xFF12294B), band = Color(0xFF12294B),
    gold = Color(0xFFB58A2E), goldIcon = Color(0xFF9A7424), goldText = Color(0xFF7A5A14), goldLight = Color(0xFFE3CE94),
    onGold = Color(0xFF2A1A00), ok = Color(0xFF2C6B45), warn = Color(0xFF7A5A14), err = Color(0xFF9E2B33),
    okBg = Color(0xFFE2EFE6), warnBg = Color(0xFFF5EACB), errBg = Color(0xFFF5E1E3), plumBg = Color(0xFFE3E9F2),
    bar = Color(0xFFDCE3EE),
)

val BtDark = BT(
    card = Color(0xFF1B2433), page = Color(0xFF121A28), card2 = Color(0xFF243046), line = Color(0xFF2D3A50),
    line2 = Color(0xFF40506B), tx = Color(0xFFF1EEE6), tx2 = Color(0xFFC3C9D6), tx3 = Color(0xFF9AA3B5),
    plum = Color(0xFF2F5A94), plumText = Color(0xFFD9E3F2), primary = Color(0xFF2F5A94), band = Color(0xFF0B1730),
    gold = Color(0xFFD3A94F), goldIcon = Color(0xFFD3A94F), goldText = Color(0xFFE0BC6A), goldLight = Color(0xFFE3CE94),
    onGold = Color(0xFF2A1A00), ok = Color(0xFF6FCB95), warn = Color(0xFFE0BC6A), err = Color(0xFFF08A98),
    okBg = Color(0xFF1B2E24), warnBg = Color(0xFF33291A), errBg = Color(0xFF38202A), plumBg = Color(0xFF1F2D45),
    bar = Color(0xFF22324C),
)

val LocalBT = staticCompositionLocalOf { BtLight }

/** اختصار مثل context.bt في Flutter: `bt.tx` */
val bt: BT @Composable get() = LocalBT.current

val Almarai = FontFamily(
    Font(R.font.almarai_regular, FontWeight.Normal),
    Font(R.font.almarai_bold, FontWeight.Bold),
    Font(R.font.almarai_extrabold, FontWeight.ExtraBold),
)

private fun almaraiTypography(): Typography {
    val d = Typography()
    fun TextStyle.f() = copy(fontFamily = Almarai)
    return Typography(
        displayLarge = d.displayLarge.f(), displayMedium = d.displayMedium.f(), displaySmall = d.displaySmall.f(),
        headlineLarge = d.headlineLarge.f(), headlineMedium = d.headlineMedium.f(), headlineSmall = d.headlineSmall.f(),
        titleLarge = d.titleLarge.f(), titleMedium = d.titleMedium.f(), titleSmall = d.titleSmall.f(),
        bodyLarge = d.bodyLarge.f(), bodyMedium = d.bodyMedium.f(), bodySmall = d.bodySmall.f(),
        labelLarge = d.labelLarge.f(), labelMedium = d.labelMedium.f(), labelSmall = d.labelSmall.f(),
    )
}

@Composable
fun BayanTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val t = if (dark) BtDark else BtLight
    val scheme = if (dark) {
        darkColorScheme(
            primary = t.primary, onPrimary = Color.White, secondary = t.gold, background = t.page,
            surface = t.card, onSurface = t.tx, onBackground = t.tx, error = t.err, outline = t.line2,
            surfaceVariant = t.card2, onSurfaceVariant = t.tx3,
        )
    } else {
        lightColorScheme(
            primary = t.primary, onPrimary = Color.White, secondary = t.gold, background = t.page,
            surface = t.card, onSurface = t.tx, onBackground = t.tx, error = t.err, outline = t.line2,
            surfaceVariant = t.card2, onSurfaceVariant = t.tx3,
        )
    }
    CompositionLocalProvider(
        LocalBT provides t,
        LocalLayoutDirection provides LayoutDirection.Rtl, // التطبيق عربي من اليمين لليسار
    ) {
        MaterialTheme(colorScheme = scheme, typography = almaraiTypography(), shapes = Shapes(
            small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(6.dp), large = RoundedCornerShape(8.dp),
        ), content = content)
    }
}
