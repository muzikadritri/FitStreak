package de.leon.fitstreak.core.exercises

import de.leon.fitstreak.core.anim.ExerciseAnimation
import de.leon.fitstreak.core.anim.Fk
import de.leon.fitstreak.core.anim.P
import de.leon.fitstreak.core.anim.downUp

internal val lowerBodyAnimations: Map<String, ExerciseAnimation> = mapOf(
    "kniebeugen" to ExerciseAnimation(
        poses = listOf(
            P.stand(armN = Fk(70f, 80f)),
            P.stand(hipX = -0.26f, hipY = 0.48f, torso = 150f, armN = Fk(80f, 85f)),
        ),
        phases = downUp(),
    ),
)
