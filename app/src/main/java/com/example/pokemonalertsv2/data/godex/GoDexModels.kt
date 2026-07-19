package com.example.pokemonalertsv2.data.godex

data class GoDexConfig(
    val url: String = "",
    val collectionTitle: String = "",
    val lastSuccessfulSyncMillis: Long = 0L,
    val notificationFilterEnabled: Boolean = false,
    val sessionCookies: String = "",
    val writeBackUrl: String = ""
) {
    val isConnected: Boolean get() = url.isNotBlank() || writeBackUrl.isNotBlank()
    val hasSession: Boolean get() = sessionCookies.isNotBlank()
    val hasWriteBackUrl: Boolean get() = writeBackUrl.isNotBlank()
}

data class GoDexSyncUiState(
    val isSyncing: Boolean = false,
    val errorMessage: String? = null
)

enum class GoDexMatchStatus {
    NEEDED,
    EVOLUTION_NEEDED,
    FORM_CHANGE_NEEDED,
    EVOLUTION_AND_FORM_CHANGE_NEEDED,
    COLLECTED,
    UNKNOWN,
    NOT_CONFIGURED
}

data class GoDexEvolutionTarget(
    val entryKey: String,
    val pokedexId: Int,
    val displayName: String,
    val distance: Int
)

data class GoDexFormChangeTarget(
    val entryKey: String,
    val pokedexId: Int,
    val displayName: String,
    val distance: Int
)

data class GoDexMatchResult(
    val status: GoDexMatchStatus,
    val matchedEntryKey: String? = null,
    val evolutionTargets: List<GoDexEvolutionTarget> = emptyList(),
    val formChangeTargets: List<GoDexFormChangeTarget> = emptyList()
) {
    val compactEvolutionLabel: String?
        get() = evolutionTargets.firstOrNull()?.let { first ->
            if (evolutionTargets.size == 1) first.displayName
            else "${first.displayName} +${evolutionTargets.size - 1}"
        }

    val compactFormChangeLabel: String?
        get() = formChangeTargets.firstOrNull()?.let { first ->
            if (formChangeTargets.size == 1) first.displayName
            else "${first.displayName} +${formChangeTargets.size - 1}"
        }
}

data class GoDexDebugEntry(
    val entryKey: String,
    val pokedexId: Int,
    val displayName: String,
    val formSlug: String?,
    val gender: String,
    val result: GoDexMatchResult
) {
    val statusLabel: String
        get() = when (result.status) {
            GoDexMatchStatus.NEEDED -> "Needed in GoDex"
            GoDexMatchStatus.EVOLUTION_NEEDED ->
                "Collected \u2022 Evolution needed: ${result.compactEvolutionLabel ?: "evolution"}"
            GoDexMatchStatus.FORM_CHANGE_NEEDED ->
                "Collected \u2022 Form change needed: ${result.compactFormChangeLabel ?: "form"}"
            GoDexMatchStatus.EVOLUTION_AND_FORM_CHANGE_NEEDED ->
                "Collected \u2022 Evolution needed: ${result.compactEvolutionLabel ?: "evolution"}" +
                    " \u2022 Form change needed: ${result.compactFormChangeLabel ?: "form"}"
            GoDexMatchStatus.COLLECTED -> "Already collected"
            GoDexMatchStatus.UNKNOWN -> "GoDex form unknown"
            GoDexMatchStatus.NOT_CONFIGURED -> "GoDex not configured"
        }
}
