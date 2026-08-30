package com.example.pokemonalertsv2.ui.alerts

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.MainActivity
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme

class AlertsMapActivity : ComponentActivity() {
    private val viewModel: PokemonAlertsViewModel by viewModels()
    private val repository by lazy { PokemonAlertsRepository.create(applicationContext) }
    private var compactPictureInPicture by mutableStateOf(false)
    private var requestedZoom by mutableStateOf(USER_LOCATION_ZOOM.toDouble())
    private var canEnterPictureInPicture = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isPictureInPictureLaunch(intent)) {
            startActivity(MainActivity.createMapIntent(this))
            finish()
            return
        }

        enableEdgeToEdge()
        canEnterPictureInPicture =
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        if (!canEnterPictureInPicture) {
            startActivity(MainActivity.createMapIntent(this))
            finish()
            return
        }

        requestedZoom = pictureInPictureZoom(intent)
        compactPictureInPicture = true
        onBackPressedDispatcher.addCallback(this) { returnToMainMap() }

        setContent {
            val themeMode by repository.observeThemeMode()
                .collectAsStateWithLifecycle(initialValue = 0)
            val darkTheme = AppThemeMode.fromStored(themeMode)
                .resolveDark(isSystemInDarkTheme())
            PokemonAlertsV2Theme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground
                ) {
                    AlertsMapRoute(
                        viewModel = viewModel,
                        onBack = ::returnToMainMap,
                        showBackButton = true,
                        presentationMode = if (compactPictureInPicture) {
                            MapPresentationMode.COMPACT_PICTURE_IN_PICTURE
                        } else {
                            MapPresentationMode.FULL
                        },
                        initialZoom = requestedZoom,
                        onEnterPictureInPicture = ::enterMapPictureInPicture
                    )
                }
            }
        }

        if (savedInstanceState == null) {
            window.decorView.post { enterMapPictureInPicture(requestedZoom) }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        compactPictureInPicture = isInPictureInPictureMode
    }

    private fun enterMapPictureInPicture(zoom: Double) {
        if (!canEnterPictureInPicture || isInPictureInPictureMode) return

        requestedZoom = normalizeMapPictureInPictureZoom(zoom)
        compactPictureInPicture = true
        val sourceRect = Rect().also(window.decorView::getGlobalVisibleRect)
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setSourceRectHint(sourceRect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(false)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            builder.setTitle("Pokemon Alerts map")
            builder.setSubtitle("Live alerts and location")
        }
        if (!enterPictureInPictureMode(builder.build())) {
            compactPictureInPicture = false
        }
    }

    private fun returnToMainMap() {
        startActivity(MainActivity.createMapIntent(this))
        finish()
    }

    companion object {
        internal const val EXTRA_LAUNCH_PICTURE_IN_PICTURE =
            "extra_launch_map_picture_in_picture"
        internal const val EXTRA_INITIAL_ZOOM = "extra_map_picture_in_picture_zoom"

        internal fun createPictureInPictureIntent(context: Context, zoom: Double): Intent =
            Intent(context, AlertsMapActivity::class.java).apply {
                putExtra(EXTRA_LAUNCH_PICTURE_IN_PICTURE, true)
                putExtra(EXTRA_INITIAL_ZOOM, normalizeMapPictureInPictureZoom(zoom))
            }

        internal fun isPictureInPictureLaunch(intent: Intent?): Boolean =
            intent?.getBooleanExtra(EXTRA_LAUNCH_PICTURE_IN_PICTURE, false) == true

        internal fun pictureInPictureZoom(intent: Intent?): Double =
            normalizeMapPictureInPictureZoom(
                intent?.takeIf { it.hasExtra(EXTRA_INITIAL_ZOOM) }
                    ?.getDoubleExtra(EXTRA_INITIAL_ZOOM, USER_LOCATION_ZOOM.toDouble())
            )
    }
}
