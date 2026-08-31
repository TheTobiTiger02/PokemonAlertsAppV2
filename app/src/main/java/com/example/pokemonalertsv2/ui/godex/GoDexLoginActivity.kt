package com.example.pokemonalertsv2.ui.godex

import android.annotation.SuppressLint
import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.godex.GoDexRepository
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import com.example.pokemonalertsv2.ui.motion.appFadeThrough
import com.example.pokemonalertsv2.ui.motion.appSharedAxisX
import com.example.pokemonalertsv2.util.GoogleAccountLoginHint
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
            // Same theme preference the rest of the app resolves; without it this screen
            // stayed light in a dark app.
            val themeMode by PokemonAlertsRepository.create(applicationContext)
                .observeThemeMode()
                .collectAsStateWithLifecycle(initialValue = 0)
            val darkTheme = AppThemeMode.fromStored(themeMode)
                .resolveDark(isSystemInDarkTheme())
            PokemonAlertsV2Theme(darkTheme = darkTheme) {
            val scope = rememberCoroutineScope()
            val repository = GoDexRepository.getInstance(applicationContext)
            val startAtPicker = intent.getBooleanExtra("EXTRA_START_AT_PICKER", false)
            var step by remember { mutableStateOf(if (startAtPicker) Step.PICK_CHECKLIST else Step.LOGIN) }
            var statusText by remember { mutableStateOf(if (startAtPicker) "Select your checklist" else "Sign in with Google or Discord") }
            var webView by remember { mutableStateOf<WebView?>(null) }
            // The Google account picked from the device, applied to Google's sign-in page
            // as login_hint. Session-only on purpose: this screen promises the app never
            // stores your email, and the system picker makes re-picking one tap.
            var googleAccount by remember { mutableStateOf<String?>(null) }

            val accountPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                if (!email.isNullOrBlank() && email != googleAccount) {
                    googleAccount = email
                    if (step == Step.LOGIN) {
                        // Restart the hand-off so Google sees the hint from the first hop.
                        webView?.loadUrl(GoDexWebSessionCookies.GODEX_LOGIN_URL)
                    }
                }
            }
            // The chooser intent is "deprecated" in favor of Credential Manager, but
            // Credential Manager only signs into the app's own OAuth client — picking an
            // account for a third-party website's web login has no newer API, and this one
            // needs no permissions because the user consents by choosing.
            @Suppress("DEPRECATION")
            fun pickGoogleAccount() {
                runCatching {
                    AccountManager.newChooseAccountIntent(
                        null, null, arrayOf("com.google"), true, null, null, null, null
                    )
                }.onSuccess(accountPickerLauncher::launch)
            }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            AnimatedContent(
                                targetState = statusText,
                                transitionSpec = { appFadeThrough() },
                                label = "godex_login_title"
                            ) { title ->
                                Text(title)
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { finish() }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            if (!startAtPicker) {
                                IconButton(onClick = { pickGoogleAccount() }) {
                                    Icon(
                                        Icons.Filled.AccountCircle,
                                        contentDescription = "Choose Google account"
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        GoDexWebSessionCookies.clearAllWebViewCookies {
                                            runOnUiThread {
                                                step = Step.LOGIN
                                                statusText = "Sign in with Google or Discord"
                                                webView?.loadUrl(
                                                    GoDexWebSessionCookies.GODEX_LOGIN_URL
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Text("Use another account")
                                }
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
                    AnimatedContent(
                        targetState = step,
                        transitionSpec = { appSharedAxisX(forward = targetState == Step.PICK_CHECKLIST) },
                        label = "godex_login_step"
                    ) { currentStep ->
                        val pickingChecklist = currentStep == Step.PICK_CHECKLIST
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (pickingChecklist) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = if (pickingChecklist) {
                                    "Signed in — open the checklist you want to sync."
                                } else {
                                    "Google or Discord may remember your account for faster reauthentication."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (pickingChecklist) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Text(
                                text = if (pickingChecklist) {
                                    "Tap one of your collections below."
                                } else {
                                    "Pokémon Alerts never stores your email or password."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pickingChecklist) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    webView = this
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        @Suppress("DEPRECATION")
                                        databaseEnabled = true
                                        userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                    }
                                    webViewClient = object : WebViewClient() {
                                        private var loginCaptured = false

                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            val url = request?.url?.toString()
                                            if (url != null) rewriteWithGoogleHint(view, url)
                                            return super.shouldOverrideUrlLoading(view, request)
                                        }

                                        override fun onPageStarted(
                                            view: WebView?,
                                            url: String?,
                                            favicon: Bitmap?
                                        ) {
                                            super.onPageStarted(view, url, favicon)
                                            if (url != null) rewriteWithGoogleHint(view, url)
                                        }

                                        /**
                                         * The hand-off onto accounts.google.com is a
                                         * server-side redirect, which never passes through
                                         * shouldOverrideUrlLoading — so the picked account
                                         * has to be applied here as well, just as the
                                         * sign-in page starts loading.
                                         */
                                        private fun rewriteWithGoogleHint(view: WebView?, url: String) {
                                            if (view == null) return
                                            val rewritten =
                                                GoogleAccountLoginHint.apply(url, googleAccount)
                                            if (rewritten != null) view.loadUrl(rewritten)
                                        }

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
                                                        if (!cookies.isNullOrBlank()) {
                                                             scope.launch {
                                                                 repository.saveAuthenticatedSession(cookies, currentUrl)
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
                                            cookieManager.flush()
                                        }
                                        loadUrl("https://godex.site")
                                    } else {
                                        // Preserve Google/Discord WebView cookies so reauthentication
                                        // can reuse the provider session without storing credentials.
                                        loadUrl(GoDexWebSessionCookies.GODEX_LOGIN_URL)
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
