/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015 ustwo studio inc (www.ustwo.com)
 *
 * Adapted for AndroidAPS
 */

package app.aaps.wear.watchfaces.utils

import android.text.format.Time

/**
 * Extension of Android's Time class to represent a point in time for watch face applications.
 * Adds millisecond precision and 12-hour format hour tracking.
 */
@Suppress("DEPRECATION")
class WatchFaceTime : Time() {

    /** Milliseconds within the current second (0-999) */
    var millis: Long = 0

    /** Hour in 12-hour format (0-11) */
    var hour12: Int = 0

    init {
        reset()
    }

    fun reset() {
        millis = 0
        hour12 = 0
    }

    override fun setToNow() {
        val now = System.currentTimeMillis()
        set(now)
    }

    override fun set(millis: Long) {
        super.set(millis)
        this.millis = millis
        this.hour12 = if (hour == 0 || hour == 12) 0 else hour % 12
    }

    fun set(other: WatchFaceTime?) {
        if (other != null) {
            super.set(other)
            this.millis = other.millis
            this.hour12 = other.hour12
        }
    }

    fun hasHourChanged(other: WatchFaceTime?): Boolean {
        return other == null || hour != other.hour
    }

    fun hasMinuteChanged(other: WatchFaceTime?): Boolean {
        return other == null || minute != other.minute
    }

    fun hasSecondChanged(other: WatchFaceTime?): Boolean {
        return other == null || second != other.second
    }

    fun hasDateChanged(other: WatchFaceTime?): Boolean {
        return other == null || monthDay != other.monthDay || month != other.month || year != other.year
    }

    fun hasTimeZoneChanged(other: WatchFaceTime?): Boolean {
        return other == null || timezone != other.timezone
    }
}
