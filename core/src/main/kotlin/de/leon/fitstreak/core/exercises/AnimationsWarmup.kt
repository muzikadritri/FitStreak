package de.leon.fitstreak.core.exercises

import de.leon.fitstreak.core.anim.Body
import de.leon.fitstreak.core.anim.ExerciseAnimation
import de.leon.fitstreak.core.anim.Fk
import de.leon.fitstreak.core.anim.Ik
import de.leon.fitstreak.core.anim.Limb
import de.leon.fitstreak.core.anim.P
import de.leon.fitstreak.core.anim.Phase
import de.leon.fitstreak.core.anim.Pose
import de.leon.fitstreak.core.anim.View
import de.leon.fitstreak.core.anim.alternate
import de.leon.fitstreak.core.anim.dir

// --- Marschieren: ein Knie hoch, der Gegenarm schwingt nach vorn; dazwischen stehen beide Füße ---

/** [knee]: +1 = nahes Knie oben, -1 = fernes Knie oben, 0 = beide Füße am Boden. */
private fun march(knee: Int): Pose {
    val up = Ik(0.40f, 0.42f)
    val armFwd = Fk(42f, 100f)
    val armBack = Fk(-32f, 6f)
    val armMid = Fk(4f, 40f)
    return Pose(
        hipX = 0f, hipY = Body.STAND_HIP, torso = 180f,
        armN = when (knee) { 1 -> armBack; -1 -> armFwd; else -> armMid },
        armF = when (knee) { 1 -> armFwd; -1 -> armBack; else -> armMid },
        legN = if (knee == 1) up else Ik(0.03f, P.GROUND),
        legF = if (knee == -1) up else Ik(-0.03f, P.GROUND),
        footN = if (knee == 1) 62f else 90f,
        footF = if (knee == -1) 62f else 90f,
    )
}

// --- Armkreisen (Seitenansicht): gestreckter Arm dreht einmal ganz um die Schulter ---

private val circleShoulder = P.shoulderOf(0f, Body.STAND_HIP, 180f)

/**
 * Gestreckter Arm im Winkel [deg]. Abwechselnd als FK und als IK-Ziel definiert: gemischte
 * Übergänge werden auf dem kürzesten Weg gedreht – so läuft der Kreis immer in dieselbe Richtung.
 */
private fun circleArm(deg: Float, asIk: Boolean): Limb =
    if (asIk) {
        val hand = circleShoulder + dir(deg) * (Body.UPPER_ARM + Body.FOREARM)
        Ik(hand.x, hand.y)
    } else {
        Fk(deg, deg)
    }

private fun circlePose(step: Int): Pose {
    val deg = step * 90f
    val asIk = step % 2 == 1
    return P.stand(
        armN = circleArm(deg, asIk),
        armF = circleArm(deg - 35f, asIk),
    )
}

internal val warmupAnimations: Map<String, ExerciseAnimation> = mapOf(

    "wu_marschieren" to ExerciseAnimation(
        poses = listOf(march(1), march(0), march(-1)),
        phases = listOf(
            Phase("absetzen", 0.3f, 1),
            Phase("Knie hoch – Arme schwingen mit", 0.45f, 2),
            Phase("absetzen", 0.3f, 1),
            Phase("Knie hoch – Arme schwingen mit", 0.45f, 0),
        ),
    ),

    "wu_hampelmann" to ExerciseAnimation(
        view = View.FRONT,
        poses = listOf(
            P.standFront(armN = Fk(8f, 10f), footHalf = 0.10f),
            P.standFront(hipY = 0.845f, armN = Fk(150f, 168f), footHalf = 0.34f),
        ),
        phases = listOf(
            Phase("auf – Arme hoch", 0.5f, 1),
            Phase("zu – Arme runter", 0.5f, 0),
        ),
    ),

    "wu_armkreisen" to ExerciseAnimation(
        poses = listOf(circlePose(0), circlePose(1), circlePose(2), circlePose(3)),
        phases = listOf(
            Phase("große Kreise", 0.45f, 1),
            Phase("große Kreise", 0.45f, 2),
            Phase("große Kreise", 0.45f, 3),
            Phase("große Kreise", 0.45f, 0),
        ),
    ),

    "wu_rumpfdrehen" to ExerciseAnimation(
        view = View.FRONT,
        poses = listOf(
            P.standFront(torso = 177f, armN = Fk(62f, 84f), armF = Fk(-28f, -78f), footHalf = 0.17f),
            P.standFront(torso = 183f, armN = Fk(-28f, -78f), armF = Fk(62f, 84f), footHalf = 0.17f),
        ),
        phases = alternate(seconds = 0.9f, labelA = "locker zur Seite drehen", labelB = "zur anderen Seite"),
    ),
)
