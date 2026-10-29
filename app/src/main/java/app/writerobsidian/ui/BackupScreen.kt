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
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.writerobsidian.AppViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The original's Backup screen, adapted to notes that live in the vault. */
@Composable
fun BackupScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val backUp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.backUp(uri)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importWriterBackup(uri)
    }

    Column(Modifier.fillMaxSize().background(c.background)) {
        WriterTopBar(title = "Backup", mono = false, navIcon = Icons.AutoMirrored.Filled.ArrowBack, navDescription = "Back", onNav = { vm.back() })
        Section(Icons.Filled.Warning, "Warning") {
            Text(
                "Do not forget to get a backup. Your notes are saved in your Obsidian vault folder, so they are backed up with it.",
                color = c.text, fontSize = 17.sp, modifier = Modifier.padding(start = 60.dp, end = 16.dp, bottom = 20.dp),
            )
        }
        Section(Icons.Filled.SdCard, "Back up to memory card") {
            Action("Backup") {
                val stamp = SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.US).format(Date())
                backUp.launch("Writer_$stamp.zip")
            }
        }
        Section(Icons.Filled.SdCard, "Restore from the old Writer app") {
            Text(
                "Pick the WriterDb_….db file made by the original app's Backup. Its notes and folders are copied into your vault.",
                color = c.secondary, fontSize = 15.sp, modifier = Modifier.padding(start = 60.dp, end = 16.dp),
            )
            Action("Restore") { restore.launch(arrayOf("*/*")) }
        }
    }
}

@Composable
private fun Section(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    val c = LocalAppColors.current
    Surface(color = c.background, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Column(Modifier.padding(top = 20.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, bottom = 12.dp)) {
                Icon(icon, contentDescription = null, tint = c.icon, modifier = Modifier.size(24.dp))
                Text(title, color = c.text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp))
            }
            content()
        }
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 60.dp)) {
        Text(label.uppercase(), color = c.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(vertical = 18.dp))
        HorizontalDivider(color = c.divider)
    }
}
