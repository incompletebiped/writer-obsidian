package app.writerobsidian.ui

import android.content.Context
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.writerobsidian.AppViewModel
import app.writerobsidian.data.HtmlExport
import kotlinx.coroutines.launch

/** The original's Print screen: prints the open note, or every note in the current folder. */
@Composable
fun PrintScreen(vm: AppViewModel) {
    val c = LocalAppColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dark by remember { mutableStateOf(false) }
    // The print framework reads from this WebView, so it must outlive the click.
    val holder = remember { arrayOfNulls<WebView>(1) }

    Box(Modifier.fillMaxSize().background(c.background)) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(Icons.Outlined.Print, contentDescription = null, tint = Color(0xFFCCCCCC), modifier = Modifier.size(170.dp))
            Text("PRINT", color = Color(0xFFCCCCCC), fontFamily = MonoFamily, fontSize = 30.sp, letterSpacing = 18.sp, modifier = Modifier.padding(top = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 48.dp)) {
                Text("Day/Night Mode", color = c.text, fontSize = 18.sp, modifier = Modifier.padding(end = 16.dp))
                Switch(
                    checked = dark,
                    onCheckedChange = { dark = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = c.secondary, uncheckedThumbColor = c.secondary, uncheckedTrackColor = c.dialog),
                )
            }
        }
        Surface(
            onClick = {
                scope.launch {
                    val notes = vm.notesToPrint()
                    if (notes.isEmpty()) {
                        Toast.makeText(context, "There is nothing to print", Toast.LENGTH_SHORT).show()
                    } else {
                        val html = HtmlExport.renderForPrint(notes, dark)
                        holder[0] = print(context, vm.printTitle, html)
                    }
                }
            },
            shape = CircleShape,
            color = c.fab,
            shadowElevation = 6.dp,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp).size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Print, contentDescription = "Print", tint = Color.White)
            }
        }
    }
}

private fun print(context: Context, jobName: String, html: String): WebView {
    val webView = WebView(context)
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val manager = context.getSystemService(PrintManager::class.java)
            manager.print(jobName, view.createPrintDocumentAdapter(jobName), null)
        }
    }
    webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    return webView
}
