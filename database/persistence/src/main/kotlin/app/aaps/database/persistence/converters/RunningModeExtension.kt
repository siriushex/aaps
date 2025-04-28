package app.aaps.database.persistence.converters

import app.aaps.core.data.model.RM
import app.aaps.database.entities.RunningMode

fun RunningMode.Mode.fromDb(): RM.Mode =
    when (this) {
        RunningMode.Mode.DISABLED_LOOP     -> RM.Mode.DISABLED_LOOP
        RunningMode.Mode.OPEN_LOOP         -> RM.Mode.OPEN_LOOP
        RunningMode.Mode.CLOSED_LOOP       -> RM.Mode.CLOSED_LOOP
        RunningMode.Mode.LGS               -> RM.Mode.LGS
        RunningMode.Mode.SUPER_BOLUS       -> RM.Mode.SUPER_BOLUS
        RunningMode.Mode.DISCONNECTED_PUMP -> RM.Mode.DISCONNECTED_PUMP
        RunningMode.Mode.PUMP_SUSPENDED    -> RM.Mode.PUMP_SUSPENDED
    }

fun RM.Mode.toDb(): RunningMode.Mode =
    when (this) {
        RM.Mode.DISABLED_LOOP     -> RunningMode.Mode.DISABLED_LOOP
        RM.Mode.OPEN_LOOP         -> RunningMode.Mode.OPEN_LOOP
        RM.Mode.CLOSED_LOOP       -> RunningMode.Mode.CLOSED_LOOP
        RM.Mode.LGS               -> RunningMode.Mode.LGS
        RM.Mode.SUPER_BOLUS       -> RunningMode.Mode.SUPER_BOLUS
        RM.Mode.DISCONNECTED_PUMP -> RunningMode.Mode.DISCONNECTED_PUMP
        RM.Mode.PUMP_SUSPENDED    -> RunningMode.Mode.PUMP_SUSPENDED
    }

fun RunningMode.fromDb(): RM =
    RM(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        ids = this.interfaceIDs.fromDb(),
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        mode = this.mode.fromDb(),
        duration = this.duration
    )

fun RM.toDb(): RunningMode =
    RunningMode(
        id = this.id,
        version = this.version,
        dateCreated = this.dateCreated,
        isValid = this.isValid,
        referenceId = this.referenceId,
        interfaceIDs_backing = this.ids.toDb(),
        timestamp = this.timestamp,
        utcOffset = this.utcOffset,
        mode = this.mode.toDb(),
        duration = this.duration
    )

