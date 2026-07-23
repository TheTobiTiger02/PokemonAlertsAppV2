package com.example.pokemonalertsv2.ui.alerts

sealed interface SyncStatus {
    data object Loading : SyncStatus
    data object Refreshing : SyncStatus
    data class Live(val updatedAtMillis: Long?) : SyncStatus
    data class Cached(val updatedAtMillis: Long?) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

internal fun AlertsUiState.toSyncStatus(): SyncStatus = when {
    isLoading && alerts.isEmpty() -> SyncStatus.Loading
    isLoading -> SyncStatus.Refreshing
    syncMetadata.isShowingCachedData && alerts.isNotEmpty() ->
        SyncStatus.Cached(syncMetadata.lastSuccessfulSyncMillis)
    errorMessage != null && alerts.isEmpty() -> SyncStatus.Failed(errorMessage)
    else -> SyncStatus.Live(syncMetadata.lastSuccessfulSyncMillis)
}

internal fun SyncStatus.alertsStatusMessage(): String? = when (this) {
    SyncStatus.Loading -> "Loading live alerts…"
    SyncStatus.Refreshing -> "Refreshing…"
    is SyncStatus.Live -> null
    is SyncStatus.Cached -> "Offline · showing cached alerts"
    is SyncStatus.Failed -> "Unable to load alerts"
}

internal fun SyncStatus.mapStatusMessage(): String? = when (this) {
    SyncStatus.Loading -> "Loading map alerts…"
    SyncStatus.Refreshing -> "Refreshing map alerts…"
    is SyncStatus.Live -> null
    is SyncStatus.Cached -> "Offline · showing cached alerts"
    is SyncStatus.Failed -> "Unable to load map alerts"
}
