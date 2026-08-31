package com.example.pokemonalertsv2.ui.auth

import android.accounts.AccountManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pokemonalertsv2.ui.theme.AppThemeMode
import com.example.pokemonalertsv2.data.PokemonAlertsRepository
import com.example.pokemonalertsv2.data.counters.PokebattlerAuthRepository
import com.example.pokemonalertsv2.data.counters.PokebattlerLoginProvider
import com.example.pokemonalertsv2.data.counters.PokebattlerLoginUrls
import com.example.pokemonalertsv2.ui.theme.PokemonAlertsV2Theme
import com.example.pokemonalertsv2.util.GoogleAccountLoginHint
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Signs the user in to Pokebattler so their Pokébox can be used as a counter source.
 *
 * A WebView rather than a Custom Tab: the session JWT has to be read back out of the flow,
 * and Pokebattler only redirects to its own hosts, so there is no app-owned callback URL to
 * intercept. Instead every navigation and every settled Pokebattler page is swept for a
 * JWT-shaped value — in the URL, in cookies, and in web storage — and each candidate is
 * verified against `GET /secure/user` before anything is stored. That keeps the flow working
 * whichever of those Pokebattler actually uses, and a false positive costs one failed call.
 *
 * The user types their provider credentials into the provider's own page inside this
 * WebView; the app neither reads nor stores them. For Google the typed part is only the
 * password: the system account chooser supplies the account, and it reaches Google's
 * sign-in page as the OAuth `login_hint` ([GoogleAccountLoginHint]).
 */
class PokebattlerLoginActivity : ComponentActivity() {

    private val repository by lazy { PokebattlerAuthRepository.getInstance(this) }

    /** One success wins; later candidates are dropped rather than racing the finish. */
    private val completed = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val provider = intent.getStringExtra(EXTRA_PROVIDER)
            ?.let { slug -> PokebattlerLoginProvider.entries.firstOrNull { it.slug == slug } }
            ?: PokebattlerLoginProvider.GOOGLE

        setContent {
            // Every other activity resolves the user's app-level theme preference; without
            // this the login screen stayed light in a dark app.
            val themeMode by PokemonAlertsRepository.create(this)
                .observeThemeMode()
                .collectAsStateWithLifecycle(initialValue = 0)
            val darkTheme = AppThemeMode.fromStored(themeMode)
                .resolveDark(isSystemInDarkTheme())
            PokemonAlertsV2Theme(darkTheme = darkTheme) {
                var loading by remember { mutableStateOf(true) }
                var status by remember { mutableStateOf<String?>(null) }
                // The Google account picked from the device, applied to Google's sign-in
                // page as login_hint. Session-only on purpose — the app does not store the
                // email either, and the system picker makes re-picking one tap.
                var googleAccount by remember { mutableStateOf<String?>(null) }
                var webView by remember { mutableStateOf<WebView?>(null) }

                val accountPickerLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val email = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    if (!email.isNullOrBlank() && email != googleAccount) {
                        googleAccount = email
                        // Restart the hand-off so Google sees the hint from the first hop.
                        webView?.loadUrl(PokebattlerLoginUrls.loginUrl(provider))
                    }
                }
                // The chooser intent is "deprecated" in favor of Credential Manager, but
                // Credential Manager only signs into the app's own OAuth client — picking
                // an account for a third-party website's web login has no newer API, and
                // this one needs no permissions because the user consents by choosing.
                @Suppress("DEPRECATION")
                fun pickGoogleAccount() {
                    runCatching {
                        AccountManager.newChooseAccountIntent(
                            null, null, arrayOf("com.google"), true, null, null, null, null
                        )
                    }.onSuccess(accountPickerLauncher::launch)
                        .onFailure { status = "Couldn't open the account picker." }
                }

                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (loading) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        status?.let { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        if (provider == PokebattlerLoginProvider.GOOGLE) {
                            TextButton(onClick = { pickGoogleAccount() }) {
                                Icon(Icons.Filled.AccountCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (googleAccount == null) "Choose Google account"
                                    else "Change Google account"
                                )
                            }
                            googleAccount?.let { email ->
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AndroidView(
                                modifier = Modifier.fillMaxSize(),
                                factory = { context ->
                                    buildWebView(
                                        context = context,
                                        provider = provider,
                                        accountEmail = { googleAccount },
                                        onLoadingChanged = { loading = it },
                                        onError = { status = it }
                                    ).also { webView = it }
                                }
                            )
                            if (loading) CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    private fun buildWebView(
        context: Context,
        provider: PokebattlerLoginProvider,
        accountEmail: () -> String?,
        onLoadingChanged: (Boolean) -> Unit,
        onError: (String?) -> Unit
    ): WebView = WebView(context).apply {
        // The provider hand-off keeps its session in third-party cookies; without them the
        // OAuth round trip ends on a blank page.
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                considerUrl(request.url.toString())
                rewriteWithHint(view, request.url.toString())
                return false
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                onLoadingChanged(true)
                url?.let {
                    considerUrl(it)
                    rewriteWithHint(view, it)
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                onLoadingChanged(false)
                if (url == null || completed.get()) return
                considerUrl(url)
                val onPokebattler = url.contains(PokebattlerLoginUrls.WEB_HOST) ||
                    url.contains(PokebattlerLoginUrls.LOGIN_HOST)
                if (!onPokebattler) return
                harvestFromPage(view)
                harvestFromCookies(url, onError)
            }

            /**
             * The hand-off onto accounts.google.com is a server-side redirect, which never
             * passes through shouldOverrideUrlLoading — so the picked account has to be
             * applied here as well, just as the sign-in page starts loading.
             */
            fun rewriteWithHint(view: WebView, url: String) {
                val rewritten = GoogleAccountLoginHint.apply(url, accountEmail())
                if (rewritten != null && !completed.get()) view.loadUrl(rewritten)
            }
        }
        loadUrl(PokebattlerLoginUrls.loginUrl(provider))
    }

    /** Looks for the token in the navigation URL itself. */
    private fun considerUrl(url: String) {
        PokebattlerLoginUrls.jwtCandidates(url).forEach(::verify)
    }

    /**
     * Looks for the token in the page's own storage.
     *
     * Pokebattler's web client holds the JWT in a Redux store that is not reachable from
     * here, but it round-trips it through web storage. Sweeping both stores for JWT-shaped
     * values and verifying each is more durable than depending on a key name.
     */
    private fun harvestFromPage(view: WebView) {
        view.evaluateJavascript(STORAGE_SWEEP_SCRIPT) { result ->
            if (result.isNullOrBlank() || completed.get()) return@evaluateJavascript
            JWT_PATTERN.findAll(result).map { it.value }.distinct().forEach(::verify)
        }
    }

    private fun harvestFromCookies(url: String, onError: (String?) -> Unit) {
        val cookies = CookieManager.getInstance().getCookie(url)
        if (cookies == null) {
            onError(null)
            return
        }
        JWT_PATTERN.findAll(cookies).map { it.value }.distinct().forEach(::verify)
    }

    /** A candidate only becomes the session once the API accepts it. */
    private fun verify(token: String) {
        if (!PokebattlerLoginUrls.looksLikeJwt(token) || completed.get()) return
        lifecycleScope.launch {
            if (completed.get()) return@launch
            repository.signIn(token).onSuccess {
                if (completed.compareAndSet(false, true)) {
                    setResult(Activity.RESULT_OK)
                    finish()
                }
            }
            // A rejected candidate is the normal case — most swept values are not the
            // session token. The user sees a cancelled sign-in if none of them work.
        }
    }

    companion object {
        private const val EXTRA_PROVIDER = "provider"

        private val JWT_PATTERN =
            Regex("[A-Za-z0-9_=-]{10,}\\.[A-Za-z0-9_=-]{10,}\\.[A-Za-z0-9_=-]{10,}")

        private val STORAGE_SWEEP_SCRIPT = """
            (function () {
              var out = [];
              function sweep(store) {
                try {
                  for (var i = 0; i < store.length; i++) {
                    var v = store.getItem(store.key(i));
                    if (!v) continue;
                    out.push(v);
                    try {
                      var parsed = JSON.parse(v);
                      if (parsed) {
                        for (var k in parsed) {
                          if (typeof parsed[k] === 'string') out.push(parsed[k]);
                        }
                      }
                    } catch (e) {}
                  }
                } catch (e) {}
              }
              sweep(window.localStorage);
              sweep(window.sessionStorage);
              return JSON.stringify(out);
            })();
        """.trimIndent()

        fun intent(context: Context, provider: PokebattlerLoginProvider): Intent =
            Intent(context, PokebattlerLoginActivity::class.java)
                .putExtra(EXTRA_PROVIDER, provider.slug)

        /** Clears the WebView session, so signing out is not undone by the next sign-in. */
        fun clearWebSession() {
            runCatching {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
            }
        }
    }
}
