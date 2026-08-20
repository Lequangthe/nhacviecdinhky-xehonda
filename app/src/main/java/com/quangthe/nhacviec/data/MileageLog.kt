// SPDX-FileCopyrightText: 2026 Kapoué
// SPDX-License-Identifier: GPL-3.0-or-later

package com.quangthe.nhacviec.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mileage_logs")
data class MileageLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val vehicleId: Int,
    val mileage: Int,
    val recordedAt: Long = System.currentTimeMillis(),
    val imagePath: String? = null
)
