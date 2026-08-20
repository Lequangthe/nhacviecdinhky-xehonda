// SPDX-FileCopyrightText: 2026 Kapoué
// SPDX-License-Identifier: GPL-3.0-or-later

package com.quangthe.nhacviec.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quangthe.nhacviec.backup.BackupManager
import com.quangthe.nhacviec.data.MileageLog
import com.quangthe.nhacviec.data.Task
import com.quangthe.nhacviec.data.TaskDatabase
import com.quangthe.nhacviec.data.TaskHistory
import com.quangthe.nhacviec.data.TaskRepository
import com.quangthe.nhacviec.data.Vehicle
import com.quangthe.nhacviec.notification.NotificationHelper
import com.quangthe.nhacviec.notification.NotificationScheduler
import com.quangthe.nhacviec.widget.NhacviecWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UndoItem(
    val task: Task,
    val previousLastDoneAt: Long,
    val previousLastDoneAtKm: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

class TaskViewModel(application: Application) : AndroidViewModel(application) {

    private val db          = TaskDatabase.getInstance(application)
    private val repository  = TaskRepository(db.taskDao(), db.vehicleDao(), db.mileageLogDao())
    private val historyDao  = db.taskHistoryDao()

    val tasks: StateFlow<List<Task>?> = repository.allTasks
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5_000),
            initialValue   = null
        )

    val vehicles: StateFlow<List<Vehicle>> = repository.allVehicles
        .stateIn(
            scope          = viewModelScope,
            started        = SharingStarted.WhileSubscribed(5_000),
            initialValue   = emptyList()
        )

    init {
        viewModelScope.launch { repository.deleteStaleOneShotTasks() }
    }

    // ── Filtre catégorie (affichage seul, en mémoire) ────────────────────────
    // null = « Toutes ». Non persisté : repart à « Toutes » à froid, survit
    // rotation/navigation. N'affecte ni notifications, ni widget, ni échéances.
    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    fun setCategoryFilter(iconKey: String?) { _selectedCategory.value = iconKey }

    fun historyForTask(taskId: Int): Flow<List<TaskHistory>> = historyDao.getForTask(taskId)
    
    fun historyForVehicle(vehicleId: Int): Flow<List<MileageLog>> = db.mileageLogDao().getLogsForVehicle(vehicleId)

    suspend fun getTaskById(id: Int): Task? = repository.getById(id)

    // ── Pile d'annulation partagée (liste + page d'actions widget) ───────────
    private val _undoItems = MutableStateFlow<List<UndoItem>>(emptyList())
    val undoItems: StateFlow<List<UndoItem>> = _undoItems.asStateFlow()

    fun markDoneWithUndo(task: Task, doneAtMillis: Long = System.currentTimeMillis(), doneKm: Int = 0) {
        val prev = task.lastDoneAt
        val prevKm = task.lastDoneAtKm
        markDone(task, doneAtMillis, doneKm)
        _undoItems.value = listOf(UndoItem(task, prev, prevKm)) + _undoItems.value
    }

    fun performUndo(item: UndoItem) {
        undoMarkDone(item.task, item.previousLastDoneAt, item.previousLastDoneAtKm)
        _undoItems.value = _undoItems.value - item
    }

    fun dismissUndo(item: UndoItem) {
        _undoItems.value = _undoItems.value - item
    }

    fun markDone(task: Task, doneAtMillis: Long = System.currentTimeMillis(), doneKm: Int = 0) {
        viewModelScope.launch {
            repository.markDone(task, doneAtMillis, doneKm)
            if (task.recurrenceType != "ONE_SHOT") {
                historyDao.insert(TaskHistory(taskId = task.id, doneAt = doneAtMillis, doneKm = doneKm))
                historyDao.trimForTask(task.id, 6)
                val updated = task.copy(lastDoneAt = doneAtMillis, lastDoneAtKm = doneKm, snoozedUntil = 0L)
                NotificationScheduler.scheduleForTask(getApplication(), updated)
            } else {
                NotificationScheduler.cancelForTask(getApplication(), task.id)
            }
            doUpdateWidget()
        }
    }

    fun addTask(
        title: String,
        intervalDays: Int,
        iconKey: String,
        note: String = "",
        recurrenceType: String = "DAYS",
        weekDays: Int = 0,
        monthDays: Int = 0,
        isDisabled: Boolean = false,
        targetDate: Long = 0L,
        lastDoneAtMillis: Long = System.currentTimeMillis(),
        vehicleId: Int? = null,
        intervalKm: Int = 0,
        lastDoneAtKm: Int = 0
    ) {
        viewModelScope.launch {
            val newTask = Task(
                title          = title,
                intervalDays   = intervalDays,
                lastDoneAt     = if (recurrenceType == "ONE_SHOT") 0L else lastDoneAtMillis,
                iconKey        = iconKey,
                note           = note,
                recurrenceType = recurrenceType,
                weekDays       = weekDays,
                monthDays      = monthDays,
                isDisabled     = isDisabled,
                targetDate     = targetDate,
                vehicleId      = vehicleId,
                intervalKm     = intervalKm,
                lastDoneAtKm   = lastDoneAtKm
            )
            val id = repository.insert(newTask)
            NotificationScheduler.scheduleForTask(getApplication(), newTask.copy(id = id.toInt()))
            doUpdateWidget()
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            val taskToSave = if (task.recurrenceType != "ONE_SHOT" && task.lastDoneAt == 0L)
                task.copy(lastDoneAt = System.currentTimeMillis())
            else task
            repository.update(taskToSave)
            NotificationScheduler.scheduleForTask(getApplication(), taskToSave)
            doUpdateWidget()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.delete(task)
            historyDao.deleteForTask(task.id)
            NotificationScheduler.cancelForTask(getApplication(), task.id)
            doUpdateWidget()
        }
    }

    fun snooze(task: Task, days: Int) {
        viewModelScope.launch {
            repository.snooze(task, days)
            val snoozed = task.copy(snoozedUntil = System.currentTimeMillis() + days * 86_400_000L)
            NotificationScheduler.scheduleForTask(getApplication(), snoozed)
            doUpdateWidget()
        }
    }

    fun snoozeUntil(task: Task, dateMillis: Long) {
        viewModelScope.launch {
            repository.snoozeUntil(task, dateMillis)
            val snoozed = task.copy(snoozedUntil = dateMillis)
            NotificationScheduler.scheduleForTask(getApplication(), snoozed)
            doUpdateWidget()
        }
    }

    fun importTasks(json: String) {
        viewModelScope.launch {
            val backup = BackupManager.importFromJson(json)
            repository.deleteAll()
            historyDao.deleteAll()

            val vehicleIdMap = mutableMapOf<Int, Int>() // originalId -> newId
            backup.vehicles.forEach { v ->
                val newId = repository.insertVehicle(v.copy(id = 0)).toInt()
                vehicleIdMap[v.id] = newId
            }

            val taskIdMap = mutableMapOf<Int, Int>() // originalId → newId
            backup.tasks.forEach { exportedTask ->
                val originalId = exportedTask.id
                val newVehicleId = exportedTask.vehicleId?.let { vehicleIdMap[it] }
                val newId = repository.insert(exportedTask.copy(id = 0, vehicleId = newVehicleId)).toInt()
                if (originalId > 0) taskIdMap[originalId] = newId
                NotificationScheduler.scheduleForTask(getApplication(), exportedTask.copy(id = newId, vehicleId = newVehicleId))
            }
            backup.history.forEach { entry ->
                val newTaskId = taskIdMap[entry.taskId] ?: return@forEach
                historyDao.insert(entry.copy(taskId = newTaskId))
            }
            backup.mileageLogs.forEach { log ->
                val newVehicleId = vehicleIdMap[log.vehicleId] ?: return@forEach
                repository.updateVehicleMileage(newVehicleId, log.mileage, log.imagePath)
            }
            doUpdateWidget()
        }
    }

    fun rescheduleAllNotifications() {
        viewModelScope.launch {
            repository.allTasks.first().forEach { task ->
                NotificationScheduler.scheduleForTask(getApplication(), task)
            }
        }
    }

    fun undoMarkDone(task: Task, previousLastDoneAt: Long, previousLastDoneAtKm: Int = 0) {
        viewModelScope.launch {
            val restored = task.copy(
                lastDoneAt = previousLastDoneAt,
                lastDoneAtKm = previousLastDoneAtKm,
                snoozedUntil = 0L
            )
            repository.update(restored)
            if (task.recurrenceType != "ONE_SHOT") {
                historyDao.deleteLatestForTask(task.id)
            }
            NotificationScheduler.scheduleForTask(getApplication(), restored)
            doUpdateWidget()
        }
    }

    // ── Vehicle & Mileage Management ─────────────────────────────────────────

    suspend fun getVehicleById(id: Int): Vehicle? = repository.getVehicleById(id)

    fun addVehicle(name: String, currentMileage: Int = 0) {
        viewModelScope.launch {
            repository.insertVehicle(Vehicle(name = name, currentMileage = currentMileage))
        }
    }

    fun updateVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.updateVehicle(vehicle)
            doUpdateWidget()
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicle)
            // Optionnel : mettre à null le vehicleId des tasks associées ?
            // Pour l'instant on laisse Room gérer ou on le fait manuellement
            doUpdateWidget()
        }
    }

    fun updateMileage(vehicleId: Int, mileage: Int, imagePath: String? = null) {
        viewModelScope.launch {
            repository.updateVehicleMileage(vehicleId, mileage, imagePath)
            doUpdateWidget()
        }
    }

    fun resetVehicleTasks(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.resetAllTasksForVehicle(vehicle.id, vehicle.currentMileage)
            doUpdateWidget()
        }
    }

    fun testNotification(task: Task) {
        NotificationHelper.showNotification(
            getApplication(), task.id, task.title, "Ceci est une notification de test"
        )
    }

    // Export helper: get all history for backup
    suspend fun getAllHistoryOnce(): List<TaskHistory> = historyDao.getAll()
    suspend fun getAllVehiclesOnce(): List<Vehicle> = repository.allVehicles.first()
    suspend fun getAllMileageLogsOnce(): List<MileageLog> = db.mileageLogDao().getAll()

    private suspend fun doUpdateWidget() {
        try {
            NhacviecWidget().updateAll(getApplication<Application>())
        } catch (e: Exception) {
            android.util.Log.e("MainTask", "Widget update failed", e)
        }
    }
}
