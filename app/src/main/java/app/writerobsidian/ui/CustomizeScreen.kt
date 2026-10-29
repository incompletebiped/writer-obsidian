package app.writerobsidian.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.writerobsidian.AppViewModel
import app.writerobsidian.data.FontChoice
import app.writerobsidian.data.Prefs
import app.writerobsidian.data.ThemeChoice
import kotlin.math.roundToInt

private const val PREVIEW_TEXT =
    "In its most general sense, the term world refers to the totality of entities, to the whole of " +
        "reality or to everything that is. The nature of the world has been conceptualized differently " +
        "in different fields. Some conceptions see the world as unique while others talk of a plurality " +
        "of worlds."

/** Text colors on Writer 4.7.0's Customize screen (activity_customize.xml), in its order. */
private val TEXT_COLORS = listOf(
    0xFF000000, 0xFFFFFFFF, 0xFFEDEDED, 0xFFFF1604, 0xFFEC1346, 0xFFFF3878, 0xFF979797,
    0xFFFF5722, 0xFFFC804A, 0xFFFFEB3B, 0xFFFFFF8F, 0xFF009688, 0xFF4CAF50, 0xFF8BC34A,
    0xFF8C2241, 0xFFFC6C64, 0xFF377A07, 0xFF73C23D, 0xFFC1D320, 0xFF163F66, 0xFF4E6DB1,
    0xFF1841E8, 0xFF50095D, 0xFF5D53A0, 0xFF721CE2, 0xFF00BCD4, 0xFF20DDCA, 0xFF4B1200,
    0xFFAE6337, 0xFFCE9A7E, 0xFFFDC685,
).map { Color(it) }

/** The original's Customize screen: a live preview, then background, text color and font. */
@Composable
fun CustomizeScreen(vm: AppViewModel, prefs: Prefs) {
    val c = LocalAppColors.current
    val editor = LocalWriterColors.current

    Column(Modifier.fillMaxSize().background(c.background)) {
        WriterTopBar(
            title = "Customize",
            mono = false,
            navIcon = Icons.AutoMirrored.Filled.ArrowBack,
            navDescription = "Back",
            onNav = { vm.back() },
        )
        Box(Modifier.fillMaxWidth().height(210.dp).background(editor.background).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(PREVIEW_TEXT, color = editor.text, fontFamily = prefs.font.family(), fontSize = prefs.fontSize.sp, lineHeight = (prefs.fontSize * 1.3f).sp)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp)) {
            Label("Background")
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                    ThemeChoice.entries.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            row.forEach { theme ->
                                Swatch(theme.colors().background, 56.dp, selected = prefs.theme == theme, label = theme.label) { vm.setTheme(theme) }
                            }
                        }
                    }
                }
            }

            Label("Text Color")
            Card {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(16.dp),
                ) {
                    TEXT_COLORS.forEach { color ->
                        val selected = prefs.textColor == color.toArgb()
                        // Tapping the chosen color again goes back to the background's own text color.
                        Swatch(color, 34.dp, selected = selected, label = null) { vm.setTextColor(if (selected) null else color.toArgb()) }
                    }
                }
            }

            Label("Font")
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FontChoice.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { font ->
                            Surface(
                                onClick = { vm.setFont(font) },
                                color = c.dialog,
                                shape = RoundedCornerShape(4.dp),
                                border = if (prefs.font == font) BorderStroke(2.dp, c.text) else null,
                                shadowElevation = 2.dp,
                                modifier = Modifier.weight(1f).height(56.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(font.sample, color = c.text, fontFamily = font.family(), fontSize = 20.sp)
                                }
                            }
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }

            Label("Size: ${prefs.fontSize}")
            Slider(
                value = prefs.fontSize.toFloat(),
                onValueChange = { vm.setFontSize(it.roundToInt()) },
                valueRange = Prefs.FONT_SIZES.first.toFloat()..Prefs.FONT_SIZES.last.toFloat(),
                steps = Prefs.FONT_SIZES.last - Prefs.FONT_SIZES.first - 1,
                colors = SliderDefaults.colors(thumbColor = c.text, activeTrackColor = c.text, inactiveTrackColor = c.dialog, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, color = LocalAppColors.current.text, fontSize = 18.sp, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 12.dp))
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(color = LocalAppColors.current.dialog, shape = RoundedCornerShape(8.dp), shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
private fun Swatch(color: Color, size: androidx.compose.ui.unit.Dp, selected: Boolean, label: String?, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .background(color, CircleShape)
            .border(1.dp, Color(0x33000000), CircleShape)
            .clickable(onClick = onClick),
    ) {
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = label, tint = if (color.luminance() > 0.5f) Color.Black else Color.White)
        }
    }
}
