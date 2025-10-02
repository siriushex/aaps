package app.aaps.implementation.instantiator

import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.implementation.pump.PumpEnactResultObject
import dagger.Reusable
import javax.inject.Inject

@Reusable
class InstantiatorImpl @Inject constructor(
    private val rh: ResourceHelper,
) : Instantiator {

    override fun providePumpEnactResult(): PumpEnactResult = PumpEnactResultObject(rh)
}