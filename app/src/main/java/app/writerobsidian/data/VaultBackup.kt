package app.writerobsidian.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes every note and folder in the vault folder into a .zip the user picked. Returns the note count. */
fun backUpVault(context: Context, vault: Vault, target: Uri): Int {
    var count = 0
    val out = context.contentResolver.openOutputStream(target) ?: error("Cannot write the backup file")
    ZipOutputStream(out.buffered()).use { zip ->
        fun walk(dir: DocumentFile, prefix: String) {
            for (file in dir.listFiles()) {
                val name = file.name ?: continue
                if (name.startsWith(".")) continue
                if (file.isDirectory) {
                    zip.putNextEntry(ZipEntry("$prefix$name/"))
                    zip.closeEntry()
                    walk(file, "$prefix$name/")
                } else if (name.endsWith(Vault.NOTE_EXTENSION, ignoreCase = true)) {
                    zip.putNextEntry(ZipEntry(prefix + name).apply { time = file.lastModified() })
                    context.contentResolver.openInputStream(file.uri)?.use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            }
        }
        walk(vault.root, "")
    }
    return count
}
