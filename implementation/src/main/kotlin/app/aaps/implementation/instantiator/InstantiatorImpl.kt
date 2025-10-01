package app.aaps.implementation.instantiator

import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.RT
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.aps.DetermineBasalResult
import app.aaps.implementation.iob.AutosensDataObject
import app.aaps.implementation.pump.PumpEnactResultObject
import dagger.Reusable
import dagger.android.HasAndroidInjector
import javax.inject.Inject

@Reusable
class InstantiatorImpl @Inject constructor(
    private val injector: HasAndroidInjector,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger,
    private val preferences: Preferences
) : Instantiator {

    override fun provideAPSResultObject(rt: RT): DetermineBasalResult = DetermineBasalResult(injector, rt)
    override fun provideAutosensDataObject(): AutosensData = AutosensDataObject(aapsLogger, preferences, dateUtil)
    override fun providePumpEnactResult(): PumpEnactResult = PumpEnactResultObject(rh)
}