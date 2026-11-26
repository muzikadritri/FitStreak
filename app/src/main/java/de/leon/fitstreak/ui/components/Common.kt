package de.leon.fitstreak.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.ui.theme.FitText
import de.leon.fitstreak.ui.theme.Motion
import de.leon.fitstreak.ui.theme.fit

/**
 * Antippbar mit ruhigem Druck-Feedback: die Fläche gibt beim Drücken minimal nach (Scale 0,98)
 * und federt beim Loslassen zurück. Kein Ripple-Feuerwerk, eine Kurve, 120 ms.
 */
fun Modifier.pressable(enabled: Boolean = true, scale: Float = 0.98f, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) scale else 1f, Motion.press(), label = "press")
    val dim by animateFloatAsState(if (pressed && enabled) 0.86f else 1f, Motion.press(), label = "pressAlpha")
    this
        .graphicsLayer { scaleX = s; scaleY = s; alpha = dim }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Feine Trennlinie als Rahmen. */
@Composable
fun Modifier.hairline(shape: Shape): Modifier = border(1.dp, fit.hairline, shape)

/** Karte: ruhige Fläche mit Haarlinie statt Schatten. */
@Composable
fun FitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = fit.card,
    padding: Dp = 16.dp,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth()
            .let { if (onClick != null) it.pressable(onClick = onClick) else it }
            .clip(shape).background(color).hairline(shape).padding(padding),
    ) {
        CompositionLocalProvider(LocalContentColor provides fit.text) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Runde Tippfläche für ein einzelnes Icon (Zurück, Schließen). */
@Composable
fun IconTap(icon: FitIcon, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = fit.text) {
    Box(modifier.size(48.dp).clip(CircleShape).pressable(scale = 0.94f, onClick = onClick), contentAlignment = Alignment.Center) {
        FitIconView(icon, tint = tint, size = 24.dp)
    }
}

/** Seite mit schlichter Kopfzeile (Zurück + Titel + Aktionen). */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = fit.bg,
        contentColor = fit.text,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(fit.bg).padding(WindowInsetsCompat.statusBars()).heightIn(min = 56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) IconTap(FitIcon.BACK, "Zurück", onBack) else Spacer(Modifier.width(12.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                )
                actions()
            }
        },
        bottomBar = bottomBar,
        content = content,
    )
}

/**
 * Hauptaktion. Pro Ansicht höchstens eine in Akzentfarbe; alles andere nutzt die ruhige Variante
 * ([color] = angehobene Fläche) mit Haarlinie.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = fit.accent,
    contentColor: Color = fit.onAccent,
    leading: (@Composable () -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.medium
    val quiet = color == fit.cardMuted
    val bg by animateColorAsState(if (enabled) color else fit.cardMuted, Motion.fast(), label = "btn")
    val fg by animateColorAsState(if (enabled) contentColor else fit.muted, Motion.fast(), label = "btnText")
    Row(
        modifier.fillMaxWidth().height(52.dp).pressable(enabled = enabled, onClick = onClick)
            .clip(shape).background(bg).let { if (quiet || !enabled) it.hairline(shape) else it }.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Ruhige Text-Aktion. */
@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = fit.accentInk) {
    Box(modifier.clip(MaterialTheme.shapes.small).pressable(onClick = onClick).heightIn(min = 40.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Auswahl-Chip: ausgewählt = Akzent, sonst angehobene Fläche mit Haarlinie. */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.small
    val bg by animateColorAsState(if (selected) fit.accent else fit.cardMuted, Motion.fast(), label = "chip")
    val fg by animateColorAsState(if (selected) fit.onAccent else fit.text, Motion.fast(), label = "chipText")
    Box(
        modifier.pressable(scale = 0.97f, onClick = onClick).clip(shape).background(bg)
            .let { if (selected) it else it.hairline(shape) }.heightIn(min = 36.dp).padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/**
 * Status als ruhiger Text mit Scheiben-Marke in der Bedeutungsfarbe.
 * Neutrale Meta-Angaben ([color] = angehobene Fläche) werden zu einem schlichten Tag.
 */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, contentColor: Color = fit.textSecondary) {
    if (color == fit.cardMuted) {
        Box(modifier.clip(MaterialTheme.shapes.extraSmall).background(fit.cardMuted).hairline(MaterialTheme.shapes.extraSmall).padding(horizontal = 8.dp, vertical = 4.dp)) {
            Text(text, color = fit.textSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            PlateMark(color, size = 12.dp)
            Spacer(Modifier.width(8.dp))
            Text(text, color = fit.textSecondary, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Fortschrittsbalken; Änderungen gleiten statt zu springen. */
@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = fit.accent,
    track: Color = fit.cardMuted,
    height: Dp = 8.dp,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), Motion.enter(), label = "progress")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(
            Modifier.fillMaxWidth().height(height)
                .graphicsLayer { scaleX = p; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
                .background(color),
        )
    }
}

@Composable
fun RoundIconButton(
    icon: FitIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    enabled: Boolean = true,
    background: Color = fit.cardMuted,
    tint: Color = fit.text,
    description: String? = null,
) {
    Box(
        modifier.size(size).pressable(enabled = enabled, scale = 0.94f, onClick = onClick).clip(CircleShape).background(background)
            .let { if (background == fit.cardMuted) it.hairline(CircleShape) else it },
        contentAlignment = Alignment.Center,
    ) {
        FitIconView(icon, tint = if (enabled) tint else fit.muted.copy(alpha = 0.5f), size = 20.dp)
    }
}

/** Zahl mit −/+ – große Tippflächen fürs Training. */
@Composable
fun Stepper(
    value: String,
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(FitIcon.MINUS, onMinus, enabled = minusEnabled, description = "weniger")
            Text(
                value,
                style = FitText.figure,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 64.dp).padding(horizontal = 8.dp),
            )
            RoundIconButton(FitIcon.PLUS, onPlus, enabled = plusEnabled, description = "mehr")
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = fit.muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            val cleaned = new.replace('.', ',').filter { it.isDigit() || (decimal && it == ',') }
            if (cleaned.length <= 6) onValueChange(cleaned)
        },
        label = { Text(label) },
        singleLine = true,
        trailingIcon = suffix?.let { { Text(it, color = fit.muted, style = FitText.meta, modifier = Modifier.padding(end = 12.dp)) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
    )
}

fun String.toDoubleDe(): Double? = replace(',', '.').toDoubleOrNull()

/** Uhrzeit-Auswahl mit Stunden/Minuten-Steppern (Minuten in 5er-Schritten). */
@Composable
fun TimeStepper(minutesOfDay: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val h = minutesOfDay / 60
    val m = minutesOfDay % 60
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Stepper(
            value = h.toString().padStart(2, '0'), label = "Stunde",
            onMinus = { onChange(((h + 23) % 24) * 60 + m) }, onPlus = { onChange(((h + 1) % 24) * 60 + m) },
        )
        Text(":", style = FitText.figure, modifier = Modifier.padding(horizontal = 4.dp).padding(bottom = 20.dp))
        Stepper(
            value = m.toString().padStart(2, '0'), label = "Minute",
            onMinus = { onChange(h * 60 + (m + 55) % 60) }, onPlus = { onChange(h * 60 + (m + 5) % 60) },
        )
    }
}

fun formatTime(minutesOfDay: Int): String =
    "${(minutesOfDay / 60).toString().padStart(2, '0')}:${(minutesOfDay % 60).toString().padStart(2, '0')}"

@Composable
fun ThinDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(fit.hairline))
}

/** Hinweis: ruhige Karte, nur das Icon trägt die Bedeutungsfarbe. */
@Composable
fun Banner(
    icon: FitIcon,
    title: String,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val shape = MaterialTheme.shapes.large
    Column(modifier.fillMaxWidth().clip(shape).background(fit.card).hairline(shape).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FitIconView(icon, tint = color, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fit.textSecondary)
        if (actions != null) {
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), content = actions)
        }
    }
}

/** Rahmen für eine Übungsfigur. */
@Composable
fun FigureBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(MaterialTheme.shapes.medium).background(fit.cardMuted), contentAlignment = Alignment.Center) { content() }
}

/** Meta-Angabe in Mono (Sätze, Gewichte, Zeiten). */
@Composable
fun Meta(text: String, modifier: Modifier = Modifier, color: Color = fit.muted) {
    Text(text, style = FitText.meta, color = color, modifier = modifier)
}

/**
 * Einmaliger Auftritt beim ersten Öffnen: kurzes Einblenden mit leichtem Anheben, gestaffelt.
 * [play] = false zeigt den Inhalt sofort (z. B. bei jedem weiteren Tab-Wechsel).
 */
fun Modifier.riseIn(index: Int, play: Boolean): Modifier = composed {
    val a = remember { androidx.compose.animation.core.Animatable(if (play) 0f else 1f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (play) a.animateTo(1f, Motion.enter(delay = (index.coerceAtMost(5)) * 45))
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 12.dp.toPx()
    }
}

/** Abhak-Feld: füllt sich grün, der Haken blendet ein. */
@Composable
fun CheckMark(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val shape = RoundedCornerShape(8.dp)
    val bg by animateColorAsState(if (checked) fit.done else Color.Transparent, Motion.fast(), label = "check")
    val border by animateColorAsState(if (checked) fit.done else fit.muted.copy(alpha = 0.6f), Motion.fast(), label = "checkBorder")
    val mark by animateFloatAsState(if (checked) 1f else 0f, Motion.fast(), label = "checkMark")
    Box(modifier.size(size).clip(shape).background(bg).border(1.5.dp, border, shape), contentAlignment = Alignment.Center) {
        FitIconView(
            FitIcon.CHECK, tint = Color.White, size = size * 0.7f,
            modifier = Modifier.graphicsLayer { alpha = mark; scaleX = 0.9f + 0.1f * mark; scaleY = 0.9f + 0.1f * mark },
        )
    }
}

/** Kennzahlen als eine ruhige Zeile mit Trennlinien statt drei gleicher Kacheln. */
@Composable
fun StatStrip(items: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    val shape = MaterialTheme.shapes.large
    Row(modifier.fillMaxWidth().clip(shape).background(fit.card).hairline(shape).height(72.dp), verticalAlignment = Alignment.CenterVertically) {
        items.forEachIndexed { i, (value, label) ->
            if (i > 0) Box(Modifier.width(1.dp).height(40.dp).background(fit.hairline))
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(value, style = FitText.figure.copy(fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)), maxLines = 1, color = fit.text)
                Text(label, style = MaterialTheme.typography.labelSmall, color = fit.muted, maxLines = 1)
            }
        }
    }
}
