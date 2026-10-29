package app.writerobsidian

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.writerobsidian.data.Entry
import app.writerobsidian.data.FontChoice
import app.writerobsidian.data.MarkdownCodec
import app.writerobsidian.data.Prefs
import app.writerobsidian.data.RichText
import app.writerobsidian.data.SettingsStore
import app.writerobsidian.data.SortChoice
import app.writerobsidian.data.ThemeChoice
import app.writerobsidian.data.Vault
import app.writerobsidian.data.WriterImport
import app.writerobsidian.data.backUpVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The original app's screens. */
enum class Screen { Browser, Search, Editor, Print, Customize, Backup, Settings }

/**
 * A note open in the editor. [savedMarkdown] is what is on disk. The editor view owns
 * the live text and supplies it through [content]; [edited] is set on any user change,
 * so opening a note and leaving never rewrites the file.
 */
class EditorState(val note: DocumentFile, val title: String, var initial: RichText, var savedMarkdown: String) {
    var content: (() -> RichText)? = null
    var edited = false
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    val prefs: StateFlow<Prefs?> = store.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var vault by mutableStateOf<Vault?>(null)
        private set

    /** Screens opened on top of the note list; the last one is showing. */
    private val screens = mutableStateListOf<Screen>()
    val screen: Screen get() = screens.lastOrNull() ?: Screen.Browser

    /** Folders opened below the vault root; empty means the root. */
    val folderStack = mutableStateListOf<DocumentFile>()
    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    var query by mutableStateOf("")
        private set
    private var allNotes by mutableStateOf<List<Entry>>(emptyList())
    /** The Search screen lists every note, oldest first, filtered by title as in the original. */
    val searchResults: List<Entry>
        get() = allNotes.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }

    var editor by mutableStateOf<EditorState?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    private var listJob: Job? = null
    private val saveLock = Mutex()

    val currentFolder: DocumentFile? get() = folderStack.lastOrNull() ?: vault?.root

    /** The original's folder title: `root/Folder/Sub/`. */
    val folderPath: String get() = "root/" + folderStack.joinToString("") { it.name.orEmpty() + "/" }

    /** Opens the persisted vault folder once settings load, if access is still granted. */
    fun restoreVault(uri: Uri?) {
        if (uri == null || vault?.root?.uri == uri) return
        val granted = getApplication<Application>().contentResolver.persistedUriPermissions
            .any { it.uri == uri && it.isReadPermission && it.isWritePermission }
        val candidate = if (granted) runCatching { Vault(getApplication(), uri) }.getOrNull() else null
        if (candidate?.isAccessible() == true) {
            vault = candidate
            refresh()
        } else {
            viewModelScope.launch { store.setVaultFolder(null) }
        }
    }

    fun chooseVaultFolder(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        // Release the previous folder so grants don't pile up (the system caps them).
        vault?.root?.uri?.takeIf { it != uri }?.let { old ->
            runCatching { resolver.releasePersistableUriPermission(old, flags) }
        }
        resolver.takePersistableUriPermission(uri, flags)
        folderStack.clear()
        screens.clear()
        vault = Vault(getApplication(), uri)
        viewModelScope.launch { store.setVaultFolder(uri) }
        refresh()
    }

    fun refresh(sort: SortChoice = prefs.value?.sort ?: SortChoice.DateNewest) {
        val v = vault ?: return
        val folder = currentFolder ?: return
        listJob?.cancel()
        listJob = viewModelScope.launch {
            loading = true
            entries = io { v.list(folder, sort) }
                .onFailure { message = "Couldn't read the folder: ${it.message}" }
                .getOrDefault(emptyList())
            loading = false
        }
    }

    fun open(target: Screen) {
        if (target == Screen.Search) {
            query = ""
            val v = vault ?: return
            viewModelScope.launch {
                allNotes = io { v.allNotes(v.root).sortedBy { it.modified } }.getOrDefault(emptyList())
            }
        }
        screens += target
    }

    fun search(text: String) {
        query = text
    }

    fun openFolder(folder: DocumentFile) {
        folderStack += folder
        refresh()
    }

    /** Handles system back. Returns false when there is nothing left to go back to. */
    fun back(): Boolean = when {
        screen == Screen.Editor -> { closeEditor(); true }
        screens.isNotEmpty() -> { screens.removeAt(screens.lastIndex); true }
        folderStack.isNotEmpty() -> { folderStack.removeAt(folderStack.lastIndex); refresh(); true }
        else -> false
    }

    fun openNote(entry: Entry) {
        val v = vault ?: return
        viewModelScope.launch {
            io { v.read(entry.file).let { it to MarkdownCodec.parse(it) } }
                .onSuccess { (markdown, doc) ->
                    editor = EditorState(entry.file, entry.title, doc, markdown)
                    screens += Screen.Editor
                }
                .onFailure { message = "Couldn't open ${entry.title}: ${it.message}" }
        }
    }

    /** The original's add dialog: a note (opened right away) or, with "Folder" ticked, a folder. */
    fun newItem(name: String, folder: Boolean) {
        if (folder) {
            mutate("create the folder") { v, parent -> v.createFolder(parent, name) }
            return
        }
        val v = vault ?: return
        val parent = currentFolder ?: return
        viewModelScope.launch {
            io { v.createNote(parent, name) }
                .onSuccess { note ->
                    val title = note.name.orEmpty().removeSuffix(Vault.NOTE_EXTENSION)
                    editor = EditorState(note, title, RichText(""), "")
                    screens += Screen.Editor
                }
                .onFailure { message = "Couldn't create a note: ${it.message}" }
        }
    }

    fun rename(entry: Entry, name: String) = mutate("rename ${entry.title}") { v, folder ->
        check(v.rename(entry.file, entry.file.parentFile ?: folder, name)) { "the storage provider refused" }
    }

    fun delete(entry: Entry) = mutate("delete ${entry.title}") { v, _ ->
        check(v.delete(entry.file)) { "the storage provider refused" }
    }

    /** Writes pending edits; called shortly after typing stops and when the app is backgrounded. */
    fun save() {
        val state = editor ?: return
        viewModelScope.launch { persist(state) }
    }

    /** Returns true when the note was written. */
    private suspend fun persist(state: EditorState): Boolean = saveLock.withLock {
        val v = vault ?: return@withLock false
        if (!state.edited) return@withLock false
        val doc = state.content?.invoke() ?: return@withLock false
        state.edited = false
        val markdown = MarkdownCodec.serialize(doc)
        if (markdown == state.savedMarkdown) return@withLock false
        io { v.write(state.note, markdown) }
            .onSuccess { state.savedMarkdown = markdown }
            .onFailure {
                state.edited = true
                message = "Couldn't save: ${it.message}"
            }
            .isSuccess
    }

    /** Leaving the editor saves and says "Saved.", as the original did on back. */
    private fun closeEditor() {
        val state = editor ?: return
        val hasText = state.content?.invoke()?.text?.isNotEmpty() ?: false
        viewModelScope.launch {
            persist(state)
            if (hasText) Toast.makeText(getApplication(), "Saved.", Toast.LENGTH_SHORT).show()
            state.content = null
            refresh()
        }
        editor = null
        screens.removeAt(screens.lastIndex)
    }

    /** Set when Print was opened from the editor: that note alone is printed. */
    private var printSingle: Pair<String, RichText>? = null

    fun printNote(title: String, doc: RichText) {
        printSingle = title to doc
        screens += Screen.Print
    }

    /** Print opened from the list prints every note in the current folder, like the original. */
    fun openPrintFolder() {
        printSingle = null
        screens += Screen.Print
    }

    val printTitle: String get() = printSingle?.first ?: folderPath

    suspend fun notesToPrint(): List<Pair<String, RichText>> {
        printSingle?.let { return listOf(it) }
        val v = vault ?: return emptyList()
        val folder = currentFolder ?: return emptyList()
        return io {
            v.list(folder, prefs.value?.sort ?: SortChoice.DateNewest)
                .filter { !it.isFolder }
                .map { it.title to MarkdownCodec.parse(v.read(it.file)) }
        }.getOrDefault(emptyList())
    }

    /** Imports into the vault folder itself; folders that already exist there are reused. */
    fun importWriterBackup(backup: Uri) = mutate("import the backup") { v, _ ->
        val result = WriterImport(getApplication(), v).import(backup, v.root)
        message = "Imported ${result.notes} notes and ${result.folders} folders" +
            if (result.skipped > 0) " (skipped ${result.skipped} empty)" else ""
    }

    fun backUp(target: Uri) = mutate("back up") { v, _ ->
        val count = backUpVault(getApplication(), v, target)
        message = "Backed up $count notes"
    }

    fun setFontSize(size: Int) = viewModelScope.launch { store.setFontSize(size) }
    fun setFont(font: FontChoice) = viewModelScope.launch { store.setFont(font) }
    fun setTheme(theme: ThemeChoice) = viewModelScope.launch { store.setTheme(theme) }
    fun setTextColor(color: Int?) = viewModelScope.launch { store.setTextColor(color) }
    fun setNight(night: Boolean) = viewModelScope.launch { store.setNight(night) }
    fun setReadOnly(readOnly: Boolean) = viewModelScope.launch { store.setReadOnly(readOnly) }
    fun setSort(sort: SortChoice) {
        refresh(sort)
        viewModelScope.launch { store.setSort(sort) }
    }

    private fun mutate(what: String, action: (Vault, DocumentFile) -> Unit) {
        val v = vault ?: return
        val folder = currentFolder ?: return
        viewModelScope.launch {
            loading = true
            io { action(v, folder) }.onFailure { message = "Couldn't $what: ${it.message}" }
            loading = false
            refresh()
        }
    }

    private suspend fun <T> io(block: () -> T): Result<T> =
        withContext(Dispatchers.IO) { runCatching(block) }
}
