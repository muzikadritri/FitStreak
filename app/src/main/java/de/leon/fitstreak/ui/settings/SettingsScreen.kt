package de.leon.fitstreak.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.core.model.Equipment
import de.leon.fitstreak.core.model.Goal
import de.leon.fitstreak.core.plan.PlanGenerator
import de.leon.fitstreak.ui.AppViewModel
import de.leon.fitstreak.ui.LocalPlatform
import de.leon.fitstreak.ui.components.Banner
import de.leon.fitstreak.ui.components.ChoiceChip
import de.leon.fitstreak.ui.components.FitCard
import de.leon.fitstreak.ui.components.FitIcon
import de.leon.fitstreak.ui.components.PrimaryButton
import de.leon.fitstreak.ui.components.ScreenScaffold
import de.leon.fitstreak.ui.components.Stepper
import de.leon.fitstreak.ui.components.ThinDivider
import de.leon.fitstreak.ui.components.TimeStepper
import de.leon.fitstreak.ui.components.formatTime
import de.leon.fitstreak.ui.onboarding.DumbbellEditor
import de.leon.fitstreak.ui.onboarding.WeekdayPicker
import de.leon.fitstreak.ui.theme.fit
import java.time.LocalDate

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) Text(subtitle, color = fit.muted, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val data by vm.data.collectAsState()
    val s = data.settings
    val platform = LocalPlatform.current

    var timeDialog by remember { mutableStateOf<String?>(null) } // "reminder" | "saver"
    var planDialog by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    // Systemzustand bei jedem Öffnen neu lesen
    var tick by remember { mutableIntStateOf(0) }
    val notificationsOk = remember(tick, s.reminderEnabled) { platform.notificationsAllowed() }
    val batteryLimited = remember(tick) { platform.batteryOptimized() }
    val requestPermission = platform.rememberNotificationPermissionRequest { tick++ }

    val export = platform.rememberTextExport(
        suggestedName = "fitstreak-backup-${LocalDate.now()}.json",
        content = { vm.exportBackup() },
        onDone = { ok -> message = if (ok) "Backup gespeichert." else "Backup konnte nicht gespeichert werden." },
    )
    val import = platform.rememberTextImport { text -> importText = text }

    // Plan-Entwurf: wird erst mit „Plan neu erstellen“ übernommen
    var goals by remember(s.goals) { mutableStateOf(s.goalSet()) }
    var equipment by remember(s.equipment) { mutableStateOf(s.equipmentSet()) }
    var weights by remember(s.dumbbellKg) { mutableStateOf(s.dumbbellKg) }
    var days by remember(s.daysPerWeek) { mutableIntStateOf(s.daysPerWeek) }
    var weekdays by remember(s.weekdays) { mutableStateOf(s.weekdays) }
    val planChanged = goals != s.goalSet() || equipment != s.equipmentSet() || weights != s.dumbbellKg || days != s.daysPerWeek
    val weekdaysChanged = weekdays != s.weekdays

    ScreenScaffold("Einstellungen", onBack) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FitCard {
                Text("Profil", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                var name by remember(s.name) { mutableStateOf(s.name) }
                OutlinedTextField(
                    value = name,
                    onValueChange = { v -> if (v.length <= 24) { name = v; vm.updateSettings { it.copy(name = v.trim()) } } },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium,
                )
            }

            FitCard {
                Text("Erinnerungen", style = MaterialTheme.typography.titleLarge)
                SwitchRow("Tägliche Erinnerung", "Um ${formatTime(s.reminderMinutes)} Uhr – passend zum Workout des Tages", s.reminderEnabled) { on ->
                    vm.updateSettings { it.copy(reminderEnabled = on) }
                    if (on) requestPermission()
                }
                if (s.reminderEnabled) TextButton(onClick = { timeDialog = "reminder" }) { Text("Uhrzeit ändern (${formatTime(s.reminderMinutes)})") }
                ThinDivider()
                SwitchRow("Streak-Retter", "Um ${formatTime(s.saverMinutes)} Uhr, nur wenn du noch nicht trainiert hast und eine Streak läuft", s.saverEnabled) { on ->
                    vm.updateSettings { it.copy(saverEnabled = on) }
                    if (on) requestPermission()
                }
                if (s.saverEnabled) TextButton(onClick = { timeDialog = "saver" }) { Text("Uhrzeit ändern (${formatTime(s.saverMinutes)})") }
                ThinDivider()
                TextButton(onClick = { platform.sendTestNotification(); tick++ }) { Text("Test-Benachrichtigung senden") }
                if (!notificationsOk) {
                    Spacer(Modifier.height(8.dp))
                    Banner(FitIcon.SHIELD, "Benachrichtigungen sind blockiert", "Android zeigt aktuell keine Erinnerungen von FitStreak an. Erlaube sie in den System-Einstellungen.", fit.streak) {
                        TextButton(onClick = { platform.openNotificationSettings() }) { Text("Einstellungen öffnen") }
                    }
                }
                if (batteryLimited) {
                    Spacer(Modifier.height(8.dp))
                    Banner(
                        FitIcon.BOLT, "Akku-Optimierung aktiv",
                        "Manche Handys verzögern oder unterdrücken Erinnerungen, um Akku zu sparen. Wenn Erinnerungen zu spät kommen: FitStreak in der Akku-Optimierung auf „Nicht optimieren“ stellen.",
                        fit.xpInk,
                    ) { TextButton(onClick = { platform.openBatterySettings() }) { Text("Akku-Einstellungen") } }
                }
            }

            FitCard {
                Text("Training", style = MaterialTheme.typography.titleLarge)
                SwitchRow("Ton", "Signal am Ende von Pause und Countdown", s.soundEnabled) { on -> vm.updateSettings { it.copy(soundEnabled = on) } }
                SwitchRow("Vibration", "Haptisches Feedback beim Abhaken und Timer-Ende", s.hapticsEnabled) { on -> vm.updateSettings { it.copy(hapticsEnabled = on) } }
                if (platform.figure3dSupported()) {
                    val hint = if (platform.figure3dFailed() && s.figure3d) {
                        "Die 3D-Ansicht ist auf diesem Gerät zuletzt abgestürzt und pausiert. Aus- und wieder einschalten, um es erneut zu versuchen"
                    } else {
                        "Übungen als drehbares 3D-Modell zeigen. Aus = gezeichnete Figur"
                    }
                    SwitchRow("3D-Figur", hint, s.figure3d) { on ->
                        if (on) platform.figure3dRetry()
                        vm.updateSettings { it.copy(figure3d = on) }
                    }
                }
                ThinDivider()
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pausen-Timer anpassen", style = MaterialTheme.typography.titleMedium)
                        Text("Standard: 60–90 Sek. je nach Übung", color = fit.muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Stepper(
                        value = (if (s.restAdjustSec >= 0) "+" else "") + s.restAdjustSec, label = "Sekunden",
                        onMinus = { vm.updateSettings { it.copy(restAdjustSec = (it.restAdjustSec - 15).coerceAtLeast(-30)) } },
                        onPlus = { vm.updateSettings { it.copy(restAdjustSec = (it.restAdjustSec + 15).coerceAtMost(120)) } },
                        minusEnabled = s.restAdjustSec > -30, plusEnabled = s.restAdjustSec < 120,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Wasser-Ziel", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Stepper(
                        value = s.waterGoal.toString(), label = "Gläser",
                        onMinus = { vm.updateSettings { it.copy(waterGoal = (it.waterGoal - 1).coerceAtLeast(4)) } },
                        onPlus = { vm.updateSettings { it.copy(waterGoal = (it.waterGoal + 1).coerceAtMost(14)) } },
                        minusEnabled = s.waterGoal > 4, plusEnabled = s.waterGoal < 14,
                    )
                }
            }

            FitCard {
                Text("Trainingstage", style = MaterialTheme.typography.titleLarge)
                Text("Verschiebe deine Trainingstage – der Plan bleibt gleich.", color = fit.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                WeekdayPicker(weekdays, { weekdays = it })
                Spacer(Modifier.height(8.dp))
                if (weekdays.size != days) {
                    Text("Bitte genau $days Tage auswählen (aktuell ${weekdays.size}).", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else if (weekdaysChanged && !planChanged) {
                    TextButton(onClick = { vm.updateSettings { it.copy(weekdays = weekdays.sorted()) } }) { Text("Tage speichern") }
                }
            }

            FitCard {
                Text("Ziele & Ausrüstung", style = MaterialTheme.typography.titleLarge)
                Text("Änderungen hier erstellen den Wochenplan neu.", color = fit.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Text("Ziele", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (g in Goal.entries) ChoiceChip(g.label, g in goals, { goals = if (g in goals) goals - g else goals + g })
                }
                Spacer(Modifier.height(12.dp))
                Text("Ausrüstung", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (e in Equipment.entries.drop(1)) ChoiceChip(e.label, e in equipment, { equipment = if (e in equipment) equipment - e else equipment + e })
                }
                if (Equipment.KURZHANTEL in equipment) {
                    Spacer(Modifier.height(12.dp))
                    DumbbellEditor(weights, { weights = it })
                }
                Spacer(Modifier.height(12.dp))
                Text("Trainingstage pro Woche", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (d in 3..5) ChoiceChip("$d Tage", d == days, { days = d; weekdays = PlanGenerator.defaultWeekdays(d) })
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton(
                    if (planChanged) "Plan neu erstellen" else "Plan zurücksetzen",
                    onClick = { planDialog = true },
                    enabled = goals.isNotEmpty() && weekdays.size == days && (Equipment.KURZHANTEL !in equipment || weights.isNotEmpty()),
                    color = if (planChanged) fit.accent else fit.cardMuted,
                    contentColor = if (planChanged) fit.onAccent else fit.text,
                )
            }

            FitCard {
                Text("Backup", style = MaterialTheme.typography.titleLarge)
                Text("Sichere alle Daten als JSON-Datei (z. B. in Downloads oder Google Drive) und spiele sie auf einem neuen Handy wieder ein.", color = fit.muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PrimaryButton("Exportieren", export, Modifier.weight(1f), color = fit.cardMuted, contentColor = fit.text)
                    PrimaryButton("Importieren", import, Modifier.weight(1f), color = fit.cardMuted, contentColor = fit.text)
                }
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = fit.textSecondary, style = MaterialTheme.typography.titleSmall)
                }
            }

            Text(
                "FitStreak ${platform.versionName} · komplett offline, keine Datensammlung",
                color = fit.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    timeDialog?.let { which ->
        var minutes by remember(which) { mutableIntStateOf(if (which == "reminder") s.reminderMinutes else s.saverMinutes) }
        AlertDialog(
            onDismissRequest = { timeDialog = null },
            title = { Text(if (which == "reminder") "Erinnerungszeit" else "Streak-Retter-Zeit") },
            text = { TimeStepper(minutes, { minutes = it }, Modifier.fillMaxWidth()) },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateSettings { if (which == "reminder") it.copy(reminderMinutes = minutes) else it.copy(saverMinutes = minutes) }
                    timeDialog = null
                }) { Text("Übernehmen") }
            },
            dismissButton = { TextButton(onClick = { timeDialog = null }) { Text("Abbrechen") } },
        )
    }

    if (planDialog) {
        AlertDialog(
            onDismissRequest = { planDialog = false },
            title = { Text("Plan neu erstellen?") },
            text = { Text("Der Wochenplan wird aus deinen Zielen, deiner Ausrüstung und den Trainingstagen neu erzeugt. Bisherige Anpassungen am Plan gehen verloren. Dein Trainingsverlauf, Rekorde, Streak und eigene Workouts bleiben erhalten.") },
            confirmButton = {
                TextButton(onClick = {
                    planDialog = false
                    val g = goals; val e = equipment; val w = weights; val d = days; val wd = weekdays
                    vm.updateProfileAndPlan {
                        it.copy(
                            goals = g.map { x -> x.name }.toSet(), equipment = e.map { x -> x.name }.toSet(),
                            dumbbellKg = if (Equipment.KURZHANTEL in e) w.sorted() else emptyList(), daysPerWeek = d, weekdays = wd.sorted(),
                        )
                    }
                    message = "Plan neu erstellt."
                }) { Text("Neu erstellen") }
            },
            dismissButton = { TextButton(onClick = { planDialog = false }) { Text("Abbrechen") } },
        )
    }

    importText?.let { text ->
        AlertDialog(
            onDismissRequest = { importText = null },
            title = { Text("Backup einspielen?") },
            text = { Text("Alle aktuellen Daten auf diesem Handy werden durch das Backup ersetzt.") },
            confirmButton = {
                TextButton(onClick = {
                    importText = null
                    vm.importBackup(text) { error -> message = error ?: "Backup eingespielt." }
                }) { Text("Ersetzen") }
            },
            dismissButton = { TextButton(onClick = { importText = null }) { Text("Abbrechen") } },
        )
    }
    LaunchedEffect(Unit) { tick++ }
}
