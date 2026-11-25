package de.leon.fitstreak.core.plan

import de.leon.fitstreak.core.exercises.ExerciseLibrary
import de.leon.fitstreak.core.model.Equipment
import de.leon.fitstreak.core.model.Exercise
import de.leon.fitstreak.core.model.Goal
import de.leon.fitstreak.core.model.LoadType
import de.leon.fitstreak.core.model.Muscle
import de.leon.fitstreak.core.model.PlanExercise
import de.leon.fitstreak.core.model.PlanWorkout
import de.leon.fitstreak.core.model.Slot
import de.leon.fitstreak.core.model.UserProfile
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Erstellt aus Ziel, Ausrüstung und Trainingstagen einen Wochenplan.
 * Reine Funktionen – keine Android-Abhängigkeit.
 */
object PlanGenerator {

    private class Blueprint(val name: String, val focus: String, val slots: List<Slot>, val core: List<Slot>)

    private val splits: Map<Int, List<Blueprint>> = mapOf(
        3 to listOf(
            Blueprint("Ganzkörper A", "Drücken, Ziehen, Beine", listOf(Slot.PUSH_H, Slot.PULL, Slot.SQUAT, Slot.PUSH_V, Slot.BICEPS), listOf(Slot.CORE_STATIC, Slot.CORE_FLEX)),
            Blueprint("Ganzkörper B", "Beine, Brust, Rücken", listOf(Slot.HINGE, Slot.PUSH_H, Slot.PULL, Slot.LUNGE, Slot.TRICEPS), listOf(Slot.CORE_DYN, Slot.CORE_ROT)),
        ),
        4 to listOf(
            Blueprint("Oberkörper A", "Brust, Rücken, Arme", listOf(Slot.PUSH_H, Slot.PULL, Slot.PUSH_V, Slot.BICEPS, Slot.TRICEPS), listOf(Slot.CORE_STATIC, Slot.CORE_FLEX)),
            Blueprint("Unterkörper A", "Beine & Po", listOf(Slot.SQUAT, Slot.HINGE, Slot.GLUTE, Slot.CALF), listOf(Slot.CORE_DYN, Slot.CORE_ROT)),
            Blueprint("Oberkörper B", "Brust, Schultern, Arme", listOf(Slot.PUSH_H, Slot.PULL, Slot.SHOULDER_ISO, Slot.BICEPS, Slot.TRICEPS), listOf(Slot.CORE_FLEX, Slot.CORE_ROT)),
            Blueprint("Unterkörper B", "Beine & unterer Rücken", listOf(Slot.LUNGE, Slot.HINGE, Slot.SQUAT, Slot.BACK_EXT), listOf(Slot.CORE_STATIC, Slot.CORE_DYN)),
        ),
        5 to listOf(
            Blueprint("Push", "Brust, Schultern, Trizeps", listOf(Slot.PUSH_H, Slot.PUSH_V, Slot.PUSH_H, Slot.TRICEPS, Slot.SHOULDER_ISO), listOf(Slot.CORE_STATIC, Slot.CORE_FLEX)),
            Blueprint("Pull", "Rücken & Bizeps", listOf(Slot.PULL, Slot.PULL, Slot.BACK_EXT, Slot.BICEPS, Slot.BICEPS), listOf(Slot.CORE_ROT, Slot.CORE_DYN)),
            Blueprint("Beine", "Beine & Po", listOf(Slot.SQUAT, Slot.HINGE, Slot.LUNGE, Slot.GLUTE, Slot.CALF), listOf(Slot.CORE_FLEX, Slot.CORE_STATIC)),
            Blueprint("Oberkörper", "Brust, Rücken, Arme", listOf(Slot.PUSH_H, Slot.PULL, Slot.PUSH_V, Slot.BICEPS, Slot.TRICEPS), listOf(Slot.CORE_DYN, Slot.CORE_ROT)),
            Blueprint("Ganzkörper", "Alles einmal", listOf(Slot.SQUAT, Slot.PUSH_H, Slot.PULL, Slot.GLUTE, Slot.SHOULDER_ISO), listOf(Slot.CORE_STATIC, Slot.CORE_FLEX)),
        ),
    )

    /** Kandidaten je Bewegungsmuster in Reihenfolge der Präferenz. "@name" = Varianten-Leiter. */
    private val candidates: Map<Slot, List<String>> = mapOf(
        Slot.PUSH_H to listOf("@liegestuetze", "bankdruecken", "bodendruecken"),
        Slot.PUSH_V to listOf("schulterdruecken", "pike_liegestuetze"),
        Slot.SHOULDER_ISO to listOf("seitheben", "band_pull_apart"),
        Slot.PULL to listOf("einarmiges_rudern", "@klimmzug", "latzug", "band_rudern", "reverse_snow_angels"),
        Slot.BACK_EXT to listOf("superman"),
        Slot.BICEPS to listOf("bizeps_curls", "hammer_curls", "konzentrations_curls", "band_curls"),
        Slot.TRICEPS to listOf("trizeps_ueberkopf", "dips_stuhl", "enge_liegestuetze", "trizeps_kickbacks"),
        Slot.SQUAT to listOf("goblet_squats", "beinpresse", "kniebeugen", "wandsitzen"),
        Slot.HINGE to listOf("rum_kreuzheben"),
        Slot.LUNGE to listOf("@ausfallschritt"),
        Slot.GLUTE to listOf("@bridge"),
        Slot.CALF to listOf("wadenheben"),
        Slot.CORE_STATIC to listOf("@plank"),
        Slot.CORE_FLEX to listOf("crunches", "@beinheben"),
        Slot.CORE_ROT to listOf("russian_twists", "fahrrad_crunches", "seitstuetz"),
        Slot.CORE_DYN to listOf("dead_bug", "mountain_climbers"),
    )

    /** Ersatz-Muster, wenn für ein Muster keine Übung verfügbar ist. */
    private val fallback: Map<Slot, Slot> = mapOf(
        Slot.SHOULDER_ISO to Slot.PUSH_V,
        Slot.HINGE to Slot.GLUTE,
        Slot.PULL to Slot.BACK_EXT,
        Slot.BICEPS to Slot.PULL,
    )

    private val fillers = listOf(Slot.GLUTE, Slot.LUNGE, Slot.SQUAT, Slot.PUSH_V, Slot.PUSH_H, Slot.CALF)
    private const val MIN_MAIN_EXERCISES = 4

    private val coreSlots = setOf(Slot.CORE_STATIC, Slot.CORE_FLEX, Slot.CORE_ROT, Slot.CORE_DYN)
    private val accessorySlots = setOf(Slot.CALF, Slot.BACK_EXT)

    fun defaultWeekdays(daysPerWeek: Int): List<Int> = when (daysPerWeek) {
        3 -> listOf(1, 3, 5)
        5 -> listOf(1, 2, 3, 5, 6)
        else -> listOf(1, 2, 4, 5)
    }

    fun generate(profile: UserProfile): List<PlanWorkout> {
        val blueprints = splits[profile.daysPerWeek.coerceIn(3, 5)]!!
        val goalMuscles: Set<Muscle> = profile.goals.flatMap { it.muscles }.toSet()
        val sixpack = Goal.SIXPACK in profile.goals
        val usedInWeek = mutableSetOf<String>()

        return blueprints.map { bp ->
            val usedHere = mutableSetOf<String>()
            val picked = mutableListOf<Exercise>()
            fun pick(slot: Slot) {
                val e = choose(slot, profile, usedInWeek, usedHere)
                    ?: fallback[slot]?.let { choose(it, profile, usedInWeek, usedHere) }
                    ?: return
                usedHere += e.id
                usedInWeek += e.id
                picked += e
            }
            bp.slots.forEach(::pick)
            // Mit wenig Ausrüstung bleiben manche Muster leer – dann mit Körpergewichts-Übungen auffüllen.
            for (slot in fillers) {
                if (picked.size >= MIN_MAIN_EXERCISES) break
                val before = picked.size
                pick(slot)
                if (picked.size == before) continue
            }
            val coreCount = if (sixpack) 2 else 1
            bp.core.take(coreCount).forEach(::pick)

            var bonusLeft = 2
            val exercises = picked.map { e ->
                val isCore = e.slot in coreSlots
                val isGoal = e.primary.any { it in goalMuscles }
                var sets = 3
                if (e.slot in accessorySlots && !isGoal) sets = 2
                if (isGoal && !isCore && bonusLeft > 0) {
                    sets += 1
                    bonusLeft--
                }
                prescriptionFor(e, profile, sets)
            }
            PlanWorkout(bp.name, bp.focus, exercises)
        }
    }

    private fun choose(slot: Slot, profile: UserProfile, usedInWeek: Set<String>, usedHere: Set<String>): Exercise? {
        val options = candidates[slot].orEmpty().mapNotNull { resolve(it, profile) }.distinctBy { it.id }
        return options.firstOrNull { it.id !in usedInWeek } ?: options.firstOrNull { it.id !in usedHere }
    }

    private fun resolve(candidate: String, profile: UserProfile): Exercise? {
        if (!candidate.startsWith("@")) {
            return ExerciseLibrary[candidate]?.takeIf { isEligible(it, profile) }
        }
        val ladder = ExerciseLibrary.ladder(candidate.drop(1)).filter { isEligible(it, profile) }
        if (ladder.isEmpty()) return null
        val wanted = desiredRank(candidate.drop(1), profile)
        return ladder.lastOrNull { it.ladderRank <= wanted } ?: ladder.first()
    }

    /** Einstieg auf der Varianten-Leiter passend zum Einstufungstest. */
    internal fun desiredRank(ladder: String, p: UserProfile): Int = when (ladder) {
        "liegestuetze" -> when {
            p.maxPushups < 6 -> 1
            p.maxPushups < 25 -> 2
            p.maxPushups < 40 -> 3
            else -> 4
        }
        "kniebeuge" -> 2
        "ausfallschritt", "bridge" -> if (p.maxSquats >= 40) 2 else 1
        "plank" -> if (p.maxPlankSec >= 90) 2 else 1
        "beinheben" -> if (p.maxPlankSec >= 90) 2 else 1
        else -> 1
    }

    /** Ausrüstung vorhanden und Gewicht für Anfänger sinnvoll? */
    fun isEligible(e: Exercise, profile: UserProfile): Boolean {
        if (!ExerciseLibrary.isAvailable(e, profile.equipment)) return false
        if (e.load == LoadType.DUMBBELL && profile.dumbbellKg.isNotEmpty()) {
            return profile.dumbbellKg.min() <= e.beginnerMaxKg
        }
        return true
    }

    /** Start-Vorgabe für eine Übung. */
    fun prescriptionFor(e: Exercise, profile: UserProfile, sets: Int = 3): PlanExercise {
        var repMin = e.repRange.first
        var repMax = e.repRange.last
        var weight = 0.0
        when (e.load) {
            LoadType.DUMBBELL -> {
                weight = pickDumbbell(e, profile.dumbbellKg)
                // Gewicht deutlich über dem Anfänger-Start: mit weniger Wiederholungen einsteigen
                if (weight >= e.startKg * 1.4) {
                    repMin = max(4, repMin - 3)
                    repMax -= 2
                }
            }
            LoadType.MACHINE -> weight = e.startKg
            LoadType.NONE -> Unit
        }
        if (e.isTimed) {
            val known = if (e.ladder == "plank" && e.ladderRank == 1) profile.maxPlankSec else 0
            val target = if (known > 0) ((known * 0.7) / 5.0).roundToInt() * 5 else repMin
            repMin = target.coerceIn(e.repRange.first, e.repRange.last)
        }
        return PlanExercise(e.id, sets, repMin, repMax, weight, restSec = e.restSec)
    }

    internal fun pickDumbbell(e: Exercise, weights: List<Double>): Double {
        if (weights.isEmpty()) return e.startKg
        val ok = weights.filter { it <= e.beginnerMaxKg }
        if (ok.isEmpty()) return weights.min()
        return ok.sortedBy { it }.minBy { abs(it - e.startKg) }
    }

    /**
     * Alternativen mit gleicher Hauptmuskelgruppe und vorhandener Ausrüstung.
     * Gleiches Bewegungsmuster zuerst.
     */
    fun alternativesFor(exerciseId: String, equipment: Set<Equipment>, exclude: Set<String> = emptySet()): List<Exercise> {
        val current = ExerciseLibrary[exerciseId] ?: return emptyList()
        return ExerciseLibrary.all
            .filter { it.id != exerciseId && it.id !in exclude }
            .filter { ExerciseLibrary.isAvailable(it, equipment) }
            .filter { it.primary.any { m -> m in current.primary } }
            .sortedWith(
                compareBy<Exercise> { if (it.slot == current.slot) 0 else 1 }
                    .thenBy { if (it.ladder != null && it.ladder == current.ladder) 0 else 1 }
                    .thenBy { abs(it.difficulty.ordinal - current.difficulty.ordinal) }
                    .thenBy { it.name },
            )
    }

    /**
     * Welches Workout ist an [date] dran? null = Ruhetag.
     * Rotation: (Wochen seit Planstart × Tage pro Woche + Platz in der Woche) mod Anzahl Workouts –
     * ergibt bei 3 Tagen A/B/A und in der Folgewoche B/A/B.
     */
    fun workoutIndexFor(date: LocalDate, planStart: LocalDate, weekdays: List<Int>, workoutCount: Int): Int? {
        if (workoutCount <= 0) return null
        val sorted = weekdays.sorted()
        val slot = sorted.indexOf(date.dayOfWeek.value)
        if (slot < 0) return null
        val monday = TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        val weeks = ChronoUnit.WEEKS.between(planStart.with(monday), date.with(monday)).toInt()
        return Math.floorMod(weeks * sorted.size + slot, workoutCount)
    }

    fun isTrainingDay(date: LocalDate, weekdays: List<Int>): Boolean = date.dayOfWeek.value in weekdays

    /** Geschätzte Dauer in Minuten inkl. Aufwärmen. */
    fun estimateMinutes(exercises: List<PlanExercise>): Int {
        var sec = ExerciseLibrary.warmup.sumOf { it.seconds }
        for (pe in exercises) {
            val e = ExerciseLibrary[pe.exerciseId] ?: continue
            val work = if (e.isTimed) pe.repMin else 40
            val sides = if (e.perSide) 2 else 1
            sec += pe.sets * (work * sides + pe.restSec) + 30
        }
        return ((sec / 60.0) / 5.0).roundToInt() * 5
    }
}
