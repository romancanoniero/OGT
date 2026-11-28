package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.shared.domain.sightingMomentLabelAt
import com.onlygoodthings.shared.domain.validateSightingObservedAt
import com.onlygoodthings.shared.realtime.currentEpochMs
import kotlinx.datetime.*

/** Calendar dates from DatePicker are UTC dates; the selected time is device-local. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SightingMomentField(value: Long, onChange: (Long) -> Unit, enabled: Boolean = true) {
    var calendar by remember { mutableStateOf(false) }
    var clock by remember { mutableStateOf(false) }
    val zone = TimeZone.currentSystemDefault()
    val local = Instant.fromEpochMilliseconds(value).toLocalDateTime(zone)
    var chosenDay by remember(value) { mutableStateOf(local.date) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { calendar = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text("Cuándo lo viste", color = OgtColors.ink)
                Text(sightingMomentLabelAt(value, currentEpochMs()).orEmpty(), color = OgtColors.secondary)
            }
        }
        OgtCaption("Elegí cuándo ocurrió el avistamiento. La fecha de carga se guarda aparte.")
    }
    if (calendar) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = local.date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
            yearRange = 1970..Clock.System.now().toLocalDateTime(zone).year,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    Instant.fromEpochMilliseconds(utcTimeMillis).toLocalDateTime(TimeZone.UTC).date <= Clock.System.now().toLocalDateTime(zone).date
            },
        )
        DatePickerDialog(onDismissRequest = { calendar = false },
            confirmButton = { TextButton(onClick = {
                dateState.selectedDateMillis?.let {
                    chosenDay = Instant.fromEpochMilliseconds(it).toLocalDateTime(TimeZone.UTC).date
                    calendar = false; clock = true
                }
            }, enabled = dateState.selectedDateMillis != null) { Text("Elegir hora") } },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text("Cancelar") } },
        ) { DatePicker(dateState) }
    }
    if (clock) {
        val state = rememberTimePickerState(initialHour = local.hour, initialMinute = local.minute, is24Hour = true)
        var error by remember { mutableStateOf<String?>(null) }
        Dialog(onDismissRequest = { clock = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = OgtColors.surface) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Hora del avistamiento", color = OgtColors.ink)
                    TimeInput(state)
                    error?.let { Text(it, color = OgtColors.error) }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton(onClick = { clock = false }) { Text("Cancelar") }
                        TextButton(onClick = {
                            try {
                                val picked = LocalDateTime(chosenDay, LocalTime(state.hour, state.minute)).toInstant(zone).toEpochMilliseconds()
                                validateSightingObservedAt(picked, currentEpochMs())
                                onChange(picked); clock = false
                            } catch (e: IllegalArgumentException) { error = e.message }
                        }) { Text("Confirmar") }
                    }
                }
            }
        }
    }
}
