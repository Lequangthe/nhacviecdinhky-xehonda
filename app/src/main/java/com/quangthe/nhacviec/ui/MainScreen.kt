// SPDX-FileCopyrightText: 2026 Kapoué
// SPDX-License-Identifier: GPL-3.0-or-later

package com.quangthe.nhacviec.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quangthe.nhacviec.R
import com.quangthe.nhacviec.data.MileageLog
import com.quangthe.nhacviec.data.Task
import com.quangthe.nhacviec.data.Vehicle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.quangthe.nhacviec.data.daysRemaining
import com.quangthe.nhacviec.data.effectiveDueAt
import com.quangthe.nhacviec.data.isOneShotDone
import com.quangthe.nhacviec.data.isSnoozed
import com.quangthe.nhacviec.data.kmRemaining
import com.quangthe.nhacviec.data.kmStatusLabel
import com.quangthe.nhacviec.data.recurrenceLabel
import com.quangthe.nhacviec.data.statusLabel
import com.quangthe.nhacviec.viewmodel.TaskViewModel
import com.quangthe.nhacviec.viewmodel.UndoItem
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// Seuils d'affichage de la barre de filtre par catégorie. Il faut toujours ≥ 2
// catégories (sinon filtrer ne sert à rien), PUIS l'une des deux conditions :
//  • diversité : ≥ MIN_CATEGORIES_FOR_FILTER catégories ayant chacune ≥ MIN_TASKS_PER_CATEGORY tâches ;
//  • volume    : liste totale ≥ MIN_TOTAL_TASKS_FOR_FILTER tâches (rattrape les listes longues déséquilibrées).
private const val MIN_TASKS_PER_CATEGORY = 3
private const val MIN_CATEGORIES_FOR_FILTER = 3
private const val MIN_TOTAL_TASKS_FOR_FILTER = 12

private fun hasNotifPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun openAppNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: TaskViewModel = viewModel(),
    onOpenSettings: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    onAddTask: () -> Unit = {},
    onEditTask: (Int) -> Unit = {}
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val vehicles by viewModel.vehicles.collectAsStateWithLifecycle()
    val undoItems by viewModel.undoItems.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()

    // Catégories réellement utilisées (icônes en jeu), dans l'ordre du picker.
    val usedCategories = remember(tasks) {
        val present = tasks.orEmpty().mapTo(HashSet()) { it.iconKey }
        taskIconOptions.map { it.key }.filter { it in present }
    }
    // Affiche la barre si ≥ 2 catégories ET (diversité suffisante OU liste longue).
    val showCategoryFilter = remember(tasks) {
        val all    = tasks.orEmpty()
        val counts = all.groupingBy { it.iconKey }.eachCount()
        val diverseEnough = counts.count { it.value >= MIN_TASKS_PER_CATEGORY } >= MIN_CATEGORIES_FOR_FILTER
        val longList      = all.size >= MIN_TOTAL_TASKS_FOR_FILTER
        counts.size >= 2 && (diverseEnough || longList)
    }

    // Repli de sécurité : si on repasse sous le seuil ou que la catégorie active
    // disparaît, on revient à « Toutes » (pas de filtre fantôme caché).
    LaunchedEffect(usedCategories) {
        val sel = selectedCategory
        if (sel != null && (!showCategoryFilter || sel !in usedCategories)) {
            viewModel.setCategoryFilter(null)
        }
    }

    var snoozingTask      by remember { mutableStateOf<Task?>(null) }
    var updatingVehicle   by remember { mutableStateOf<Vehicle?>(null) }
    var resettingVehicle  by remember { mutableStateOf<Vehicle?>(null) }
    var historyVehicle    by remember { mutableStateOf<Vehicle?>(null) }
    var showMenu          by remember { mutableStateOf(false) }
    var scrollTopOnUpdate by remember { mutableStateOf(false) }

    // Reste en haut de liste après une complétion faite depuis le haut
    LaunchedEffect(tasks) {
        if (scrollTopOnUpdate) { listState.scrollToItem(0); scrollTopOnUpdate = false }
    }

    val markDoneWithUndo: (Task) -> Unit = { task ->
        if (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
            scrollTopOnUpdate = true
        }
        viewModel.markDoneWithUndo(task)
    }

    // ── Permission notifications ─────────────────────────────────────────────
    val prefs = remember { context.getSharedPreferences("maintask_prefs", Context.MODE_PRIVATE) }
    var notifGranted by remember { mutableStateOf(hasNotifPermission(context)) }

    // Rafraîchit l'état au retour des réglages système
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notifGranted = hasNotifPermission(context)
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    // Demande depuis le bouton + : enchaîne sur l'ouverture du formulaire
    val addPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted; onAddTask() }

    // Demande depuis le bandeau : met juste à jour l'état
    val bannerPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }

    val onBannerClick: () -> Unit = {
        if (!prefs.getBoolean("notif_requested", false)) {
            prefs.edit().putBoolean("notif_requested", true).apply()
            bannerPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Déjà demandé une fois : seul un passage par les réglages peut réactiver
            openAppNotificationSettings(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_menu),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_settings)) },
                                leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                onClick = { showMenu = false; onOpenSettings() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_about)) },
                                leadingIcon = { Icon(Icons.Filled.Info, null) },
                                onClick = { showMenu = false; onOpenAbout() }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor    = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!notifGranted) {
                        prefs.edit().putBoolean("notif_requested", true).apply()
                        addPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onAddTask()
                    }
                },
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 10.dp, pressedElevation = 12.dp
                ),
                modifier = Modifier.border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp)
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.fab_new_task))
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
          Column(modifier = Modifier.fillMaxSize()) {
            if (!notifGranted && tasks?.isNotEmpty() == true) {
                NotifPermissionBanner(onClick = onBannerClick)
            }
            if (showCategoryFilter) {
                CategoryFilterRow(
                    usedCategories = usedCategories,
                    selected       = selectedCategory,
                    onSelect       = { viewModel.setCategoryFilter(it) }
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                tasks == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                tasks!!.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle, contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                            Text(stringResource(R.string.empty_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            Text(stringResource(R.string.empty_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        }
                    }
                }
                else -> {
                    val allTasks      = if (selectedCategory == null) tasks!!
                                        else tasks!!.filter { it.iconKey == selectedCategory }
                    
                    val groupedTasks = allTasks.groupBy { it.vehicleId }
                    val vehiclesMap = vehicles.associateBy { it.id }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // First show tasks with vehicles
                        vehicles.forEach { vehicle ->
                            val vehicleTasks = groupedTasks[vehicle.id] ?: emptyList()
                            if (vehicleTasks.isNotEmpty()) {
                                item(key = "vehicle_group_${vehicle.id}") {
                                    VehicleGroup(
                                        vehicle = vehicle,
                                        tasks = vehicleTasks,
                                        onUpdateKm = { updatingVehicle = vehicle },
                                        onHistory = { historyVehicle = vehicle },
                                        onReset = { resettingVehicle = vehicle },
                                        onTaskClick = { onEditTask(it.id) },
                                        onMarkDone = { task -> viewModel.markDoneWithUndo(task, doneKm = vehicle.currentMileage) },
                                        onSnooze = { snoozingTask = it },
                                        onQuickSnooze = { viewModel.snooze(it, 1) }
                                    )
                                }
                            }
                        }

                        // Then show tasks without vehicles
                        val noVehicleTasks = groupedTasks[null] ?: emptyList()
                        if (noVehicleTasks.isNotEmpty()) {
                            if (vehicles.isNotEmpty()) {
                                item { SectionHeader(stringResource(R.string.filter_all)) }
                            }
                            
                            val activeTasks   = noVehicleTasks.filter {
                                !it.isDisabled && !it.isOneShotDone &&
                                !(it.recurrenceType == "ONE_SHOT" && it.targetDate == 0L)
                            }
                            val aFaireUnJour  = noVehicleTasks.filter {
                                it.recurrenceType == "ONE_SHOT" && it.targetDate == 0L && !it.isOneShotDone
                            }
                            val disabledTasks = noVehicleTasks.filter { it.isDisabled }
                            val doneOneShot   = noVehicleTasks.filter { it.isOneShotDone }

                            items(activeTasks, key = { it.id }) { task ->
                                SwipeableTaskCard(
                                    task           = task,
                                    onMarkDone     = { viewModel.markDoneWithUndo(task) },
                                    onEdit         = { onEditTask(task.id) },
                                    onSnoozeDialog = { snoozingTask = task },
                                    onQuickSnooze  = { viewModel.snooze(task, 1) }
                                )
                            }

                            if (aFaireUnJour.isNotEmpty()) {
                                item { SectionHeader(stringResource(R.string.section_someday)) }
                                items(aFaireUnJour, key = { it.id }) { task ->
                                    SwipeableTaskCard(
                                        task           = task,
                                        onMarkDone     = { viewModel.markDoneWithUndo(task) },
                                        onEdit         = { onEditTask(task.id) },
                                        onSnoozeDialog = null,
                                        onQuickSnooze  = null
                                    )
                                }
                            }

                            if (disabledTasks.isNotEmpty()) {
                                items(disabledTasks, key = { it.id }) { task ->
                                    TaskCard(task = task,
                                        onMarkDone = { viewModel.markDoneWithUndo(task) },
                                        onEdit     = { onEditTask(task.id) },
                                        onSnooze   = null)
                                }
                            }

                            if (doneOneShot.isNotEmpty()) {
                                items(doneOneShot, key = { it.id }) { task ->
                                    TaskCard(task = task,
                                        onMarkDone = {},
                                        onEdit     = { onEditTask(task.id) },
                                        onSnooze   = null)
                                }
                            }
                        }
                    }
                }
            }
            }
          }

            if (undoItems.isNotEmpty()) {
                UndoSnackbarStack(
                    items    = undoItems,
                    onUndo   = { item -> viewModel.performUndo(item) },
                    onExpire = { item -> viewModel.dismissUndo(item) },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 88.dp)
                )
            }
        }
    }

    snoozingTask?.let { task ->
        SnoozeDialog(
            onSnooze      = { days -> viewModel.snooze(task, days); snoozingTask = null },
            onSnoozeUntil = { millis -> viewModel.snoozeUntil(task, millis); snoozingTask = null },
            onDismiss     = { snoozingTask = null }
        )
    }

    updatingVehicle?.let { vehicle ->
        MileageUpdateDialog(
            vehicle = vehicle,
            onDismiss = { updatingVehicle = null },
            onConfirm = { m, path ->
                viewModel.updateMileage(vehicle.id, m, path)
                updatingVehicle = null
            }
        )
    }

    resettingVehicle?.let { vehicle ->
        AlertDialog(
            onDismissRequest = { resettingVehicle = null },
            title = { Text(stringResource(R.string.vehicle_reset_all)) },
            text = { Text(stringResource(R.string.vehicle_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetVehicleTasks(vehicle)
                    resettingVehicle = null
                }) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { resettingVehicle = null }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    historyVehicle?.let { vehicle ->
        MileageHistoryDialog(
            vehicle = vehicle,
            viewModel = viewModel,
            onDismiss = { historyVehicle = null }
        )
    }
}

// ── Swipe wrapper ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskCard(
    task: Task,
    currentMileage: Int = 0,
    onMarkDone: () -> Unit,
    onEdit: () -> Unit,
    onSnoozeDialog: (() -> Unit)?,
    onQuickSnooze: (() -> Unit)?,
    elevation: androidx.compose.ui.unit.Dp = 2.dp,
    padding: androidx.compose.ui.unit.Dp = 12.dp,
    shape: androidx.compose.ui.graphics.Shape = CardDefaults.shape
) {
    val canSwipeRight = !task.isOneShotDone
    val canSwipeLeft  = !task.isOneShotDone && task.recurrenceType != "ONE_SHOT"

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when {
                value == SwipeToDismissBoxValue.StartToEnd && canSwipeRight -> { onMarkDone(); false }
                value == SwipeToDismissBoxValue.EndToStart && canSwipeLeft  -> { onQuickSnooze?.invoke(); false }
                else -> false
            }
        },
        positionalThreshold = { distance -> distance * 0.4f }
    )

    SwipeToDismissBox(
        state                       = state,
        enableDismissFromStartToEnd = canSwipeRight,
        enableDismissFromEndToStart = canSwipeLeft,
        backgroundContent = { SwipeBackground(state.targetValue, padding) }
    ) {
        TaskCard(task, currentMileage, onMarkDone, onEdit, onSnoozeDialog, elevation, padding, shape)
    }
}

@Composable
private fun SwipeBackground(direction: SwipeToDismissBoxValue, padding: androidx.compose.ui.unit.Dp) {
    if (direction == SwipeToDismissBoxValue.Settled) return
    val isRight = direction == SwipeToDismissBoxValue.StartToEnd
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = padding)
            .clip(CardDefaults.shape)
            .background(if (isRight) Color(0xFF4CAF50) else Color(0xFFF57C00)),
        contentAlignment = if (isRight) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Icon(
            imageVector = if (isRight) Icons.Filled.CheckCircle else Icons.Filled.Alarm,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// ── Undo snackbar stack ──────────────────────────────────────────────────────

@Composable
private fun UndoSnackbarStack(
    items: List<UndoItem>,
    onUndo: (UndoItem) -> Unit,
    onExpire: (UndoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { item ->
            key(item.addedAt) {
                LaunchedEffect(item.addedAt) {
                    delay(6_000)
                    onExpire(item)
                }
                UndoSnackbarItem(title = item.task.title, onUndo = { onUndo(item) })
            }
        }
    }
}

@Composable
private fun UndoSnackbarItem(title: String, onUndo: () -> Unit) {
    val bgColor = Color(0xFF323232)
    var textOverflows by remember { mutableStateOf(false) }

    Surface(
        shape           = MaterialTheme.shapes.small,
        color           = bgColor,
        shadowElevation = 6.dp,
        modifier        = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
    ) {
        Row(
            modifier          = Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text     = "✓ $title",
                    color    = Color.White,
                    style    = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.fillMaxWidth(),
                    onTextLayout = { result -> textOverflows = result.hasVisualOverflow }
                )
                if (textOverflows) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(48.dp)
                            .background(Brush.horizontalGradient(listOf(Color.Transparent, bgColor)))
                    )
                }
            }
            TextButton(onClick = onUndo) {
                Text(stringResource(R.string.undo_label),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

// ── Bandeau permission notifications ─────────────────────────────────────────

@Composable
private fun NotifPermissionBanner(onClick: () -> Unit) {
    Surface(
        color    = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier          = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.NotificationsActive, contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.notif_banner_message),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.notif_banner_action).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

// ── Filtre catégories (chips scrollables + fondu) ────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryFilterRow(
    usedCategories: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    val rowState = rememberLazyListState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // « Toutes » — épinglé à gauche, ne scrolle jamais
        Spacer(Modifier.width(12.dp))
        FilterChip(
            selected = selected == null,
            onClick  = { onSelect(null) },
            label    = { Text(stringResource(R.string.filter_all)) }
        )
        Spacer(Modifier.width(8.dp))

        // Catégories — scrollables, avec fondu bord-conscient
        LazyRow(
            state    = rowState,
            modifier = Modifier
                .weight(1f)
                .horizontalFadingEdges(rowState),
            contentPadding        = PaddingValues(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(usedCategories, key = { it }) { key ->
                val option = taskIconOptions.first { it.key == key }
                FilterChip(
                    selected = selected == key,
                    onClick  = { onSelect(key) },
                    label    = { Text(stringResource(option.labelResId)) },
                    leadingIcon = {
                        Icon(option.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}

// ── Composables partagés ─────────────────────────────────────────────────────

@Composable
private fun SectionHeader(text: String) {
    Text(
        text     = text.uppercase(),
        style    = MaterialTheme.typography.labelSmall,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun VehicleGroup(
    vehicle: Vehicle,
    tasks: List<Task>,
    onUpdateKm: () -> Unit,
    onHistory: () -> Unit,
    onReset: () -> Unit,
    onTaskClick: (Task) -> Unit,
    onMarkDone: (Task) -> Unit,
    onSnooze: (Task) -> Unit,
    onQuickSnooze: (Task) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // Header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(vehicle.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "${vehicle.currentMileage} ${stringResource(R.string.mileage_unit)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onHistory) {
                            Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        TextButton(onClick = onUpdateKm) {
                            Text(stringResource(R.string.vehicle_update_mileage))
                        }
                        IconButton(onClick = onReset) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            // Tasks as "Rows"
            tasks.forEachIndexed { index, task ->
                SwipeableTaskCard(
                    task = task,
                    currentMileage = vehicle.currentMileage,
                    onMarkDone = { onMarkDone(task) },
                    onEdit = { onTaskClick(task) },
                    onSnoozeDialog = { onSnooze(task) },
                    onQuickSnooze = { onQuickSnooze(task) },
                    elevation = 0.dp,
                    padding = 0.dp,
                    shape = RoundedCornerShape(0.dp)
                )
                if (index < tasks.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
fun MileageHistoryDialog(
    vehicle: Vehicle,
    viewModel: TaskViewModel,
    onDismiss: () -> Unit
) {
    val logs by viewModel.historyForVehicle(vehicle.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val today = java.util.Calendar.getInstance()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vehicle_mileage_history)) },
        text = {
            if (logs.isEmpty()) {
                Text(stringResource(R.string.vehicle_no_history))
            } else {
                LazyColumn(modifier = Modifier.height(400.dp)) {
                    itemsIndexed(logs) { index, log ->
                        val prevLog = logs.getOrNull(index + 1)
                        val delta = if (prevLog != null) log.mileage - prevLog.mileage else null
                        
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = log.recordedAt }
                        val fmt = if (cal.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR))
                            SimpleDateFormat("d MMMM HH:mm", Locale.getDefault())
                        else
                            SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault())
                        
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("${log.mileage} km", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                    if (prevLog != null) {
                                        Text("${prevLog.mileage} km ➔ ${log.mileage} km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        Text("Bắt đầu: ${log.mileage} km", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (delta != null) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            "+$delta km",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                            
                            Text(fmt.format(Date(log.recordedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

                            log.imagePath?.let {
                                Spacer(Modifier.height(8.dp))
                                coil.compose.AsyncImage(
                                    model = it,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                )
                            }
                            if (index < logs.size - 1) {
                                HorizontalDivider(modifier = Modifier.padding(top = 12.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_ok)) }
        }
    )
}

// Couleur de la pastille de statut — partagée TaskCard / page d'actions
internal fun taskStatusColor(task: Task, currentMileage: Int = 0): Color {
    val days = task.daysRemaining
    val kmRem = task.kmRemaining(currentMileage)
    
    return when {
        task.isDisabled    -> Color(0xFF9E9E9E)
        task.isOneShotDone -> Color(0xFF66BB6A)
        task.recurrenceType == "ONE_SHOT" && task.targetDate == 0L -> Color(0xFF78909C)
        task.isSnoozed     -> Color(0xFF607D8B)
        days < 0 || kmRem < 0 -> Color(0xFFD32F2F)
        days <= 3 || (task.intervalKm > 0 && kmRem <= 200) -> Color(0xFFF57C00)
        else               -> Color(0xFF388E3C)
    }
}

@Composable
fun TaskCard(
    task: Task,
    currentMileage: Int = 0,
    onMarkDone: () -> Unit,
    onEdit: () -> Unit,
    onSnooze: (() -> Unit)?,
    elevation: androidx.compose.ui.unit.Dp = 2.dp,
    padding: androidx.compose.ui.unit.Dp = 12.dp,
    shape: androidx.compose.ui.graphics.Shape = CardDefaults.shape
) {
    val context = LocalContext.current

    val statusColor = taskStatusColor(task, currentMileage)
    val subtitle    = task.statusLabel(context, currentMileage)
    val recLabel    = task.recurrenceLabel(context)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = padding)
            .alpha(if (task.isDisabled || task.isOneShotDone) 0.5f else 1f)
            .clickable(onClick = onEdit),
        shape = shape,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(6.dp).height(72.dp).background(statusColor))
            Spacer(modifier = Modifier.width(14.dp))
            Icon(
                imageVector = taskIcon(task.iconKey), contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = task.title, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f))
                    if (task.isDisabled) {
                        Icon(Icons.Filled.PauseCircle, contentDescription = stringResource(R.string.cd_paused),
                            tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                    } else if (!task.isOneShotDone && task.note.isNotEmpty()) {
                        Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = stringResource(R.string.cd_note),
                            tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                    }
                }
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = statusColor)
                if (recLabel != null) {
                    Text(text = recLabel, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
            if (onSnooze != null && !task.isDisabled && !task.isOneShotDone) {
                IconButton(onClick = onSnooze) {
                    Icon(Icons.Filled.Alarm, contentDescription = stringResource(R.string.cd_snooze),
                        tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                }
            }
            if (!task.isOneShotDone) {
                IconButton(onClick = onMarkDone) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.cd_mark_done),
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                }
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}
