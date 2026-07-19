package com.example.pokemonalertsv2.ui.godex

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import kotlinx.coroutines.launch

/**
 * Two-step login + checklist picker flow:
 *
 * Step 1 – LOGIN: User signs in via Google/Discord OAuth. Detected when the
 *   WebView navigates away from /login to a godex.site page.
 *
 * Step 2 – PICK CHECKLIST: After login the user is prompted to navigate to
 *   the checklist they want to sync. When they open a collection page
 *   (URL contains "/collection/" but not "/public-collection/") the app
 *   captures that URL and stores it alongside the session cookies.
 */
class GoDexLoginActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val scope = rememberCoroutineScope()
            val repository = GoDexRepository.getInstance(applicationContext)
            val startAtPicker = intent.getBooleanExtra("EXTRA_START_AT_PICKER", false)
            var step by remember { mutableStateOf(if (startAtPicker) Step.PICK_CHECKLIST else Step.LOGIN) }
            var statusText by remember { mutableStateOf(if (startAtPicker) "Select your checklist" else "Sign in with Google or Discord") }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(statusText) },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        }
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    AnimatedVisibility(visible = step == Step.PICK_CHECKLIST) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "✅ Logged in! Now open the checklist you want to sync.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Tap on one of your collections below.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        @Suppress("DEPRECATION")
                                        databaseEnabled = true
                                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                    }
                                    webViewClient = object : WebViewClient() {
                                        private var loginCaptured = false
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            val currentUrl = url ?: return
                                            when (step) {
                                                Step.LOGIN -> {
                                                    val isOnGoDex = currentUrl.contains("godex.site")
                                                    val isLoginPage = currentUrl.contains("/login")
                                                    val isAuthCallback = currentUrl.contains("/auth/") || currentUrl.contains("/callback")

                                                    if (isOnGoDex && !isLoginPage && !isAuthCallback) {
                                                        step = Step.PICK_CHECKLIST
                                                        statusText = "Select your checklist"
                                                    }
                                                }

                                                Step.PICK_CHECKLIST -> {
                                                    val isOnGoDex = currentUrl.contains("godex.site")
                                                    if (isOnGoDex && isCollectionUrl(currentUrl)) {
                                                        val cookies = CookieManager.getInstance().getCookie("https://godex.site")
                                                        if (cookies != null && cookies.contains("godex_session")) {
                                                             scope.launch {
                                                                 repository.saveSessionCookies(cookies)
                                                                 repository.saveWriteBackUrl(currentUrl)
                                                                 com.example.pokemonalertsv2.work.GoDexSyncWorker.enqueueImmediate(context)
                                                                 com.example.pokemonalertsv2.work.GoDexSyncWorker.schedule(context)
                                                                 Toast.makeText(
                                                                     context,
                                                                     "Checklist selected! Two-way sync is ready.",
                                                                     Toast.LENGTH_LONG
                                                                 ).show()
                                                                 setResult(Activity.RESULT_OK)
                                                                 finish()
                                                             }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    if (startAtPicker) {
                                        val sessionCookies = repository.config.value.sessionCookies
                                        if (sessionCookies.isNotBlank()) {
                                            val cookieManager = CookieManager.getInstance()
                                            cookieManager.setAcceptCookie(true)
                                            sessionCookies.split(";").forEach { cookie ->
                                                cookieManager.setCookie("https://godex.site", cookie.trim())
                                            }
                                        }
                                        loadUrl("https://godex.site")
                                    } else {
                                        CookieManager.getInstance().removeAllCookies(null)
                                        loadUrl("https://godex.site/login")
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    private enum class Step { LOGIN, PICK_CHECKLIST }

    companion object {
        fun createIntent(context: Context, startAtPicker: Boolean = false): Intent =
            Intent(context, GoDexLoginActivity::class.java).apply {
                putExtra("EXTRA_START_AT_PICKER", startAtPicker)
            }

        /**
         * Checks if a URL looks like an authenticated collection page on GoDex.
         * Public collection URLs (/public-collection/) are excluded since those
         * are read-only. We accept any URL that looks like it could be a
         * user's own collection view.
         */
        private fun isCollectionUrl(url: String): Boolean {
            val path = android.net.Uri.parse(url).path ?: return false
            // Match paths like /collection/ID, /c/ID, /collections/ID, etc.
            // but NOT /public-collection/
            if (path.contains("public-collection", ignoreCase = true)) return false
            // Accept if it looks like a collection page with a path segment after it
            return path.matches(Regex("^/(?:collection|collections|c)/[A-Za-z0-9_\\-]+.*$", RegexOption.IGNORE_CASE))
        }
    }
}
