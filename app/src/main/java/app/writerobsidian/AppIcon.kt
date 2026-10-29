package app.writerobsidian

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The launcher icon, chosen by enabling one of the manifest's activity aliases. The
 * enabled alias is the stored choice, so there is no separate setting to keep in sync.
 */
enum class AppIcon(val label: String, private val alias: String) {
    Original("Original", "LauncherOriginal"),
    Dark("Dark", "LauncherDark");

    private fun component(context: Context) = ComponentName(context, "${context.packageName}.$alias")

    companion object {
        fun current(context: Context): AppIcon {
            val pm = context.packageManager
            return entries.firstOrNull {
                pm.getComponentEnabledSetting(it.component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } ?: Original // Neither changed yet: the manifest default.
        }

        fun set(context: Context, icon: AppIcon) {
            val pm = context.packageManager
            // Enable the new alias first so the app always has a launcher entry.
            for (it in entries.sortedBy { it != icon }) {
                val state = if (it == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                pm.setComponentEnabledSetting(it.component(context), state, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
