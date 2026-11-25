package de.leon.fitstreak.core.anim

import kotlin.math.sqrt

/** Kleine Helfer, damit Posen kurz und geometrisch stimmig definiert werden können. */
object P {
    const val GROUND = Body.ANKLE

    /** Aufrechter Stand (Seitenansicht), Füße leicht versetzt und per IK am Boden. */
    fun stand(
        hipX: Float = 0f,
        hipY: Float = Body.STAND_HIP,
        torso: Float = 180f,
        head: Float? = null,
        armN: Limb = Fk(0f, 0f),
        armF: Limb = armN,
        nearFootX: Float = 0.06f,
        farFootX: Float = -0.06f,
    ) = Pose(
        hipX = hipX, hipY = hipY, torso = torso, head = head, armN = armN, armF = armF,
        legN = Ik(nearFootX, GROUND), legF = Ik(farFootX, GROUND),
    )

    /** Stand in der Frontansicht. "Nah" ist rechts im Bild, "fern" links (gespiegelt). */
    fun standFront(
        hipX: Float = 0f,
        hipY: Float = Body.STAND_HIP,
        torso: Float = 180f,
        armN: Limb = Fk(6f, 6f),
        armF: Limb = armN,
        footHalf: Float = 0.12f,
    ) = Pose(
        hipX = hipX, hipY = hipY, torso = torso, armN = armN, armF = armF,
        legN = Ik(footHalf, GROUND), legF = Ik(-footHalf, GROUND),
    )

    /**
     * Gerader Körper im Stütz (Kopf rechts): Knöchel fest bei ([ankleX], [ankleY]),
     * Schultern auf Höhe [shoulderY]. Hüfte und Rumpfwinkel werden berechnet.
     * [hipSag] > 0 lässt die Hüfte durchhängen, < 0 hebt sie an.
     */
    fun plankLine(
        ankleX: Float,
        ankleY: Float = 0.15f,
        shoulderY: Float,
        head: Float? = null,
        armN: Limb,
        armF: Limb = armN,
        foot: Float = 5f,
        hipSag: Float = 0f,
    ): Pose {
        val legs = Body.THIGH + Body.SHIN
        val len = legs + Body.TORSO
        val dy = shoulderY - ankleY
        val dx = sqrt(len * len - dy * dy)
        val ux = dx / len
        val uy = dy / len
        val hip = Vec(ankleX + ux * legs + uy * hipSag, ankleY + uy * legs - ux * hipSag)
        val shoulder = Vec(ankleX + dx, shoulderY)
        return Pose(
            hipX = hip.x, hipY = hip.y,
            torso = angleOf(shoulder - hip),
            head = head, armN = armN, armF = armF,
            legN = Ik(ankleX, ankleY), legF = Ik(ankleX, ankleY),
            footN = foot, footF = foot,
        )
    }

    /** Schulterposition einer Pose – praktisch für IK-Ziele relativ zur Schulter. */
    fun shoulderOf(hipX: Float, hipY: Float, torso: Float): Vec = Vec(hipX, hipY) + dir(torso) * Body.TORSO
}
