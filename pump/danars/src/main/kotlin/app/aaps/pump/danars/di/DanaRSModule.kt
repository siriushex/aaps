package app.aaps.pump.danars.di

import app.aaps.pump.danars.comm.DanaRSPacket
import dagger.Module
import dagger.Provides

@Module(
    includes = [
        DanaRSCommModule::class,
        DanaRSActivitiesModule::class,
        DanaRSServicesModule::class,
        DanaRSCommandsModule::class
    ]
)
open class DanaRSModule {

    @Provides
    fun providesCommands(
        @DanaRSCommandsModule.DanaRSCommand rsCommands: Set<@JvmSuppressWildcards DanaRSPacket>,
    )
        : Set<@JvmSuppressWildcards DanaRSPacket> = rsCommands
}