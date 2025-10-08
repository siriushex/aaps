package app.aaps.pump.danars.di

import app.aaps.pump.danars.comm.DanaRSPacket
import app.aaps.pump.danars.comm.DanaRSPacketNotifyAlarm
import app.aaps.pump.danars.comm.DanaRSPacketNotifyDeliveryComplete
import app.aaps.pump.danars.comm.DanaRSPacketNotifyDeliveryRateDisplay
import app.aaps.pump.danars.comm.DanaRSPacketNotifyMissedBolusAlarm
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import javax.inject.Qualifier

@Suppress("unused")
@Module
/**
 * Only packets which are not respond to sent packet must be listed
 */
abstract class DanaRSCommandsModule {

    @Binds
    @DanaRSCommand
    @IntoSet
    abstract fun bindDanaRSPacketNotifyAlarm(packet: DanaRSPacketNotifyAlarm): DanaRSPacket

    @Binds
    @DanaRSCommand
    @IntoSet
    abstract fun bindDanaRSPacketNotifyDeliveryComplete(packet: DanaRSPacketNotifyDeliveryComplete): DanaRSPacket

    @Binds
    @DanaRSCommand
    @IntoSet
    abstract fun bindDanaRSPacketNotifyDeliveryRateDisplay(packet: DanaRSPacketNotifyDeliveryRateDisplay): DanaRSPacket

    @Binds
    @DanaRSCommand
    @IntoSet
    abstract fun bindDanaRSPacketNotifyMissedBolusAlarm(packet: DanaRSPacketNotifyMissedBolusAlarm): DanaRSPacket

    @Qualifier
    annotation class DanaRSCommand
}