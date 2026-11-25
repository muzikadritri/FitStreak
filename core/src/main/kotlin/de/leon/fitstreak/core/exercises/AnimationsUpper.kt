package de.leon.fitstreak.core.exercises

import de.leon.fitstreak.core.anim.Dumbbell
import de.leon.fitstreak.core.anim.ExerciseAnimation
import de.leon.fitstreak.core.anim.Fk
import de.leon.fitstreak.core.anim.Hands
import de.leon.fitstreak.core.anim.Ik
import de.leon.fitstreak.core.anim.P
import de.leon.fitstreak.core.anim.Vec
import de.leon.fitstreak.core.anim.View
import de.leon.fitstreak.core.anim.downUp
import de.leon.fitstreak.core.anim.upDown

internal val upperBodyAnimations: Map<String, ExerciseAnimation> = mapOf(
    "liegestuetze" to ExerciseAnimation(
        poses = listOf(
            P.plankLine(ankleX = -1.25f, shoulderY = 0.57f, armN = Ik(0.02f, 0.03f)),
            P.plankLine(ankleX = -1.25f, shoulderY = 0.20f, armN = Ik(0.02f, 0.03f)),
        ),
        phases = downUp(),
        pivot = Vec(-1.25f, 0.15f),
    ),
    "bizeps_curls" to ExerciseAnimation(
        poses = listOf(
            P.stand(armN = Fk(0f, 8f), armF = Fk(-4f, 4f)),
            P.stand(armN = Fk(4f, 155f), armF = Fk(-4f, 4f)),
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
        phases = upDown(),
        props = listOf(Dumbbell(Hands.BOTH)),
    ),
)
