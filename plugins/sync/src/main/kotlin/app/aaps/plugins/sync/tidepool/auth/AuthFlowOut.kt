package app.aaps.plugins.sync.tidepool.auth

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.net.toUri
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.plugins.sync.tidepool.events.EventTidepoolStatus
import app.aaps.plugins.sync.tidepool.keys.TidepoolBooleanKey
import app.aaps.plugins.sync.tidepool.keys.TidepoolStringNonKey
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.AuthorizationServiceConfiguration.RetrieveConfigurationCallback
import net.openid.appauth.ResponseTypeValues
import net.openid.appauth.browser.BrowserAllowList
import net.openid.appauth.browser.VersionedBrowserMatcher
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JamOrHam
 *
 *
 * Handler for new style Tidepool openid auth
 */
@Singleton
class AuthFlowOut @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences,
    private val context: Context,
    private val cryptoUtil: CryptoUtil,
    private val rxBus: RxBus
) {

    companion object {

        private const val CLIENT_ID = "aaps"
        private const val REDIRECT_URI = "aaps://callback/tidepool"
        private const val INTEGRATION_BASE_URL = "https://auth.integration.tidepool.org/realms/integration"
        private const val PRODUCTION_BASE_URL = "https://auth.tidepool.org/realms/tidepool"
    }

    var authService: AuthorizationService? = null
        private set
        get() {
            if (field == null) {
                val appAuthConfig = AppAuthConfiguration.Builder()
                    .setBrowserMatcher(
                        BrowserAllowList(
                            VersionedBrowserMatcher.CHROME_CUSTOM_TAB,
                            VersionedBrowserMatcher.FIREFOX_CUSTOM_TAB,
                            VersionedBrowserMatcher.SAMSUNG_CUSTOM_TAB
                        )
                    )
                    .build()
                field = AuthorizationService(context, appAuthConfig)
            }
            return field
        }

    var authState: AuthState? = null
        private set
        get() {
            if (field == null) {
                try {
                    field = AuthState(AuthorizationServiceConfiguration.fromJson(preferences.get(TidepoolStringNonKey.ServiceConfiguration)))
                    // Check if we have an authorized state
                    if (preferences.get(TidepoolStringNonKey.AuthState).isNotEmpty())
                        field = AuthState.jsonDeserialize(preferences.get(TidepoolStringNonKey.AuthState))
                    aapsLogger.debug("Loaded auth state : ${field?.getAccessToken()}")
                } catch (e: Exception) {
                    aapsLogger.error("Error during getAuthState - could just be empty cache data", e)
                }
            }
            return field
        }

    fun saveAuthState() {
        authState?.let {
            preferences.put(TidepoolStringNonKey.AuthState, it.jsonSerializeString())
        }
    }

    fun eraseAuthState() {
        preferences.put(TidepoolStringNonKey.AuthState, "")
    }

    @Synchronized
    private fun resetAll() {
        authState = null
        authService = null
    }

    @Synchronized
    fun clearAllSavedData() {
        preferences.put(TidepoolStringNonKey.ServiceConfiguration, "")
        eraseAuthState()
        resetAll()
        rxBus.send(EventTidepoolStatus(("Credentials cleared")))
    }

    fun doTidePoolInitialLogin() {
        rxBus.send(EventTidepoolStatus(("Opening login screen")))
        AuthorizationServiceConfiguration.fetchFromIssuer(
            if (preferences.get(TidepoolBooleanKey.UseTestServers)) INTEGRATION_BASE_URL.toUri()
            else PRODUCTION_BASE_URL.toUri(),
            RetrieveConfigurationCallback { serviceConfiguration, exception ->
                if (exception != null || serviceConfiguration == null) {
                    rxBus.send(EventTidepoolStatus(("Failed to fetch configuration $exception")))
                    return@RetrieveConfigurationCallback
                }
                preferences.put(TidepoolStringNonKey.ServiceConfiguration, serviceConfiguration.toJsonString())
                resetAll()

                val codeVerifierChallengeMethod = "S256"
                val encoding = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
                val challenge = cryptoUtil.getRandomKey(64)
                val codeVerifier = Base64.encodeToString(challenge, encoding)
                val digest: MessageDigest? = try {
                    MessageDigest.getInstance("SHA-256")
                } catch (e: NoSuchAlgorithmException) {
                    aapsLogger.error("Failed to get message digest", e)
                    return@RetrieveConfigurationCallback
                }
                val hash = digest?.digest(codeVerifier.toByteArray(StandardCharsets.UTF_8))
                val codeChallenge = Base64.encodeToString(hash, encoding)

                val authRequest =
                    AuthorizationRequest.Builder(
                        serviceConfiguration,  // the authorization service configuration
                        CLIENT_ID,  // the client ID, typically pre-registered and static
                        ResponseTypeValues.CODE,  // the response_type value: we want a code
                        REDIRECT_URI.toUri() // the redirect URI to which the auth response is sent
                    )
                        .setScopes("openid", "offline_access")
                        .setCodeVerifier(codeVerifier, codeChallenge, codeVerifierChallengeMethod)
                        .build()

                authService?.performAuthorizationRequest(
                    authRequest,
                    PendingIntent.getActivity(context, 0, Intent(context, AuthFlowIn::class.java), PendingIntent.FLAG_IMMUTABLE),
                    PendingIntent.getActivity(context, 0, Intent(context, AuthFlowIn::class.java), PendingIntent.FLAG_IMMUTABLE)
                )
            })
    }
}
