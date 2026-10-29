package app.writerobsidian.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The editor fonts of Writer 4.7.0 (its FontType enum), in the order and with the sample
 * words of its Customize grid. [Default] is the system font.
 */
enum class FontChoice(val sample: String = "Hello") {
    Default, RobotoMono, Almarai("مرحبًا"), BeVietnam("Xin chào"), Arimo("你好"), Monoton, Montaga,
    XanhMono, AguafinaScript, Cantarell, Caveat, Courgette, Gabriela, GreatVibes, IndieFlower,
    KalamLight, Lobster, Pattaya, PetitFormalScript, RajdhaniLight, Roboto, Sacramento,
    ShadowsIntoLight, Timmana, Yellowtail,
}

/** The original's seven editor backgrounds (Settings.theme 1–7), in its Customize order. */
enum class ThemeChoice(val label: String) { Default("White"), Dark("Dark"), Black("Black"), Bluer("Deep blue"), Paper("Paper"), Pink("Pink"), Blue("Blue") }

/** The original's Sort dialog choices. */
enum class SortChoice(val label: String) { NameAsc("Name (a-z)"), NameDesc("Name (z-a)"), DateNewest("Date (newest)"), DateOldest("Date (oldest)") }

data class Prefs(
    val vaultFolder: Uri? = null,
    val fontSize: Int = DEFAULT_FONT_SIZE,
    val font: FontChoice = FontChoice.RobotoMono,
    val theme: ThemeChoice = ThemeChoice.Default,
    /** Editor text color as ARGB; null follows the background. */
    val textColor: Int? = null,
    /** Day/Night Mode for the app's screens; null follows the system. */
    val night: Boolean? = null,
    val sort: SortChoice = SortChoice.DateNewest,
    val readOnly: Boolean = false,
) {
    companion object {
        // The original app's defaults and its font-size slider range.
        const val DEFAULT_FONT_SIZE = 15
        val FONT_SIZES = 10..50
    }
}

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val vaultFolder = stringPreferencesKey("vault_folder")
        val fontSize = intPreferencesKey("font_size")
        val font = stringPreferencesKey("font")
        val theme = stringPreferencesKey("theme")
        val textColor = intPreferencesKey("text_color")
        val night = booleanPreferencesKey("night")
        val sort = stringPreferencesKey("sort")
        val readOnly = booleanPreferencesKey("read_only")
    }

    val prefs: Flow<Prefs> = context.dataStore.data.map { p ->
        Prefs(
            vaultFolder = p[Keys.vaultFolder]?.let(Uri::parse),
            fontSize = (p[Keys.fontSize] ?: Prefs.DEFAULT_FONT_SIZE).coerceIn(Prefs.FONT_SIZES),
            font = enumOrDefault(p[Keys.font], FontChoice.RobotoMono),
            theme = enumOrDefault(p[Keys.theme], ThemeChoice.Default),
            textColor = p[Keys.textColor],
            night = p[Keys.night],
            sort = enumOrDefault(p[Keys.sort], SortChoice.DateNewest),
            readOnly = p[Keys.readOnly] ?: false,
        )
    }

    suspend fun setVaultFolder(uri: Uri?) = context.dataStore.edit {
        if (uri == null) it.remove(Keys.vaultFolder) else it[Keys.vaultFolder] = uri.toString()
    }

    suspend fun setFontSize(size: Int) = context.dataStore.edit { it[Keys.fontSize] = size }

    suspend fun setFont(font: FontChoice) = context.dataStore.edit { it[Keys.font] = font.name }

    suspend fun setTheme(theme: ThemeChoice) = context.dataStore.edit { it[Keys.theme] = theme.name }

    suspend fun setTextColor(color: Int?) = context.dataStore.edit {
        if (color == null) it.remove(Keys.textColor) else it[Keys.textColor] = color
    }

    suspend fun setNight(night: Boolean) = context.dataStore.edit { it[Keys.night] = night }

    suspend fun setSort(sort: SortChoice) = context.dataStore.edit { it[Keys.sort] = sort.name }

    suspend fun setReadOnly(readOnly: Boolean) = context.dataStore.edit { it[Keys.readOnly] = readOnly }
}

private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default
