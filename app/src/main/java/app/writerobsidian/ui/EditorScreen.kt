package app.writerobsidian.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.speech.RecognizerIntent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.TextFormat
import androidx.compose.material.icons.filled.FormatClear
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import app.writerobsidian.AppViewModel
import app.writerobsidian.EditorState
import app.writerobsidian.data.Prefs
import app.writerobsidian.editor.WriterEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private const val AUTOSAVE_DELAY_MS = 2000L
private val TOOLBAR_HEIGHT = 56.dp

/** The original's floating bar appears once the toolbar has scrolled this far (raw pixels). */
private const val FLOATING_BAR_SCROLL_PX = 85

/**
 * The note editor, laid out like Writer 4.7.0's: a toolbar (undo, redo, editor, print,
 * dictation, menu) that scrolls away with the text, a floating bar with the word count once
 * it has, and a formatting bar that slides up when text is selected or Editor is tapped,
 * with a text-color strip above it.
 */
@Composable
fun EditorScreen(vm: AppViewModel, state: EditorState, prefs: Prefs) {
    val context = LocalContext.current
    val colors = LocalWriterColors.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val edit = remember { WriterEditText(context) }
    val scroll = remember { ScrollView(context) }
    var scrollY by remember { mutableIntStateOf(0) }
    var showFormatBar by remember { mutableStateOf(false) }
    var showColors by remember { mutableStateOf(false) }
    var readOnly by remember { mutableStateOf(prefs.readOnly) }
    var words by remember { mutableIntStateOf(wordCount(state.initial.text)) }
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var listen by remember { mutableStateOf(false) }
    var share by remember { mutableStateOf(false) }

    // Dictation appends each recognized phrase and, like the original, starts listening
    // again until the user cancels.
    val speechIntent = remember {
        android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
    }
    var relisten by remember { mutableStateOf(0) }
    val dictation = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == android.app.Activity.RESULT_OK && heard != null) {
            edit.append("$heard ")
            relisten++
        }
    }
    fun startDictation() {
        runCatching { dictation.launch(speechIntent) }
            .onFailure { Toast.makeText(context, "Speech recognition isn't available", Toast.LENGTH_SHORT).show() }
    }
    LaunchedEffect(relisten) { if (relisten > 0) startDictation() }

    DisposableEffect(edit) {
        var saveJob: Job? = null
        edit.load(state.initial)
        state.content = { edit.content() }
        edit.onEdited = {
            state.edited = true
            words = wordCount(edit.text?.toString().orEmpty())
            saveJob?.cancel()
            saveJob = scope.launch {
                delay(AUTOSAVE_DELAY_MS)
                vm.save()
            }
        }
        edit.onSelectionChange = { start, end -> if (start != end) showFormatBar = true }
        onDispose {
            saveJob?.cancel()
            // Keep unsaved edits if the screen is rebuilt (e.g. on rotation) before autosave.
            if (state.edited) state.initial = edit.content()
            edit.onEdited = null
            edit.onSelectionChange = null
        }
    }

    LaunchedEffect(colors, prefs.font, prefs.fontSize) {
        val typeface = prefs.font.fontRes()?.let { ResourcesCompat.getFont(context, it) } ?: android.graphics.Typeface.DEFAULT
        edit.applyLook(colors.text.toArgb(), colors.accent.toArgb(), typeface, prefs.fontSize)
    }
    LaunchedEffect(readOnly) { edit.isEnabled = !readOnly }

    val actions = EditorActionHandlers(
        // The original's Editor button shows the formatting bar, or hides it and the color strip.
        onEditor = {
            if (showFormatBar) {
                showFormatBar = false
                showColors = false
            } else {
                showFormatBar = true
            }
        },
        onPrint = { vm.printNote(state.title, edit.content()) },
        onSpeak = { startDictation() },
        onMenu = { menu = true },
    )

    Box(Modifier.fillMaxSize().background(colors.background)) {
        val toolbarPx = with(density) { TOOLBAR_HEIGHT.roundToPx() }
        AndroidView(
            modifier = Modifier.fillMaxSize().statusBarsPadding().imePadding().navigationBarsPadding(),
            factory = {
                scroll.apply {
                    isFillViewport = true
                    isVerticalScrollBarEnabled = true
                    setOnScrollChangeListener { _, _, y, _, _ -> scrollY = y }
                    addView(
                        LinearLayout(it).apply {
                            orientation = LinearLayout.VERTICAL
                            addView(View(it), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, toolbarPx))
                            addView(edit, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                        },
                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                    )
                }
            },
        )

        // Toolbar: scrolls away with the text.
        Row(
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .offset { IntOffset(0, -minOf(scrollY, toolbarPx)) }
                .fillMaxWidth()
                .height(TOOLBAR_HEIGHT)
                .background(colors.background),
        ) {
            EditorActions(colors.accent, edit, actions)
        }

        // Floating bar with the word count once the toolbar is out of view.
        AnimatedVisibility(
            visible = scrollY >= FLOATING_BAR_SCROLL_PX,
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 8.dp, end = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(if (colors.light) Color(0x7D898989) else Color(0x7D252323), RoundedCornerShape(49.dp))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
            ) {
                Text(
                    words.toString(),
                    color = colors.accent,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .background(Color(0x1B797979), RoundedCornerShape(75.dp))
                        .padding(10.dp),
                )
                EditorActions(colors.accent, edit, actions)
            }
        }

        // Formatting bar, with the text-color strip above it: appears when text is selected.
        AnimatedVisibility(
            visible = showFormatBar && !readOnly,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).imePadding().navigationBarsPadding(),
        ) {
          Column {
            AnimatedVisibility(visible = showColors) {
                Surface(color = colors.background, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        EDITOR_COLORS.forEach { c ->
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .background(Color(c), CircleShape)
                                    .border(1.dp, Color(0x33000000), CircleShape)
                                    .clickable { edit.applyColor(c.toInt()) },
                            )
                        }
                    }
                }
            }
            Surface(color = colors.background, shadowElevation = 5.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    FormatButton(Icons.Default.FormatBold, "Bold", colors.accent) { edit.toggleBold() }
                    FormatButton(Icons.Default.FormatItalic, "Italic", colors.accent) { edit.toggleItalic() }
                    FormatButton(Icons.Default.FormatColorText, "Text color", colors.accent) { showColors = !showColors }
                    FormatButton(Icons.Default.FormatUnderlined, "Underline", colors.accent) { edit.toggleUnderline() }
                    FormatButton(Icons.Default.FormatStrikethrough, "Strikethrough", colors.accent) { edit.toggleStrikethrough() }
                    FormatButton(Icons.Default.FormatQuote, "Quote", colors.accent) { edit.toggleQuote() }
                    FormatButton(Icons.AutoMirrored.Filled.FormatListBulleted, "Bullet", colors.accent) { edit.toggleBullet() }
                    FormatButton(Icons.Default.ContentCopy, "Copy", colors.accent) { copySelection(context, edit) }
                    FormatButton(Icons.Default.KeyboardDoubleArrowUp, "Top", colors.accent) { scroll.scrollTo(0, 0) }
                    FormatButton(Icons.Default.KeyboardDoubleArrowDown, "Bottom", colors.accent) { scroll.fullScroll(View.FOCUS_DOWN) }
                    FormatButton(Icons.Default.FormatClear, "Clear", colors.accent) { edit.clearFormats() }
                    FormatButton(Icons.Default.SelectAll, "All", colors.accent) {
                        edit.requestFocus()
                        edit.selectAll()
                    }
                }
            }
          }
        }

        // Read-only mode: a button to start editing, as in the original.
        if (readOnly) {
            FloatingActionButton(
                onClick = { readOnly = false },
                containerColor = colors.accent,
                contentColor = colors.background,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 10.dp, bottom = 5.dp),
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
        }
    }

    if (menu) {
        EditorMenu(
            onDismiss = { menu = false },
            onTop = { scroll.scrollTo(0, 0) },
            onEnd = { scroll.fullScroll(View.FOCUS_DOWN) },
            onPrint = actions.onPrint,
            onDetails = { details = true },
            onListen = { listen = true },
            onShare = { share = true },
        )
    }
    if (details) DetailsDialog(edit.text?.toString().orEmpty()) { details = false }
    if (listen) ListenDialog(edit.text?.toString().orEmpty(), prefs.font) { listen = false }
    if (share) ShareSheet(state.title, { edit.content() }) { share = false }
}

/** Text colors in Writer 4.7.0's editor color strip (activity_text.xml), in its order. */
private val EDITOR_COLORS = listOf(
    0xFFFF1604, 0xFFEC1346, 0xFFFF3878, 0xFF000000, 0xFFEDEDED, 0xFF979797, 0xFFFF5722, 0xFFFC804A,
    0xFFFFEB3B, 0xFFFFFF8F, 0xFF009688, 0xFF4CAF50, 0xFF8BC34A, 0xFF8C2241, 0xFFFC6C64, 0xFF377A07,
    0xFF73C23D, 0xFFC1D320, 0xFF163F66, 0xFF4E6DB1, 0xFF1841E8, 0xFF50095D, 0xFF5D53A0, 0xFF721CE2,
    0xFF00BCD4, 0xFF20DDCA, 0xFF4B1200, 0xFFAE6337, 0xFFCE9A7E, 0xFFFDC685,
)

private class EditorActionHandlers(
    val onEditor: () -> Unit,
    val onPrint: () -> Unit,
    val onSpeak: () -> Unit,
    val onMenu: () -> Unit,
)

/** The toolbar buttons of Writer 4.7.0: Undo, Redo, Editor, Print, Speak, Menu. */
@Composable
private fun EditorActions(tint: Color, edit: WriterEditText, actions: EditorActionHandlers) {
    IconButton(onClick = { edit.undo() }) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = tint) }
    IconButton(onClick = { edit.redo() }) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = tint) }
    IconButton(onClick = actions.onEditor) { Icon(Icons.Default.TextFormat, "Editor", tint = tint) }
    IconButton(onClick = actions.onPrint) { Icon(Icons.Default.Print, "Print", tint = tint) }
    IconButton(onClick = actions.onSpeak) { Icon(Icons.Default.Mic, "Speak", tint = tint) }
    IconButton(onClick = actions.onMenu) { Icon(Icons.Default.MoreVert, "Menu", tint = tint) }
}

@Composable
private fun FormatButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.padding(horizontal = 2.dp)) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}

private fun copySelection(context: Context, edit: WriterEditText) {
    val start = edit.selectionStart
    val end = edit.selectionEnd
    if (start < 0 || start >= end) return
    val text = edit.text?.subSequence(start, end)?.toString() ?: return
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("Writer", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

fun wordCount(text: String): Int = text.split(Regex("""\s+""")).count { it.isNotBlank() }
