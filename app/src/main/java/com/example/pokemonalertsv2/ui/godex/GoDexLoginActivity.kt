package com.example.pokemonalertsv2.ui.godex

import com.example.pokemonalertsv2.util.AppText
import com.example.pokemonalertsv2.R
import androidx.compose.ui.res.stringResource
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
import com.example.pokemonalertsv2.util.GoogleAccountLoginHint
import kotlinx.coroutines.launch

/**
 * goDex sign-in: the user signs in with Google or Discord in a WebView. Once a godex.site page
 * shows the signed-in account (its profile link), the session cookies are saved and the screen
 * closes. Which collections the app uses is chosen afterwards in the app, from the account's
 * collection list; signing in again never changes it.
 */
class GoDexLoginActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val darkTheme = com.example.pokemonalertsv2.ui.theme.rememberAppDarkTheme()
            PokemonAlertsV2Theme(darkTheme = darkTheme) {
            val scope = rememberCoroutineScope()
            val repository = GoDexRepository.getInstance(applicationContext)
            var statusText by remember { mutableStateOf(AppText.get(R.string.godex_sign_in_with_google_or_discord)) }
            var saving by remember { mutableStateOf(false) }
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
                    // Restart the hand-off so Google sees the hint from the first hop.
                    webView?.loadUrl(GoDexWebSessionCookies.GODEX_LOGIN_URL)
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
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.catchroutes_close))
                            }
                        },
                        actions = {
                            run {
                                IconButton(onClick = { pickGoogleAccount() }) {
                                    Icon(
                                        Icons.Filled.AccountCircle,
                                        contentDescription = stringResource(R.string.godex_choose_google_account)
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        GoDexWebSessionCookies.clearAllWebViewCookies {
                                            runOnUiThread {
                                                statusText = AppText.get(R.string.godex_sign_in_with_google_or_discord)
                                                webView?.loadUrl(
                                                    GoDexWebSessionCookies.GODEX_LOGIN_URL
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Text(stringResource(R.string.godex_use_another_account))
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Google or Discord may remember your account for faster reauthentication.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Pokémon Alerts never stores your email or password.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                            if (saving || view == null || !currentUrl.contains("godex.site")) return
                                            if (currentUrl.contains("/login") || currentUrl.contains("/auth/") || currentUrl.contains("/callback")) return
                                            // goDex answers its pages signed out too; only the
                                            // account's profile link shows the sign-in worked.
                                            view.evaluateJavascript(SIGNED_IN_CHECK) { answer ->
                                                if (answer != "true" || saving) return@evaluateJavascript
                                                val cookies = CookieManager.getInstance().getCookie("https://godex.site")
                                                if (cookies.isNullOrBlank()) return@evaluateJavascript
                                                saving = true
                                                scope.launch {
                                                    repository.saveSession(cookies)
                                                    Toast.makeText(context, AppText.get(R.string.godex_signed_in), Toast.LENGTH_LONG).show()
                                                    setResult(Activity.RESULT_OK)
                                                    finish()
                                                }
                                            }
                                        }
                                    }

                                    // Preserve Google/Discord WebView cookies so reauthentication
                                    // can reuse the provider session without storing credentials.
                                    loadUrl(GoDexWebSessionCookies.GODEX_LOGIN_URL)
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

    companion object {
        private const val SIGNED_IN_CHECK =
            "!!document.querySelector('[dusk=desktop-profile],[dusk=mobile-profile],[dusk=collections-container]')"

        fun createIntent(context: Context): Intent = Intent(context, GoDexLoginActivity::class.java)
    }
}
