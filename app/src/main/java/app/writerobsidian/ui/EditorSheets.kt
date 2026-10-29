package app.writerobsidian.ui

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.writerobsidian.data.FontChoice
import app.writerobsidian.data.HtmlExport
import app.writerobsidian.data.RichText
import app.writerobsidian.data.Vault
import java.io.File

/** The editor's "Menu" bottom sheet, with Writer 4.7.0's entries. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorMenu(
    onDismiss: () -> Unit,
    onTop: () -> Unit,
    onEnd: () -> Unit,
    onDetails: () -> Unit,
    onListen: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit,
) {
    val c = LocalAppColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.background,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp),
        dragHandle = null,
    ) {
        Column(Modifier.navigationBarsPadding().padding(vertical = 12.dp)) {
            Text("Menu", color = c.secondary, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
            SheetRow(Icons.Default.KeyboardDoubleArrowUp, "Text Top") { onDismiss(); onTop() }
            SheetRow(Icons.Default.KeyboardDoubleArrowDown, "Text End") { onDismiss(); onEnd() }
            SheetRow(Icons.Default.Info, "Details") { onDismiss(); onDetails() }
            SheetRow(Icons.AutoMirrored.Filled.VolumeUp, "Listen") { onDismiss(); onListen() }
            SheetRow(Icons.Default.Print, "Print") { onDismiss(); onPrint() }
            SheetRow(Icons.Default.Share, "Share") { onDismiss(); onShare() }
        }
    }
}

@Composable
private fun SheetItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** Word and character counts, as in the original's Details dialog. */
@Composable
fun DetailsDialog(text: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Words: ${wordCount(text)}")
                Text("Characters: ${text.length}")
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Reads the note aloud with speed controls, replacing the original's Listen screen. */
@Composable
fun ListenDialog(text: String, font: FontChoice, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var ready by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var rate by remember { mutableFloatStateOf(1f) }
    val tts = remember {
        TextToSpeech(context.applicationContext) { status -> ready = status == TextToSpeech.SUCCESS }
    }
    DisposableEffect(tts) {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                if (utteranceId == LAST_UTTERANCE) speaking = false
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                speaking = false
            }
        })
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }
    fun play() {
        tts.setSpeechRate(rate)
        // Engines cap a single utterance; queue paragraphs within that limit.
        val max = TextToSpeech.getMaxSpeechInputLength()
        val chunks = text.split(Regex("""\n\s*\n""")).filter { it.isNotBlank() }.flatMap { it.chunked(max) }
        if (chunks.isEmpty()) return
        chunks.forEachIndexed { i, chunk ->
            val mode = if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(chunk, mode, null, if (i == chunks.lastIndex) LAST_UTTERANCE else "part-$i")
        }
        speaking = true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Listen") },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { rate = (rate - 0.25f).coerceAtLeast(0.25f); if (speaking) play() }) {
                    Icon(Icons.Default.Remove, contentDescription = "Slower")
                }
                Text("%.2fx".format(rate), fontFamily = font.family())
                IconButton(onClick = { rate = (rate + 0.25f).coerceAtMost(3f); if (speaking) play() }) {
                    Icon(Icons.Default.Add, contentDescription = "Faster")
                }
                IconButton(
                    enabled = ready,
                    onClick = {
                        if (speaking) { tts.stop(); speaking = false } else play()
                    },
                ) {
                    if (speaking) Icon(Icons.Default.Stop, "Stop") else Icon(Icons.Default.PlayArrow, "Play")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private const val LAST_UTTERANCE = "last"

/** The original's share options: Word document, HTML, plain text, and plain-text file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(title: String, content: () -> RichText, onDismiss: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 8.dp).fillMaxWidth()) {
            Text("Share", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            SheetItem(Icons.Default.Description, "Word document (.doc)") {
                onDismiss()
                shareFile(context, title, "doc", "application/msword", HtmlExport.render(title, content()))
            }
            SheetItem(Icons.Default.Code, "Web page (.html)") {
                onDismiss()
                shareFile(context, title, "html", "text/html", HtmlExport.render(title, content()))
            }
            SheetItem(Icons.AutoMirrored.Filled.Article, "Plain text") {
                onDismiss()
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, content().text)
                context.startActivity(Intent.createChooser(send, "Share"))
            }
            SheetItem(Icons.Default.TextSnippet, "Text file (.txt)") {
                onDismiss()
                shareFile(context, title, "txt", "text/plain", content().text)
            }
        }
    }
}

private fun shareFile(context: Context, title: String, extension: String, mime: String, body: String) {
    val dir = File(context.cacheDir, "share").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, Vault.sanitizeTitle(title) + "." + extension)
    file.writeText(body)
    val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share"))
}
