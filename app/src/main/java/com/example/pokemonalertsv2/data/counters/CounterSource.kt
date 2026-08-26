package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.pokegenie.PokeGenieRepository

/**
 * Where the counter list comes from, and what "mine" means.
 *
 * The two members that matter are [attackerSpec], which feeds both the request path and
 * the cache key, and [decorate]. Between them they are the whole seam that lets a
 * Pokébox-backed source drop in later without the repository, DTOs, ViewModel or UI
 * changing.
 */
interface CounterSource {
    val id: CounterSourceId

    /** Which Pokémon Pokebattler should simulate. */
    suspend fun attackerSpec(options: RaidCounterOptions): AttackerSpec

    /** False means "not set up"; the UI shows an empty state instead of firing a request. */
    suspend fun isAvailable(): Boolean

    /** Auth header value, or null when the source needs no account. */
    suspend fun authorizationHeader(): String? = null

    suspend fun decorate(counters: List<RaidCounter>): List<DecoratedCounter>
}

/** The default: Pokebattler's generic level-N ranking. */
class AllPokemonSource : CounterSource {
    override val id = CounterSourceId.ALL_POKEMON
    override suspend fun attackerSpec(options: RaidCounterOptions) =
        AttackerSpec.Level(options.attackerLevel)
    override suspend fun isAvailable() = true
    override suspend fun decorate(counters: List<RaidCounter>) = counters.map { DecoratedCounter(it) }
}

/**
 * The same ranking, annotated with what the user actually owns.
 *
 * Note the attacker spec is still a plain level. A CSV cannot change a server-side
 * simulation, so this filters and annotates a level-N ranking rather than re-ranking
 * against the user's real Pokémon. The UI must say so plainly; closing that gap is what
 * the Pokébox source is for.
 */
class PokeGenieSource(private val repository: PokeGenieRepository) : CounterSource {
    override val id = CounterSourceId.POKE_GENIE

    override suspend fun attackerSpec(options: RaidCounterOptions) =
        AttackerSpec.Level(options.attackerLevel)

    override suspend fun isAvailable(): Boolean = repository.count() > 0

    override suspend fun decorate(counters: List<RaidCounter>): List<DecoratedCounter> {
        val index = repository.index()
        return counters.map { DecoratedCounter(it, index.bestOwned(it.pokemonId)) }
    }
}

/**
 * The signed-in user's Pokébox, ranked server-side with their real IVs and movesets.
 *
 * [userId] is Pokébattler's internal account id from `GET /secure/user`, *not* the in-game
 * trainer number — `attackers/users/<trainer number>` 404s, and the endpoint is not public
 * in any case, which is why [authorizationHeader] must carry the session token.
 */
class PokebattlerPokeBoxSource(
    private val userId: String,
    private val authorization: String?
) : CounterSource {
    override val id = CounterSourceId.POKEBATTLER_POKEBOX

    override suspend fun attackerSpec(options: RaidCounterOptions) =
        AttackerSpec.PokebattlerUser(userId)

    override suspend fun isAvailable() = userId.isNotBlank() && !authorization.isNullOrBlank()

    override suspend fun authorizationHeader(): String? = authorization

    override suspend fun decorate(counters: List<RaidCounter>) = counters.map { DecoratedCounter(it) }
}
