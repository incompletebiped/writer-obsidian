package app.writerobsidian.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.writerobsidian.data.Entry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The original's top bar: a navigation icon, a monospace title and icon actions. */
@Composable
fun WriterTopBar(
    title: String,
    navIcon: ImageVector,
    navDescription: String,
    onNav: () -> Unit,
    letterSpaced: Boolean = false,
    mono: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().background(c.background).statusBarsPadding().heightIn(min = 64.dp),
    ) {
        IconButton(onClick = onNav, modifier = Modifier.padding(start = 4.dp)) {
            Icon(navIcon, contentDescription = navDescription, tint = c.icon)
        }
        Text(
            title,
            color = c.text,
            fontFamily = if (mono) MonoFamily else FontFamily.Default,
            fontSize = 22.sp,
            fontWeight = if (mono) FontWeight.Normal else FontWeight.Medium,
            letterSpacing = if (letterSpaced) 12.sp else 0.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        )
        actions()
    }
}

@Composable
fun BarAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = description, tint = LocalAppColors.current.icon) }
}

private val DATE_FORMAT = SimpleDateFormat("dd MMMM yyyy HH:mm:ss", Locale.getDefault())

/** A list row: gray folder or document icon, monospace name, and "N files" or the date. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryRow(entry: Entry, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val c = LocalAppColors.current
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 72.dp).padding(vertical = 10.dp)) {
            Icon(
                if (entry.isFolder) Icons.Filled.Folder else Icons.AutoMirrored.Filled.Article,
                contentDescription = null,
                tint = c.icon,
                modifier = Modifier.padding(start = 16.dp).size(34.dp),
            )
            Column(Modifier.padding(start = 22.dp, end = 16.dp)) {
                Text(entry.title, color = c.text, fontFamily = MonoFamily, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (entry.isFolder) "${entry.count} files" else DATE_FORMAT.format(Date(entry.modified)),
                    color = c.secondary,
                    fontFamily = MonoFamily,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        HorizontalDivider(color = c.divider, thickness = 1.dp)
    }
}

/** The round dark "+" button. */
@Composable
fun AddButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = LocalAppColors.current.fab,
        shadowElevation = 6.dp,
        modifier = modifier.size(64.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Add, contentDescription = "Add", tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

/** The original's square-cornered dialog with uppercase text buttons on the right. */
@Composable
fun WriterDialog(
    onDismiss: () -> Unit,
    title: String? = null,
    buttons: List<Pair<String, () -> Unit>>,
    content: @Composable () -> Unit,
) {
    val c = LocalAppColors.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(color = c.dialog, shape = RoundedCornerShape(2.dp), shadowElevation = 8.dp) {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp)) {
                if (title != null) {
                    Text(title, color = c.text, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 24.dp))
                    Box(Modifier.height(16.dp))
                }
                Box(Modifier.padding(horizontal = 16.dp)) { content() }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth().padding(top = 24.dp, end = 8.dp)) {
                    buttons.forEach { (label, action) ->
                        TextButton(onClick = action) {
                            Text(label.uppercase(), color = c.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                        Box(Modifier.width(4.dp))
                    }
                }
            }
        }
    }
}

/** A row in a bottom-sheet menu: outlined icon and label. */
@Composable
fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalAppColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = c.icon, modifier = Modifier.size(26.dp))
        Text(label, color = c.text, fontSize = 18.sp, modifier = Modifier.padding(start = 34.dp))
    }
}
