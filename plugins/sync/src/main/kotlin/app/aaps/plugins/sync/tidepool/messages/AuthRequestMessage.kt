package app.aaps.plugins.sync.tidepool.messages

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.tidepool.keys.TidepoolStringKey
import okhttp3.Credentials

object AuthRequestMessage : BaseMessage() {

    fun getAuthRequestHeader(preferences: Preferences): String? {
        val username = preferences.get(TidepoolStringKey.Username)
        val password = preferences.get(TidepoolStringKey.Password)

        return if (username.isEmpty() || password.isEmpty()) null
        else Credentials.basic(username.trim { it <= ' ' }, password)
    }
}
