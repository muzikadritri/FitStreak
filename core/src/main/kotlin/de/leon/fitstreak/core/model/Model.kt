package de.leon.fitstreak.core.model

import de.leon.fitstreak.core.anim.ExerciseAnimation

enum class Muscle(val label: String) {
    BRUST("Brust"),
    RUECKEN("Rücken"),
    SCHULTERN("Schultern"),
    BIZEPS("Bizeps"),
    TRIZEPS("Trizeps"),
    BAUCH("Bauch"),
    BEINE("Beine"),
    PO("Po"),
}

enum class Equipment(val label: String, val hint: String) {
    KOERPERGEWICHT("Körpergewicht", "Immer dabei"),
    KURZHANTEL("Kurzhanteln", "Gewichte unten eintragen"),
    KLIMMZUGSTANGE("Klimmzugstange", "z. B. im Türrahmen"),
    BAND("Widerstandsbänder", "Fitnessband / Tube"),
    BANK("Bank", "Hantelbank"),
    STUDIO("Fitnessstudio", "Geräte und Kabelzug"),
}

enum class Goal(val label: String, val detail: String, val muscles: Set<Muscle>) {
    SIXPACK("Sixpack / Bauch", "Starke, sichtbare Bauchmuskeln", setOf(Muscle.BAUCH)),
    OBERKOERPER(
        "Oberkörper", "Brust, Arme, Schultern",
        setOf(Muscle.BRUST, Muscle.SCHULTERN, Muscle.BIZEPS, Muscle.TRIZEPS),
    ),
    RUECKEN("Rücken", "Breiter, starker Rücken", setOf(Muscle.RUECKEN)),
    BEINE_PO("Beine & Po", "Kräftiger Unterkörper", setOf(Muscle.BEINE, Muscle.PO)),
    GANZKOERPER("Ganzkörper-Kraft", "Überall stärker werden", emptySet()),
}

enum class Difficulty(val label: String) { LEICHT("Leicht"), MITTEL("Mittel"), SCHWER("Schwer") }

enum class ExerciseKind { REPS, TIME }

/** Art der Last: keine, Kurzhantel (nur vorhandene Gewichte) oder Gerät (frei wählbar). */
enum class LoadType { NONE, DUMBBELL, MACHINE }

/** Bewegungsmuster – danach füllt der Plan-Generator die Workouts. */
enum class Slot {
    PUSH_H, PUSH_V, SHOULDER_ISO, PULL, BACK_EXT, BICEPS, TRICEPS,
    SQUAT, HINGE, LUNGE, GLUTE, CALF,
    CORE_STATIC, CORE_FLEX, CORE_ROT, CORE_DYN,
}

/** Feinere Körperregionen für die farbige Hervorhebung in Figur und Körperkarte. */
enum class BodyPart {
    CHEST, UPPER_BACK, LOWER_BACK, SHOULDER, BICEPS, TRICEPS,
    ABS, OBLIQUES, QUADS, HAMSTRINGS, GLUTES, CALVES,
}

fun Muscle.defaultParts(): Set<BodyPart> = when (this) {
    Muscle.BRUST -> setOf(BodyPart.CHEST)
    Muscle.RUECKEN -> setOf(BodyPart.UPPER_BACK)
    Muscle.SCHULTERN -> setOf(BodyPart.SHOULDER)
    Muscle.BIZEPS -> setOf(BodyPart.BICEPS)
    Muscle.TRIZEPS -> setOf(BodyPart.TRICEPS)
    Muscle.BAUCH -> setOf(BodyPart.ABS)
    Muscle.BEINE -> setOf(BodyPart.QUADS, BodyPart.HAMSTRINGS)
    Muscle.PO -> setOf(BodyPart.GLUTES)
}

enum class Tempo(val label: String) {
    NORMAL(""),
    LANGSAM("3 Sek. runter"),
    PAUSE("3 Sek. runter + 1 Sek. halten"),
}

enum class Rating(val label: String) { LEICHT("Leicht"), PASST("Passt"), SCHWER("Schwer") }

data class Exercise(
    val id: String,
    val name: String,
    val primary: List<Muscle>,
    val secondary: List<Muscle> = emptyList(),
    /** Benötigte Ausrüstung. Leer = nur Körpergewicht. */
    val equipment: Set<Equipment> = emptySet(),
    val difficulty: Difficulty,
    val slot: Slot,
    val kind: ExerciseKind = ExerciseKind.REPS,
    /** Wiederholungen bzw. Sekunden (bei Zeit-Übungen). */
    val repRange: IntRange,
    val perSide: Boolean = false,
    val load: LoadType = LoadType.NONE,
    /** Empfohlenes Startgewicht für Anfänger (kg). */
    val startKg: Double = 0.0,
    /** Schwerer sollte das leichteste vorhandene Gewicht für einen Anfänger nicht sein. */
    val beginnerMaxKg: Double = 0.0,
    val restSec: Int = 60,
    /** Varianten-Leiter: gleiche Leiter = gleiche Bewegung, höherer Rang = schwerer. */
    val ladder: String? = null,
    val ladderRank: Int = 0,
    val summary: String,
    val steps: List<String>,
    val mistakes: List<String>,
    val breathing: String,
    /** Überschreibt die Hervorhebung der primären Muskeln (z. B. Waden statt ganzes Bein). */
    val highlight: Set<BodyPart>? = null,
    val animation: ExerciseAnimation,
) {
    val isTimed get() = kind == ExerciseKind.TIME
    val primaryParts: Set<BodyPart> get() = highlight ?: primary.flatMap { it.defaultParts() }.toSet()
    val secondaryParts: Set<BodyPart> get() = secondary.flatMap { it.defaultParts() }.toSet() - primaryParts
    val unit get() = if (isTimed) "Sek." else "Wdh."
}

/** Aktuelle Vorgabe für eine Übung im Plan. Bei Zeit-Übungen ist [repMin] das aktuelle Sekunden-Ziel. */
data class PlanExercise(
    val exerciseId: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val weightKg: Double = 0.0,
    val tempo: Tempo = Tempo.NORMAL,
    val restSec: Int = 60,
    val failStreak: Int = 0,
)

data class PlanWorkout(
    val name: String,
    val focus: String,
    val exercises: List<PlanExercise>,
)

data class UserProfile(
    val goals: Set<Goal>,
    val equipment: Set<Equipment>,
    val dumbbellKg: List<Double> = emptyList(),
    val daysPerWeek: Int,
    val maxPushups: Int = 0,
    val maxPlankSec: Int = 0,
    val maxSquats: Int = 0,
)
