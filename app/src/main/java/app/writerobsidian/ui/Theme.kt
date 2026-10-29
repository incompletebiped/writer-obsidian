package app.writerobsidian.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import app.writerobsidian.R
import app.writerobsidian.data.FontChoice
import app.writerobsidian.data.Prefs
import app.writerobsidian.data.ThemeChoice

/**
 * Editor colors from the original's AppTheme*Text styles: [background] is colorPrimary,
 * [accent] is colorAccent (toolbar icons, bullets, quote stripes).
 */
@Immutable
data class WriterColors(val background: Color, val accent: Color, val text: Color, val light: Boolean)

fun ThemeChoice.colors(): WriterColors = when (this) {
    ThemeChoice.Default -> WriterColors(Color(0xFFFCFCFC), Color(0xFF202020), Color(0xDE000000), light = true)
    ThemeChoice.Paper -> WriterColors(Color(0xFFF9F2DA), Color(0xFF292929), Color(0xDE000000), light = true)
    ThemeChoice.Dark -> WriterColors(Color(0xFF2E2E2E), Color(0xFFF5F5F5), Color.White, light = false)
    ThemeChoice.Black -> WriterColors(Color(0xFF000000), Color(0xFFF1F1F1), Color.White, light = false)
    ThemeChoice.Pink -> WriterColors(Color(0xFFFF6FBA), Color(0xFF292929), Color.White, light = false)
    ThemeChoice.Blue -> WriterColors(Color(0xFF58A5FF), Color(0xFF292929), Color.White, light = false)
    ThemeChoice.Bluer -> WriterColors(Color(0xFF071828), Color(0xFFFFFFFF), Color.White, light = false)
}

/** The editor's colors with the Customize text color applied. */
fun Prefs.editorColors(): WriterColors = theme.colors().let { c -> textColor?.let { c.copy(text = Color(it)) } ?: c }

/**
 * Colors of the app's own screens (list, menus, dialogs), matched to the original in
 * Day and Night Mode.
 */
@Immutable
data class AppColors(
    val background: Color,
    val dialog: Color,
    val field: Color,
    val text: Color,
    val secondary: Color,
    val icon: Color,
    val divider: Color,
    val fab: Color,
    val night: Boolean,
)

private val NightColors = AppColors(
    background = Color(0xFF2E2E2E), dialog = Color(0xFF424242), field = Color(0xFF3A3A3A),
    text = Color(0xFFF5F5F5), secondary = Color(0xFFA0A0A0), icon = Color(0xFF757575),
    divider = Color(0xFF454545), fab = Color(0xFF424242), night = true,
)

private val DayColors = AppColors(
    background = Color(0xFFFAFAFA), dialog = Color(0xFFFFFFFF), field = Color(0xFFEEEEEE),
    text = Color(0xFF212121), secondary = Color(0xFF9E9E9E), icon = Color(0xFF6B6B6B),
    divider = Color(0xFFDDDDDD), fab = Color(0xFF424242), night = false,
)

val LocalAppColors = staticCompositionLocalOf { DayColors }
val LocalWriterColors = staticCompositionLocalOf { ThemeChoice.Default.colors() }

/** The original lists and titles are set in Roboto Mono. */
val MonoFamily = FontFamily(Font(R.font.roboto_mono))

/** The bundled font file, or null for the system font. */
fun FontChoice.fontRes(): Int? = when (this) {
    FontChoice.Default -> null
    FontChoice.RobotoMono -> R.font.roboto_mono
    FontChoice.Almarai -> R.font.almarai
    FontChoice.BeVietnam -> R.font.be_vietnam
    FontChoice.Arimo -> R.font.arimo
    FontChoice.Monoton -> R.font.monoton
    FontChoice.Montaga -> R.font.montaga
    FontChoice.XanhMono -> R.font.xanh_mono
    FontChoice.AguafinaScript -> R.font.aguafina_script
    FontChoice.Cantarell -> R.font.cantarell
    FontChoice.Caveat -> R.font.caveat
    FontChoice.Courgette -> R.font.courgette
    FontChoice.Gabriela -> R.font.gabriela
    FontChoice.GreatVibes -> R.font.great_vibes
    FontChoice.IndieFlower -> R.font.indie_flower
    FontChoice.KalamLight -> R.font.kalam_light
    FontChoice.Lobster -> R.font.lobster
    FontChoice.Pattaya -> R.font.pattaya
    FontChoice.PetitFormalScript -> R.font.petit_formal_script
    FontChoice.RajdhaniLight -> R.font.rajdhani_light
    FontChoice.Roboto -> R.font.roboto
    FontChoice.Sacramento -> R.font.sacramento
    FontChoice.ShadowsIntoLight -> R.font.shadows_into_light
    FontChoice.Timmana -> R.font.timmana
    FontChoice.Yellowtail -> R.font.yellowtail
}

fun FontChoice.family(): FontFamily = fontRes()?.let { FontFamily(Font(it)) } ?: FontFamily.Default

@Composable
fun WriterTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val app = if (prefs.night ?: isSystemInDarkTheme()) NightColors else DayColors
    val scheme = if (app.night) {
        darkColorScheme(
            primary = app.text, onPrimary = app.background, secondary = app.text,
            background = app.background, surface = app.background, onBackground = app.text, onSurface = app.text,
            surfaceContainerHigh = app.dialog, surfaceContainerLow = app.background, outline = app.secondary,
        )
    } else {
        lightColorScheme(
            primary = app.text, onPrimary = app.background, secondary = app.text,
            background = app.background, surface = app.background, onBackground = app.text, onSurface = app.text,
            surfaceContainerHigh = app.dialog, surfaceContainerLow = app.background, outline = app.secondary,
        )
    }
    CompositionLocalProvider(LocalAppColors provides app, LocalWriterColors provides prefs.editorColors()) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
