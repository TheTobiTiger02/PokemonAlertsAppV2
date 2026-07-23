package com.example.pokemonalertsv2.data.godex

data class GoDexConfig(
    val url: String = "",
    val collectionTitle: String = "",
    val lastSuccessfulSyncMillis: Long = 0L,
    val notificationFilterEnabled: Boolean = false,
    val sessionCookies: String = "",
    val writeBackUrl: String = "",
    val sessionState: GoDexSessionState = GoDexSessionState.NONE,
    val lastSuccessfulWriteMillis: Long = 0L,
    val lastWriteError: String? = null
) {
    val isConnected: Boolean get() = url.isNotBlank() || writeBackUrl.isNotBlank()
    val hasSession: Boolean
        get() = sessionState == GoDexSessionState.AUTHENTICATED && sessionCookies.isNotBlank()
    val hasWriteBackUrl: Boolean get() = writeBackUrl.isNotBlank()
}

enum class GoDexSessionState {
    NONE,
    AUTHENTICATED,
    REAUTH_REQUIRED
}

data class GoDexSyncUiState(
    val isSyncing: Boolean = false,
    val pendingCount: Int = 0,
    val lastSuccessfulWriteMillis: Long = 0L,
    val sessionState: GoDexSessionState = GoDexSessionState.NONE,
    val errorMessage: String? = null
)

sealed interface GoDexWriteResult {
    val refreshedCookies: String

    data class Applied(override val refreshedCookies: String) : GoDexWriteResult
    data class AlreadyApplied(override val refreshedCookies: String) : GoDexWriteResult
    data class ReauthenticationRequired(
        val message: String,
        override val refreshedCookies: String
    ) : GoDexWriteResult
    data class RetryableFailure(
        val message: String,
        val cause: Throwable? = null,
        override val refreshedCookies: String
    ) : GoDexWriteResult
    data class PermanentFailure(
        val message: String,
        override val refreshedCookies: String
    ) : GoDexWriteResult
}

class GoDexAuthenticationException(
    message: String,
    val refreshedCookies: String
) : IllegalStateException(message)

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
