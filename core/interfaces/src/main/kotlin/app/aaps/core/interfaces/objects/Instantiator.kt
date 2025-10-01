package app.aaps.core.interfaces.objects

import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.pump.PumpEnactResult

interface Instantiator {

    fun provideAPSResultObject(rt: RT): APSResult
    fun provideAutosensDataObject(): AutosensData
    fun providePumpEnactResult(): PumpEnactResult
}