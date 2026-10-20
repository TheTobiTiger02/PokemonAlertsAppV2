package com.example.pokemonalertsv2.util

import android.os.Trace

/**
 * Named trace sections for atrace/Perfetto captures on any build type (`perf/app_perf.sh`).
 *
 * JVM unit tests run with an unmocked `android.os.Trace`, which throws, so the first call probes
 * once and the sections become no-ops off-device.
 */
object PerfTrace {
    @PublishedApi
    internal val available: Boolean by lazy {
        runCatching { Trace.beginSection("probe"); Trace.endSection() }.isSuccess
    }

    inline fun <T> section(name: String, block: () -> T): T {
        if (!available) return block()
        Trace.beginSection(name)
        try {
            return block()
        } finally {
            Trace.endSection()
        }
    }
}
