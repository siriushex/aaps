package app.aaps.implementation.locale

import android.content.res.Resources
import app.aaps.core.interfaces.local.LocaleDependentSetting

class LocaleDependentSettingImpl : LocaleDependentSetting {

    private val language get() = Resources.getSystem().configuration.locales[0]
    override val ntpServer: String
        get() =
            if (language.language.startsWith("zh")) "ntp1.aliyun.com"
            else "time.google.com"

}