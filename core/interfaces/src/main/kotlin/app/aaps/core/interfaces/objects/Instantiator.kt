package app.aaps.core.interfaces.objects

import app.aaps.core.interfaces.pump.PumpEnactResult

interface Instantiator {

    fun providePumpEnactResult(): PumpEnactResult
}