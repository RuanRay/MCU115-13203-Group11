package com.group11.hometide.receiver

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.ReminderStatus
import com.group11.hometide.data.local.entity.TripReminder
import com.group11.hometide.util.ReminderRules
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** FR-RMD-03 通知內容；TC-40／FR-BUS-05：國道客運以「業者 路線號」並加註「實際以現場為準」 */
@RunWith(AndroidJUnit4::class)
class ReminderTextTest {

    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun reminder(op: Operator, no: String, type: String, o: String, d: String) = TripReminder(
        1, "u1", op, no, type, o, d, ReminderRules.departAt("2026-11-18", "14:30"), 15,
        ReminderRules.departAt("2026-11-18", "14:15"), ReminderStatus.SCHEDULED,
    )

    @Test
    fun rail() {
        assertEquals(
            "自強 139 次 14:30 桃園 → 臺南",
            ReminderReceiver.contentText(ctx, reminder(Operator.TRA, "139", "自強", "桃園", "臺南")),
        )
    }

    @Test
    fun bus_tc40() {
        assertEquals(
            "統聯客運 1610 14:30 臺北轉運站 → 臺南轉運站（實際以現場為準）",
            ReminderReceiver.contentText(ctx, reminder(Operator.BUS, "1610-14:30", "統聯客運 1610", "臺北轉運站", "臺南轉運站")),
        )
    }
}
