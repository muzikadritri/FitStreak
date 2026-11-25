package de.leon.fitstreak.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.leon.fitstreak.ui.theme.fit

@Composable
fun FitCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = fit.card,
    padding: Dp = 16.dp,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth().clip(shape).let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = shape,
        color = color,
        shadowElevation = if (fit.isDark) 0.dp else 1.dp,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

/** Seite mit eigener, schlichter Kopfzeile (Zurück + Titel + Aktionen). */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    Modifier.fillMaxWidth().padding(WindowInsetsCompat.statusBars()).heightIn(min = 56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück") }
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    )
                    actions()
                }
            }
        },
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    leading: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

/** Auswahl-Chip (ein-/mehrfach). */
@Composable
fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
) {
    val bg = if (selected) selectedColor else fit.cardMuted
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        modifier.clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, contentColor: Color = Color.White) {
    Box(modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp)) {
        Text(text, color = contentColor, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    track: Color = fit.cardMuted,
    height: Dp = 10.dp,
) {
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).clip(RoundedCornerShape(50)).background(color))
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
    tint: Color = MaterialTheme.colorScheme.onSurface,
    description: String? = null,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(background).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        FitIconView(icon, tint = if (enabled) tint else tint.copy(alpha = 0.35f), size = size * 0.45f)
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
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.widthIn(min = 64.dp).padding(horizontal = 6.dp),
            )
            RoundIconButton(FitIcon.PLUS, onPlus, enabled = plusEnabled, description = "mehr")
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = fit.muted)
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
        trailingIcon = suffix?.let { { Text(it, color = fit.muted, modifier = Modifier.padding(end = 12.dp)) } },
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
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
        Text(":", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(horizontal = 6.dp).padding(bottom = 18.dp))
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
    Box(modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** Hinweis-Banner mit optionalen Aktionen. */
@Composable
fun Banner(
    icon: FitIcon,
    title: String,
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(color.copy(alpha = if (fit.isDark) 0.22f else 0.12f))
            .border(1.dp, color.copy(alpha = 0.45f), MaterialTheme.shapes.large).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FitIconView(icon, tint = color, size = 22.dp)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
        if (actions != null) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

/** Rahmen für eine Übungsfigur. */
@Composable
fun FigureBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.clip(MaterialTheme.shapes.medium).background(fit.cardMuted), contentAlignment = Alignment.Center) { content() }
}
