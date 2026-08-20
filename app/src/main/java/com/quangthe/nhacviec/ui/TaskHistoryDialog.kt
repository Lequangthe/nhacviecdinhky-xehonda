// SPDX-FileCopyrightText: 2026 Kapoué
// SPDX-License-Identifier: GPL-3.0-or-later

package com.quangthe.nhacviec.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quangthe.nhacviec.R
import com.quangthe.nhacviec.data.Task
import com.quangthe.nhacviec.viewmodel.TaskViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TaskHistoryDialog(
    task: Task,
    onDismiss: () -> Unit,
    vm: TaskViewModel = viewModel()
) {
    val history by vm.historyForTask(task.id).collectAsState(initial = emptyList())
    val today = java.util.Calendar.getInstance()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_title)) },
        text = {
            if (history.isEmpty()) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    LazyColumn {
                        items(history) { entry ->
                            val cal = java.util.Calendar.getInstance().apply { timeInMillis = entry.doneAt }
                            val fmt = if (cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR))
                                SimpleDateFormat("d MMMM", Locale.getDefault())
                            else
                                SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
                            val dateStr = fmt.format(Date(entry.doneAt))
                            val kmStr = if (entry.doneKm > 0) " (${entry.doneKm} km)" else ""
                            Text(
                                text = stringResource(R.string.history_done_on, dateStr) + kmStr,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.about_close)) }
        }
    )
}
