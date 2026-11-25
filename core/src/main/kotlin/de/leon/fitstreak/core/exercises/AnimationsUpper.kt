package de.leon.fitstreak.core.exercises

import de.leon.fitstreak.core.anim.Band
import de.leon.fitstreak.core.anim.Body
import de.leon.fitstreak.core.anim.Box
import de.leon.fitstreak.core.anim.BoxStyle
import de.leon.fitstreak.core.anim.Dumbbell
import de.leon.fitstreak.core.anim.DumbbellLook
import de.leon.fitstreak.core.anim.ExerciseAnimation
import de.leon.fitstreak.core.anim.Fixed
import de.leon.fitstreak.core.anim.Fk
import de.leon.fitstreak.core.anim.Hands
import de.leon.fitstreak.core.anim.Ik
import de.leon.fitstreak.core.anim.JointRef
import de.leon.fitstreak.core.anim.Limb
import de.leon.fitstreak.core.anim.OnJoint
import de.leon.fitstreak.core.anim.P
import de.leon.fitstreak.core.anim.Phase
import de.leon.fitstreak.core.anim.Pose
import de.leon.fitstreak.core.anim.PullBar
import de.leon.fitstreak.core.anim.Vec
import de.leon.fitstreak.core.anim.View
import de.leon.fitstreak.core.anim.angleOf
import de.leon.fitstreak.core.anim.downUp
import de.leon.fitstreak.core.anim.upDown
import kotlin.math.sqrt

// ---------------------------------------------------------------------------------------------
// Helfer
// ---------------------------------------------------------------------------------------------

/** Zwei Posen [Start, Ende]: erst zur Endpose, optional halten, dann zurück. */
private fun thereAndBack(
    there: String, thereSec: Float,
    back: String, backSec: Float,
    hold: Float = 0f,
): List<Phase> = buildList {
    add(Phase(there, thereSec, 1))
    if (hold > 0f) add(Phase("halten – ${if (hold == 1f) "1" else hold.toString().replace('.', ',')} Sek", hold, 1))
    add(Phase(back, backSec, 0))
}

/** Knie-Liegestütz: Knie fest am Boden, Körper von Knie bis Schulter gerade. */
private val KNEE = Vec(-0.75f, 0.05f)

private fun kneePushup(shoulderY: Float, hand: Limb): Pose {
    val len = Body.THIGH + Body.TORSO
    val dy = shoulderY - KNEE.y
    val dx = sqrt(len * len - dy * dy)
    val hip = Vec(KNEE.x + dx / len * Body.THIGH, KNEE.y + dy / len * Body.THIGH)
    return Pose(
        hipX = hip.x, hipY = hip.y,
        torso = angleOf(Vec(dx, dy)),
        armN = hand,
        // Unterschenkel leicht angehoben, Füße zeigen nach hinten
        legN = Ik(KNEE.x - 0.41f, KNEE.y + 0.09f),
        footN = -60f,
    )
}

/** Vorgebeugter Stand mit Abstützen am Stuhl (Rudern, Kickbacks). Stuhl steht rechts. */
private fun bentOver(armN: Limb): Pose = Pose(
    hipX = -0.18f, hipY = 0.85f,
    torso = 100f,
    armN = armN,
    armF = Ik(0.58f, 0.49f),
    legN = Ik(-0.08f, P.GROUND),
    legF = Ik(-0.28f, P.GROUND),
)

private val SUPPORT_CHAIR = Box(x = 0.42f, w = 0.45f, h = 0.45f, style = BoxStyle.CHAIR)

/** Hang an der Stange bzw. Klimmzug-Endposition. */
private val BAR = PullBar(0f, 2.15f)

private val hangPose = Pose(
    hipX = -0.02f, hipY = 1.10f, torso = 180f,
    armN = Ik(BAR.x, BAR.y),
    legN = Fk(8f, -14f), legF = Fk(2f, -20f),
    footN = 55f,
)

private val chinOverBarPose = Pose(
    hipX = -0.21f, hipY = 1.50f, torso = 176f,
    armN = Ik(BAR.x, BAR.y),
    legN = Fk(14f, -22f), legF = Fk(6f, -30f),
    footN = 55f,
)

/** Rückenlage (Kopf links), Füße aufgestellt. */
private fun supine(hipY: Float, feetX: Float, armN: Limb, armF: Limb): Pose = Pose(
    hipX = 0f, hipY = hipY, torso = -90f, head = -97f,
    armN = armN, armF = armF,
    legN = Ik(feetX, P.GROUND), legF = Ik(feetX - 0.06f, P.GROUND),
)

// ---------------------------------------------------------------------------------------------
// Animationen
// ---------------------------------------------------------------------------------------------

internal val upperBodyAnimations: Map<String, ExerciseAnimation> = mapOf(

    // ---------- Brust ----------
    "knie_liegestuetze" to ExerciseAnimation(
        poses = listOf(
            kneePushup(shoulderY = 0.56f, hand = Ik(0.09f, 0.03f)),
            kneePushup(shoulderY = 0.20f, hand = Ik(0.09f, 0.03f)),
        ),
        phases = downUp(),
        pivot = KNEE,
    ),
    "liegestuetze" to ExerciseAnimation(
        poses = listOf(
            P.plankLine(ankleX = -1.25f, shoulderY = 0.57f, armN = Ik(0.03f, 0.03f)),
            P.plankLine(ankleX = -1.25f, shoulderY = 0.20f, armN = Ik(0.03f, 0.03f)),
        ),
        phases = downUp(),
        pivot = Vec(-1.25f, 0.15f),
    ),
    "liegestuetze_erhoeht" to ExerciseAnimation(
        poses = listOf(
            P.plankLine(ankleX = -1.30f, ankleY = 0.62f, shoulderY = 0.57f, armN = Ik(-0.06f, 0.03f)),
            P.plankLine(ankleX = -1.30f, ankleY = 0.62f, shoulderY = 0.26f, armN = Ik(-0.06f, 0.03f)),
        ),
        phases = downUp(),
        props = listOf(Box(x = -1.52f, w = 0.45f, h = 0.45f, style = BoxStyle.CHAIR)),
        pivot = Vec(-1.30f, 0.62f),
    ),
    "archer_liegestuetze" to ExerciseAnimation(
        // Naher Arm beugt und trägt die Last, der ferne Arm bleibt gestreckt und gleitet nach außen.
        // Der ferne Arm läuft über Winkel (FK), damit er auch zwischen den Keyframes gestreckt bleibt.
        poses = listOf(
            P.plankLine(ankleX = -1.25f, shoulderY = 0.50f, armN = Ik(-0.14f, 0.03f), armF = Fk(35f, 35f)),
            P.plankLine(ankleX = -1.25f, shoulderY = 0.18f, armN = Ik(-0.14f, 0.03f), armF = Fk(76f, 76f)),
        ),
        phases = downUp(),
        pivot = Vec(-1.25f, 0.15f),
    ),
    "bodendruecken" to ExerciseAnimation(
        poses = listOf(
            supine(hipY = 0.10f, feetX = 0.62f, armN = Fk(90f, 180f), armF = Fk(86f, 80f)),
            supine(hipY = 0.10f, feetX = 0.62f, armN = Fk(178f, 180f), armF = Fk(86f, 80f)),
        ),
        phases = upDown(),
        props = listOf(Dumbbell(Hands.NEAR)),
    ),
    "bankdruecken" to ExerciseAnimation(
        poses = listOf(
            supine(hipY = 0.54f, feetX = 0.44f, armN = Fk(80f, 180f), armF = Fk(80f, 180f)),
            supine(hipY = 0.54f, feetX = 0.44f, armN = Fk(178f, 180f), armF = Fk(178f, 180f)),
        ),
        phases = upDown(),
        props = listOf(
            Box(x = -0.88f, w = 1.02f, h = 0.45f, style = BoxStyle.BENCH),
            Dumbbell(Hands.BOTH),
        ),
    ),

    // ---------- Schultern ----------
    "pike_liegestuetze" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = -0.30f, hipY = 0.877f, torso = 50f,
                armN = Ik(0.10f, 0.03f),
                legN = Ik(-0.62f, 0.10f), footN = 50f,
            ),
            Pose(
                hipX = -0.13f, hipY = 0.775f, torso = 30f,
                armN = Ik(0.10f, 0.03f),
                legN = Ik(-0.62f, 0.10f), footN = 50f,
            ),
        ),
        phases = downUp(),
        pivot = Vec(-0.62f, 0.10f),
    ),
    "schulterdruecken" to ExerciseAnimation(
        view = View.FRONT,
        poses = listOf(
            P.standFront(armN = Fk(78f, 180f), armF = Fk(6f, 6f)),
            P.standFront(armN = Fk(168f, 178f), armF = Fk(6f, 6f)),
        ),
        phases = upDown(),
        props = listOf(Dumbbell(Hands.NEAR)),
    ),
    "seitheben" to ExerciseAnimation(
        view = View.FRONT,
        poses = listOf(
            P.standFront(armN = Fk(8f, 10f)),
            P.standFront(armN = Fk(82f, 88f)),
        ),
        phases = upDown(up = 1f, down = 2f, hold = 1f),
        props = listOf(Dumbbell(Hands.BOTH)),
    ),
    "band_pull_apart" to ExerciseAnimation(
        view = View.FRONT,
        poses = listOf(
            P.standFront(armN = Fk(25f, 215f)),
            P.standFront(armN = Fk(88f, 90f)),
        ),
        phases = thereAndBack("auseinanderziehen – 1 Sek", 1f, "zurück – 2 Sek", 2f),
        props = listOf(Band(OnJoint(JointRef.HAND_N), OnJoint(JointRef.HAND_F))),
    ),

    // ---------- Rücken ----------
    "einarmiges_rudern" to ExerciseAnimation(
        poses = listOf(
            bentOver(armN = Fk(0f, 2f)),
            bentOver(armN = Fk(-100f, 6f)),
        ),
        phases = thereAndBack("ziehen – 1 Sek", 1f, "ablassen – 2 Sek", 2f, hold = 1f),
        props = listOf(SUPPORT_CHAIR, Dumbbell(Hands.NEAR)),
    ),
    "negativ_klimmzuege" to ExerciseAnimation(
        poses = listOf(chinOverBarPose, hangPose),
        phases = listOf(
            Phase("langsam ablassen – 4 Sek", 4f, 1),
            Phase("aushängen – 1 Sek", 1f, 1),
            Phase("wieder hochsteigen", 1f, 0),
            Phase("oben halten – 1 Sek", 1f, 0),
        ),
        props = listOf(BAR, Box(x = -0.78f, w = 0.42f, h = 0.45f, style = BoxStyle.CHAIR)),
    ),
    "klimmzuege" to ExerciseAnimation(
        poses = listOf(hangPose, chinOverBarPose),
        phases = upDown(),
        props = listOf(BAR),
    ),
    "band_rudern" to ExerciseAnimation(
        poses = listOf(
            P.stand(hipY = 0.86f, armN = Fk(65f, 75f)),
            P.stand(hipY = 0.86f, armN = Fk(-35f, 80f)),
        ),
        phases = thereAndBack("ziehen – 1 Sek", 1f, "zurück – 2 Sek", 2f, hold = 1f),
        props = listOf(
            // Türrahmen/Pfosten als Befestigung
            Box(x = 0.98f, w = 0.07f, h = 1.45f, style = BoxStyle.STEP),
            Band(Fixed(0.98f, 1.12f), OnJoint(JointRef.HAND_N)),
        ),
    ),
    "latzug" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = -0.05f, hipY = 0.52f, torso = 185f,
                armN = Ik(-0.04f, 1.56f),
                legN = Ik(0.40f, P.GROUND), legF = Ik(0.33f, P.GROUND),
            ),
            Pose(
                hipX = -0.05f, hipY = 0.52f, torso = 188f,
                armN = Ik(-0.01f, 1.0f),
                legN = Ik(0.40f, P.GROUND), legF = Ik(0.33f, P.GROUND),
            ),
        ),
        phases = thereAndBack("ziehen – 1 Sek", 1f, "nach oben – 2 Sek", 2f, hold = 1f),
        props = listOf(
            Box(x = -0.32f, w = 0.5f, h = 0.45f, style = BoxStyle.BENCH),
            Band(Fixed(0f, 2.1f), OnJoint(JointRef.HAND_N)),
            Dumbbell(Hands.NEAR),
        ),
    ),
    "reverse_snow_angels" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = 0f, hipY = 0.10f, torso = 95f, head = 97f,
                armN = Fk(96f, 96f),
                legN = Fk(-90f, -90f), footN = -90f,
            ),
            Pose(
                hipX = 0f, hipY = 0.10f, torso = 95f, head = 97f,
                armN = Fk(264f, 264f),
                legN = Fk(-90f, -90f), footN = -90f,
            ),
        ),
        phases = thereAndBack("Arme zur Hüfte – 2 Sek", 2f, "nach vorn – 2 Sek", 2f),
    ),
    "superman" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = 0f, hipY = 0.10f, torso = 90f,
                armN = Fk(90f, 90f),
                legN = Fk(-90f, -90f), footN = -90f,
            ),
            Pose(
                hipX = 0f, hipY = 0.10f, torso = 101f, head = 104f,
                armN = Fk(101f, 104f),
                legN = Fk(-99f, -99f), footN = -99f,
            ),
        ),
        phases = upDown(up = 1f, down = 2f, hold = 2f),
    ),

    // ---------- Bizeps ----------
    "bizeps_curls" to ExerciseAnimation(
        poses = listOf(
            P.stand(armN = Fk(0f, 8f), armF = Fk(-4f, 4f)),
            P.stand(armN = Fk(4f, 155f), armF = Fk(-4f, 4f)),
        ),
        phases = upDown(),
        props = listOf(Dumbbell(Hands.NEAR)),
    ),
    "hammer_curls" to ExerciseAnimation(
        poses = listOf(
            P.stand(armN = Fk(0f, 6f), armF = Fk(-4f, 4f)),
            P.stand(armN = Fk(6f, 140f), armF = Fk(-4f, 4f)),
        ),
        phases = upDown(),
        props = listOf(Dumbbell(Hands.NEAR, DumbbellLook.BAR, angle = 90f)),
    ),
    "konzentrations_curls" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = -0.10f, hipY = 0.53f, torso = 140f, head = 130f,
                armN = Fk(5f, 12f), armF = Ik(0.30f, 0.56f),
                legN = Ik(0.32f, P.GROUND), legF = Ik(0.24f, P.GROUND),
            ),
            Pose(
                hipX = -0.10f, hipY = 0.53f, torso = 140f, head = 130f,
                armN = Fk(5f, 150f), armF = Ik(0.30f, 0.56f),
                legN = Ik(0.32f, P.GROUND), legF = Ik(0.24f, P.GROUND),
            ),
        ),
        phases = upDown(),
        props = listOf(Box(x = -0.36f, w = 0.45f, h = 0.45f, style = BoxStyle.CHAIR), Dumbbell(Hands.NEAR)),
    ),
    "band_curls" to ExerciseAnimation(
        poses = listOf(
            P.stand(armN = Fk(2f, 10f)),
            P.stand(armN = Fk(5f, 150f)),
        ),
        phases = upDown(),
        props = listOf(Band(OnJoint(JointRef.TOE_N), OnJoint(JointRef.HAND_N))),
    ),

    // ---------- Trizeps ----------
    "dips_stuhl" to ExerciseAnimation(
        poses = listOf(
            Pose(
                hipX = -0.10f, hipY = 0.52f, torso = 191.5f, head = 184f,
                armN = Ik(-0.24f, 0.48f),
                legN = Ik(0.47f, P.GROUND), legF = Ik(0.40f, P.GROUND),
            ),
            Pose(
                hipX = -0.05f, hipY = 0.22f, torso = 192f, head = 184f,
                armN = Ik(-0.24f, 0.48f),
                legN = Ik(0.47f, P.GROUND), legF = Ik(0.40f, P.GROUND),
            ),
        ),
        phases = downUp(),
        props = listOf(Box(x = -0.66f, w = 0.46f, h = 0.45f, style = BoxStyle.CHAIR)),
    ),
    "enge_liegestuetze" to ExerciseAnimation(
        // Hände unter der Brust, Ellbogen streifen eng am Körper nach hinten
        poses = listOf(
            P.plankLine(ankleX = -1.25f, shoulderY = 0.55f, armN = Ik(-0.09f, 0.03f)),
            P.plankLine(ankleX = -1.25f, shoulderY = 0.19f, armN = Ik(-0.09f, 0.03f)),
        ),
        phases = downUp(),
        pivot = Vec(-1.25f, 0.15f),
    ),
    "trizeps_ueberkopf" to ExerciseAnimation(
        // [gebeugt hinter dem Kopf, gestreckt]
        poses = listOf(
            P.stand(armN = Fk(174f, 290f)),
            P.stand(armN = Fk(178f, 182f)),
        ),
        phases = thereAndBack("strecken – 1 Sek", 1f, "senken – 2 Sek", 2f),
        props = listOf(Dumbbell(Hands.NEAR, DumbbellLook.BAR, angle = 0f)),
    ),
    "trizeps_kickbacks" to ExerciseAnimation(
        poses = listOf(
            bentOver(armN = Fk(-95f, 0f)),
            bentOver(armN = Fk(-95f, -93f)),
        ),
        phases = thereAndBack("strecken – 1 Sek", 1f, "zurück – 2 Sek", 2f, hold = 1f),
        props = listOf(SUPPORT_CHAIR, Dumbbell(Hands.NEAR)),
    ),
)
