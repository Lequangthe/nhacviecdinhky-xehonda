// SPDX-FileCopyrightText: 2026 Kapoué
// SPDX-License-Identifier: GPL-3.0-or-later

package com.quangthe.nhacviec.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.quangthe.nhacviec.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val intervalDays: Int,
    val lastDoneAt: Long,
    val iconKey: String,
    @ColumnInfo(defaultValue = "0")    val snoozedUntil: Long     = 0L,
    @ColumnInfo(defaultValue = "")     val note: String           = "",
    @ColumnInfo(defaultValue = "DAYS") val recurrenceType: String = "DAYS",
    @ColumnInfo(defaultValue = "0")    val weekDays: Int          = 0,
    // Bitmask : bit 0 = ngày cuối tháng, bits 1-31 = các ngày 1-31
    @ColumnInfo(defaultValue = "0")    val monthDays: Int         = 0,
    @ColumnInfo(defaultValue = "0")    val isDisabled: Boolean    = false,
    // ONE_SHOT ngày cố định (0 = một ngày nào đó, >0 = ngày đến hạn)
    @ColumnInfo(defaultValue = "0")    val targetDate: Long       = 0L,
    @ColumnInfo(defaultValue = "NULL") val vehicleId: Int?        = null,
    @ColumnInfo(defaultValue = "0")    val intervalKm: Int        = 0,
    @ColumnInfo(defaultValue = "0")    val lastDoneAtKm: Int      = 0
)

// Tác vụ một lần được đánh dấu là xong (lastDoneAt > 0 có nghĩa là "xong")
val Task.isOneShotDone: Boolean
    get() = recurrenceType == "ONE_SHOT" && lastDoneAt > 0L

val Task.nextDueAt: Long
    get() {
        return when (recurrenceType) {
            "ONE_SHOT" -> when {
                lastDoneAt > 0L -> lastDoneAt
                targetDate > 0L -> targetDate
                else            -> Long.MAX_VALUE / 2
            }
            "WEEKLY" -> {
                if (weekDays == 0) return lastDoneAt + 7 * 86_400_000L
                val cal = Calendar.getInstance().apply {
                    timeInMillis = lastDoneAt
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
                }
                for (i in 0 until 7) {
                    if ((weekDays shr cal.get(Calendar.DAY_OF_WEEK)) and 1 == 1)
                        return cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                lastDoneAt + 7 * 86_400_000L
            }
            "MONTHLY" -> {
                if (monthDays == 0) return lastDoneAt + 30 * 86_400_000L
                val cal = Calendar.getInstance().apply {
                    timeInMillis = lastDoneAt
                    add(Calendar.DAY_OF_YEAR, 1)
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
                }
                for (i in 0 until 62) {
                    val dom    = cal.get(Calendar.DAY_OF_MONTH)
                    val maxDom = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val isLastDay   = (monthDays and 1 == 1) && dom == maxDom
                    val isDayBitSet = dom <= 31 && (monthDays shr dom) and 1 == 1
                    if (isLastDay || isDayBitSet) return cal.timeInMillis
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                lastDoneAt + 30 * 86_400_000L
            }
            else -> lastDoneAt + intervalDays * 86_400_000L
        }
    }

// Snooze non applicable aux ONE_SHOT
val Task.isSnoozed: Boolean
    get() = recurrenceType != "ONE_SHOT" &&
            snoozedUntil > System.currentTimeMillis() &&
            snoozedUntil > nextDueAt

val Task.effectiveDueAt: Long
    get() = if (isSnoozed) snoozedUntil else nextDueAt

val Task.daysRemaining: Int
    get() {
        val due = effectiveDueAt
        if (due >= Long.MAX_VALUE / 2) return Int.MAX_VALUE
        val dueCal = Calendar.getInstance().apply {
            timeInMillis = due
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }
        val todayCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
        }
        return ((dueCal.timeInMillis - todayCal.timeInMillis) / 86_400_000L).toInt()
    }

// Nhãn định kỳ cho TaskCard (dòng thứ 3)
fun Task.recurrenceLabel(context: Context): String? = when {
    isDisabled || isOneShotDone -> null
    recurrenceType == "ONE_SHOT" -> null
    recurrenceType == "DAYS" ->
        if (intervalDays == 1) context.getString(R.string.rec_every_day)
        else context.getString(R.string.rec_every_n_days, intervalDays)
    recurrenceType == "WEEKLY" -> {
        val names = context.resources.getStringArray(R.array.day_short_names)
        val days = (1..7).filter { (weekDays shr it) and 1 == 1 }.joinToString(", ") { names[it] }
        if (days.isEmpty()) context.getString(R.string.rec_weekly)
        else context.getString(R.string.rec_weekly_days, days)
    }
    recurrenceType == "MONTHLY" -> {
        val parts = (1..31).filter { (monthDays shr it) and 1 == 1 }
            .map { if (it == 1) context.getString(R.string.month_first_ordinal) else "$it" }.toMutableList()
        if (monthDays and 1 == 1) parts.add(context.getString(R.string.month_last_day_short))
        if (parts.isEmpty()) context.getString(R.string.rec_monthly)
        else context.getString(R.string.rec_monthly_days, parts.joinToString(", "))
    }
    else -> null
}

fun Task.kmRemaining(currentMileage: Int): Int {
    if (intervalKm <= 0) return Int.MAX_VALUE
    return intervalKm - (currentMileage - lastDoneAtKm)
}

fun Task.kmStatusLabel(context: Context, currentMileage: Int): String? {
    if (intervalKm <= 0) return null
    val remaining = kmRemaining(currentMileage)
    return if (remaining < 0) context.getString(R.string.status_km_overdue, -remaining)
    else context.getString(R.string.status_km_remaining, remaining)
}

// Nhãn trạng thái (đến hạn) — được chia sẻ giữa TaskCard và trang hành động
fun Task.statusLabel(context: Context, currentMileage: Int = 0): String {
    val days = daysRemaining
    val kmLabel = kmStatusLabel(context, currentMileage)
    
    val dueDateLabel: String = run {
        if (isOneShotDone || (recurrenceType == "ONE_SHOT" && targetDate == 0L)) return@run ""
        val cal = Calendar.getInstance().apply { timeInMillis = effectiveDueAt }
        val today = Calendar.getInstance()
        val fmt = if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR))
            SimpleDateFormat("d MMM", Locale.getDefault())
        else
            SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        fmt.format(cal.time)
    }
    return when {
        isDisabled    -> context.getString(R.string.status_paused)
        isOneShotDone -> {
            if (targetDate == 0L) {
                val cal = Calendar.getInstance().apply { timeInMillis = lastDoneAt }
                val today = Calendar.getInstance()
                val fmt = if (cal.get(Calendar.YEAR) == today.get(Calendar.YEAR))
                    SimpleDateFormat("d MMM", Locale.getDefault())
                else
                    SimpleDateFormat("d MMM yyyy", Locale.getDefault())
                context.getString(R.string.status_event_done, fmt.format(cal.time))
            } else {
                val daysLeft = (7 - ((System.currentTimeMillis() - lastDoneAt) / 86_400_000L).toInt()).coerceAtLeast(0)
                when {
                    daysLeft > 1 -> context.getString(R.string.status_done_days, daysLeft)
                    daysLeft == 1 -> context.getString(R.string.status_done_tomorrow)
                    else -> context.getString(R.string.status_done_today)
                }
            }
        }
        recurrenceType == "ONE_SHOT" && targetDate == 0L -> context.getString(R.string.status_someday)
        isSnoozed -> if (days <= 1) context.getString(R.string.status_snoozed_tomorrow, dueDateLabel)
                     else context.getString(R.string.status_snoozed_days, days, dueDateLabel)
        days < -1  -> context.getString(R.string.status_overdue_days, -days, dueDateLabel)
        days == -1 -> context.getString(R.string.status_overdue_1day, dueDateLabel)
        days == 0  -> context.getString(R.string.status_today, dueDateLabel)
        days == 1  -> context.getString(R.string.status_tomorrow, dueDateLabel)
        else       -> context.getString(R.string.status_in_days, days, dueDateLabel)
    }.let { timeLabel ->
        if (kmLabel != null) {
            if (timeLabel.isEmpty()) kmLabel else "$timeLabel · $kmLabel"
        } else timeLabel
    }
}
