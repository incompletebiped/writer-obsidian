package app.writerobsidian.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** One row in the browser: a subfolder or a Markdown note. [count] is a folder's note count. */
data class Entry(
    val file: DocumentFile,
    val title: String,
    val isFolder: Boolean,
    val modified: Long,
    val count: Int = 0,
)

/**
 * Notes live as plain `.md` files inside a folder the user granted through the
 * Storage Access Framework, so Obsidian (or any sync tool) sees the same files.
 */
class Vault(private val context: Context, rootUri: Uri) {
    val root: DocumentFile = DocumentFile.fromTreeUri(context, rootUri)
        ?: throw IllegalArgumentException("Not a folder: $rootUri")

    fun isAccessible(): Boolean = root.exists() && root.canWrite()

    /** Folders first, then notes, each ordered by [sort], as in the original list. */
    fun list(folder: DocumentFile, sort: SortChoice): List<Entry> =
        folder.listFiles()
            .mapNotNull(::toEntry)
            .map { if (it.isFolder) it.copy(count = it.file.listFiles().count(::isNote)) else it }
            .sortedWith(compareByDescending<Entry> { it.isFolder }.then(sort.comparator()))

    /** Every note below [folder], for the Search screen. */
    fun allNotes(folder: DocumentFile): List<Entry> {
        val notes = mutableListOf<Entry>()
        fun walk(dir: DocumentFile) {
            for (file in dir.listFiles()) {
                val entry = toEntry(file) ?: continue
                if (entry.isFolder) walk(file) else notes += entry
            }
        }
        walk(folder)
        return notes
    }

    private fun isNote(file: DocumentFile) =
        file.isFile && file.name?.let { !it.startsWith(".") && it.endsWith(NOTE_EXTENSION, ignoreCase = true) } == true

    fun read(note: DocumentFile): String =
        context.contentResolver.openInputStream(note.uri)?.bufferedReader()?.use { it.readText() }.orEmpty()

    fun write(note: DocumentFile, text: String) {
        val out = context.contentResolver.openOutputStream(note.uri, "wt")
            ?: error("Cannot write ${note.name}")
        out.bufferedWriter().use { it.write(text) }
    }

    fun createNote(folder: DocumentFile, title: String, text: String = ""): DocumentFile {
        val name = uniqueName(folder, sanitizeTitle(title), NOTE_EXTENSION)
        // octet-stream stops providers from appending an extension of their own.
        val note = folder.createFile("application/octet-stream", name)
            ?: error("Cannot create $name")
        if (text.isNotEmpty()) write(note, text)
        return note
    }

    fun createFolder(parent: DocumentFile, title: String): DocumentFile {
        val name = uniqueName(parent, sanitizeTitle(title), "")
        return parent.createDirectory(name) ?: error("Cannot create folder $name")
    }

    /** The folder named [title] in [parent], created if there is none. */
    fun folder(parent: DocumentFile, title: String): DocumentFile =
        parent.findFile(sanitizeTitle(title))?.takeIf { it.isDirectory } ?: createFolder(parent, title)

    /** Renames to [title], adding a numeric suffix on collision. Returns false if the provider refused. */
    fun rename(entry: DocumentFile, parent: DocumentFile, title: String): Boolean {
        val extension = if (entry.isDirectory) "" else NOTE_EXTENSION
        val wanted = sanitizeTitle(title) + extension
        if (entry.name == wanted) return true
        return entry.renameTo(uniqueName(parent, sanitizeTitle(title), extension))
    }

    fun delete(entry: DocumentFile): Boolean = entry.delete()

    private fun toEntry(file: DocumentFile): Entry? {
        val name = file.name ?: return null
        // Skip Obsidian's own config folders (.obsidian, .trash) and other hidden files.
        if (name.startsWith(".")) return null
        return when {
            file.isDirectory -> Entry(file, name, isFolder = true, modified = file.lastModified())
            name.endsWith(NOTE_EXTENSION, ignoreCase = true) ->
                Entry(file, name.dropLast(NOTE_EXTENSION.length), isFolder = false, modified = file.lastModified())
            else -> null
        }
    }

    private fun uniqueName(folder: DocumentFile, base: String, extension: String): String {
        var candidate = base + extension
        var n = 2
        while (folder.findFile(candidate) != null) candidate = "$base $n$extension".also { n++ }
        return candidate
    }

    companion object {
        const val NOTE_EXTENSION = ".md"

        fun SortChoice.comparator(): Comparator<Entry> = when (this) {
            SortChoice.NameAsc -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortChoice.NameDesc -> compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.title }
            SortChoice.DateNewest -> compareByDescending { it.modified }
            SortChoice.DateOldest -> compareBy { it.modified }
        }

        /**
         * Obsidian rejects `* " \ / < > : | ?` on some platforms and treats `# ^ [ ]`
         * as link syntax, so those become spaces.
         */
        fun sanitizeTitle(title: String): String =
            title.replace(Regex("""[\\/:*?"<>|#^\[\]]"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
                .trimStart('.')
                .take(120)
                .ifBlank { "Untitled" }
    }
}
