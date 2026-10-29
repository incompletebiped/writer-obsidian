package app.writerobsidian.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File

data class ImportResult(val notes: Int, val folders: Int, val skipped: Int)

/**
 * Converts a backup from the original Writer app (app.Xeasec.writer) into Markdown
 * files. Its local backups are verbatim copies of its SQLite database, written to
 * `/WriterApp/Backup/WriterDb_<timestamp>.db`.
 *
 * Schema (from the original app's Database class):
 * - `Items`: type 1 = folder, type 2 = document; `categoryId` is the parent folder id,
 *   0 for the top level. Writer 4.7 adds `path` and nested folders.
 * - `Texts`: one row per document, `fileId` → `Items.id`, `context` holds the body
 *   as the rich-text editor's HTML, converted here by [HtmlToMarkdown].
 *
 * Per-document passwords (`Customize.pass`) are not carried over: the result is
 * plain files in the vault.
 */
class WriterImport(private val context: Context, private val vault: Vault) {

    fun import(backup: Uri, target: DocumentFile): ImportResult {
        val copy = File.createTempFile("writer-import", ".db", context.cacheDir)
        try {
            context.contentResolver.openInputStream(backup)?.use { input ->
                copy.outputStream().use { input.copyTo(it) }
            } ?: error("Cannot read the selected file")
            return SQLiteDatabase.openDatabase(copy.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                require(db.hasTable("Items") && db.hasTable("Texts")) {
                    "This file is not a Writer backup (no Items/Texts tables)"
                }
                convert(db, target)
            }
        } finally {
            copy.delete()
        }
    }

    private fun convert(db: SQLiteDatabase, target: DocumentFile): ImportResult {
        // Writer 4.7 nests folders: a folder's parent is its categoryId, and its `path`
        // ("0/12/34/") lists its ancestors. 3.0.x has no path and only top-level folders.
        val hasPath = db.hasColumn("Items", "path")
        data class FolderRow(val name: String, val parent: Int)
        val rows = linkedMapOf<Int, FolderRow>()
        val folderQuery = "select id, name, categoryId" + (if (hasPath) ", path" else "") + " from Items where type = 1 order by id"
        db.rawQuery(folderQuery, null).use { c ->
            while (c.moveToNext()) {
                val fromPath = if (hasPath) parentFromPath(c.getString(3), c.getInt(0)) else 0
                val parent = c.getInt(2).takeIf { it != 0 } ?: fromPath
                rows[c.getInt(0)] = FolderRow(c.getString(1).orEmpty(), parent)
            }
        }
        val folders = mutableMapOf<Int, DocumentFile>()
        fun folderFor(id: Int, seen: Set<Int> = emptySet()): DocumentFile {
            folders[id]?.let { return it }
            val row = rows[id] ?: return target
            // A missing or cyclic parent puts the folder at the top level.
            val parent = if (row.parent == 0 || row.parent in seen || row.parent !in rows) target else folderFor(row.parent, seen + id)
            return vault.createFolder(parent, row.name).also { folders[id] = it }
        }
        rows.keys.forEach { folderFor(it) }

        // The storage framework can't set a file's date, so notes are written oldest-edited
        // first: their new modification times then sort in the original order.
        var notes = 0
        var skipped = 0
        val query = """
            select i.categoryId, i.name, t.context
            from Items i left join Texts t on t.fileId = i.id
            where i.type = 2
            order by max(coalesce(i.datetime, ''), coalesce(t.datetime, '')), i.id, t.id
        """.trimIndent()
        db.rawQuery(query, null).use { c ->
            while (c.moveToNext()) {
                val title = c.getString(1).orEmpty()
                val body = HtmlToMarkdown.convert(c.getString(2).orEmpty())
                if (title.isBlank() && body.isBlank()) {
                    skipped++
                    continue
                }
                // A document in a folder that no longer exists lands at the top level.
                val parent = folders[c.getInt(0)] ?: target
                vault.createNote(parent, title, body)
                notes++
            }
        }
        return ImportResult(notes = notes, folders = folders.size, skipped = skipped)
    }

    private fun SQLiteDatabase.hasColumn(table: String, column: String): Boolean =
        rawQuery("pragma table_info($table)", null).use { c ->
            generateSequence { if (c.moveToNext()) c.getString(1) else null }.any { it.equals(column, ignoreCase = true) }
        }

    /** The last folder id in a 4.7 path such as "0/12/34/", ignoring the item itself. */
    private fun parentFromPath(path: String?, self: Int): Int =
        path.orEmpty().split('/').mapNotNull { it.toIntOrNull() }.filter { it != 0 && it != self }.lastOrNull() ?: 0

    private fun SQLiteDatabase.hasTable(name: String): Boolean =
        rawQuery("select 1 from sqlite_master where type = 'table' and name = ?", arrayOf(name))
            .use { it.moveToFirst() }
}
