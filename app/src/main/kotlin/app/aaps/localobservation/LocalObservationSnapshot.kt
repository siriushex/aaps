package app.aaps.localobservation

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.profile.ProfileSealed
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal suspend fun readLocalObservation(db: PersistenceLayer, dateUtil: DateUtil, now: Long): ByteArray {
    val from = now - 86_400_000L
    val samples = db.getBgReadingsDataFromTimeToTime(now - 30 * 60_000L, now, false)
    val profile = db.getEffectiveProfileSwitchActiveAt(now)
    val boluses = db.getBolusesFromTimeToTime(from, now, true).filter { it.type != BS.Type.PRIMING && it.amount > 0 }
    val basals = db.getTemporaryBasalsStartingFromTimeToTime(from, now, true)
    val extended = db.getExtendedBolusesStartingFromTimeToTime(from, now, true)
    val count = boluses.size + basals.size + extended.size
    check(samples.size <= 256 && count <= 512)
    return buildJsonObject {
        put("version", 1)
        put("at", now)
        put("glucose", buildJsonArray {
            samples.filter { it.isValid }.forEach { sample ->
                add(buildJsonObject {
                    put("date", sample.timestamp)
                    put("sgv", sample.value)
                    put("sensor", sample.sourceSensor.name)
                })
            }
        })
        if (profile != null && profile.isValid) {
            put("profile", buildJsonObject {
                put("date", profile.timestamp)
                put("utcOffset", profile.utcOffset / 60_000L)
                put("eventType", "Note")
                put("originalProfileName", profile.originalProfileName)
                put("originalCustomizedName", profile.originalCustomizedName)
                put("originalTimeshift", profile.originalTimeshift)
                put("originalPercentage", profile.originalPercentage)
                put("originalDuration", profile.originalDuration)
                put("originalEnd", profile.originalEnd)
                put("profileJson", ProfileSealed.EPS(profile, null).toPureNsJson(dateUtil).toString())
                put("icfg", profile.iCfg.observationJson())
            })
        }
        put("insulin", buildJsonArray {
            boluses.filter { it.isValid }.forEach { bolus ->
                add(buildJsonObject {
                    put("eventType", "Correction Bolus")
                    put("date", bolus.timestamp)
                    put("utcOffset", bolus.utcOffset / 60_000L)
                    put("insulin", bolus.amount)
                    put("type", bolus.type.name)
                    put("isBasalInsulin", bolus.isBasalInsulin)
                    put("icfg", bolus.iCfg.observationJson())
                    pumpIdentity(bolus.ids)
                })
            }
            basals.filter { it.isValid }.forEach { basal ->
                add(buildJsonObject {
                    put("eventType", "Temp Basal")
                    put("date", basal.timestamp)
                    put("utcOffset", basal.utcOffset / 60_000L)
                    put("durationInMilliseconds", basal.duration)
                    put("type", basal.type.name)
                    if (basal.isAbsolute) put("absolute", basal.rate) else put("percent", basal.rate - 100)
                    pumpIdentity(basal.ids)
                })
            }
            extended.filter { it.isValid }.forEach { bolus ->
                add(buildJsonObject {
                    put("eventType", "Combo Bolus")
                    put("date", bolus.timestamp)
                    put("utcOffset", bolus.utcOffset / 60_000L)
                    put("durationInMilliseconds", bolus.duration)
                    put("enteredinsulin", bolus.amount)
                    put("isEmulatingTempBasal", bolus.isEmulatingTempBasal)
                    pumpIdentity(bolus.ids)
                })
            }
        })
    }.toString().encodeToByteArray().also { check(it.size <= 196_608) }
}

private fun ICfg.observationJson() = buildJsonObject {
    put("insulinLabel", insulinLabel)
    put("insulinEndTime", insulinEndTime)
    put("insulinPeakTime", insulinPeakTime)
    put("concentration", concentration)
}

private fun JsonObjectBuilder.pumpIdentity(ids: IDs) {
    val id = ids.pumpId ?: return
    val type = ids.pumpType ?: return
    val serial = ids.pumpSerial?.takeIf { it.isNotBlank() } ?: return
    put("pumpId", id)
    put("pumpType", type.name)
    put("pumpSerial", serial)
    ids.endId?.let { put("endId", it) }
}
