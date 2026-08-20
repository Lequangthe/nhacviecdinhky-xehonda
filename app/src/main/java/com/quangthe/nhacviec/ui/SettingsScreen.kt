// SPDX-FileCopyrightText: 2026 Kapoué
// SPDX-License-Identifier: GPL-3.0-or-later

package com.quangthe.nhacviec.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quangthe.nhacviec.R
import com.quangthe.nhacviec.backup.BackupManager
import com.quangthe.nhacviec.notification.NotificationPreference
import com.quangthe.nhacviec.ui.theme.AppTheme
import com.quangthe.nhacviec.viewmodel.TaskViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: TaskViewModel = viewModel(),
    currentTheme: AppTheme = AppTheme.AZURE_CLAIR,
    onThemeChange: (AppTheme) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    val tasks by viewModel.tasks.collectAsState()

    val initHour    = remember { NotificationPreference.getHour(context) }
    val initAdvance = remember { NotificationPreference.getAdvance(context) }
    var selHour     by remember { mutableIntStateOf(initHour) }
    var selAdvance  by remember { mutableStateOf(initAdvance) }
    val hourState   = rememberLazyListState()

    var exportJson         by remember { mutableStateOf("") }
    var pendingImportJson  by remember { mutableStateOf<String?>(null) }
    var pendingImportCount by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { hourState.scrollToItem((selHour - 6).coerceAtLeast(0)) }

    // Sauvegarde des préférences notif au départ de l'écran (si modifiées)
    val savedSelHour    by rememberUpdatedState(selHour)
    val savedSelAdvance by rememberUpdatedState(selAdvance)
    DisposableEffect(Unit) {
        onDispose {
            if (savedSelHour != initHour || savedSelAdvance != initAdvance) {
                NotificationPreference.save(context, savedSelHour, savedSelAdvance)
                viewModel.rescheduleAllNotifications()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(exportJson.toByteArray()) }
            Toast.makeText(context, context.getString(R.string.toast_export_success), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_export_error), Toast.LENGTH_SHORT).show()
        }
    }

    // Demande de permission notif proposée juste après un import
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* accordée ou non — l'import a déjà eu lieu */ }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { it.reader().readText() }
                ?: return@rememberLauncherForActivityResult
            val parsed = BackupManager.importFromJson(json)
            pendingImportJson  = json
            pendingImportCount = parsed.tasks.size
        } catch (e: IllegalArgumentException) {
            Toast.makeText(context, context.getString(R.string.toast_import_version_error), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(context, context.getString(R.string.toast_file_invalid), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.menu_settings)) },
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ── Thème ───────────────────────────────────────────────────────────
            Text(stringResource(R.string.about_section_theme),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 4.dp))

            val availableThemes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                AppTheme.entries.toList()
            else
                AppTheme.entries.filter { it != AppTheme.DYNAMIQUE }

            availableThemes.forEach { theme ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onThemeChange(theme) }
                ) {
                    RadioButton(selected = currentTheme == theme, onClick = { onThemeChange(theme) })
                    Text(stringResource(theme.labelResId), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))

            // ── Notifications ────────────────────────────────────────────────────
            Text(stringResource(R.string.about_section_notifications),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 4.dp))
            Text(stringResource(R.string.about_advance_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    shape    = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    selected = selAdvance == "J3",
                    onClick  = { selAdvance = "J3" }
                ) { Text(stringResource(R.string.about_advance_3days)) }
                SegmentedButton(
                    shape    = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    selected = selAdvance == "J1",
                    onClick  = { selAdvance = "J1" }
                ) { Text(stringResource(R.string.about_advance_day_before)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.about_hour_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            LazyRow(
                state                 = hourState,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding        = PaddingValues(horizontal = 2.dp)
            ) {
                items((6..18).toList()) { hour ->
                    FilterChip(
                        selected = hour == selHour,
                        onClick  = { selHour = hour },
                        label    = { Text("${hour}h") }
                    )
                }
            }

            Spacer(Modifier.height(8.dp)); HorizontalDivider(); Spacer(Modifier.height(8.dp))

            // ── Sauvegarde ───────────────────────────────────────────────────────
            Text(stringResource(R.string.settings_section_backup),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 8.dp))

            Button(
                onClick = {
                    scope.launch {
                        val history = viewModel.getAllHistoryOnce()
                        val vehicles = viewModel.getAllVehiclesOnce()
                        val logs = viewModel.getAllMileageLogsOnce()
                        exportJson = BackupManager.exportToJson(tasks ?: emptyList(), history, vehicles, logs)
                        val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault())
                        exportLauncher.launch("maintask_${sdf.format(Date())}.json")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Backup, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.menu_export))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Restore, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.menu_import))
            }
        }
    }

    pendingImportJson?.let { json ->
        AlertDialog(
            onDismissRequest = { pendingImportJson = null },
            title = { Text(stringResource(R.string.import_dialog_title)) },
            text  = { Text(stringResource(R.string.import_dialog_message, pendingImportCount)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.importTasks(json)
                    pendingImportJson = null
                    Toast.makeText(context, context.getString(R.string.toast_import_success), Toast.LENGTH_SHORT).show()
                    // Propose d'activer les notifs si l'import a créé des tâches
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                        context.getSharedPreferences("maintask_prefs", Context.MODE_PRIVATE)
                            .edit().putBoolean("notif_requested", true).apply()
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) { Text(stringResource(R.string.btn_import)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportJson = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }
}
