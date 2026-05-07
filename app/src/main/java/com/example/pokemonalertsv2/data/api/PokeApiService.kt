package com.example.pokemonalertsv2.data.api

import retrofit2.http.GET
import retrofit2.http.Query

interface PokeApiService {
    @GET("api/v2/pokemon")
    suspend fun getPokemonList(
        @Query("limit") limit: Int = 1500
    ): PokemonListResponse
}
