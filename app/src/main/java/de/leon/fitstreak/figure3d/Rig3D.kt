package de.leon.fitstreak.figure3d

import de.leon.fitstreak.core.anim.Anchor
import de.leon.fitstreak.core.anim.Band
import de.leon.fitstreak.core.anim.Body
import de.leon.fitstreak.core.anim.Box
import de.leon.fitstreak.core.anim.BoxStyle
import de.leon.fitstreak.core.anim.Dumbbell
import de.leon.fitstreak.core.anim.DumbbellLook
import de.leon.fitstreak.core.anim.ExerciseAnimation
import de.leon.fitstreak.core.anim.Fixed
import de.leon.fitstreak.core.anim.Hands
import de.leon.fitstreak.core.anim.JointRef
import de.leon.fitstreak.core.anim.OnJoint
import de.leon.fitstreak.core.anim.PullBar
import de.leon.fitstreak.core.anim.Skeleton
import de.leon.fitstreak.core.anim.Vec
import de.leon.fitstreak.core.anim.View
import de.leon.fitstreak.core.anim.Wall
import de.leon.fitstreak.core.anim.dir
import de.leon.fitstreak.core.anim.resolve

/** Ergebnis für einen Frame: Welt- und lokale Matrizen aller Knoten plus die Körperachsen. */
class Solved(
    val world: Array<FloatArray>,
    val local: Array<FloatArray>,
    val left: V3,
    val up: V3,
    val forward: V3,
    /** Griffmitte der Hände (nah, fern) – dort sitzen Hanteln, Stange und Band. */
    val gripNear: V3,
    val gripFar: V3,
)

enum class Prop3Kind { BOX, CYLINDER }

enum class Prop3Role { MAT, STEEL, DARK, BAND }

/** Requisite als skaliertes Grundobjekt (Einheitswürfel oder -zylinder). */
class Prop3(val kind: Prop3Kind, val matrix: FloatArray, val role: Prop3Role)

/**
 * Überträgt die 2D-Keyframe-Posen der Übungen auf das 3D-Skelett des Mannequins.
 *
 * Welt: +Y oben, +Z Blickrichtung der Figur, +X ihre linke Seite (wie das Modell in der T-Pose).
 * Seitenansicht: die 2D-Ebene ist die Y/Z-Ebene, „nah“ = rechter Arm/rechtes Bein.
 * Frontansicht: die 2D-Ebene ist die X/Y-Ebene, „nah“ (rechts im Bild) = linke Körperseite.
 *
 * Jeder Knochen bekommt eine Welt-Drehung, die seine Ruhe-Richtung auf die Zielrichtung dreht und
 * dabei die Scharnierachse (Ellbogen/Knie) festlegt – so verdrehen sich die Gliedmaßen nicht.
 * Was die flache 2D-Zeichnung nicht braucht, ergänzt die 3D-Lösung: Hände liegen flach auf Boden
 * oder Stuhl, greifen Hanteln mit geschlossenen Fingern, und die Füße stehen nebeneinander statt versetzt.
 */
class Rig3D(private val skeleton: FigureSkeleton) {
    private fun i(name: String) = skeleton.index(name).also { require(it >= 0) { "Gelenk fehlt: $name" } }
    private fun restPos(index: Int) = Mat4.translation(skeleton.restWorld[index])
    private fun restLin(index: Int) = Mat4.linear(skeleton.restWorld[index])

    private val torso = listOf("Hips", "Spine", "Spine1", "Spine2", "Neck", "LeftShoulder", "RightShoulder").map(::i)
    private val head = i("Head")

    private inner class Arm(side: String) {
        val root = i("${side}Arm")
        val elbow = i("${side}ForeArm")
        val hand = i("${side}Hand")
        val restDir = (restPos(hand) - restPos(elbow)).normalized()

        /** Fingerglieder mit ihrer Beuge-Achse (Welt, Ruhehaltung). Daumen beugt quer zur Hand. */
        val fingers: List<Pair<Int, V3>> = buildList {
            val curl = V3.Z * -restDir.x
            for (finger in listOf("Index", "Middle", "Ring", "Pinky")) for (k in 1..3) {
                skeleton.index("${side}Hand$finger$k").takeIf { it >= 0 }?.let { add(it to curl) }
            }
            for (k in 1..3) skeleton.index("${side}HandThumb$k").takeIf { it >= 0 }?.let { add(it to (V3.Y * -restDir.x + V3.Z * -0.4f * restDir.x).normalized()) }
        }
    }

    private inner class Leg(side: String) {
        val root = i("${side}UpLeg")
        val knee = i("${side}Leg")
        val foot = i("${side}Foot")
        val toe = i("${side}ToeBase")
    }

    private val armL = Arm("Left")
    private val armR = Arm("Right")
    private val legL = Leg("Left")
    private val legR = Leg("Right")

    private val hipOrigin = (restPos(legL.root) + restPos(legR.root)) * 0.5f
    private val shoulderOrigin = (restPos(armL.root) + restPos(armR.root)) * 0.5f

    /** Die Figur wird so angehoben, dass die Sohlen den Boden berühren (das 2D-Rig rechnet mit Knöchelhöhe). */
    val lift: Float = restPos(legL.foot).y - Body.ANKLE

    private val torsoScale = Body.TORSO / (shoulderOrigin.y - hipOrigin.y)

    private fun point(v: Vec, view: View) = if (view == View.SIDE) V3(0f, v.y + lift, v.x) else V3(v.x, v.y + lift, 0f)
    private fun vector(a: Vec, b: Vec, view: View) = if (view == View.SIDE) V3(0f, b.y - a.y, b.x - a.x) else V3(b.x - a.x, b.y - a.y, 0f)

    /** Was sich aus der ganzen Übung ergibt (einmal pro Animation berechnet). */
    private class Info(
        val anim: ExerciseAnimation,
        /** Beide Arme machen dasselbe (liegen in der Seitenansicht deckungsgleich). */
        val armsTogether: Boolean,
        /** Eine Hantel wird mit beiden Händen gehalten (Goblet Squat, Trizeps über Kopf). */
        val sharedDumbbell: Boolean,
        val gripNear: Boolean,
        val gripFar: Boolean,
        /** Auflageflächen: Boden und Oberseiten von Stuhl/Bank (x-Bereich, Höhe im 2D-Rig). */
        val surfaces: List<Triple<Float, Float, Float>>,
    )

    private var cached: Info? = null

    private fun info(anim: ExerciseAnimation): Info {
        cached?.let { if (it.anim === anim) return it }
        val side = anim.view == View.SIDE
        val together = side && anim.poses.all { p ->
            val sk = p.resolve(anim.view)
            (sk.handN - sk.handF).length < 0.05f && (sk.elbowN - sk.elbowF).length < 0.05f
        }
        val dumbbells = anim.props.filterIsInstance<Dumbbell>()
        val shared = together && dumbbells.any { it.hands == Hands.NEAR && it.look == DumbbellLook.BAR && kotlin.math.abs(it.angle) < 20f }
        val bands = anim.props.filterIsInstance<Band>().flatMap { listOf(it.from, it.to) }.filterIsInstance<OnJoint>().map { it.joint }
        val bar = anim.props.any { it is PullBar }
        val near = bar || dumbbells.any { it.hands != Hands.FAR } || JointRef.HAND_N in bands
        val far = bar || dumbbells.any { it.hands != Hands.NEAR } || JointRef.HAND_F in bands || (together && near)
        val surfaces = listOf(Triple(-100f, 100f, 0f)) +
            anim.props.filterIsInstance<Box>().map { Triple(it.x - 0.06f, it.x + it.w + 0.06f, it.h) }
        return Info(anim, together, shared, near, far, surfaces).also { cached = it }
    }

    /** Zwei-Knochen-IK: Gelenkposition zwischen [root] und [target], geknickt in Richtung [pole]. */
    private fun bend(root: V3, target: V3, l1: Float, l2: Float, pole: V3): Pair<V3, V3> {
        val to = target - root
        val dist = to.length.coerceIn(kotlin.math.abs(l1 - l2) + 1e-3f, l1 + l2 - 1e-4f)
        val axis = to.normalized(-V3.Y)
        val cosA = ((l1 * l1 + dist * dist - l2 * l2) / (2f * l1 * dist)).coerceIn(-1f, 1f)
        val out = pole.rejectFrom(axis).normalized(V3.Z.rejectFrom(axis).normalized(V3.X))
        val joint = root + axis * (l1 * cosA) + out * (l1 * kotlin.math.sqrt(1f - cosA * cosA))
        return joint to (root + axis * dist)
    }

    private fun smooth(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun solve(anim: ExerciseAnimation, time: Float): Solved = solve(anim, anim.frameAt(time).pose.resolve(anim.view))

    fun solve(anim: ExerciseAnimation, sk: Skeleton): Solved {
        val info = info(anim)
        val view = anim.view
        val n = skeleton.nodes.size
        val world = arrayOfNulls<FloatArray>(n)
        val localOverride = arrayOfNulls<FloatArray>(n)
        val side = view == View.SIDE

        val hipMid = point(sk.hip, view)
        val up = vector(sk.hip, sk.shoulder, view).normalized()
        val leftRaw = if (side) V3.X else vector(sk.shoulderF, sk.shoulderN, view)
        val left = leftRaw.rejectFrom(up).normalized(V3.X)
        val forward = left cross up
        val torsoRot = M3(left, up, forward)

        fun placed(index: Int) = hipMid + torsoRot * ((restPos(index) - hipOrigin) * torsoScale)

        for (j in torso) world[j] = Mat4.compose(torsoRot * restLin(j), placed(j))

        // Kopf: eigene Richtung (Blick folgt dem 2D-Rig)
        val headDir = vector(sk.shoulder, sk.head, view).normalized(up)
        val headLeft = left.rejectFrom(headDir).normalized(left)
        world[head] = Mat4.compose(M3(headLeft, headDir, headLeft cross headDir) * restLin(head), placed(head))

        fun hingeBlend(computed: V3, fallback: V3): V3 {
            val k = (computed.length / 0.2f).coerceIn(0f, 1f)
            return (computed.normalized(fallback) * k + fallback * (1f - k)).normalized(fallback)
        }

        /** Höhe (Welt) der Auflagefläche unter einem 2D-Punkt und wie stark die Hand dort aufliegt (0..1). */
        fun support(h: Vec): Pair<Float, Float> {
            var best = 0f to 0f
            for ((x0, x1, top) in info.surfaces) {
                val x = if (side) h.x else 0f
                if (x < x0 || x > x1) continue
                val w = 1f - smooth(0.07f, 0.16f, kotlin.math.abs(h.y - top))
                if (w > best.second) best = (if (top == 0f) 0f else top + lift) to w
            }
            return best
        }

        fun solveArm(arm: Arm, s: Vec, e: Vec, h: Vec, grip: Boolean): V3 {
            val root = placed(arm.root)
            val l1 = (e - s).length
            val l2 = (h - e).length
            var pElbow = root + vector(s, e, view)
            var pHand = pElbow + vector(e, h, view)
            var changed = false
            // Eine Hantel für beide Hände: Hände treffen sich vor der Körpermitte
            if (info.sharedDumbbell) {
                val off = (root - hipMid) dot left
                pHand -= left * (off - 0.04f * kotlin.math.sign(off))
                changed = true
            }
            // Aufstützen: Handgelenk knapp über der Fläche, Hand flach
            val (surfaceY, weight) = if (grip) 0f to 0f else support(h)
            val pressing = weight * smooth(0.15f, 0.5f, -(pHand - pElbow).normalized().y)
            if (pressing > 0f) {
                pHand = pHand.copy(y = pHand.y + (surfaceY + PALM - pHand.y) * pressing)
                changed = true
            }
            if (changed) {
                val (joint, end) = bend(root, pHand, l1, l2, pElbow - root)
                pElbow = joint; pHand = end
            }
            val d1 = (pElbow - root).normalized(-up)
            val d2 = (pHand - pElbow).normalized(d1)
            // gestreckter Arm: Ellbogen beugt nach vorn (bzw. nach oben, wenn der Arm nach vorn zeigt)
            val flex = forward.rejectFrom(d1).let { if (it.length < 0.2f) up.rejectFrom(d1) else it }.normalized()
            val hinge = hingeBlend(d1 cross d2, (d1 cross flex).normalized())
            val rest1 = (restPos(arm.elbow) - restPos(arm.root)).normalized()
            val restHinge = rest1 cross V3.Z
            val rot1 = M3.frame(d1, hinge) * M3.frame(rest1, restHinge).transposed()
            val rot2 = M3.frame(d2, hinge) * M3.frame(arm.restDir, restHinge).transposed()
            world[arm.root] = Mat4.compose(rot1 * restLin(arm.root), root)
            world[arm.elbow] = Mat4.compose(rot2 * restLin(arm.elbow), pElbow)

            // Hand: folgt dem Unterarm oder liegt flach auf (Finger zeigen Richtung Kopf bzw. nach vorn)
            var handRot = rot2
            if (pressing > 0.01f) {
                val flatDir = (up.copy(y = 0f) + forward.copy(y = 0f) * 0.6f).normalized(V3.Z)
                val dirBlend = (d2 * (1f - pressing) + flatDir * pressing).normalized(flatDir)
                val palmBlend = ((rot2 * -V3.Y) * (1f - pressing) + V3.Y * -pressing).normalized(-V3.Y)
                val palm = palmBlend.rejectFrom(dirBlend).normalized(-V3.Y)
                handRot = M3(dirBlend, palm, dirBlend cross palm) * M3(arm.restDir, -V3.Y, arm.restDir cross -V3.Y).transposed()
            }
            world[arm.hand] = Mat4.compose(handRot * restLin(arm.hand), pHand)

            // Finger: locker gebeugt, beim Greifen geschlossen
            val curl = if (grip) 62f else 14f * (1f - pressing)
            for ((joint, axis) in arm.fingers) {
                val lin = restLin(joint)
                val scale = lin.cx.length
                val a = (lin.transposed() * axis).normalized()
                localOverride[joint] = Mat4.mul(skeleton.nodes[joint].restLocal, Mat4.compose(M3.rotation(a, curl), V3.ZERO))
                if (scale == 0f) localOverride[joint] = null
            }
            val handDir = handRot * arm.restDir
            val palmNormal = handRot * -V3.Y
            return pHand + handDir * 0.085f + palmNormal * (if (grip) 0.028f else 0f)
        }

        fun solveLeg(leg: Leg, hp: Vec, k: Vec, a: Vec, toe: Vec, ankleShift: Float) {
            val root = placed(leg.root)
            var pKnee = root + vector(hp, k, view)
            var pAnkle = pKnee + vector(k, a, view)
            if (ankleShift != 0f) {
                val (joint, end) = bend(root, pAnkle + V3.Z * ankleShift, (k - hp).length, (a - k).length, pKnee - root)
                pKnee = joint; pAnkle = end
            }
            val d1 = (pKnee - root).normalized(-up)
            val d2 = (pAnkle - pKnee).normalized(d1)
            val hinge = hingeBlend(d1 cross d2, left.rejectFrom(d1).normalized(V3.X))
            val rest1 = (restPos(leg.knee) - restPos(leg.root)).normalized()
            val rest2 = (restPos(leg.foot) - restPos(leg.knee)).normalized()
            world[leg.root] = Mat4.compose(M3.frame(d1, hinge) * M3.frame(rest1, V3.X).transposed() * restLin(leg.root), root)
            world[leg.knee] = Mat4.compose(M3.frame(d2, hinge) * M3.frame(rest2, V3.X).transposed() * restLin(leg.knee), pKnee)
            // Fuß: Seitenansicht wie im 2D-Rig, Frontansicht in Richtung der Kniescheibe
            val toeDir = if (side) vector(a, toe, view).normalized(forward) else (d2 cross hinge).copy(y = 0f).normalized(forward)
            val footHinge = if (side) hinge else (V3.Y cross toeDir)
            world[leg.foot] = Mat4.compose(M3.frame(toeDir, footHinge) * M3.frame(V3.Z, V3.X).transposed() * restLin(leg.foot), pAnkle)
        }

        // Seitenansicht: nah = rechts. Frontansicht: nah (rechts im Bild) = linke Körperseite.
        val nearArm = if (side) armR else armL
        val farArm = if (side) armL else armR
        val nearLeg = if (side) legR else legL
        val farLeg = if (side) legL else legR
        val gripNear = solveArm(nearArm, sk.shoulderN, sk.elbowN, sk.handN, info.gripNear)
        val gripFar = solveArm(farArm, sk.shoulderF, sk.elbowF, sk.handF, info.gripFar)

        // Im 2D-Bild stehen die Füße leicht versetzt, damit man beide sieht – in 3D stehen sie nebeneinander.
        val gap = sk.ankleN.x - sk.ankleF.x
        val standing = side && kotlin.math.abs(gap) < 0.14f &&
            kotlin.math.abs(sk.ankleN.y - Body.ANKLE) < 0.02f && kotlin.math.abs(sk.ankleF.y - Body.ANKLE) < 0.02f
        val shift = if (standing) gap / 2f else 0f
        solveLeg(nearLeg, sk.hipN, sk.kneeN, sk.ankleN, sk.toeN, -shift)
        solveLeg(farLeg, sk.hipF, sk.kneeF, sk.ankleF, sk.toeF, shift)

        // Alle übrigen Knoten (Zehen, Fingerspitzen …) folgen ihrem Elternknoten
        val w = Array(n) { Mat4.identity() }
        val local = Array(n) { localOverride[it] ?: skeleton.nodes[it].restLocal }
        for (index in skeleton.order) {
            val node = skeleton.nodes[index]
            val driven = world[index]
            if (driven != null) {
                w[index] = driven
                local[index] = if (node.parent < 0) driven else Mat4.mul(Mat4.inverseAffine(w[node.parent]), driven)
            } else {
                w[index] = if (node.parent < 0) local[index] else Mat4.mul(w[node.parent], local[index])
            }
        }
        return Solved(w, local, left, up, forward, gripNear, gripFar)
    }

    /** Requisiten der Übung als 3D-Grundobjekte, passend zur gelösten Pose. */
    fun props(anim: ExerciseAnimation, solved: Solved): List<Prop3> {
        val info = info(anim)
        val view = anim.view
        val side = view == View.SIDE
        val out = ArrayList<Prop3>()
        val nearLeg = if (side) legR else legL
        val farLeg = if (side) legL else legR

        fun rigPoint(x: Float, y: Float) = point(Vec(x, y), view)
        fun cylinder(a: V3, b: V3, diameter: Float, role: Prop3Role) {
            val d = b - a
            if (d.length > 1e-4f) out += Prop3(Prop3Kind.CYLINDER, Mat4.alongAxis((a + b) * 0.5f, d, d.length, diameter), role)
        }
        /** Quader: [along] = Ausdehnung in der 2D-Bildrichtung, [across] = quer dazu. */
        fun box(x: Float, yBase: Float, along: Float, height: Float, across: Float, role: Prop3Role, lateral: Float = 0f) {
            val base = if (side) V3(lateral, yBase, x) else V3(x, yBase, lateral)
            out += Prop3(Prop3Kind.BOX, if (side) Mat4.box(base, across, height, along) else Mat4.box(base, along, height, across), role)
        }
        fun dumbbell(c: V3, axis: V3) {
            cylinder(c - axis * 0.09f, c + axis * 0.09f, 0.028f, Prop3Role.STEEL)
            for (s in listOf(-1f, 1f)) cylinder(c + axis * (s * 0.07f), c + axis * (s * 0.14f), 0.115f, Prop3Role.DARK)
        }

        // Matte unter der Figur
        val b = anim.bounds
        val center = rigPoint((b.minX + b.maxX) / 2f, 0f).copy(y = -0.006f)
        out += Prop3(Prop3Kind.CYLINDER, Mat4.alongAxis(center, V3.Y, 0.012f, maxOf(1.7f, b.width + 0.8f)), Prop3Role.MAT)

        for (prop in anim.props) when (prop) {
            is Dumbbell -> {
                val axis = when (prop.look) {
                    DumbbellLook.END_ON -> if (side) solved.left else V3.Z
                    DumbbellLook.BAR -> dir(prop.angle).let { if (side) V3(0f, it.y, it.x) else V3(it.x, it.y, 0f) }
                }.normalized()
                when {
                    info.sharedDumbbell -> dumbbell((solved.gripNear + solved.gripFar) * 0.5f, axis)
                    prop.hands == Hands.BOTH || (prop.hands == Hands.NEAR && info.armsTogether) -> {
                        dumbbell(solved.gripNear, axis); dumbbell(solved.gripFar, axis)
                    }
                    prop.hands == Hands.NEAR -> dumbbell(solved.gripNear, axis)
                    else -> dumbbell(solved.gripFar, axis)
                }
            }
            is Box -> {
                val top = prop.h + lift
                val mid = prop.x + prop.w / 2f
                when (prop.style) {
                    BoxStyle.CHAIR -> {
                        box(mid, top - 0.04f, prop.w, 0.04f, 0.46f, Prop3Role.DARK)
                        for (dx in listOf(-1f, 1f)) for (dz in listOf(-1f, 1f)) {
                            box(mid + dx * (prop.w / 2f - 0.03f), 0f, 0.035f, top - 0.04f, 0.035f, Prop3Role.STEEL, lateral = dz * 0.2f)
                        }
                    }
                    BoxStyle.BENCH -> {
                        box(mid, top - 0.07f, prop.w, 0.07f, 0.34f, Prop3Role.DARK)
                        for (dx in listOf(-1f, 1f)) box(mid + dx * prop.w * 0.36f, 0f, 0.05f, top - 0.07f, 0.3f, Prop3Role.STEEL)
                    }
                    BoxStyle.STEP -> box(mid, 0f, prop.w, top, 0.5f, Prop3Role.DARK)
                }
            }
            is Wall -> box(prop.x - 0.03f, 0f, 0.04f, prop.height, 1.4f, Prop3Role.DARK)
            is PullBar -> {
                val rig = rigPoint(prop.x, prop.y)
                val hands = (solved.gripNear + solved.gripFar) * 0.5f
                // Die Stange sitzt dort, wo die Hände tatsächlich greifen
                val c = if ((hands - rig).length < 0.3f) hands else rig
                val axis = if (side) V3.X else V3.Z
                cylinder(c - axis * 0.6f, c + axis * 0.6f, 0.032f, Prop3Role.STEEL)
            }
            is Band -> {
                fun anchor(a: Anchor, far: Boolean): V3 = when (a) {
                    is Fixed -> rigPoint(a.x, a.y)
                    is OnJoint -> when (a.joint) {
                        JointRef.HAND_N -> if (far) solved.gripFar else solved.gripNear
                        JointRef.HAND_F -> solved.gripFar
                        JointRef.ANKLE_N -> Mat4.translation(solved.world[(if (far) farLeg else nearLeg).foot])
                        JointRef.ANKLE_F -> Mat4.translation(solved.world[farLeg.foot])
                        JointRef.TOE_N -> Mat4.translation(solved.world[(if (far) farLeg else nearLeg).toe])
                    }
                }
                cylinder(anchor(prop.from, false), anchor(prop.to, false), 0.014f, Prop3Role.BAND)
                // Beide Arme ziehen: das Band läuft zu beiden Händen
                val usesFar = listOf(prop.from, prop.to).any { it is OnJoint && it.joint == JointRef.HAND_F }
                if (info.armsTogether && !usesFar) cylinder(anchor(prop.from, true), anchor(prop.to, true), 0.014f, Prop3Role.BAND)
            }
        }
        return out
    }

    private companion object {
        /** Höhe des Handgelenks über der Fläche, wenn die Hand flach aufliegt. */
        const val PALM = 0.03f
    }
}
