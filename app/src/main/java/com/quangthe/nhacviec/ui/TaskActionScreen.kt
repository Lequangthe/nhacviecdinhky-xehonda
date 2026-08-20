// SPDX-FileCopyrightText: 2026 Kapoué
// SPDX-License-Identifier: GPL-3.0-or-later

package com.quangthe.nhacviec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.quangthe.nhacviec.R
import com.quangthe.nhacviec.data.Task
import com.quangthe.nhacviec.data.Vehicle
import com.quangthe.nhacviec.data.recurrenceLabel
import com.quangthe.nhacviec.data.statusLabel
import com.quangthe.nhacviec.ui.taskStatusColor
import com.quangthe.nhacviec.viewmodel.TaskViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskActionScreen(
    viewModel: TaskViewModel = viewModel(),
    taskId: Int,
    onBack: () -> Unit = {},
    onEdit: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    var task       by remember { mutableStateOf<Task?>(null) }
    var vehicle    by remember { mutableStateOf<Vehicle?>(null) }
    var loading    by remember { mutableStateOf(true) }
    var showSnooze by remember { mutableStateOf(false) }
    var showDoneDialog by remember { mutableStateOf(false) }
    var showDoneDatePicker by remember { mutableStateOf(false) }
    var pendingDoneDate by remember { mutableLongStateOf(0L) }
    var doneMileage by remember { mutableStateOf("") }

    LaunchedEffect(taskId) {
        val t = viewModel.getTaskById(taskId)
        task = t
        if (t?.vehicleId != null) {
            vehicle = viewModel.getVehicleById(t.vehicleId)
            doneMileage = vehicle?.currentMileage?.toString() ?: ""
        }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val t = task
    if (t == null) {
        // Tâche introuvable (supprimée entre-temps) → on revient à la liste
        LaunchedEffect(Unit) { onBack() }
        return
    }

    if (showSnooze) {
        SnoozeDialog(
            onSnooze      = { days -> viewModel.snooze(t, days); onBack() },
            onSnoozeUntil = { millis -> viewModel.snoozeUntil(t, millis); onBack() },
            onDismiss     = { showSnooze = false }
        )
    }

    if (showDoneDialog) {
        AlertDialog(
            onDismissRequest = { showDoneDialog = false },
            title = { Text(stringResource(R.string.action_done_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (vehicle != null && t.intervalKm > 0) {
                        OutlinedTextField(
                            value = doneMileage,
                            onValueChange = { if (it.all(Char::isDigit)) doneMileage = it },
                            label = { Text(stringResource(R.string.vehicle_mileage_label)) },
                            suffix = { Text(stringResource(R.string.mileage_unit)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    TextButton(
                        onClick = {
                            pendingDoneDate = 0L
                            showDoneDialog = false
                            val km = doneMileage.toIntOrNull() ?: vehicle?.currentMileage ?: 0
                            viewModel.markDoneWithUndo(t, doneKm = km)
                            if (vehicle != null && km > vehicle!!.currentMileage) {
                                viewModel.updateMileage(vehicle!!.id, km)
                            }
                            onBack()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Today, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.action_done_today))
                    }
                    HorizontalDivider()
                    TextButton(
                        onClick = { showDoneDialog = false; pendingDoneDate = System.currentTimeMillis(); showDoneDatePicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CalendarMonth, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.action_done_other_date))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDoneDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showDoneDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = pendingDoneDate
        )
        DatePickerDialog(
            onDismissRequest = { showDoneDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { selected ->
                        val km = doneMileage.toIntOrNull() ?: vehicle?.currentMileage ?: 0
                        viewModel.markDoneWithUndo(t, selected, doneKm = km)
                        if (vehicle != null && km > vehicle!!.currentMileage) {
                            viewModel.updateMileage(vehicle!!.id, km)
                        }
                    }
                    showDoneDatePicker = false
                    onBack()
                }) { Text(stringResource(R.string.btn_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDoneDatePicker = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        ) { DatePicker(state = state) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Titre + icône ────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = taskIcon(t.iconKey), contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(t.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            }

            // ── Statut ───────────────────────────────────────────────────────
            Text(
                t.statusLabel(context, vehicle?.currentMileage ?: 0),
                style = MaterialTheme.typography.bodyLarge,
                color = taskStatusColor(t, vehicle?.currentMileage ?: 0)
            )

            // ── Récurrence ───────────────────────────────────────────────────
            t.recurrenceLabel(context)?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // ── Note ─────────────────────────────────────────────────────────
            if (t.note.isNotBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(t.note, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp))
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Actions ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showSnooze = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Alarm, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_snooze))
                }
                Button(
                    onClick = { showDoneDialog = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CheckCircle, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.action_done))
                }
            }
            TextButton(
                onClick = { onEdit(t.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Edit, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_edit))
            }
        }
    }
}
