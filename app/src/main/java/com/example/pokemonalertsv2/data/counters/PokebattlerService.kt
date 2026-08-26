package com.example.pokemonalertsv2.data.counters

import com.example.pokemonalertsv2.data.gamemaster.MasterfileResponse
import com.example.pokemonalertsv2.data.gamemaster.PokebattlerMovesResponse
import com.example.pokemonalertsv2.data.gamemaster.PokebattlerPokemonResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.QueryMap
import retrofit2.http.Url

interface PokebattlerService {

    /**
     * Counters for one raid boss.
     *
     * Takes the path as a [Url] rather than templated segments because the attacker part
     * varies in *shape*, not just value: `attackers/levels/40` today, `attackers/users/{id}`
     * once a Pokébox account can be linked. Build it with [PokebattlerUrls.countersPath].
     *
     * @param authorization future Pokébox token, sent as `Bearer: <token>` (Pokebattler
     *   really does include the colon). Retrofit omits the header entirely when null.
     */
    @GET
    suspend fun getCounters(
        @Url path: String,
        @QueryMap query: Map<String, String>,
        @Header("X-Authorization") authorization: String?
    ): PokebattlerCountersResponse

    /**
     * The signed-in account.
     *
     * The Pokébox path segment needs Pokébattler's internal user id, which is *not* the
     * in-game trainer number — `attackers/users/<trainer number>` 404s. This is the only
     * way to learn it, and a 200 here is also how a stored token is proven still valid.
     */
    @GET("secure/user")
    suspend fun getCurrentUser(
        @Header("X-Authorization") authorization: String
    ): PokebattlerUserResponse

    /**
     * The same account lookup against an explicit host.
     *
     * Sign-in happens on `user.pokebattler.com`, a different host from the `fight.` one
     * this client is based at. Both answer `/secure/user` with 401 when unauthenticated,
     * so which of them owns the account record is not observable from outside — the
     * repository tries the base host first and falls back here.
     */
    @GET
    suspend fun getCurrentUserAt(
        @Url url: String,
        @Header("X-Authorization") authorization: String
    ): PokebattlerUserResponse

    @GET("raids")
    suspend fun getRaidCatalogue(): PokebattlerRaidsResponse

    /** Base stats, types and legal movesets. About 400 KB gzipped. */
    @GET("pokemon")
    suspend fun getPokemon(): PokebattlerPokemonResponse

    /** Move power, duration and energy. About 13 KB. */
    @GET("moves")
    suspend fun getMoves(): PokebattlerMovesResponse

    /**
     * The UICONS masterfile, fetched by absolute URL from a different host.
     *
     * Only used to turn Pokebattler form names into the icon set numeric ids.
     */
    @GET
    suspend fun getMasterfile(@Url url: String): MasterfileResponse
}
