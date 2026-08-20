// SPDX-FileCopyrightText: 2026 Kapoué
// SPDX-License-Identifier: GPL-3.0-or-later

package com.quangthe.nhacviec.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks")
    suspend fun getAllTasksOnce(): List<Task>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Task?

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    // Supprime les ONE_SHOT terminés depuis plus de 7 jours (sauf les événements "Someday")
    @Query("DELETE FROM tasks WHERE recurrenceType = 'ONE_SHOT' AND targetDate > 0 AND lastDoneAt > 0 AND lastDoneAt < :threshold")
    suspend fun deleteStaleOneShotTasks(threshold: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Delete
    suspend fun delete(task: Task)
}
