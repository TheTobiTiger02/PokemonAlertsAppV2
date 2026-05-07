package com.example.pokemonalertsv2.data

import android.content.Context
import com.example.pokemonalertsv2.data.api.PokeApiService
import com.example.pokemonalertsv2.data.database.AppDatabase
import com.example.pokemonalertsv2.data.database.PokemonSpeciesEntity
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit

class PokemonSpeciesRepository private constructor(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val speciesDao = database.pokemonSpeciesDao()

    private val json = Json { ignoreUnknownKeys = true }
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://pokeapi.co/")
        .client(OkHttpClient.Builder().build())
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api = retrofit.create(PokeApiService::class.java)

    suspend fun syncIfNeeded() {
        withContext(Dispatchers.IO) {
            if (speciesDao.getCount() == 0) {
                try {
                    val response = api.getPokemonList()
                    val entities = response.results.map {
                        PokemonSpeciesEntity(
                            id = it.id,
                            name = it.name.replaceFirstChar { char -> char.uppercase() },
                            imageUrl = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${it.id}.png"
                        )
                    }
                    speciesDao.insertAll(entities)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun searchSpecies(query: String): Flow<List<PokemonSpeciesEntity>> {
        return if (query.isBlank()) {
            speciesDao.getAllSpecies()
        } else {
            speciesDao.searchSpecies(query)
        }
    }

    suspend fun getAllSpeciesNames(): List<String> {
        return withContext(Dispatchers.IO) {
            speciesDao.getAllSpeciesNames()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PokemonSpeciesRepository? = null

        fun getInstance(context: Context): PokemonSpeciesRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = PokemonSpeciesRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
