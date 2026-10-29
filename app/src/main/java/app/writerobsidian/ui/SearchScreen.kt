package app.writerobsidian.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.writerobsidian.AppViewModel

/** The original's Search screen: a "Search" field over every note, matched by name. */
@Composable
fun SearchScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(Modifier.fillMaxSize().background(c.background)) {
        Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
            BasicTextField(
                value = vm.query,
                onValueChange = vm::search,
                singleLine = true,
                textStyle = TextStyle(color = c.text, fontSize = 22.sp),
                cursorBrush = SolidColor(c.text),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                decorationBox = { inner ->
                    Box(Modifier.padding(vertical = 10.dp)) {
                        if (vm.query.isEmpty()) Text("Search", color = c.secondary, fontSize = 22.sp)
                        inner()
                    }
                },
            )
            HorizontalDivider(color = c.divider)
        }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            items(vm.searchResults, key = { it.file.uri.toString() }) { entry ->
                EntryRow(entry, onClick = { vm.openNote(entry) })
            }
        }
    }
}
