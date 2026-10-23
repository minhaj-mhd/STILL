package app.still.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.still.launcher.R
import app.still.launcher.data.CornerChoice
import app.still.launcher.ui.HomeButton
import app.still.launcher.ui.LauncherApp

/** What a corner button on home opens: nothing, the phone's own app for it, or any app. Choosing closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CornerButtonSheet(
    title: String,
    button: HomeButton,
    apps: List<LauncherApp>,
    onChoose: (CornerChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val workLabel = stringResource(R.string.apps_work_label)
    val shown = apps.filter { !it.isHidden && (query.isBlank() || it.label.contains(query.trim(), ignoreCase = true)) }
    val choice = button.choice

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            ChoiceRow(stringResource(R.string.corner_off), selected = !choice.on) { onChoose(CornerChoice(on = false)) }
            button.defaultApp?.let { default ->
                ChoiceRow(stringResource(R.string.corner_default_app, default.label), selected = choice.on && choice.appKey == null) {
                    onChoose(CornerChoice())
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.apps_search_placeholder)) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
            LazyColumn {
                items(shown, key = { it.key }) { app ->
                    ChoiceRow(
                        text = if (app.isWorkProfile) "${app.label} · $workLabel" else app.label,
                        selected = choice.on && choice.appKey == app.key,
                    ) { onChoose(CornerChoice(appKey = app.key)) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}
