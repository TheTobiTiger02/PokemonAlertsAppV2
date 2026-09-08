package com.example.pokemonalertsv2.baselineprofile

import android.os.SystemClock
import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Configurator
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Run with PerformanceFixtureSetupTest's offline 200-alert/1000-entry fixture on both APKs. */
@OptIn(ExperimentalMetricApi::class)
class ScreenReadinessBenchmark {
    @get:Rule val rule = MacrobenchmarkRule()

    @Test fun majorScreenJourneys() = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric(), MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.Require),
        iterations = 5,
        setupBlock = {
            killProcess()
            device.prepareBenchmarkPermissions()
            startActivityAndWait()
            device.completeOnboardingIfNeeded()
            device.requireClick(By.text("Alerts"))
            device.requireVisible(By.text("Pokémon Alerts"))
        }
    ) {
        val oldIdleTimeout = Configurator.getInstance().waitForIdleTimeout
        Configurator.getInstance().waitForIdleTimeout = 0
        try {
        val args = InstrumentationRegistry.getArguments()
        val label = args.getString("performanceLabel", "local")
        fun measure(name: String, action: () -> Unit, ready: BySelector) {
            val start = SystemClock.elapsedRealtimeNanos()
            action()
            device.requireVisible(ready)
            val elapsed = (SystemClock.elapsedRealtimeNanos() - start) / 1e6
            File(InstrumentationRegistry.getInstrumentation().context.getExternalFilesDir(null),
                "screen-readiness-$label.csv").appendText("$name,$elapsed\n")
        }
        repeat(2) { visit ->
            measure("history-$visit", { device.requireClick(By.text("History")) }, By.text("Alert History"))
            device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                device.displayWidth / 2, device.displayHeight / 3, 24)
            measure("settings-$visit", { device.requireClick(By.text("Settings")) }, By.text("Appearance & behavior"))
            measure("appearance-$visit", { device.requireClick(By.text("Appearance & behavior")) }, By.text("Display and sorting"))
            device.pressBack()
            measure("filters-$visit", { device.requireClick(By.text("Filters")) }, By.text("Filter Studio"))
            device.pressBack()
            measure("godex-settings-$visit", { device.requireClick(By.text("GoDex checklist")) }, By.text("Performance fixture"))
            measure("godex-collection-$visit", { device.requireClick(By.text("1000 still needed")) }, By.text("Search Pokémon, form, number, or key"))
            val search = device.findObject(By.clazz("android.widget.EditText"))
                ?: error("GoDex search field missing")
            measure("godex-search-$visit", { search.text = "Fixture Pokemon 99" }, By.text("Fixture Pokemon 99"))
            device.pressBack()
            device.pressBack()
            measure("map-$visit", { device.requireClick(By.text("Map")) }, By.desc("Map settings and filters"))
            measure("map-filters-$visit", { device.requireClick(By.desc("Map settings and filters")) }, By.textContains("alerts visible"))
            device.pressBack()
            measure("feed-$visit", { device.requireClick(By.text("Alerts")) }, By.text("Pokémon Alerts"))
            measure("detail-$visit", { device.requireClick(By.text("Pikachu")) }, By.desc("Back"))
            device.pressBack()
        }
        } finally {
            Configurator.getInstance().waitForIdleTimeout = oldIdleTimeout
        }
    }
}
