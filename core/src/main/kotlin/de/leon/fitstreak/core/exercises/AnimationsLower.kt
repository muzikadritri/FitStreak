package de.leon.fitstreak.core.exercises

import de.leon.fitstreak.core.anim.Body
import de.leon.fitstreak.core.anim.Box
import de.leon.fitstreak.core.anim.BoxStyle
import de.leon.fitstreak.core.anim.Dumbbell
import de.leon.fitstreak.core.anim.DumbbellLook
import de.leon.fitstreak.core.anim.ExerciseAnimation
import de.leon.fitstreak.core.anim.Fk
import de.leon.fitstreak.core.anim.Hands
import de.leon.fitstreak.core.anim.Ik
import de.leon.fitstreak.core.anim.Limb
import de.leon.fitstreak.core.anim.P
import de.leon.fitstreak.core.anim.Phase
import de.leon.fitstreak.core.anim.Pose
import de.leon.fitstreak.core.anim.PullBar
import de.leon.fitstreak.core.anim.Vec
import de.leon.fitstreak.core.anim.Wall
import de.leon.fitstreak.core.anim.alternate
import de.leon.fitstreak.core.anim.angleOf
import de.leon.fitstreak.core.anim.downUp
import de.leon.fitstreak.core.anim.hold
import de.leon.fitstreak.core.anim.upDown
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Hilfen
// ---------------------------------------------------------------------------------------------

/** Höhe der Hüfte, wenn die Figur auf dem Boden liegt oder sitzt. */
private const val FLOOR_HIP = 0.10f

/** Winkel relativ zum Rumpf: [a] gilt für die aufrechte Figur (Rumpf 180°) und wird mitgedreht. */
private fun rel(torso: Float, a: Float): Float = a + torso - 180f

/** Hände in die Hüfte gestützt (für aufrechten bis leicht geneigten Rumpf). */
private fun handsOnHips(torso: Float = 180f): Limb = Fk(rel(torso, -28f), rel(torso, 32f))

/** Hände an den Schläfen, Ellbogen zeigen nach vorn – dreht mit dem Rumpf mit. */
private fun handsAtTemples(torso: Float, spread: Float = 0f): Limb =
    Fk(rel(torso, 112f + spread), rel(torso, 236f + spread))

/** Beine exakt gestreckt zum Knöchel [ankle] (FK) – IK am Anschlag lässt sonst das Knie leicht einknicken. */
private fun Pose.straightLegsTo(ankle: Vec): Pose {
    val a = angleOf(ankle - Vec(hipX, hipY))
    return copy(legN = Fk(a, a), legF = Fk(a, a))
}

// --- Glute Bridge: Schultern bleiben am Boden, die Hüfte dreht um die Schultern nach oben ---

private val bridgeShoulder = Vec(-0.50f, FLOOR_HIP)

private fun bridge(lift: Float, legN: Limb, legF: Limb = legN, footN: Float = 90f, footF: Float = footN): Pose {
    val r = Math.toRadians(lift.toDouble())
    val hip = Vec(
        bridgeShoulder.x + Body.TORSO * cos(r).toFloat(),
        bridgeShoulder.y + Body.TORSO * sin(r).toFloat(),
    )
    return Pose(
        hipX = hip.x, hipY = hip.y,
        torso = angleOf(bridgeShoulder - hip),
        head = -90f,
        armN = Fk(92f, 96f),
        legN = legN, legF = legF,
        footN = footN, footF = footF,
    )
}

// --- Rückenlage mit aufgestellten Füßen (Crunches) ---

private fun crunch(torso: Float, headTuck: Float) = Pose(
    hipX = 0f, hipY = FLOOR_HIP,
    torso = torso, head = torso - headTuck,
    armN = handsAtTemples(torso),
    legN = Ik(0.50f, P.GROUND),
)

// --- Stütz auf gestreckten Armen (Mountain Climbers) ---

private const val CLIMB_ANKLE_X = -1.25f

private fun climber(nearTucked: Boolean): Pose {
    val base = P.plankLine(ankleX = CLIMB_ANKLE_X, shoulderY = 0.57f, armN = Ik(0.02f, 0.03f), hipSag = -0.13f)
    val tucked = Ik(-0.54f, 0.22f)
    val long = Ik(CLIMB_ANKLE_X, 0.15f)
    return base.copy(
        legN = if (nearTucked) tucked else long,
        legF = if (nearTucked) long else tucked,
    )
}

// ---------------------------------------------------------------------------------------------
// Animationen
// ---------------------------------------------------------------------------------------------

internal val lowerBodyAnimations: Map<String, ExerciseAnimation> = mapOf(

    // ---------- Beine ----------

    "kniebeugen" to ExerciseAnimation(
        poses = listOf(
            P.stand(armN = Fk(70f, 80f)),
            P.stand(hipX = -0.26f, hipY = 0.48f, torso = 150f, head = 165f, armN = Fk(80f, 85f)),
        ),
        phases = downUp(),
    ),

    "goblet_squats" to ExerciseAnimation(
        poses = listOf(
            P.stand(armN = Fk(12f, 158f)),
            P.stand(hipX = -0.22f, hipY = 0.44f, torso = 162f, head = 172f, armN = Fk(20f, 150f)),
        ),
        phases = downUp(),
        props = listOf(Dumbbell(Hands.NEAR, DumbbellLook.BAR, angle = 0f)),
    ),

    // Horizontale Beinpresse: Plattform fest, der Sitz gleitet auf der Schiene zurück.
    "beinpresse" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = -0.04f, hipY = 0.345f, torso = 205f, head = 195f,
                armN = Fk(-8f, 28f),
                legN = Ik(0.755f, 0.58f), legF = Ik(0.755f, 0.50f),
                footN = 180f,
            ),
            Pose(
                hipX = 0.20f, hipY = 0.345f, torso = 205f, head = 195f,
                armN = Fk(-8f, 28f),
                legN = Ik(0.755f, 0.58f), legF = Ik(0.755f, 0.50f),
                footN = 180f,
            ),
        ),
        phases = listOf(
            Phase("beugen – 2 Sek", 2f, 1),
            Phase("wegdrücken – 1 Sek", 1f, 0),
        ),
        props = listOf(
            Box(x = -0.80f, w = 1.15f, h = 0.26f, style = BoxStyle.BENCH),
            Box(x = 0.80f, w = 0.08f, h = 0.95f, style = BoxStyle.STEP),
        ),
    ),

    "wandsitzen" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = 0f, hipY = 0.47f, torso = 180f,
                armN = Fk(4f, 12f),
                legN = Ik(0.42f, P.GROUND), legF = Ik(0.38f, P.GROUND),
            ),
            Pose(
                hipX = 0f, hipY = 0.478f, torso = 180f,
                armN = Fk(6f, 16f),
                legN = Ik(0.42f, P.GROUND), legF = Ik(0.38f, P.GROUND),
            ),
        ),
        phases = hold(),
        props = listOf(Wall(x = -0.125f, height = 1.45f)),
    ),

    // Schritt nach vorn (Fuß hebt ab), tief absenken, zurück in den Stand.
    "ausfallschritte" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = 0f, hipY = Body.STAND_HIP, torso = 180f,
                armN = handsOnHips(),
                legN = Ik(0.06f, P.GROUND), legF = Ik(-0.06f, P.GROUND),
            ),
            Pose(
                hipX = 0.16f, hipY = 0.86f, torso = 180f,
                armN = handsOnHips(),
                legN = Ik(0.52f, 0.20f), legF = Ik(-0.04f, 0.07f),
                footN = 80f, footF = 75f,
            ),
            Pose(
                hipX = 0.45f, hipY = 0.50f, torso = 176f,
                armN = handsOnHips(176f),
                legN = Ik(0.87f, P.GROUND), legF = Ik(0.045f, 0.15f),
                footN = 90f, footF = 20f,
            ),
        ),
        phases = listOf(
            Phase("Schritt nach vorn", 0.8f, 1),
            Phase("runter – 1,5 Sek", 1.5f, 2),
            Phase("hochdrücken – 1 Sek", 1f, 1),
            Phase("zurück in den Stand", 0.7f, 0),
        ),
    ),

    // Hinterer Fuß (fern) liegt auf dem Stuhl, das vordere Bein (nah) arbeitet.
    "bulgarian_split_squats" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = 0.14f, hipY = 0.86f, torso = 176f,
                armN = handsOnHips(176f),
                legN = Ik(0.32f, P.GROUND), legF = Ik(-0.45f, 0.50f),
                footN = 90f, footF = -90f,
            ),
            Pose(
                hipX = 0.04f, hipY = 0.50f, torso = 166f,
                armN = handsOnHips(166f),
                legN = Ik(0.32f, P.GROUND), legF = Ik(-0.45f, 0.50f),
                footN = 90f, footF = -90f,
            ),
        ),
        phases = downUp(),
        props = listOf(Box(x = -0.84f, w = 0.44f, h = 0.45f, style = BoxStyle.CHAIR)),
    ),

    "rum_kreuzheben" to ExerciseAnimation(
        poses = listOf(
            P.stand(armN = Fk(6f, 4f)),
            P.stand(hipX = -0.20f, hipY = 0.84f, torso = 106f, armN = Fk(2f, 0f)),
        ),
        phases = listOf(
            Phase("vorbeugen – 2 Sek", 2f, 1),
            Phase("aufrichten – 1 Sek", 1f, 0),
        ),
        props = listOf(Dumbbell(Hands.NEAR)),
    ),

    // Zehen bleiben am Boden, die Fersen heben ab.
    "wadenheben" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = 0f, hipY = Body.STAND_HIP, torso = 180f,
                armN = handsOnHips(),
                legN = Ik(0.06f, P.GROUND), legF = Ik(-0.06f, P.GROUND),
                footN = 90f,
            ),
            Pose(
                hipX = 0.057f, hipY = 0.168f + Body.THIGH + Body.SHIN, torso = 180f,
                armN = handsOnHips(),
                legN = Ik(0.117f, 0.168f), legF = Ik(-0.003f, 0.168f),
                footN = 40f,
            ),
        ),
        phases = upDown(up = 1f, down = 2f, hold = 1f),
    ),

    // ---------- Po ----------

    "glute_bridge" to ExerciseAnimation(
        poses = listOf(
            bridge(lift = 0f, legN = Ik(0.50f, P.GROUND)),
            bridge(lift = 35f, legN = Ik(0.50f, P.GROUND)),
        ),
        phases = upDown(up = 1f, down = 2f, hold = 1f),
        pivot = bridgeShoulder,
    ),

    // Standbein nah (dunkel), das gestreckte Bein fern (hell) in Verlängerung des Oberschenkels.
    "glute_bridge_einbeinig" to ExerciseAnimation(
        poses = listOf(
            bridge(lift = 0f, legN = Ik(0.50f, P.GROUND), legF = Fk(136f, 136f), footN = 90f, footF = 226f),
            bridge(lift = 35f, legN = Ik(0.50f, P.GROUND), legF = Fk(110f, 110f), footN = 90f, footF = 200f),
        ),
        phases = upDown(up = 1f, down = 2f, hold = 1f),
        pivot = bridgeShoulder,
    ),

    // ---------- Bauch ----------

    "plank" to ExerciseAnimation(
        poses = listOf(
            P.plankLine(ankleX = -1.25f, shoulderY = 0.32f, armN = Fk(0f, 90f))
                .straightLegsTo(Vec(-1.25f, 0.15f)),
            P.plankLine(ankleX = -1.25f, shoulderY = 0.326f, armN = Fk(0f, 90f), hipSag = -0.008f)
                .straightLegsTo(Vec(-1.25f, 0.15f)),
        ),
        phases = hold(),
        pivot = Vec(-1.25f, 0.15f),
    ),

    "hollow_hold" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = 0f, hipY = FLOOR_HIP, torso = -105f,
                armN = Fk(-113f, -113f),
                legN = Fk(105f, 105f), footN = 190f,
            ),
            Pose(
                hipX = 0f, hipY = FLOOR_HIP, torso = -107.5f,
                armN = Fk(-116f, -116f),
                legN = Fk(107.5f, 107.5f), footN = 192f,
            ),
        ),
        phases = hold(),
    ),

    // Stützarm fern (unter der Schulter), der obere Arm zeigt senkrecht nach oben.
    "seitstuetz" to ExerciseAnimation(
        poses = listOf(
            P.plankLine(
                ankleX = -1.25f, ankleY = 0.07f, shoulderY = 0.33f,
                armN = Fk(180f, 180f), armF = Fk(0f, 78f), foot = 112f,
            ).straightLegsTo(Vec(-1.25f, 0.07f)),
            P.plankLine(
                ankleX = -1.25f, ankleY = 0.07f, shoulderY = 0.336f,
                armN = Fk(180f, 178f), armF = Fk(0f, 78f), foot = 112f, hipSag = -0.01f,
            ).straightLegsTo(Vec(-1.25f, 0.07f)),
        ),
        phases = hold(),
        pivot = Vec(-1.25f, 0.07f),
    ),

    "crunches" to ExerciseAnimation(
        poses = listOf(
            crunch(torso = -95f, headTuck = 4f),
            crunch(torso = -122f, headTuck = 12f),
        ),
        phases = listOf(
            Phase("einrollen – 1 Sek", 1f, 1),
            Phase("halten – 1 Sek", 1f, 1),
            Phase("abrollen – 2 Sek", 2f, 0),
        ),
    ),

    "beinheben" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = 0f, hipY = FLOOR_HIP, torso = -90f, head = -90f,
                armN = Fk(90f, 94f),
                legN = Fk(98f, 98f), footN = 188f,
            ),
            Pose(
                hipX = 0f, hipY = FLOOR_HIP, torso = -90f, head = -90f,
                armN = Fk(90f, 94f),
                legN = Fk(178f, 178f), footN = 268f,
            ),
        ),
        phases = upDown(up = 1f, down = 3f),
    ),

    "haengendes_knieheben" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = 0f, hipY = 1.03f, torso = 180f,
                armN = Ik(0f, 2.08f),
                legN = Fk(6f, -4f), footN = 62f,
            ),
            Pose(
                hipX = 0.03f, hipY = 1.035f, torso = 183f,
                armN = Ik(0f, 2.08f),
                legN = Fk(108f, 14f), footN = 96f,
            ),
        ),
        phases = upDown(up = 1f, down = 2f, hold = 0.5f),
        props = listOf(PullBar(0f, 2.08f)),
    ),

    // Mitte → rechter Arm + linkes Bein strecken → Mitte → andere Seite.
    "dead_bug" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = 0f, hipY = FLOOR_HIP, torso = -90f, head = -90f,
                armN = Fk(180f, 180f), armF = Fk(184f, 184f),
                legN = Fk(180f, 90f), legF = Fk(184f, 92f),
                footN = 180f, footF = 182f,
            ),
            Pose(
                hipX = 0f, hipY = FLOOR_HIP, torso = -90f, head = -90f,
                armN = Fk(257f, 257f), armF = Fk(184f, 184f),
                legN = Fk(180f, 90f), legF = Fk(101f, 101f),
                footN = 180f, footF = 191f,
            ),
            Pose(
                hipX = 0f, hipY = FLOOR_HIP, torso = -90f, head = -90f,
                armN = Fk(180f, 180f), armF = Fk(257f, 257f),
                legN = Fk(101f, 101f), legF = Fk(184f, 92f),
                footN = 191f, footF = 182f,
            ),
        ),
        phases = listOf(
            Phase("strecken – 2 Sek", 2f, 1),
            Phase("zurück zur Mitte", 1f, 0),
            Phase("andere Seite – 2 Sek", 2f, 2),
            Phase("zurück zur Mitte", 1f, 0),
        ),
    ),

    "mountain_climbers" to ExerciseAnimation(
        poses = listOf(climber(nearTucked = true), climber(nearTucked = false)),
        phases = alternate(seconds = 0.55f, labelA = "Knie wechseln – zügig"),
    ),

    // Seitenansicht: Mitte (Hände vor der Brust) → Hände neben die Hüfte → Mitte → andere Seite.
    "russian_twists" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = 0f, hipY = FLOOR_HIP, torso = 222f, head = 205f,
                armN = Ik(0.02f, 0.47f), armF = Ik(0.04f, 0.45f),
                legN = Ik(0.60f, 0.06f), legF = Ik(0.56f, 0.06f),
                footN = 125f,
            ),
            Pose(
                hipX = 0f, hipY = FLOOR_HIP, torso = 225f, head = 207f,
                armN = Ik(0.06f, 0.15f), armF = Ik(0.12f, 0.19f),
                legN = Ik(0.60f, 0.06f), legF = Ik(0.56f, 0.06f),
                footN = 125f,
            ),
            Pose(
                hipX = 0f, hipY = FLOOR_HIP, torso = 219f, head = 203f,
                armN = Ik(-0.08f, 0.24f), armF = Ik(-0.14f, 0.20f),
                legN = Ik(0.60f, 0.06f), legF = Ik(0.56f, 0.06f),
                footN = 125f,
            ),
        ),
        phases = listOf(
            Phase("zur Seite drehen", 0.8f, 1),
            Phase("zurück zur Mitte", 0.6f, 0),
            Phase("zur anderen Seite", 0.8f, 2),
            Phase("zurück zur Mitte", 0.6f, 0),
        ),
    ),

    "fahrrad_crunches" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = 0f, hipY = FLOOR_HIP, torso = -113f, head = -122f,
                armN = handsAtTemples(-113f, spread = 12f), armF = handsAtTemples(-113f, spread = -8f),
                legN = Fk(206f, 96f), legF = Fk(104f, 104f),
                footN = 186f, footF = 194f,
            ),
            Pose(
                hipX = 0f, hipY = FLOOR_HIP, torso = -113f, head = -122f,
                armN = handsAtTemples(-113f, spread = -8f), armF = handsAtTemples(-113f, spread = 12f),
                legN = Fk(104f, 104f), legF = Fk(206f, 96f),
                footN = 194f, footF = 186f,
            ),
        ),
        phases = alternate(seconds = 1.2f, labelA = "Ellbogen zum Knie", labelB = "Seite wechseln"),
    ),
)
