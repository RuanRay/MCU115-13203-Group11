package com.group11.hometide.ui.result

import android.content.Context
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.TimetableCache
import com.group11.hometide.data.local.entity.TripReminder
import com.group11.hometide.databinding.DialogReminderBinding
import com.group11.hometide.util.ReminderRules
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 出發提醒單選對話框（FR-RMD-01），P4 存根與下一班主車票共用。
 * 「發車時間 − 提前分鐘」已過的選項停用；已設定時預選原分鐘數並多一個「取消提醒」。
 */
object ReminderDialog {

    private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** trip_reminder.train_type_name：鐵路為車種（自強），國道客運為「業者 路線號」（FR-BUS-05） */
    fun typeName(context: Context, train: TimetableCache): String =
        if (train.operator == Operator.BUS) {
            context.getString(R.string.bus_meta, train.trainTypeName.orEmpty(), train.trainTypeCode ?: train.trainNo)
        } else {
            train.trainTypeName.orEmpty()
        }

    fun show(
        context: Context,
        train: TimetableCache,
        existing: TripReminder?,
        onSet: (beforeMin: Int) -> Unit,
        onCancelReminder: () -> Unit,
        now: Long = System.currentTimeMillis(),
    ) {
        val b = DialogReminderBinding.inflate(LayoutInflater.from(context))
        val departAt = ReminderRules.departAt(train.travelDate, train.departTime)
        val buttons = listOf(b.rbBefore15, b.rbBefore30, b.rbBefore60)
        ReminderRules.OPTIONS.zip(buttons).forEach { (min, rb) ->
            val at = Instant.ofEpochMilli(ReminderRules.triggerAt(departAt, min)).atZone(ZoneId.systemDefault()).format(HHMM)
            rb.text = context.getString(R.string.reminder_option, min, at)
            rb.isEnabled = ReminderRules.isAvailable(departAt, min, now)
        }
        // 預選：原設定的分鐘數；沒有時選第一個可用的
        val preselect = ReminderRules.OPTIONS.indexOf(existing?.remindBeforeMin).takeIf { it >= 0 && buttons[it].isEnabled }
            ?: buttons.indexOfFirst { it.isEnabled }
        if (preselect >= 0) buttons[preselect].isChecked = true

        val title = if (train.operator == Operator.BUS) {
            context.getString(R.string.reminder_dialog_title_bus, typeName(context, train), train.departTime)
        } else {
            context.getString(R.string.reminder_dialog_title_rail, train.trainTypeName.orEmpty(), train.trainNo, train.departTime)
        }
        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(title)
            .setView(b.root)
            .setNegativeButton(R.string.btn_cancel, null)
            .setPositiveButton(R.string.btn_confirm) { _, _ ->
                val i = buttons.indexOfFirst { it.isChecked && it.isEnabled }
                if (i >= 0) onSet(ReminderRules.OPTIONS[i])
            }
        if (existing != null) builder.setNeutralButton(R.string.btn_cancel_reminder) { _, _ -> onCancelReminder() }
        val dialog = builder.show()
        // 三個選項都已過時不能確定
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = preselect >= 0
    }
}
