package app.aaps.plugins.sync.tidepool.auth

import android.content.Intent
import android.os.Bundle
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import app.aaps.plugins.sync.tidepool.keys.TidepoolStringKey
import app.aaps.plugins.sync.tidepool.messages.AuthReplyMessage
import dagger.android.support.DaggerAppCompatActivity
import net.openid.appauth.AppAuthConfiguration
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject

/**
 *
 * Handle aaps://callback/tidepool
 */
class AuthFlowIn : DaggerAppCompatActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var authFlowOut: AuthFlowOut
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var tidepoolUploader: TidepoolUploader
    @Inject lateinit var rxBus: RxBus

    public override fun onCreate(savedInstanceBundle: Bundle?) {
        super.onCreate(savedInstanceBundle)
        aapsLogger.debug(LTag.TIDEPOOL, "Got response")
        processResponse(intent)
        this.finish()
    }

    private fun processResponse(intent: Intent) {
        val authorizationResponse = AuthorizationResponse.fromIntent(intent)
        val authorizationException = AuthorizationException.fromIntent(intent)
        val state = authFlowOut.authState ?: return
        if (authorizationResponse == null && authorizationException == null) {
            aapsLogger.error(LTag.TIDEPOOL, "Authentication not received")
            return
        }
        state.update(authorizationResponse, authorizationException)
        if (authorizationException != null) {
            aapsLogger.debug("Got authorization error - resetting state: $authorizationException")
            authFlowOut.eraseAuthState()
        }
        if (authorizationResponse != null) {
            // authorization completed
            authFlowOut.saveAuthState()

            val service = authFlowOut.authService
            service?.performTokenRequest(authorizationResponse.createTokenExchangeRequest()) { tokenResponse, exception ->
                state.update(tokenResponse, exception)
                if (exception != null) {
                    aapsLogger.debug(LTag.TIDEPOOL, "Token request exception: $exception")
                    authFlowOut.eraseAuthState()
                }
                if (tokenResponse != null) {
                    aapsLogger.debug(LTag.TIDEPOOL, "Got first token")
                    authFlowOut.saveAuthState()

                    val configuration = state.getAuthorizationServiceConfiguration()
                    if (configuration == null) {
                        aapsLogger.error(LTag.TIDEPOOL, "Got null for authorization service configuration")
                        return@performTokenRequest
                    }
                    val discoveryDoc = configuration.discoveryDoc
                    if (discoveryDoc == null) {
                        aapsLogger.error(LTag.TIDEPOOL, "Got null for discoveryDoc")
                        return@performTokenRequest
                    }
                    val userInfoEndpoint = discoveryDoc.userinfoEndpoint
                    if (userInfoEndpoint == null) {
                        aapsLogger.error(LTag.TIDEPOOL, "Got null for userInfoEndpoint")
                        return@performTokenRequest
                    }

                    try {
                        val conn = AppAuthConfiguration.DEFAULT.connectionBuilder.openConnection(userInfoEndpoint)
                        // TODO should hardcoded Bearer be replaced by token type?
                        conn.setRequestProperty("Authorization", "Bearer " + tokenResponse.accessToken)
                        conn.setInstanceFollowRedirects(false)
                        val response = BufferedReader(InputStreamReader(conn.getInputStream())).readText()
                        aapsLogger.debug(LTag.TIDEPOOL, "UserInfo: $response")
                        val userInfo = JSONObject(response)

                        state.performActionWithFreshTokens(service, { accessToken, idToken, authorizationException1 ->
                            if (authorizationException1 != null) {
                                aapsLogger.error(LTag.TIDEPOOL, "Got fresh token exception: $authorizationException1")
                                return@performActionWithFreshTokens
                            }
                            val session = tidepoolUploader.createSession(tokenResponse.tokenType)
                            session.authReply = AuthReplyMessage().apply { userid = idToken }
                            session.token = accessToken
                            try {
                                val email = userInfo.optString("email")
                                if (email.isNotEmpty() == true) {
                                    aapsLogger.debug(LTag.TIDEPOOL, "Setting username to: $email")
                                    preferences.put(TidepoolStringKey.Username, email)
                                } else {
                                    aapsLogger.error(LTag.TIDEPOOL, "Could not get userinfo email")
                                }
                                session.authReply?.userid = userInfo.optString("sub")
                                if (session.authReply?.userid?.isNotEmpty() == true) {
                                    preferences.put(TidepoolStringKey.SubName, session.authReply?.userid!!)
                                    tidepoolUploader.startSession(session)
                                } else {
                                    aapsLogger.error(LTag.TIDEPOOL, "Could not get 'sub' field - cannot proceed")
                                }
                            } catch (e: JSONException) {
                                aapsLogger.error(LTag.TIDEPOOL, "Getting Access Token 1 Exception: ", e)
                            }
                        })
                    } catch (ioException: IOException) {
                        aapsLogger.error(LTag.TIDEPOOL, "Network error when querying userinfo endpoint", ioException)
                    } catch (_: JSONException) {
                        aapsLogger.error(LTag.TIDEPOOL, "Failed to parse userinfo response")
                    }
                }
            }
        } else {
            aapsLogger.error(LTag.TIDEPOOL, "Got response failure $authorizationException")
        }
    }
}