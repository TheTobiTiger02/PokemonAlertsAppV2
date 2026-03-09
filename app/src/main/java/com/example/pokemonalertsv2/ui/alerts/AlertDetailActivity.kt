package com.example.pokemonalertsv2.ui.alerts

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.pokemonalertsv2.data.HundoCP
import com.example.pokemonalertsv2.data.PokemonAlert
import com.example.pokemonalertsv2.data.PokemonMoves
import com.example.pokemonalertsv2.data.PokemonReward
import com.example.pokemonalertsv2.data.PvpRanking
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

class AlertDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val alert = intent?.toPokemonAlert() ?: run {
            finish()
            return
        }
        setContent {
            PokemonAlertsV2Theme {
                AlertDetailScreen(alert = alert)
            }
        }
    }

    private fun Intent.toPokemonAlert(): PokemonAlert? {
        val name = getStringExtra(EXTRA_ALERT_NAME) ?: return null
        val description = getStringExtra(EXTRA_ALERT_DESCRIPTION) ?: ""
        val imageUrl = getStringExtra(EXTRA_ALERT_IMAGE_URL)
        val thumbnailUrl = getStringExtra(EXTRA_ALERT_THUMBNAIL_URL)
        val latitude = getDoubleExtra(EXTRA_ALERT_LATITUDE, 0.0).takeIf { hasExtra(EXTRA_ALERT_LATITUDE) }
        val longitude = getDoubleExtra(EXTRA_ALERT_LONGITUDE, 0.0).takeIf { hasExtra(EXTRA_ALERT_LONGITUDE) }
        val endTime = getStringExtra(EXTRA_ALERT_END_TIME) ?: ""
        val typeJson = getStringExtra(EXTRA_ALERT_TYPE)
        val type = typeJson?.let {
            runCatching { json.decodeFromString<List<String>>(it) }.getOrNull()
        }

        // New structured fields
        val pokemon = getStringExtra(EXTRA_POKEMON)
        val pokemonForm = getStringExtra(EXTRA_POKEMON_FORM)
        val pokedexId = getIntExtra(EXTRA_POKEDEX_ID, -1).takeIf { it >= 0 }
        val iv = getStringExtra(EXTRA_IV)
        val ivAttack = getIntExtra(EXTRA_IV_ATTACK, -1).takeIf { it >= 0 }
        val ivDefense = getIntExtra(EXTRA_IV_DEFENSE, -1).takeIf { it >= 0 }
        val ivStamina = getIntExtra(EXTRA_IV_STAMINA, -1).takeIf { it >= 0 }
        val gender = getStringExtra(EXTRA_GENDER)
        val isShiny = getBooleanExtra(EXTRA_IS_SHINY, false).takeIf { hasExtra(EXTRA_IS_SHINY) }
        val cp = getIntExtra(EXTRA_CP, -1).takeIf { it >= 0 }
        val level = getIntExtra(EXTRA_LEVEL, -1).takeIf { it >= 0 }
        val isWeatherBoosted = getBooleanExtra(EXTRA_IS_WEATHER_BOOSTED, false).takeIf { hasExtra(EXTRA_IS_WEATHER_BOOSTED) }
        val currentWeather = getStringExtra(EXTRA_CURRENT_WEATHER)
        val pokemonLocation = getStringExtra(EXTRA_POKEMON_LOCATION)
        val gym = getStringExtra(EXTRA_GYM)
        val pokestop = getStringExtra(EXTRA_POKESTOP)
        val movesFast = getStringExtra(EXTRA_MOVES_FAST)
        val movesCharged = getStringExtra(EXTRA_MOVES_CHARGED)
        val moves = if (movesFast != null || movesCharged != null) {
            PokemonMoves(fast = movesFast, charged = movesCharged)
        } else null
        val hundoCPL20 = getIntExtra(EXTRA_HUNDO_CP_L20, -1).takeIf { it >= 0 }
        val hundoCPL25 = getIntExtra(EXTRA_HUNDO_CP_L25, -1).takeIf { it >= 0 }
        val hundoCP = if (hundoCPL20 != null || hundoCPL25 != null) {
            HundoCP(level20 = hundoCPL20, level25 = hundoCPL25)
        } else null
        val gruntType = getStringExtra(EXTRA_GRUNT_TYPE)
        val questTask = getStringExtra(EXTRA_QUEST_TASK)
        val questReward = getStringExtra(EXTRA_QUEST_REWARD)
        val requiresAR = getBooleanExtra(EXTRA_REQUIRES_AR, false).takeIf { hasExtra(EXTRA_REQUIRES_AR) }
        val createdAt = getStringExtra(EXTRA_CREATED_AT)
        
        // PvP Rankings (serialized as JSON)
        val pvpRankingsJson = getStringExtra(EXTRA_PVP_RANKINGS)
        val pvpRankings = pvpRankingsJson?.let {
            runCatching { json.decodeFromString<List<PvpRanking>>(it) }.getOrNull()
        }

        // Pokemon Rewards (serialized as JSON)
        val pokemonRewardsJson = getStringExtra(EXTRA_POKEMON_REWARDS)
        val pokemonRewards = pokemonRewardsJson?.let {
            runCatching { json.decodeFromString<List<PokemonReward>>(it) }.getOrNull()
        }

        return PokemonAlert(
            name = name,
            description = description,
            imageUrl = imageUrl,
            longitude = longitude,
            latitude = latitude,
            endTime = endTime,
            type = type,
            thumbnailUrl = thumbnailUrl,
            pokemon = pokemon,
            pokemonForm = pokemonForm,
            pokedexId = pokedexId,
            iv = iv,
            ivAttack = ivAttack,
            ivDefense = ivDefense,
            ivStamina = ivStamina,
            gender = gender,
            isShiny = isShiny,
            cp = cp,
            level = level,
            isWeatherBoosted = isWeatherBoosted,
            currentWeather = currentWeather,
            pokemonLocation = pokemonLocation,
            gym = gym,
            pokestop = pokestop,
            moves = moves,
            hundoCP = hundoCP,
            pvpRankings = pvpRankings,
            gruntType = gruntType,
            questTask = questTask,
            questReward = questReward,
            requiresAR = requiresAR,
            pokemonRewards = pokemonRewards,
            createdAt = createdAt
        )
    }

    companion object {
        private const val EXTRA_ALERT_NAME = "extra_alert_name"
        private const val EXTRA_ALERT_DESCRIPTION = "extra_alert_description"
        private const val EXTRA_ALERT_IMAGE_URL = "extra_alert_image"
        private const val EXTRA_ALERT_LATITUDE = "extra_alert_latitude"
        private const val EXTRA_ALERT_LONGITUDE = "extra_alert_longitude"
        private const val EXTRA_ALERT_END_TIME = "extra_alert_end_time"
        private const val EXTRA_ALERT_TYPE = "extra_alert_type"
        private const val EXTRA_ALERT_THUMBNAIL_URL = "extra_alert_thumbnail"
        
        // New structured field extras
        private const val EXTRA_POKEMON = "extra_pokemon"
        private const val EXTRA_POKEMON_FORM = "extra_pokemon_form"
        private const val EXTRA_POKEDEX_ID = "extra_pokedex_id"
        private const val EXTRA_IV = "extra_iv"
        private const val EXTRA_IV_ATTACK = "extra_iv_attack"
        private const val EXTRA_IV_DEFENSE = "extra_iv_defense"
        private const val EXTRA_IV_STAMINA = "extra_iv_stamina"
        private const val EXTRA_GENDER = "extra_gender"
        private const val EXTRA_IS_SHINY = "extra_is_shiny"
        private const val EXTRA_CP = "extra_cp"
        private const val EXTRA_LEVEL = "extra_level"
        private const val EXTRA_IS_WEATHER_BOOSTED = "extra_is_weather_boosted"
        private const val EXTRA_CURRENT_WEATHER = "extra_current_weather"
        private const val EXTRA_POKEMON_LOCATION = "extra_pokemon_location"
        private const val EXTRA_GYM = "extra_gym"
        private const val EXTRA_POKESTOP = "extra_pokestop"
        private const val EXTRA_MOVES_FAST = "extra_moves_fast"
        private const val EXTRA_MOVES_CHARGED = "extra_moves_charged"
        private const val EXTRA_HUNDO_CP_L20 = "extra_hundo_cp_l20"
        private const val EXTRA_HUNDO_CP_L25 = "extra_hundo_cp_l25"
        private const val EXTRA_GRUNT_TYPE = "extra_grunt_type"
        private const val EXTRA_QUEST_TASK = "extra_quest_task"
        private const val EXTRA_QUEST_REWARD = "extra_quest_reward"
        private const val EXTRA_REQUIRES_AR = "extra_requires_ar"
        private const val EXTRA_CREATED_AT = "extra_created_at"
        private const val EXTRA_PVP_RANKINGS = "extra_pvp_rankings"
        private const val EXTRA_POKEMON_REWARDS = "extra_pokemon_rewards"

        fun createIntent(context: Context, alert: PokemonAlert): Intent {
            return Intent(context, AlertDetailActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_ALERT_NAME, alert.name)
                putExtra(EXTRA_ALERT_DESCRIPTION, alert.description)
                putExtra(EXTRA_ALERT_IMAGE_URL, alert.imageUrl)
                alert.latitude?.let { putExtra(EXTRA_ALERT_LATITUDE, it) }
                alert.longitude?.let { putExtra(EXTRA_ALERT_LONGITUDE, it) }
                putExtra(EXTRA_ALERT_END_TIME, alert.endTime)
                alert.type?.let { types ->
                    if (types.isNotEmpty()) {
                        putExtra(EXTRA_ALERT_TYPE, Json.encodeToString(types))
                    }
                }
                putExtra(EXTRA_ALERT_THUMBNAIL_URL, alert.thumbnailUrl)
                
                // New structured fields
                putExtra(EXTRA_POKEMON, alert.pokemon)
                putExtra(EXTRA_POKEMON_FORM, alert.pokemonForm)
                alert.pokedexId?.let { putExtra(EXTRA_POKEDEX_ID, it) }
                putExtra(EXTRA_IV, alert.iv)
                alert.ivAttack?.let { putExtra(EXTRA_IV_ATTACK, it) }
                alert.ivDefense?.let { putExtra(EXTRA_IV_DEFENSE, it) }
                alert.ivStamina?.let { putExtra(EXTRA_IV_STAMINA, it) }
                putExtra(EXTRA_GENDER, alert.gender)
                alert.isShiny?.let { putExtra(EXTRA_IS_SHINY, it) }
                alert.cp?.let { putExtra(EXTRA_CP, it) }
                alert.level?.let { putExtra(EXTRA_LEVEL, it) }
                alert.isWeatherBoosted?.let { putExtra(EXTRA_IS_WEATHER_BOOSTED, it) }
                putExtra(EXTRA_CURRENT_WEATHER, alert.currentWeather)
                putExtra(EXTRA_POKEMON_LOCATION, alert.pokemonLocation)
                putExtra(EXTRA_GYM, alert.gym)
                putExtra(EXTRA_POKESTOP, alert.pokestop)
                putExtra(EXTRA_MOVES_FAST, alert.moves?.fast)
                putExtra(EXTRA_MOVES_CHARGED, alert.moves?.charged)
                alert.hundoCP?.level20?.let { putExtra(EXTRA_HUNDO_CP_L20, it) }
                alert.hundoCP?.level25?.let { putExtra(EXTRA_HUNDO_CP_L25, it) }
                putExtra(EXTRA_GRUNT_TYPE, alert.gruntType)
                putExtra(EXTRA_QUEST_TASK, alert.questTask)
                putExtra(EXTRA_QUEST_REWARD, alert.questReward)
                alert.requiresAR?.let { putExtra(EXTRA_REQUIRES_AR, it) }
                putExtra(EXTRA_CREATED_AT, alert.createdAt)
                alert.pvpRankings?.let { rankings ->
                    if (rankings.isNotEmpty()) {
                        putExtra(EXTRA_PVP_RANKINGS, Json.encodeToString(rankings))
                    }
                }
                alert.pokemonRewards?.let { rewards ->
                    if (rewards.isNotEmpty()) {
                        putExtra(EXTRA_POKEMON_REWARDS, Json.encodeToString(rewards))
                    }
                }
            }
        }
    }
}
