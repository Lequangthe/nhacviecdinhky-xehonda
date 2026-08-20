// SPDX-FileCopyrightText: 2026 Kapoué
// SPDX-License-Identifier: GPL-3.0-or-later

package com.quangthe.nhacviec.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TaskRepository(
    private val taskDao: TaskDao,
    private val vehicleDao: VehicleDao,
    private val mileageLogDao: MileageLogDao
) {

    val allTasks: Flow<List<Task>> = taskDao.getAllTasks()
        .map { list -> list.sortedWith(compareBy({ it.sortPriority() }, { it.effectiveDueAt })) }

    val allVehicles: Flow<List<Vehicle>> = vehicleDao.getAllVehicles()

    suspend fun markDone(task: Task, doneAtMillis: Long = System.currentTimeMillis(), doneKm: Int = 0) {
        taskDao.update(task.copy(lastDoneAt = doneAtMillis, lastDoneAtKm = doneKm, snoozedUntil = 0L))
    }

    suspend fun updateVehicleMileage(vehicleId: Int, mileage: Int, imagePath: String? = null) {
        val vehicle = vehicleDao.getById(vehicleId) ?: return
        if (mileage > vehicle.currentMileage) {
            vehicleDao.update(vehicle.copy(currentMileage = mileage, lastMileageUpdate = System.currentTimeMillis()))
            mileageLogDao.insert(MileageLog(vehicleId = vehicleId, mileage = mileage, imagePath = imagePath))
        }
    }

    suspend fun resetAllTasksForVehicle(vehicleId: Int, currentMileage: Int) {
        val tasks = taskDao.getAllTasksOnce().filter { it.vehicleId == vehicleId }
        val now = System.currentTimeMillis()
        tasks.forEach { task ->
            taskDao.update(task.copy(lastDoneAt = now, lastDoneAtKm = currentMileage, snoozedUntil = 0L))
        }
    }

    suspend fun getVehicleById(id: Int): Vehicle? = vehicleDao.getById(id)
    suspend fun insertVehicle(vehicle: Vehicle): Long = vehicleDao.insert(vehicle)
    suspend fun updateVehicle(vehicle: Vehicle) = vehicleDao.update(vehicle)
    suspend fun deleteVehicle(vehicle: Vehicle) = vehicleDao.delete(vehicle)

    suspend fun getMileageLogs(vehicleId: Int): Flow<List<MileageLog>> = mileageLogDao.getLogsForVehicle(vehicleId)

    suspend fun snooze(task: Task, days: Int) {
        taskDao.update(task.copy(snoozedUntil = System.currentTimeMillis() + days * 86_400_000L))
    }

    suspend fun snoozeUntil(task: Task, dateMillis: Long) {
        taskDao.update(task.copy(snoozedUntil = dateMillis))
    }

    suspend fun deleteStaleOneShotTasks() {
        taskDao.deleteStaleOneShotTasks(System.currentTimeMillis() - 7 * 86_400_000L)
    }

    suspend fun getById(id: Int): Task? = taskDao.getById(id)
    suspend fun update(task: Task)  = taskDao.update(task)
    suspend fun insert(task: Task): Long = taskDao.insert(task)
    suspend fun delete(task: Task)  = taskDao.delete(task)
    suspend fun deleteAll()         {
        taskDao.deleteAll()
        vehicleDao.deleteAll()
        mileageLogDao.deleteAll()
    }
}

// Priorité de tri :
// 0 = actives (récurrentes + ONE_SHOT date fixe non faite)
// 1 = ONE_SHOT "à faire un jour"
// 2 = en pause
// 3 = ONE_SHOT terminées (limbo 7 jours)
private fun Task.sortPriority(): Int = when {
    recurrenceType == "ONE_SHOT" && lastDoneAt > 0L    -> 3
    isDisabled                                          -> 2
    recurrenceType == "ONE_SHOT" && targetDate == 0L   -> 1
    else                                               -> 0
}
