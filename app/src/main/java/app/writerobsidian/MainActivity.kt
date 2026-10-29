package app.writerobsidian

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.writerobsidian.ui.BackupScreen
import app.writerobsidian.ui.BrowserScreen
import app.writerobsidian.ui.CustomizeScreen
import app.writerobsidian.ui.EditorScreen
import app.writerobsidian.ui.LocalAppColors
import app.writerobsidian.ui.MonoFamily
import app.writerobsidian.ui.PrintScreen
import app.writerobsidian.ui.SearchScreen
import app.writerobsidian.ui.SettingsScreen
import app.writerobsidian.ui.WriterTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { App(vm) }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val prefs = vm.prefs.collectAsStateWithLifecycle().value ?: return

    LaunchedEffect(prefs.vaultFolder) { vm.restoreVault(prefs.vaultFolder) }
    // Leaving the app (home, switching apps, screen off) flushes the open note.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.save() }

    WriterTheme(prefs) {
        // Light status-bar icons on the dark Night Mode screens, dark ones by day.
        val view = LocalView.current
        val night = LocalAppColors.current.night
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !night
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !night
            }
        }
        val snackbar = remember { SnackbarHostState() }
        LaunchedEffect(vm.message) {
            vm.message?.let {
                snackbar.showSnackbar(it)
                vm.message = null
            }
        }
        Box(Modifier.fillMaxSize().background(LocalAppColors.current.background)) {
            if (vm.vault == null) {
                ChooseVault(vm)
            } else {
                BackHandler(enabled = vm.screen != Screen.Browser || vm.folderStack.isNotEmpty()) { vm.back() }
                val editor = vm.editor
                when (vm.screen) {
                    Screen.Browser -> BrowserScreen(vm, prefs)
                    Screen.Search -> SearchScreen(vm)
                    Screen.Editor -> if (editor != null) EditorScreen(vm, editor, prefs)
                    Screen.Print -> PrintScreen(vm)
                    Screen.Customize -> CustomizeScreen(vm, prefs)
                    Screen.Backup -> BackupScreen(vm)
                    Screen.Settings -> SettingsScreen(vm, prefs)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

@Composable
private fun ChooseVault(vm: AppViewModel) {
    val c = LocalAppColors.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.chooseVaultFolder(uri)
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Writer", color = c.text, fontFamily = MonoFamily, fontSize = 30.sp, letterSpacing = 14.sp)
        Text(
            "Choose a folder inside your Obsidian vault. Every note is saved there as a Markdown file, " +
                "so it shows up in Obsidian and syncs with the rest of your vault.",
            color = c.secondary,
            textAlign = TextAlign.Center,
            fontSize = 17.sp,
        )
        TextButton(onClick = { pick.launch(null) }) {
            Text("CHOOSE FOLDER", color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}
