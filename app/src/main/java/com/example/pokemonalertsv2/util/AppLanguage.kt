package com.example.pokemonalertsv2.util

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import com.example.pokemonalertsv2.R
import java.util.Locale

/**
 * The app's own language, independent of the phone's.
 *
 * Android 13 and newer keep a per-app language for us ([LocaleManager]): it is
 * stored by the system, applies to every context of the process (activities,
 * services, widgets) and recreates the open activities. Older versions have no
 * such setting, so there the choice is hidden and the app follows the phone.
 * `res/xml/locales_config.xml` lists the languages the system may offer.
 */
object AppLanguage {
    enum class Choice(val tag: String?, @StringRes private val labelRes: Int) {
        SYSTEM(null, R.string.language_system),
        ENGLISH("en", R.string.language_english),
        GERMAN("de", R.string.language_german);

        val label: String get() = AppText.get(labelRes)
    }

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun current(context: Context): Choice {
        if (!isSupported) return Choice.SYSTEM
        val locales = context.getSystemService(LocaleManager::class.java)?.applicationLocales
        if (locales == null || locales.isEmpty) return Choice.SYSTEM
        val language = locales[0].language
        return Choice.entries.firstOrNull { it.tag == language } ?: Choice.SYSTEM
    }

    fun set(context: Context, choice: Choice) {
        if (!isSupported) return
        context.getSystemService(LocaleManager::class.java)?.applicationLocales =
            choice.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
    }

    /** The language the app is showing right now: the per-app one, or the phone's. */
    fun displayLanguage(): String = Locale.getDefault().language
}
