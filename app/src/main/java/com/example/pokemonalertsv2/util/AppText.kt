package com.example.pokemonalertsv2.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.annotation.StringRes
import com.example.pokemonalertsv2.R
import java.io.File

/**
 * String resources for code that has no Context or composition at hand: enum labels, formatters,
 * status messages. Composables use stringResource instead.
 *
 * The application context follows the per-app language, so this reads the user's language. In
 * JVM unit tests there is no context; there the English values/ XML files are read directly, so
 * tests keep asserting the English text they always did.
 */
@SuppressLint("StaticFieldLeak")
object AppText {
    // Only ever the application context (or the Application's own base context), which lives as
    // long as the process, so holding it statically leaks nothing.
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var context: Context? = null

    fun init(context: Context) {
        this.context = context.applicationContext ?: context
    }

    fun get(@StringRes id: Int, vararg args: Any?): String {
        context?.let { return it.getString(id, *args) }
        val pattern = englishFallback(id) ?: return ""
        return if (args.isEmpty()) pattern else String.format(pattern, *args)
    }

    private val fallback: Map<String, String> by lazy {
        val dir = listOf("src/main/res/values", "app/src/main/res/values").map(::File).firstOrNull { it.isDirectory }
            ?: return@lazy emptyMap()
        val entry = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
        dir.listFiles { file -> file.extension == "xml" }.orEmpty().flatMap { file ->
            entry.findAll(file.readText()).map { it.groupValues[1] to unescape(it.groupValues[2]) }.toList()
        }.toMap()
    }

    private fun englishFallback(id: Int): String? {
        val name = runCatching {
            R.string::class.java.fields.firstOrNull { it.getInt(null) == id }?.name
        }.getOrNull() ?: return null
        return fallback[name]
    }

    private fun unescape(value: String): String = value
        .replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
        .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
}
