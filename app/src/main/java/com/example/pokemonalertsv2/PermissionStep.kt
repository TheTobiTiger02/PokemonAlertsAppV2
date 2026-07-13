package com.example.pokemonalertsv2

internal enum class PermissionStep {
    IDLE,
    NOTIFICATION,
    FOREGROUND_LOCATION,
    BACKGROUND_LOCATION,
    COMPLETE
}

internal fun PermissionStep.isRequestActive(): Boolean = when (this) {
    PermissionStep.NOTIFICATION,
    PermissionStep.FOREGROUND_LOCATION,
    PermissionStep.BACKGROUND_LOCATION -> true
    PermissionStep.IDLE,
    PermissionStep.COMPLETE -> false
}

internal fun PermissionStep.afterResult(granted: Boolean = true): PermissionStep = when (this) {
    PermissionStep.NOTIFICATION -> PermissionStep.FOREGROUND_LOCATION
    PermissionStep.FOREGROUND_LOCATION -> if (granted) PermissionStep.BACKGROUND_LOCATION else PermissionStep.COMPLETE
    PermissionStep.BACKGROUND_LOCATION -> PermissionStep.COMPLETE
    PermissionStep.IDLE,
    PermissionStep.COMPLETE -> this
}
