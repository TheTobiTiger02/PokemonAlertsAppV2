package com.example.pokemonalertsv2.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.PokemonSpeciesRepository
import com.example.pokemonalertsv2.data.database.PokemonSpeciesEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SpeciesSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val speciesRepository = PokemonSpeciesRepository.getInstance(application)
    private val alertPreferences = PokemonAlertsRepository.create(application).alertPreferences
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    
    // Configured via intent or initialization
    private val _alertType = MutableStateFlow("hundo")
    
    val speciesList: StateFlow<List<PokemonSpeciesEntity>> = _searchQuery
        .flatMapLatest { query -> speciesRepository.searchSpecies(query) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allowedSpecies: StateFlow<Set<String>> = _alertType
        .flatMapLatest { type ->
            when (type.lowercase()) {
                "hundo" -> alertPreferences.allowedHundoSpecies
                "nundo" -> alertPreferences.allowedNundoSpecies
                "pvp" -> alertPreferences.allowedPvpSpecies
                "spawn", "rare" -> alertPreferences.allowedSpawnSpecies
                else -> alertPreferences.allowedHundoSpecies // Default fallback
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
        
    fun setAlertType(type: String) {
        _alertType.value = type
    }
        
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    fun toggleSpecies(speciesName: String) {
        viewModelScope.launch {
            val currentSet = allowedSpecies.value.toMutableSet()
            if (currentSet.isEmpty()) {
                // If it's currently empty, it means "all are allowed". We are unselecting ONE species.
                // So we need to put ALL species in the allowed set EXCEPT the clicked one.
                val allSpecies = speciesRepository.getAllSpeciesNames().map { it.lowercase() }
                currentSet.addAll(allSpecies)
                currentSet.remove(speciesName.lowercase())
            } else {
                if (currentSet.contains(speciesName.lowercase())) {
                    currentSet.remove(speciesName.lowercase())
                    if (currentSet.isEmpty()) {
                        // If we removed the last one, an empty set means "all allowed" in our logic.
                        // To prevent this meaning inversion, we add an invalid item.
                        currentSet.add("_none_")
                    }
                } else {
                    currentSet.remove("_none_")
                    currentSet.add(speciesName.lowercase())
                }
            }
            saveSelection(currentSet)
        }
    }
    
    fun selectAll() {
        // Here we'd add all currently filtered items, or perhaps clear the set if "Select All" means "empty set = everything allowed"
        // By design, an empty set means EVERYTHING is allowed.
        saveSelection(emptySet())
    }
    
    fun clearSelection() {
        // Technically an empty set means everything is allowed according to our alert filtering logic.
        // So "Clearing" might mean leaving an impossible-to-match string if we want to truly silence it, or a fake "_none_" element.
        // Actually, if the set has "_none_", it drops. Let's just put an arbitrary invalid species to block all.
        saveSelection(setOf("_none_"))
    }
    
    private fun saveSelection(newSet: Set<String>) {
        viewModelScope.launch {
            when (_alertType.value.lowercase()) {
                "hundo" -> alertPreferences.updateAllowedHundoSpecies(newSet)
                "nundo" -> alertPreferences.updateAllowedNundoSpecies(newSet)
                "pvp" -> alertPreferences.updateAllowedPvpSpecies(newSet)
                "spawn", "rare" -> alertPreferences.updateAllowedSpawnSpecies(newSet)
            }
        }
    }
}
