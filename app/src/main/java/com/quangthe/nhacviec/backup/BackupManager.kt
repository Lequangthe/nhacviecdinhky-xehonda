// SPDX-FileCopyrightText: 2026 Kapoué
// SPDX-License-Identifier: GPL-3.0-or-later

package com.quangthe.nhacviec.backup

import com.quangthe.nhacviec.data.MileageLog
import com.quangthe.nhacviec.data.Task
import com.quangthe.nhacviec.data.TaskHistory
import com.quangthe.nhacviec.data.Vehicle
import org.json.JSONArray
import org.json.JSONObject

data class BackupData(
    val tasks: List<Task>,           // task.id = ID xuất bản gốc (0 nếu trước v6)
    val history: List<TaskHistory>,  // taskId = ID tác vụ xuất bản gốc
    val vehicles: List<Vehicle> = emptyList(),
    val mileageLogs: List<MileageLog> = emptyList()
)

object BackupManager {

    private const val VERSION = 10

    fun exportToJson(
        tasks: List<Task>,
        history: List<TaskHistory>,
        vehicles: List<Vehicle>,
        mileageLogs: List<MileageLog>
    ): String {
        val tasksArray = JSONArray()
        tasks.forEach { task ->
            tasksArray.put(JSONObject().apply {
                put("id",            task.id)
                put("title",         task.title)
                put("intervalDays",  task.intervalDays)
                put("lastDoneAt",    task.lastDoneAt)
                put("iconKey",       task.iconKey)
                put("snoozedUntil",  task.snoozedUntil)
                put("note",          task.note)
                put("recurrenceType",task.recurrenceType)
                put("weekDays",      task.weekDays)
                put("monthDays",     task.monthDays)
                put("isDisabled",    task.isDisabled)
                put("targetDate",    task.targetDate)
                put("vehicleId",     task.vehicleId ?: JSONObject.NULL)
                put("intervalKm",    task.intervalKm)
                put("lastDoneAtKm",  task.lastDoneAtKm)
            })
        }
        val historyArray = JSONArray()
        history.forEach { entry ->
            historyArray.put(JSONObject().apply {
                put("taskId", entry.taskId)
                put("doneAt", entry.doneAt)
                put("doneKm", entry.doneKm)
                put("imagePath", entry.imagePath ?: JSONObject.NULL)
            })
        }
        val vehiclesArray = JSONArray()
        vehicles.forEach { v ->
            vehiclesArray.put(JSONObject().apply {
                put("id", v.id)
                put("name", v.name)
                put("currentMileage", v.currentMileage)
                put("lastMileageUpdate", v.lastMileageUpdate)
            })
        }
        val logsArray = JSONArray()
        mileageLogs.forEach { log ->
            logsArray.put(JSONObject().apply {
                put("vehicleId", log.vehicleId)
                put("mileage", log.mileage)
                put("recordedAt", log.recordedAt)
                put("imagePath", log.imagePath ?: JSONObject.NULL)
            })
        }
        
        return JSONObject().apply {
            put("version", VERSION)
            put("tasks", tasksArray)
            put("history", historyArray)
            put("vehicles", vehiclesArray)
            put("mileageLogs", logsArray)
        }.toString(2)
    }

    fun importFromJson(json: String): BackupData {
        val root = JSONObject(json)
        val version = root.optInt("version", 1)
        if (version > VERSION) {
            throw IllegalArgumentException("Định dạng sao lưu không được hỗ trợ (phiên bản $version)")
        }
        val tasksArray = root.getJSONArray("tasks")
        val tasks = (0 until tasksArray.length()).map { i ->
            tasksArray.getJSONObject(i).let { obj ->
                Task(
                    id             = obj.optInt("id", 0),
                    title          = obj.getString("title"),
                    intervalDays   = obj.getInt("intervalDays"),
                    lastDoneAt     = obj.getLong("lastDoneAt"),
                    iconKey        = obj.getString("iconKey"),
                    snoozedUntil   = obj.optLong("snoozedUntil", 0L),
                    note           = obj.optString("note", ""),
                    recurrenceType = obj.optString("recurrenceType", "DAYS"),
                    weekDays       = obj.optInt("weekDays", 0),
                    monthDays      = obj.optInt("monthDays", 0),
                    isDisabled     = obj.optBoolean("isDisabled", false),
                    targetDate     = obj.optLong("targetDate", 0L),
                    vehicleId      = if (obj.isNull("vehicleId")) null else obj.optInt("vehicleId"),
                    intervalKm     = obj.optInt("intervalKm", 0),
                    lastDoneAtKm   = obj.optInt("lastDoneAtKm", 0)
                )
            }
        }
        val historyArray = root.optJSONArray("history")
        val history = if (historyArray != null) {
            (0 until historyArray.length()).map { i ->
                historyArray.getJSONObject(i).let { obj ->
                    TaskHistory(
                        taskId = obj.getInt("taskId"),
                        doneAt = obj.getLong("doneAt"),
                        doneKm = obj.optInt("doneKm", 0),
                        imagePath = if (obj.isNull("imagePath")) null else obj.optString("imagePath")
                    )
                }
            }
        } else emptyList()

        val vehiclesArray = root.optJSONArray("vehicles")
        val vehicles = if (vehiclesArray != null) {
            (0 until vehiclesArray.length()).map { i ->
                vehiclesArray.getJSONObject(i).let { obj ->
                    Vehicle(
                        id = obj.optInt("id", 0),
                        name = obj.getString("name"),
                        currentMileage = obj.optInt("currentMileage", 0),
                        lastMileageUpdate = obj.optLong("lastMileageUpdate", System.currentTimeMillis())
                    )
                }
            }
        } else emptyList()

        val logsArray = root.optJSONArray("mileageLogs")
        val logs = if (logsArray != null) {
            (0 until logsArray.length()).map { i ->
                logsArray.getJSONObject(i).let { obj ->
                    MileageLog(
                        vehicleId = obj.getInt("vehicleId"),
                        mileage = obj.getInt("mileage"),
                        recordedAt = obj.optLong("recordedAt", System.currentTimeMillis()),
                        imagePath = if (obj.isNull("imagePath")) null else obj.optString("imagePath")
                    )
                }
            }
        } else emptyList()

        return BackupData(tasks, history, vehicles, logs)
    }
}
