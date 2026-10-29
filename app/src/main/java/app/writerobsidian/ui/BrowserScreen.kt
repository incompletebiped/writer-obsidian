package app.writerobsidian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.writerobsidian.AppViewModel
import app.writerobsidian.Screen
import app.writerobsidian.data.Entry
import app.writerobsidian.data.Prefs
import app.writerobsidian.data.SortChoice

private sealed interface Dialog {
    data object New : Dialog
    data object Sort : Dialog
    data class Item(val entry: Entry) : Dialog
    data class ConfirmDelete(val entry: Entry) : Dialog
}

/** The note list, laid out like the original's main screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(vm: AppViewModel, prefs: Prefs) {
    val c = LocalAppColors.current
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var menu by remember { mutableStateOf(false) }
    val atRoot = vm.folderStack.isEmpty()

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.fillMaxSize()) {
            WriterTopBar(
                title = if (atRoot) "Writer" else vm.folderPath,
                letterSpaced = atRoot,
                navIcon = if (atRoot) Icons.Filled.Menu else Icons.AutoMirrored.Filled.ArrowBack,
                navDescription = if (atRoot) "Menu" else "Up",
                onNav = { if (atRoot) menu = true else vm.back() },
            ) {
                BarAction(Icons.Filled.Search, "Search") { vm.open(Screen.Search) }
                if (!atRoot) BarAction(Icons.Filled.Print, "Print") { vm.openPrintFolder() }
                BarAction(Icons.Filled.FilterList, "Sort") { dialog = Dialog.Sort }
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.secondary, trackColor = c.background)
            LazyColumn(Modifier.fillMaxSize()) {
                items(vm.entries, key = { it.file.uri.toString() }) { entry ->
                    EntryRow(
                        entry,
                        onClick = { if (entry.isFolder) vm.openFolder(entry.file) else vm.openNote(entry) },
                        onLongClick = { dialog = Dialog.Item(entry) },
                    )
                }
            }
        }
        AddButton(
            onClick = { dialog = Dialog.New },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 24.dp, bottom = 24.dp),
        )
    }

    if (menu) {
        ModalBottomSheet(
            onDismissRequest = { menu = false },
            containerColor = c.background,
            shape = RoundedCornerShape(0.dp),
            dragHandle = null,
        ) {
            Column(Modifier.navigationBarsPadding().padding(vertical = 12.dp)) {
                Text("Menu", color = c.secondary, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp))
                SheetRow(Icons.Outlined.DarkMode, "Day/Night Mode") { menu = false; vm.setNight(!c.night) }
                SheetRow(Icons.Outlined.Palette, "Customize") { menu = false; vm.open(Screen.Customize) }
                SheetRow(Icons.Outlined.Print, "Print") { menu = false; vm.openPrintFolder() }
                SheetRow(Icons.Outlined.Restore, "Backup") { menu = false; vm.open(Screen.Backup) }
                SheetRow(Icons.Outlined.Tune, "Settings") { menu = false; vm.open(Screen.Settings) }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        Dialog.Sort -> SortDialog(prefs.sort, onDismiss = { dialog = null }) { vm.setSort(it); dialog = null }
        Dialog.New -> NameDialog(initial = "", showFolder = true, onDismiss = { dialog = null }) { name, folder ->
            dialog = null
            vm.newItem(name, folder)
        }
        is Dialog.Item -> NameDialog(
            initial = d.entry.title,
            showFolder = false,
            onDismiss = { dialog = null },
            onDelete = { dialog = Dialog.ConfirmDelete(d.entry) },
        ) { name, _ ->
            dialog = null
            if (name != d.entry.title) vm.rename(d.entry, name)
        }
        is Dialog.ConfirmDelete -> WriterDialog(
            onDismiss = { dialog = null },
            title = "Delete",
            buttons = listOf("Cancel" to { dialog = null }, "Yes" to { dialog = null; vm.delete(d.entry) }),
        ) {
            Text(
                if (d.entry.isFolder) "\"${d.entry.title}\" and everything in it will be deleted from your vault."
                else "\"${d.entry.title}\" will be deleted from your vault.",
                color = c.text,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
}

@Composable
private fun SortDialog(current: SortChoice, onDismiss: () -> Unit, onChoose: (SortChoice) -> Unit) {
    val c = LocalAppColors.current
    var choice by remember { mutableStateOf(current) }
    WriterDialog(
        onDismiss = onDismiss,
        title = "Sort",
        buttons = listOf("Cancel" to onDismiss, "Yes" to { onChoose(choice) }),
    ) {
        Column {
            SortChoice.entries.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { choice = option }.padding(vertical = 4.dp),
                ) {
                    RadioButton(
                        selected = choice == option,
                        onClick = { choice = option },
                        colors = RadioButtonDefaults.colors(selectedColor = c.text, unselectedColor = c.text),
                    )
                    Text(option.label, color = c.text, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/**
 * The original's add dialog ("name..." with a Folder checkbox). Long-pressing an item
 * reuses it to rename, with a Delete button.
 */
@Composable
private fun NameDialog(
    initial: String,
    showFolder: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onSave: (name: String, folder: Boolean) -> Unit,
) {
    val c = LocalAppColors.current
    var name by remember { mutableStateOf(initial) }
    var folder by remember { mutableStateOf(false) }
    val buttons = buildList {
        if (onDelete != null) add("Delete" to onDelete)
        add("Cancel" to onDismiss)
        add("Save" to { if (name.isNotBlank()) onSave(name.trim(), folder) })
    }
    WriterDialog(onDismiss = onDismiss, buttons = buttons) {
        Column {
            BasicTextField(
                value = name,
                onValueChange = { name = it.replace("\n", " ") },
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontFamily = MonoFamily, fontSize = 18.sp),
                cursorBrush = SolidColor(c.text),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(
                        Modifier.fillMaxWidth().background(c.field, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 20.dp),
                    ) {
                        if (name.isEmpty()) Text("name...", color = c.secondary, fontFamily = MonoFamily, fontSize = 18.sp)
                        inner()
                    }
                },
            )
            if (showFolder) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 12.dp).clickable { folder = !folder },
                ) {
                    Checkbox(
                        checked = folder,
                        onCheckedChange = { folder = it },
                        colors = CheckboxDefaults.colors(checkedColor = c.text, uncheckedColor = c.text, checkmarkColor = c.dialog),
                    )
                    Text("Folder", color = c.text, fontFamily = MonoFamily, fontSize = 18.sp)
                }
            }
        }
    }
}
