package app.aaps.pump.danars.di

import app.aaps.pump.danars.comm.DanaRSPacket
import app.aaps.pump.danars.comm.DanaRSPacketAPSBasalSetTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketAPSHistoryEvents
import app.aaps.pump.danars.comm.DanaRSPacketAPSSetEventHistory
import app.aaps.pump.danars.comm.DanaRSPacketBasalGetBasalRate
import app.aaps.pump.danars.comm.DanaRSPacketBasalGetProfileNumber
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetCancelTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetProfileBasalRate
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetProfileNumber
import app.aaps.pump.danars.comm.DanaRSPacketBasalSetTemporaryBasal
import app.aaps.pump.danars.comm.DanaRSPacketBolusGet24CIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetBolusOption
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetCIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetCalculationInformation
import app.aaps.pump.danars.comm.DanaRSPacketBolusGetStepBolusInformation
import app.aaps.pump.danars.comm.DanaRSPacketBolusSet24CIRCFArray
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetExtendedBolus
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetExtendedBolusCancel
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetStepBolusStart
import app.aaps.pump.danars.comm.DanaRSPacketBolusSetStepBolusStop
import app.aaps.pump.danars.comm.DanaRSPacketEtcKeepConnection
import app.aaps.pump.danars.comm.DanaRSPacketGeneralGetPumpCheck
import app.aaps.pump.danars.comm.DanaRSPacketGeneralGetShippingInformation
import app.aaps.pump.danars.comm.DanaRSPacketGeneralInitialScreenInformation
import app.aaps.pump.danars.comm.DanaRSPacketGeneralSetHistoryUploadMode
import app.aaps.pump.danars.comm.DanaRSPacketHistoryAlarm
import app.aaps.pump.danars.comm.DanaRSPacketHistoryBasal
import app.aaps.pump.danars.comm.DanaRSPacketHistoryBloodGlucose
import app.aaps.pump.danars.comm.DanaRSPacketHistoryBolus
import app.aaps.pump.danars.comm.DanaRSPacketHistoryCarbohydrate
import app.aaps.pump.danars.comm.DanaRSPacketHistoryDaily
import app.aaps.pump.danars.comm.DanaRSPacketHistoryPrime
import app.aaps.pump.danars.comm.DanaRSPacketHistoryRefill
import app.aaps.pump.danars.comm.DanaRSPacketHistorySuspend
import app.aaps.pump.danars.comm.DanaRSPacketNotifyAlarm
import app.aaps.pump.danars.comm.DanaRSPacketNotifyDeliveryComplete
import app.aaps.pump.danars.comm.DanaRSPacketNotifyDeliveryRateDisplay
import app.aaps.pump.danars.comm.DanaRSPacketNotifyMissedBolusAlarm
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetPumpTime
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetPumpUTCAndTimeZone
import app.aaps.pump.danars.comm.DanaRSPacketOptionGetUserOption
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetPumpTime
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetPumpUTCAndTimeZone
import app.aaps.pump.danars.comm.DanaRSPacketOptionSetUserOption
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import javax.inject.Qualifier

@Module
@Suppress("unused")
interface DanaRSCommModule {

    /*
     * Only packets which are not respond to sent packet must be listed
     */
    @Binds
    @DanaRSCommand
    @IntoSet
    fun bindDanaRSPacketNotifyAlarm(packet: DanaRSPacketNotifyAlarm): DanaRSPacket

    @Binds
    @DanaRSCommand
    @IntoSet
    fun bindDanaRSPacketNotifyDeliveryComplete(packet: DanaRSPacketNotifyDeliveryComplete): DanaRSPacket

    @Binds
    @DanaRSCommand
    @IntoSet
    fun bindDanaRSPacketNotifyDeliveryRateDisplay(packet: DanaRSPacketNotifyDeliveryRateDisplay): DanaRSPacket

    @Binds
    @DanaRSCommand
    @IntoSet
    fun bindDanaRSPacketNotifyMissedBolusAlarm(packet: DanaRSPacketNotifyMissedBolusAlarm): DanaRSPacket

    @Qualifier
    annotation class DanaRSCommand

    /*
     * The rest only bind
     */
    @Binds fun bindsDanaRSPacket(packet: DanaRSPacket): DanaRSPacket
    @Binds fun bindsDanaRSPacketAPSBasalSetTemporaryBasal(packet: DanaRSPacketAPSBasalSetTemporaryBasal): DanaRSPacket
    @Binds fun bindsDanaRSPacketAPSHistoryEvents(packet: DanaRSPacketAPSHistoryEvents): DanaRSPacket
    @Binds fun bindsDanaRSPacketAPSSetEventHistory(packet: DanaRSPacketAPSSetEventHistory): DanaRSPacket
    @Binds fun bindsDanaRSPacketBasalGetBasalRate(packet: DanaRSPacketBasalGetBasalRate): DanaRSPacket
    @Binds fun bindsDanaRSPacketBasalGetProfileNumber(packet: DanaRSPacketBasalGetProfileNumber): DanaRSPacket
    @Binds fun bindsDanaRSPacketBasalSetCancelTemporaryBasal(packet: DanaRSPacketBasalSetCancelTemporaryBasal): DanaRSPacket
    @Binds fun bindsDanaRSPacketBasalSetProfileBasalRate(packet: DanaRSPacketBasalSetProfileBasalRate): DanaRSPacket
    @Binds fun bindsDanaRSPacketBasalSetProfileNumber(packet: DanaRSPacketBasalSetProfileNumber): DanaRSPacket
    @Binds fun bindsDanaRSPacketBasalSetTemporaryBasal(packet: DanaRSPacketBasalSetTemporaryBasal): DanaRSPacket
    @Binds fun bindsDanaRSPacketBolusGet24CIRCFArray(packet: DanaRSPacketBolusGet24CIRCFArray): DanaRSPacket
    @Binds fun bindsDanaRSPacketBolusGetBolusOption(packet: DanaRSPacketBolusGetBolusOption): DanaRSPacket
    @Binds fun bindsDanaRSPacketBolusGetCalculationInformation(packet: DanaRSPacketBolusGetCalculationInformation): DanaRSPacket
    @Binds fun bindsDanaRSPacketBolusGetCIRCFArray(packet: DanaRSPacketBolusGetCIRCFArray): DanaRSPacket
    @Binds fun bindsDanaRSPacketBolusGetStepBolusInformation(packet: DanaRSPacketBolusGetStepBolusInformation): DanaRSPacket
    @Binds fun bindsDanaRSPacketBolusSet24CIRCFArray(packet: DanaRSPacketBolusSet24CIRCFArray): DanaRSPacket
    @Binds fun bindsDanaRSPacketBolusSetExtendedBolus(packet: DanaRSPacketBolusSetExtendedBolus): DanaRSPacket
    @Binds fun bindsDanaRSPacketBolusSetExtendedBolusCancel(packet: DanaRSPacketBolusSetExtendedBolusCancel): DanaRSPacket
    @Binds fun bindsDanaRSPacketBolusSetStepBolusStart(packet: DanaRSPacketBolusSetStepBolusStart): DanaRSPacket
    @Binds fun bindsDanaRSPacketBolusSetStepBolusStop(packet: DanaRSPacketBolusSetStepBolusStop): DanaRSPacket
    @Binds fun bindsDanaRSPacketEtcKeepConnection(packet: DanaRSPacketEtcKeepConnection): DanaRSPacket
    @Binds fun bindsDanaRSPacketGeneralGetPumpCheck(packet: DanaRSPacketGeneralGetPumpCheck): DanaRSPacket
    @Binds fun bindsDanaRSPacketGeneralGetShippingInformation(packet: DanaRSPacketGeneralGetShippingInformation): DanaRSPacket
    @Binds fun bindsDanaRSPacketGeneralInitialScreenInformation(packet: DanaRSPacketGeneralInitialScreenInformation): DanaRSPacket
    @Binds fun bindsDanaRSPacketGeneralSetHistoryUploadMode(packet: DanaRSPacketGeneralSetHistoryUploadMode): DanaRSPacket
    @Binds fun bindsDanaRSPacketOptionGetPumpTime(packet: DanaRSPacketOptionGetPumpTime): DanaRSPacket
    @Binds fun bindsDanaRSPacketOptionGetUserOption(packet: DanaRSPacketOptionGetUserOption): DanaRSPacket
    @Binds fun bindsDanaRSPacketOptionSetPumpTime(packet: DanaRSPacketOptionSetPumpTime): DanaRSPacket
    @Binds fun bindsDanaRSPacketOptionSetUserOption(packet: DanaRSPacketOptionSetUserOption): DanaRSPacket
    @Binds fun bindsDanaRSPacketHistoryAlarm(packet: DanaRSPacketHistoryAlarm): DanaRSPacket
    @Binds fun bindsDanaRSPacketHistoryBasal(packet: DanaRSPacketHistoryBasal): DanaRSPacket
    @Binds fun bindsDanaRSPacketHistoryBloodGlucose(packet: DanaRSPacketHistoryBloodGlucose): DanaRSPacket
    @Binds fun bindsDanaRSPacketHistoryBolus(packet: DanaRSPacketHistoryBolus): DanaRSPacket
    @Binds fun bindsDanaRSPacketHistoryCarbohydrate(packet: DanaRSPacketHistoryCarbohydrate): DanaRSPacket
    @Binds fun bindsDanaRSPacketHistoryDaily(packet: DanaRSPacketHistoryDaily): DanaRSPacket
    @Binds fun bindsDanaRSPacketHistoryPrime(packet: DanaRSPacketHistoryPrime): DanaRSPacket
    @Binds fun bindsDanaRSPacketHistoryRefill(packet: DanaRSPacketHistoryRefill): DanaRSPacket
    @Binds fun bindsDanaRSPacketHistorySuspend(packet: DanaRSPacketHistorySuspend): DanaRSPacket
    @Binds fun bindsDanaRSPacketOptionGetPumpUTCAndTimeZone(packet: DanaRSPacketOptionGetPumpUTCAndTimeZone): DanaRSPacket
    @Binds fun bindsDanaRSPacketOptionSetPumpUTCAndTimeZone(packet: DanaRSPacketOptionSetPumpUTCAndTimeZone): DanaRSPacket
}