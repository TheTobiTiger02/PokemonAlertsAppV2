package com.example.pokemonalertsv2.hunt

/**
 * When the hunt may next ask the routing endpoints for anything.
 *
 * `api/routes/matrix` and `api/routes/path` sit behind one per-client allowance at the edge, so a 429 on
 * either is a 429 on both. Without a shared deadline the hunt would answer a rate-limited matrix by
 * immediately asking for geometry, and keep the limit permanently tripped.
 */
internal object HuntRoutingGate {
    @Volatile
    var blockedUntilMillis: Long = 0L
        private set

    /** Holds every hunt routing request until [untilMillis]; a later deadline always wins. */
    fun blockUntil(untilMillis: Long) {
        if (untilMillis > blockedUntilMillis) blockedUntilMillis = untilMillis
    }

    /** A successful call means the allowance is back. */
    fun clear() {
        blockedUntilMillis = 0L
    }
}
