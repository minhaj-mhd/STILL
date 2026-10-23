package app.still.launcher.ui.gates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.still.launcher.R
import app.still.launcher.StillApplication
import app.still.launcher.data.GateRepository
import app.still.launcher.data.gates.GateCondition
import app.still.launcher.data.gates.TaskGate
import app.still.launcher.data.productivity.Priority
import app.still.launcher.gates.GateCheck
import app.still.launcher.gates.GateInputs
import app.still.launcher.gates.appliesAt
import app.still.launcher.gates.check
import app.still.launcher.ui.LauncherViewModel
import app.still.launcher.ui.blocking.AppPickerSheet
import app.still.launcher.ui.blocking.daysLabel
import app.still.launcher.ui.blocking.minuteToTime
import app.still.launcher.ui.components.FieldLabel
import app.still.launcher.ui.components.formatTime
import app.still.launcher.ui.components.rememberCurrentMinute
import app.still.launcher.ui.editor.EditorScaffold
import app.still.launcher.ui.editor.EditorTarget
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class GatesViewModel(private val repository: GateRepository, val inputs: StateFlow<GateInputs?>) : ViewModel() {

    fun setEnabled(gate: TaskGate, enabled: Boolean) {
        viewModelScope.launch { repository.saveGate(gate.copy(enabled = enabled)) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = (this[APPLICATION_KEY] as StillApplication).container
                GatesViewModel(container.gateRepository, container.taskGates.inputs)
            }
        }
    }
}

/**
 * Settings > Task gates. Each gate shows whether it's closed and how much is left; switching
 * off or editing a gate that's closed right now asks first, since it would let the apps open.
 * Below the gates, the apps no gate ever closes; adding one while a gate is closed asks too.
 */
@Composable
fun GatesPage(
    precisionOn: Boolean,
    onBack: () -> Unit,
    onOpenEditor: (EditorTarget.EditGate) -> Unit,
    viewModel: GatesViewModel = viewModel(factory = GatesViewModel.Factory),
    launcherViewModel: LauncherViewModel = viewModel(factory = LauncherViewModel.Factory),
) {
    val inputs by viewModel.inputs.collectAsStateWithLifecycle()
    val launcher by launcherViewModel.uiState.collectAsStateWithLifecycle()
    val now = rememberCurrentMinute().value.toLocalDateTime()
    var turningOff by remember { mutableStateOf<TaskGate?>(null) }
    var confirmingEdit by remember { mutableStateOf<TaskGate?>(null) }
    var confirmingAlwaysOpen by remember { mutableStateOf(false) }
    var pickingAlwaysOpen by remember { mutableStateOf(false) }
    // Apps a gate could close, plus the ones already kept open; one row per package.
    val alwaysOpenCandidates = remember(launcher.apps, launcher.gateAlwaysOpen) {
        launcher.apps.filter { it.canGate || it.component.packageName in launcher.gateAlwaysOpen }.distinctBy { it.component.packageName }
    }
    val anyClosed = inputs?.let { current -> current.gates.any { it.check(current.agenda, now).isClosed } } == true

    EditorScaffold(title = stringResource(R.string.gates_title), onDone = onBack) {
        val current = inputs ?: return@EditorScaffold
        Text(stringResource(R.string.gates_intro), style = MaterialTheme.typography.bodyLarge)
        if (!precisionOn) {
            Text(
                text = stringResource(R.string.gates_precision_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        current.gates.forEach { gate ->
            val check = gate.check(current.agenda, now)
            GateRow(
                gate = gate,
                check = check,
                now = now,
                onOpen = { if (check.isClosed) confirmingEdit = gate else onOpenEditor(EditorTarget.EditGate(gate.id)) },
                onSwitch = { on ->
                    when {
                        on -> viewModel.setEnabled(gate, true)
                        check.isClosed -> turningOff = gate
                        else -> viewModel.setEnabled(gate, false)
                    }
                },
            )
        }
        TextButton(onClick = { onOpenEditor(EditorTarget.EditGate(null)) }) { Text(stringResource(R.string.gates_new)) }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FieldLabel(stringResource(R.string.gates_always_open))
            Text(
                text = stringResource(R.string.gates_always_open_detail),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            alwaysOpenCandidates.filter { it.component.packageName in launcher.gateAlwaysOpen }.forEach { app ->
                Text(app.label, style = MaterialTheme.typography.titleMedium)
            }
            TextButton(onClick = { if (anyClosed) confirmingAlwaysOpen = true else pickingAlwaysOpen = true }) {
                Text(stringResource(R.string.schedules_choose_apps))
            }
        }
    }

    if (pickingAlwaysOpen) {
        AppPickerSheet(
            apps = alwaysOpenCandidates,
            usage = emptyMap(),
            selected = launcher.gateAlwaysOpen,
            onChange = launcherViewModel::setGateAlwaysOpen,
            onDismiss = { pickingAlwaysOpen = false },
        )
    }

    if (confirmingAlwaysOpen) {
        AlertDialog(
            onDismissRequest = { confirmingAlwaysOpen = false },
            title = { Text(stringResource(R.string.gates_always_open_closed_title)) },
            text = { Text(stringResource(R.string.gates_closed_now_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingAlwaysOpen = false
                    pickingAlwaysOpen = true
                }) { Text(stringResource(R.string.schedules_choose_apps)) }
            },
            dismissButton = { TextButton(onClick = { confirmingAlwaysOpen = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    turningOff?.let { gate ->
        AlertDialog(
            onDismissRequest = { turningOff = null },
            title = { Text(stringResource(R.string.gates_turn_off_title, gate.name)) },
            text = { Text(stringResource(R.string.gates_closed_now_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setEnabled(gate, false)
                    turningOff = null
                }) { Text(stringResource(R.string.schedules_off_permanently)) }
            },
            dismissButton = { TextButton(onClick = { turningOff = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    confirmingEdit?.let { gate ->
        AlertDialog(
            onDismissRequest = { confirmingEdit = null },
            title = { Text(stringResource(R.string.gates_edit_closed_title, gate.name)) },
            text = { Text(stringResource(R.string.gates_closed_now_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingEdit = null
                    onOpenEditor(EditorTarget.EditGate(gate.id))
                }) { Text(stringResource(R.string.item_edit)) }
            },
            dismissButton = { TextButton(onClick = { confirmingEdit = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun GateRow(gate: TaskGate, check: GateCheck, now: LocalDateTime, onOpen: () -> Unit, onSwitch: (Boolean) -> Unit) {
    val status = when {
        !gate.enabled -> stringResource(R.string.schedules_status_off)
        !gate.appliesAt(now) -> stringResource(R.string.gates_status_outside_hours)
        check.isClosed -> pluralStringResource(R.plurals.gates_status_closed, check.pendingCount, check.pendingCount)
        else -> stringResource(R.string.gates_status_open)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(gate.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = gateSummary(gate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        }
        Switch(checked = gate.enabled, onCheckedChange = onSwitch)
    }
}

/** "High priority · Weekdays 06:00–18:00 · 3 apps", or "2 chosen items, any one · 3 apps". */
@Composable
private fun gateSummary(gate: TaskGate): String {
    val waitsFor = when (gate.condition) {
        GateCondition.PRIORITY -> stringResource(
            when (gate.minPriority) {
                Priority.HIGH -> R.string.gates_priority_high
                Priority.MEDIUM -> R.string.gates_priority_medium
                else -> R.string.gates_priority_any
            },
        )
        GateCondition.ITEMS -> {
            val items = pluralStringResource(R.plurals.gates_summary_items, gate.items.size, gate.items.size)
            if (gate.requireAll) items else "$items, ${stringResource(R.string.gates_require_any).lowercase()}"
        }
    }
    val hours = gate.window?.let { window ->
        "${daysLabel(window.days)} ${formatTime(minuteToTime(window.startMinute))}–${formatTime(minuteToTime(window.endMinute))}"
    }
    val apps = pluralStringResource(R.plurals.settings_app_count, gate.packages.size, gate.packages.size)
    return listOfNotNull(waitsFor, hours, apps).joinToString(" · ")
}
