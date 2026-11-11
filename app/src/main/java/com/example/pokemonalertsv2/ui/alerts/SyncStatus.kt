package com.example.pokemonalertsv2.ui.alerts
import com.example.pokemonalertsv2.R
import com.example.pokemonalertsv2.util.AppText



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
    SyncStatus.Loading -> AppText.get(R.string.alerts_loading_live_alerts)
    SyncStatus.Refreshing -> AppText.get(R.string.alerts_refreshing)
    is SyncStatus.Live -> null
    is SyncStatus.Cached -> AppText.get(R.string.alerts_offline_showing_cached_alerts)
    is SyncStatus.Failed -> AppText.get(R.string.alerts_unable_to_load_alerts)
}
