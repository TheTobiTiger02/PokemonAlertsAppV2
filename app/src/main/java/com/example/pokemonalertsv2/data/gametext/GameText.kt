package com.example.pokemonalertsv2.data.gametext

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.content.edit
import com.example.pokemonalertsv2.data.PokemonAlertsApi
import com.example.pokemonalertsv2.util.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import java.text.Normalizer

/**
 * Game names in one language, as the backend publishes them (`/api/game-text/<lang>`).
 * [names] is keyed by category and game id; [byEnglish] by category and the normalized
 * English name, for alerts that carry no id.
 */
@Serializable
data class GameTextDictionary(
    val lang: String,
    val version: String = "",
    val names: Map<String, Map<String, String>> = emptyMap(),
    val byEnglish: Map<String, Map<String, String>> = emptyMap()
)

interface GameTextService {
    @GET("api/game-text/{lang}")
    suspend fun getGameText(
        @Path("lang") lang: String,
        @Header("If-None-Match") etag: String? = null
    ): Response<GameTextDictionary>
}

/**
 * Keeps the dictionary for the language the app shows. A snapshot ships in the
 * APK (`assets/game_text_<lang>.json`), so German names work on the first start
 * and offline; a newer copy from the backend replaces it once downloaded.
 */
class GameTextRepository private constructor(private val context: Context) {
    private val preferences = context.getSharedPreferences("game_text_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var loaded: GameTextDictionary? = null

    /** The dictionary for the current language, or null when the app shows English. */
    fun current(): GameTextDictionary? {
        val lang = AppLanguage.displayLanguage()
        if (lang !in SUPPORTED) return null
        loaded?.takeIf { it.lang == lang }?.let { return it }
        val dictionary = cached(lang) ?: bundled(lang)
        loaded = dictionary
        return dictionary
    }

    private fun cached(lang: String): GameTextDictionary? = preferences.getString("dictionary_$lang", null)
        ?.let { raw -> runCatching { json.decodeFromString(GameTextDictionary.serializer(), raw) }.getOrNull() }

    private fun bundled(lang: String): GameTextDictionary? = runCatching {
        context.assets.open("game_text_$lang.json").bufferedReader().use { reader ->
            json.decodeFromString(GameTextDictionary.serializer(), reader.readText())
        }
    }.getOrNull()

    /** Fetches a newer dictionary for the current language in the background. */
    fun refreshAsync() {
        val lang = AppLanguage.displayLanguage()
        if (lang !in SUPPORTED) return
        // Loading the bundled copy here keeps the first lookup off the main thread.
        scope.launch { runCatching { current() }; runCatching { refresh(lang) } }
    }

    private suspend fun refresh(lang: String) {
        val response = service.getGameText(lang, preferences.getString("etag_$lang", null))
        val body = response.body() ?: return
        if (!response.isSuccessful || body.names.isEmpty()) return
        preferences.edit {
            putString("dictionary_$lang", json.encodeToString(GameTextDictionary.serializer(), body))
            putString("etag_$lang", response.headers()["ETag"])
        }
        loaded = body
    }

    companion object {
        private val SUPPORTED = setOf("de")
        private val service: GameTextService get() = PokemonAlertsApi.gameTextService

        // Holds only the application context, which lives as long as the process.
        @SuppressLint("StaticFieldLeak")
        @Volatile private var instance: GameTextRepository? = null

        fun get(context: Context): GameTextRepository = instance ?: synchronized(this) {
            instance ?: GameTextRepository(context.applicationContext).also { instance = it }
        }

        /** The dictionary in use, if the repository has been created (it is, in Application.onCreate). */
        internal fun dictionary(): GameTextDictionary? = instance?.current()
    }
}

/**
 * Display-only translation of game names. Every function returns its English
 * input unchanged when the app shows English, when no dictionary is loaded or
 * when the name is unknown, so callers can wrap any display text safely.
 * Never use the result as a filter value, a key or a stored identity: those
 * stay English.
 */
object GameNames {
    /** Tests and previews set this; the app reads the repository. */
    @Volatile internal var override: GameTextDictionary? = null

    private fun dictionary(): GameTextDictionary? = override ?: GameTextRepository.dictionary()

    fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9]"), "")

    private fun lookup(category: String, id: Int?, english: String?): String? {
        val text = dictionary() ?: return english
        id?.let { text.names[category]?.get(it.toString()) }?.let { return it }
        val name = english?.trim()?.takeIf { it.isNotEmpty() } ?: return english
        return text.byEnglish[category]?.get(normalize(name)) ?: english
    }

    fun pokemon(english: String?, dexId: Int? = null): String? {
        val name = english?.trim() ?: return lookup("pokemon", dexId, null)
        lookup("pokemon", null, name)?.takeIf { it != name }?.let { return it }
        // "Spinda 04", "Mega Charizard X", "Shadow Mewtwo": translate the species word and keep the rest.
        val dictionary = dictionary() ?: return name
        dexId?.let { id -> dictionary.names["pokemon"]?.get(id.toString()) }?.let { german ->
            val englishSpecies = dictionary.byEnglish["pokemon"]?.entries?.firstOrNull { it.value == german }?.key
            val words = name.split(" ")
            val index = words.indexOfFirst { normalize(it) == englishSpecies }
            if (index >= 0) return words.toMutableList().also { it[index] = german }.joinToString(" ")
        }
        val words = name.split(" ")
        val translated = words.map { word -> dictionary.byEnglish["pokemon"]?.get(normalize(word)) ?: word }
        return if (translated != words) translated.joinToString(" ") else name
    }

    fun form(english: String?, formId: Int? = null): String? = lookup("form", formId, english)
    fun move(english: String?, moveId: Int? = null): String? = lookup("move", moveId, english)
    fun item(english: String?, itemId: Int? = null): String? = lookup("item", itemId, english)
    fun type(english: String?): String? = lookup("type", null, english)

    /** Grunt names are often a type ("Fire") or a role ("Grunt (Female)"). */
    fun grunt(english: String?, gruntId: Int? = null): String? {
        lookup("grunt", gruntId, english)?.takeIf { it != english }?.let { return it }
        return lookup("type", null, english)
    }

    /**
     * A quest reward the backend only sent in English: "Petilil", "Great Ball ×5",
     * "500 Stardust". Pokémon and items are looked up; anything else stays as it is.
     */
    fun reward(english: String?): String? {
        val text = english?.trim()?.takeIf { it.isNotEmpty() } ?: return english
        if (dictionary() == null) return text
        val amount = Regex("""\s*×\d+$""").find(text)?.value.orEmpty()
        val name = text.removeSuffix(amount)
        val translated = lookup("item", null, name)?.takeIf { it != name }
            ?: pokemon(name)?.takeIf { it != name }
            ?: Regex("""^(\d+) (.+)$""").find(name)?.let { match ->
                lookup("reward", null, match.groupValues[2])?.takeIf { it != match.groupValues[2] }?.let { "${match.groupValues[1]} $it" }
            }
        return (translated ?: name) + amount
    }

    /** "Pokémon Alerts"-style move pairs: "Confusion / Psystrike". */
    fun moves(fast: String?, charged: String?, fastId: Int? = null, chargedId: Int? = null): Pair<String?, String?> =
        move(fast, fastId) to move(charged, chargedId)
}

/** The backend's rendering of this alert's sentences in the language the app shows, if any. */
fun com.example.pokemonalertsv2.data.PokemonAlert.translation(): com.example.pokemonalertsv2.data.AlertTranslation? =
    i18n?.get(AppLanguage.displayLanguage())

/** The species as the app shows it; [com.example.pokemonalertsv2.data.PokemonAlert.pokemon] stays English. */
val com.example.pokemonalertsv2.data.PokemonAlert.displayPokemon: String
    get() = GameNames.pokemon(pokemon ?: cleanPokemonName, pokedexId) ?: cleanPokemonName

val com.example.pokemonalertsv2.data.PokemonAlert.displayGrunt: String?
    get() = GameNames.grunt(gruntType, gruntTypeId)

val com.example.pokemonalertsv2.data.PokemonAlert.displayForm: String?
    get() = GameNames.form(pokemonForm, formId)

/** "Confusion / Psystrike" in the shown language. */
val com.example.pokemonalertsv2.data.PokemonAlert.displayMoves: String?
    get() {
        val (fast, charged) = GameNames.moves(moves?.fast, moves?.charged, moveIds?.fast, moveIds?.charged)
        return listOfNotNull(fast, charged).takeIf { it.isNotEmpty() }?.joinToString(" / ")
    }
