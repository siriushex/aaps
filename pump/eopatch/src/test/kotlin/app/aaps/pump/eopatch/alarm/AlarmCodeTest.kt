package app.aaps.pump.eopatch.alarm

import android.content.Intent
import android.net.Uri
import app.aaps.pump.eopatch.code.AlarmCategory
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class AlarmCodeTest {

    @Test
    fun `should have exactly 28 alarm codes`() {
        assertThat(AlarmCode.entries).hasSize(29)
    }

    @Test
    fun `should contain all A-type alarm codes`() {
        val aCodes = AlarmCode.entries.filter { it.type == 'A' }

        assertThat(aCodes).hasSize(22)
        assertThat(aCodes).contains(AlarmCode.A002)
        assertThat(aCodes).contains(AlarmCode.A003)
        assertThat(aCodes).contains(AlarmCode.A004)
        assertThat(aCodes).contains(AlarmCode.A118)
    }

    @Test
    fun `should contain all B-type alert codes`() {
        val bCodes = AlarmCode.entries.filter { it.type == 'B' }

        assertThat(bCodes).hasSize(7)
        assertThat(bCodes).contains(AlarmCode.B000)
        assertThat(bCodes).contains(AlarmCode.B001)
        assertThat(bCodes).contains(AlarmCode.B018)
    }

    @Test
    fun `type should be correctly extracted from name`() {
        assertThat(AlarmCode.A002.type).isEqualTo('A')
        assertThat(AlarmCode.B003.type).isEqualTo('B')
        assertThat(AlarmCode.A118.type).isEqualTo('A')
    }

    @Test
    fun `code should be correctly extracted from name`() {
        assertThat(AlarmCode.A002.code).isEqualTo(2)
        assertThat(AlarmCode.B003.code).isEqualTo(3)
        assertThat(AlarmCode.A118.code).isEqualTo(118)
        assertThat(AlarmCode.B000.code).isEqualTo(0)
    }

    @Test
    fun `A-type alarms should have ALARM category`() {
        assertThat(AlarmCode.A002.alarmCategory).isEqualTo(AlarmCategory.ALARM)
        assertThat(AlarmCode.A003.alarmCategory).isEqualTo(AlarmCategory.ALARM)
        assertThat(AlarmCode.A118.alarmCategory).isEqualTo(AlarmCategory.ALARM)
    }

    @Test
    fun `B-type alerts should have ALERT category`() {
        assertThat(AlarmCode.B000.alarmCategory).isEqualTo(AlarmCategory.ALERT)
        assertThat(AlarmCode.B003.alarmCategory).isEqualTo(AlarmCategory.ALERT)
        assertThat(AlarmCode.B018.alarmCategory).isEqualTo(AlarmCategory.ALERT)
    }

    @Test
    fun `aeCode should be code plus 100 for A-type alarms`() {
        assertThat(AlarmCode.A002.aeCode).isEqualTo(102) // 2 + 100
        assertThat(AlarmCode.A003.aeCode).isEqualTo(103) // 3 + 100
        assertThat(AlarmCode.A118.aeCode).isEqualTo(218) // 118 + 100
    }

    @Test
    fun `aeCode should be code for B-type alerts`() {
        assertThat(AlarmCode.B000.aeCode).isEqualTo(0)
        assertThat(AlarmCode.B003.aeCode).isEqualTo(3)
        assertThat(AlarmCode.B018.aeCode).isEqualTo(18)
    }

    @Test
    fun `isPatchOccurrenceAlert should identify correct alerts`() {
        assertThat(AlarmCode.B003.isPatchOccurrenceAlert).isTrue() // Low reservoir
        assertThat(AlarmCode.B005.isPatchOccurrenceAlert).isTrue() // Patch expired
        assertThat(AlarmCode.B006.isPatchOccurrenceAlert).isTrue() // Patch will expire
        assertThat(AlarmCode.B018.isPatchOccurrenceAlert).isTrue() // Battery low

        assertThat(AlarmCode.B000.isPatchOccurrenceAlert).isFalse()
        assertThat(AlarmCode.B001.isPatchOccurrenceAlert).isFalse()
        assertThat(AlarmCode.A002.isPatchOccurrenceAlert).isFalse()
    }

    @Test
    fun `isPatchOccurrenceAlarm should identify correct alarms`() {
        assertThat(AlarmCode.A002.isPatchOccurrenceAlarm).isTrue() // Empty reservoir
        assertThat(AlarmCode.A003.isPatchOccurrenceAlarm).isTrue() // Patch expired
        assertThat(AlarmCode.A004.isPatchOccurrenceAlarm).isTrue() // Occlusion
        assertThat(AlarmCode.A018.isPatchOccurrenceAlarm).isTrue()
        assertThat(AlarmCode.A118.isPatchOccurrenceAlarm).isTrue()

        assertThat(AlarmCode.A005.isPatchOccurrenceAlarm).isFalse() // Self test failure - not in list
        assertThat(AlarmCode.B003.isPatchOccurrenceAlarm).isFalse()
    }

    @Test
    fun `fromStringToCode should return correct alarm code`() {
        assertThat(AlarmCode.fromStringToCode("A002")).isEqualTo(AlarmCode.A002)
        assertThat(AlarmCode.fromStringToCode("B003")).isEqualTo(AlarmCode.B003)
        assertThat(AlarmCode.fromStringToCode("A118")).isEqualTo(AlarmCode.A118)
    }

    @Test
    fun `fromStringToCode should return null for invalid code`() {
        assertThat(AlarmCode.fromStringToCode("X999")).isNull()
        assertThat(AlarmCode.fromStringToCode("")).isNull()
        assertThat(AlarmCode.fromStringToCode("invalid")).isNull()
    }

    @Test
    fun `findByPatchAeCode should find A-type alarms correctly`() {
        assertThat(AlarmCode.findByPatchAeCode(102)).isEqualTo(AlarmCode.A002) // 102 - 100 = 2
        assertThat(AlarmCode.findByPatchAeCode(103)).isEqualTo(AlarmCode.A003)
        assertThat(AlarmCode.findByPatchAeCode(218)).isEqualTo(AlarmCode.A118)
    }

    @Test
    fun `findByPatchAeCode should find B-type alerts correctly`() {
        assertThat(AlarmCode.findByPatchAeCode(0)).isEqualTo(AlarmCode.B000)
        assertThat(AlarmCode.findByPatchAeCode(3)).isEqualTo(AlarmCode.B003)
        assertThat(AlarmCode.findByPatchAeCode(18)).isEqualTo(AlarmCode.B018)
    }

    @Test
    fun `findByPatchAeCode should return null for non-existent code`() {
        assertThat(AlarmCode.findByPatchAeCode(999)).isNull()
        assertThat(AlarmCode.findByPatchAeCode(-1)).isNull()
    }

    @Test
    fun `getUri should create correct URI for alarm code`() {
        val uri = AlarmCode.getUri(AlarmCode.A002)

        assertThat(uri.scheme).isEqualTo("alarmkey")
        assertThat(uri.authority).isEqualTo("info.nightscout.androidaps")
        assertThat(uri.lastPathSegment).isEqualTo("alarmkey")
        assertThat(uri.getQueryParameter("alarmcode")).isEqualTo("A002")
    }

    @Test
    fun `getUri should work for all alarm codes`() {
        val uriA = AlarmCode.getUri(AlarmCode.A118)
        assertThat(uriA.getQueryParameter("alarmcode")).isEqualTo("A118")

        val uriB = AlarmCode.getUri(AlarmCode.B003)
        assertThat(uriB.getQueryParameter("alarmcode")).isEqualTo("B003")
    }

    @Test
    fun `getAlarmCode should extract alarm code from valid URI`() {
        val uri = AlarmCode.getUri(AlarmCode.A002)
        val alarmCode = AlarmCode.getAlarmCode(uri)

        assertThat(alarmCode).isEqualTo(AlarmCode.A002)
    }

    @Test
    fun `getAlarmCode should return null for invalid URI scheme`() {
        val uri = Uri.parse("http://example.com/alarmkey?alarmcode=A002")

        assertThat(AlarmCode.getAlarmCode(uri)).isNull()
    }

    @Test
    @Disabled("Uri.Builder not available in unit tests without Robolectric")
    fun `getAlarmCode should return null for invalid URI path`() {
        val uri = Uri.Builder()
            .scheme("alarmkey")
            .authority("info.nightscout.androidaps")
            .path("wrongpath")
            .appendQueryParameter("alarmcode", "A002")
            .build()

        assertThat(AlarmCode.getAlarmCode(uri)).isNull()
    }

    @Test
    @Disabled("Uri.Builder not available in unit tests without Robolectric")
    fun `getAlarmCode should return null for missing query parameter`() {
        val uri = Uri.Builder()
            .scheme("alarmkey")
            .authority("info.nightscout.androidaps")
            .path("alarmkey")
            .build()

        assertThat(AlarmCode.getAlarmCode(uri)).isNull()
    }

    @Test
    fun `fromIntent should extract alarm code from intent with data`() {
        val uri = AlarmCode.getUri(AlarmCode.A003)
        val intent = Intent().apply { data = uri }

        val alarmCode = AlarmCode.fromIntent(intent)

        assertThat(alarmCode).isEqualTo(AlarmCode.A003)
    }

    @Test
    fun `fromIntent should return null for intent without data`() {
        val intent = Intent()

        assertThat(AlarmCode.fromIntent(intent)).isNull()
    }

    @Test
    fun `all alarm codes should have valid resource IDs`() {
        AlarmCode.entries.forEach { alarmCode ->
            assertThat(alarmCode.resId).isGreaterThan(0)
        }
    }

    @Test
    fun `TYPE_ALARM constant should be A`() {
        assertThat(AlarmCode.TYPE_ALARM).isEqualTo('A')
    }

    @Test
    fun `TYPE_ALERT constant should be B`() {
        assertThat(AlarmCode.TYPE_ALERT).isEqualTo('B')
    }

    @Test
    fun `round trip URI conversion should preserve alarm code`() {
        AlarmCode.entries.forEach { original ->
            val uri = AlarmCode.getUri(original)
            val recovered = AlarmCode.getAlarmCode(uri)
            assertThat(recovered).isEqualTo(original)
        }
    }
}
