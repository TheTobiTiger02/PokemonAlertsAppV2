package com.example.pokemonalertsv2.data.counters

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

    @GET("raids")
    suspend fun getRaidCatalogue(): PokebattlerRaidsResponse
}
