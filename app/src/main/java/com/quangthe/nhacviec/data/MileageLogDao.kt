// SPDX-FileCopyrightText: 2026 Kapoué
// SPDX-License-Identifier: GPL-3.0-or-later

package com.quangthe.nhacviec.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MileageLogDao {
    @Query("SELECT * FROM mileage_logs WHERE vehicleId = :vehicleId ORDER BY recordedAt DESC")
    fun getLogsForVehicle(vehicleId: Int): Flow<List<MileageLog>>

    @Query("SELECT * FROM mileage_logs")
    suspend fun getAll(): List<MileageLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MileageLog): Long

    @Delete
    suspend fun delete(log: MileageLog)

    @Query("DELETE FROM mileage_logs")
    suspend fun deleteAll()
}
