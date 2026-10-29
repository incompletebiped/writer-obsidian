package app.writerobsidian.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.writerobsidian.AppIcon
import app.writerobsidian.AppViewModel
import app.writerobsidian.BuildConfig
import app.writerobsidian.Screen
import app.writerobsidian.data.Prefs

/** The original's Settings screen (a preference list), with the vault folder added. */
@Composable
fun SettingsScreen(vm: AppViewModel, prefs: Prefs) {
    val c = LocalAppColors.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.chooseVaultFolder(uri)
    }
    var about by remember { mutableStateOf(false) }
    var sources by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var icon by remember { mutableStateOf(AppIcon.current(context)) }
    var iconDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState())) {
        Text("Settings", color = c.text, fontSize = 26.sp, fontWeight = FontWeight.Medium, modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 24.dp, bottom = 20.dp))

        Category("General")
        Preference("Backup", "Database backup.") { vm.open(Screen.Backup) }
        Preference("Vault folder", vm.vault?.root?.name ?: "Not chosen") { pickFolder.launch(prefs.vaultFolder) }

        Category("Customize")
        Preference("Theme", "Theme style.") { vm.open(Screen.Customize) }
        Preference("App icon", icon.label) { iconDialog = true }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().clickable { vm.setReadOnly(!prefs.readOnly) }.padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Read Only", color = c.text, fontSize = 19.sp)
                Text("Preview text.", color = c.secondary, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Checkbox(
                checked = prefs.readOnly,
                onCheckedChange = { vm.setReadOnly(it) },
                colors = CheckboxDefaults.colors(checkedColor = c.text, uncheckedColor = c.text, checkmarkColor = c.background),
            )
        }

        Category("About")
        Preference("About", "Developers.") { about = true }
        Preference("Open Sources", "Open source lib.") { sources = true }
    }

    if (about) {
        WriterDialog(onDismiss = { about = false }, title = "About", buttons = listOf("OK" to { about = false })) {
            Text(
                "Writer ${BuildConfig.VERSION_NAME}\n\nA rewrite of the Writer app by Xeasec that saves notes as Markdown in an Obsidian vault.",
                color = c.text, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
    if (iconDialog) {
        WriterDialog(onDismiss = { iconDialog = false }, title = "App icon", buttons = listOf("OK" to { iconDialog = false })) {
            Column {
                AppIcon.entries.forEach { option ->
                    val choose = {
                        AppIcon.set(context, option)
                        icon = option
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = choose).padding(vertical = 4.dp),
                    ) {
                        RadioButton(
                            selected = icon == option,
                            onClick = choose,
                            colors = RadioButtonDefaults.colors(selectedColor = c.text, unselectedColor = c.text),
                        )
                        Text(option.label, color = c.text, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Text(
                    "Your launcher may take a moment to show the new icon, and may remove the app from your home screen; add it back from the app drawer.",
                    color = c.secondary, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
    if (sources) {
        WriterDialog(onDismiss = { sources = false }, title = "Open Sources", buttons = listOf("OK" to { sources = false })) {
            Text(
                "AndroidX and Jetpack Compose — Apache License 2.0\n" +
                    "Kotlin coroutines — Apache License 2.0\n" +
                    "Fonts (24 Google Fonts families) — SIL Open Font License 1.1 or Apache License 2.0\n" +
                    "App icon — © xeasec, from the original Writer",
                color = c.text, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun Category(title: String) {
    Text(title, color = LocalAppColors.current.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
private fun Preference(title: String, summary: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(title, color = c.text, fontSize = 19.sp)
            Text(summary, color = c.secondary, fontSize = 16.sp, modifier = Modifier.padding(top = 2.dp))
        }
        HorizontalDivider(color = c.divider)
    }
}
