package de.leon.fitstreak.ui.tips

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.ui.components.FitCard
import de.leon.fitstreak.ui.components.FitIcon
import de.leon.fitstreak.ui.components.FitIconView
import de.leon.fitstreak.ui.components.ScreenScaffold
import de.leon.fitstreak.ui.theme.Brand

class Tip(val icon: FitIcon, val color: Color, val title: String, val text: String)

val tips = listOf(
    Tip(
        FitIcon.DUMBBELL, Brand.Blue, "Wie Muskeln wachsen",
        "Muskelaufbau braucht vier Dinge: einen Trainingsreiz, Erholung, genug Eiweiß und Schlaf. Im Training setzt du nur den Reiz – gewachsen wird danach. Fehlt einer der vier Bausteine, bleibt der Fortschritt aus.",
    ),
    Tip(
        FitIcon.CHART, Brand.Green, "Progressive Steigerung",
        "Dein Körper passt sich nur an, wenn die Anforderung steigt. Deshalb schlägt FitStreak nach und nach mehr Wiederholungen, mehr Gewicht, schwerere Varianten oder ein langsameres Tempo vor. Kleine Schritte, aber stetig.",
    ),
    Tip(
        FitIcon.CHECK, Brand.Orange, "Saubere Technik vor Gewicht",
        "Eine Wiederholung zählt nur, wenn sie sauber ist: voller Bewegungsumfang, kontrolliert runter, kein Schwung. Lieber zwei Wiederholungen weniger als mit schlechter Form – so trifft der Reiz den richtigen Muskel und du bleibst verletzungsfrei.",
    ),
    Tip(
        FitIcon.FLAME, Brand.Red, "Sixpack = Bauchtraining + wenig Körperfett",
        "Bauchmuskeln hat jeder – sichtbar werden sie erst bei niedrigem Körperfettanteil. Bauchtraining macht sie kräftig und dick, die Ernährung legt sie frei. Du brauchst kein Kalorienzählen: viel Eiweiß, wenig Zuckergetränke, genug Schlaf und regelmäßiges Training wirken zusammen.",
    ),
    Tip(
        FitIcon.MOON, Brand.Blue, "Ruhetage sind Trainingstage",
        "Nach dem Training ist der Muskel erst einmal schwächer. In den 24–48 Stunden danach repariert und verstärkt ihn der Körper. Wer jeden Tag dieselben Muskeln trainiert, unterbricht genau das. Darum brechen Ruhetage deine Streak nicht.",
    ),
    Tip(
        FitIcon.EGG, Brand.Yellow, "Eiweiß und genug Essen",
        "Muskeln bestehen aus Eiweiß. Iss zu jeder Mahlzeit eine Portion: Eier, Quark, Joghurt, Hähnchen, Fisch, Linsen, Bohnen oder Tofu. Und iss insgesamt genug – wer zu wenig isst, kann kaum Muskeln aufbauen.",
    ),
    Tip(
        FitIcon.CALENDAR, Brand.Green, "Geduld: denk in Monaten",
        "In den ersten Wochen wirst du vor allem stärker, weil Nerven und Muskeln besser zusammenarbeiten. Sichtbare Veränderungen kommen nach 2–3 Monaten. Mach alle paar Wochen ein Foto oder miss den Oberarm – im Spiegel fällt der Fortschritt von Tag zu Tag nicht auf.",
    ),
    Tip(
        FitIcon.SHIELD, Brand.Red, "Bei Schmerzen aufhören",
        "Brennende Muskeln und Muskelkater sind normal. Stechender Schmerz, Schmerz im Gelenk oder im Rücken ist ein Stoppsignal: Übung abbrechen, leichtere Variante wählen und bei anhaltenden Beschwerden ärztlich abklären lassen.",
    ),
    Tip(
        FitIcon.TIMER, Brand.Orange, "Pausen zwischen den Sätzen",
        "60–90 Sekunden Pause sind kein Faulenzen: Der Muskel lädt seine Energiespeicher wieder auf, damit du im nächsten Satz wieder sauber arbeiten kannst. Bei schweren Übungen wie Klimmzügen dürfen es auch 2 Minuten sein.",
    ),
)

@Composable
fun TipsScreen(onBack: () -> Unit) {
    ScreenScaffold("Tipps", onBack) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tips) { tip -> TipCard(tip) }
        }
    }
}

@Composable
fun TipCard(tip: Tip) {
    FitCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(tip.color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                FitIconView(tip.icon, tint = tip.color, size = 22.dp)
            }
            Spacer(Modifier.width(12.dp))
            Text(tip.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text(tip.text, style = MaterialTheme.typography.bodyLarge)
    }
}
