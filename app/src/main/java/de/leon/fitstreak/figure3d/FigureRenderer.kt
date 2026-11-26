package de.leon.fitstreak.figure3d

import android.content.Context
import android.util.Log
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.Camera
import com.google.android.filament.ColorGrading
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndirectLight
import com.google.android.filament.LightManager
import com.google.android.filament.MaterialInstance
import com.google.android.filament.Renderer
import com.google.android.filament.SwapChain
import com.google.android.filament.ToneMapper
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.Gltfio
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import de.leon.fitstreak.core.anim.ExerciseAnimation
import de.leon.fitstreak.core.model.BodyPart
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/** Farben der 3D-Szene als 0xRRGGBB (sRGB). */
data class Figure3Colors(
    val body: Int,
    val joint: Int,
    val primary: Int,
    val secondary: Int,
    val mat: Int,
    val steel: Int,
    val dark: Int,
    val band: Int,
)

/**
 * Was alle 3D-Ansichten teilen: die Filament-Engine, die Modell-Dateien und das Skelett.
 * Wird beim ersten Gebrauch angelegt und lebt so lange wie der Prozess – das Öffnen weiterer
 * Übungen kostet dann nur noch das Anlegen der Szene.
 */
class Figure3DRuntime private constructor(context: Context) {
    val engine: Engine
    val materials: UbershaderProvider
    val loader: AssetLoader
    val resources: ResourceLoader
    val colorGrading: ColorGrading
    val figureBytes: ByteBuffer
    val boxBytes: ByteBuffer
    val cylinderBytes: ByteBuffer
    val skeleton: FigureSkeleton
    val rig: Rig3D

    init {
        Filament.init()
        Gltfio.init()
        val figure = context.assets.open("figure3d/figure.glb").use { it.readBytes() }
        skeleton = FigureSkeleton.from(GlbFile(figure))
        rig = Rig3D(skeleton)
        figureBytes = direct(figure)
        boxBytes = direct(context.assets.open("figure3d/box.glb").use { it.readBytes() })
        cylinderBytes = direct(context.assets.open("figure3d/cylinder.glb").use { it.readBytes() })
        engine = Engine.create()
        materials = UbershaderProvider(engine)
        loader = AssetLoader(engine, materials, EntityManager.get())
        resources = ResourceLoader(engine)
        // „PBR Neutral“ lässt die Grundfarben stehen – das Rot der Zielmuskeln bleibt das Rot der App
        colorGrading = ColorGrading.Builder().toneMapper(ToneMapper.PBRNeutralToneMapper()).build(engine)
    }

    private fun direct(bytes: ByteArray): ByteBuffer =
        ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).also { it.rewind() }

    fun load(bytes: ByteBuffer): FilamentAsset {
        bytes.rewind()
        val asset = loader.createAsset(bytes) ?: error("Modell konnte nicht geladen werden")
        resources.loadResources(asset)
        asset.releaseSourceData()
        return asset
    }

    companion object {
        const val TAG = "Figure3D"
        private var instance: Figure3DRuntime? = null
        private var failed = false

        /** Nur vom Haupt-Thread aufrufen. Liefert null, wenn die Engine auf diesem Gerät nicht startet. */
        fun get(context: Context): Figure3DRuntime? {
            instance?.let { return it }
            if (failed) return null
            return try {
                Figure3DRuntime(context.applicationContext).also { instance = it }
            } catch (t: Throwable) {
                Log.w(TAG, "3D-Engine nicht verfügbar", t)
                failed = true
                null
            }
        }
    }
}

/**
 * Zeichnet die Figur in eine [TextureView]: eigene Szene, Kamera und Lichter, gemeinsame Engine.
 * Die Pose kommt pro Frame aus [Rig3D] – es gibt keine vorgefertigten Animationsclips.
 */
class FigureRenderer(private val runtime: Figure3DRuntime, private val textureView: TextureView) {
    private val engine = runtime.engine
    private val renderer: Renderer = engine.createRenderer()
    private val scene = engine.createScene()
    private val view: View = engine.createView()
    private val cameraEntity = EntityManager.get().create()
    private val camera: Camera = engine.createCamera(cameraEntity)
    private val sun = EntityManager.get().create()
    private val indirect: IndirectLight
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)
    private val displayHelper = DisplayHelper(textureView.context)
    private var swapChain: SwapChain? = null

    private val figure: FilamentAsset = runtime.load(runtime.figureBytes)
    private val figureMaterials: Array<MaterialInstance> = figure.instance.materialInstances
    private val nodeEntities: IntArray
    private val nodeInstances: IntArray

    private inner class PropSlot(bytes: ByteBuffer) {
        val asset: FilamentAsset = runtime.load(bytes)
        val material: MaterialInstance? = asset.instance.materialInstances.firstOrNull()
        val transform: Int = engine.transformManager.getInstance(asset.root)
        var visible = false
        var role: Prop3Role? = null

        fun show(matrix: FloatArray, newRole: Prop3Role) {
            engine.transformManager.setTransform(transform, matrix)
            if (role != newRole) {
                role = newRole
                paint(material, propColor(newRole), roughness = if (newRole == Prop3Role.STEEL) 0.35f else 0.8f, metallic = if (newRole == Prop3Role.STEEL) 0.6f else 0f)
            }
            if (!visible) { scene.addEntities(asset.entities); visible = true }
        }

        fun hide() {
            if (visible) { scene.removeEntities(asset.entities); visible = false }
        }
    }

    private val boxes = List(10) { PropSlot(runtime.boxBytes) }
    private val cylinders = List(14) { PropSlot(runtime.cylinderBytes) }

    private var colors: Figure3Colors? = null
    private var anim: ExerciseAnimation? = null
    private var primary: Set<BodyPart> = emptySet()
    private var secondary: Set<BodyPart> = emptySet()
    private var orbit: Orbit? = null
    private var aspect = 1.0
    private var dirty = true
    private var lastTime = Float.NaN
    private var destroyed = false

    /** Wird einmal aufgerufen, sobald das erste Bild auf dem Schirm ist. */
    var onFirstFrame: (() -> Unit)? = null

    init {
        val skeleton = runtime.skeleton
        val tm = engine.transformManager
        nodeEntities = IntArray(skeleton.nodes.size) { figure.getFirstEntityByName(skeleton.nodes[it].name) }
        nodeInstances = IntArray(skeleton.nodes.size) { if (nodeEntities[it] != 0) tm.getInstance(nodeEntities[it]) else 0 }
        // Die Hüllbox stammt aus der T-Pose; liegende Posen würden sonst fälschlich weggeschnitten.
        val rm = engine.renderableManager
        for (entity in figure.entities) if (rm.hasComponent(entity)) rm.setCulling(rm.getInstance(entity), false)
        scene.addEntities(figure.entities)

        LightManager.Builder(LightManager.Type.DIRECTIONAL)
            .color(1f, 0.98f, 0.95f)
            .intensity(78_000f)
            .direction(-0.45f, -0.8f, -0.4f)
            .castShadows(true)
            .shadowOptions(LightManager.ShadowOptions().apply { mapSize = 1024; shadowFar = 12f; stable = true })
            .build(engine, sun)
        scene.addEntity(sun)
        // gleichmäßiges Umgebungslicht, damit Schattenseiten nicht absaufen
        indirect = IndirectLight.Builder().irradiance(1, floatArrayOf(1f, 1f, 1f)).intensity(42_000f).build(engine)
        scene.indirectLight = indirect

        view.scene = scene
        view.camera = camera
        view.blendMode = View.BlendMode.TRANSLUCENT
        view.colorGrading = runtime.colorGrading
        view.setShadowingEnabled(true)
        view.multiSampleAntiAliasingOptions = View.MultiSampleAntiAliasingOptions().apply { enabled = true; sampleCount = 4 }
        renderer.clearOptions = Renderer.ClearOptions().apply { clear = true }

        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                swapChain?.let { engine.destroySwapChain(it) }
                swapChain = engine.createSwapChain(surface, uiHelper.swapChainFlags)
                textureView.display?.let { displayHelper.attach(renderer, it) }
                dirty = true
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                swapChain?.let {
                    engine.destroySwapChain(it)
                    engine.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                view.viewport = Viewport(0, 0, width, height)
                aspect = width.toDouble() / height.coerceAtLeast(1)
                dirty = true
            }
        }
        uiHelper.isOpaque = false
        uiHelper.attachTo(textureView)
    }

    private fun linear(c: Int): Float = (c / 255f).let { if (it <= 0.04045f) it / 12.92f else ((it + 0.055f) / 1.055f).pow(2.4f) }

    private fun paint(material: MaterialInstance?, rgb: Int, roughness: Float, metallic: Float = 0f) {
        material ?: return
        material.setParameter("baseColorFactor", linear((rgb shr 16) and 0xFF), linear((rgb shr 8) and 0xFF), linear(rgb and 0xFF), 1f)
        material.setParameter("roughnessFactor", roughness)
        material.setParameter("metallicFactor", metallic)
    }

    private fun propColor(role: Prop3Role): Int {
        val c = colors ?: return 0x808080
        return when (role) {
            Prop3Role.MAT -> c.mat
            Prop3Role.STEEL -> c.steel
            Prop3Role.DARK -> c.dark
            Prop3Role.BAND -> c.band
        }
    }

    private fun applyColors() {
        val c = colors ?: return
        for (material in figureMaterials) {
            when (paintFor(material.name, primary, secondary)) {
                Paint3.BODY -> paint(material, c.body, 0.62f)
                Paint3.JOINT -> paint(material, c.joint, 0.5f)
                Paint3.PRIMARY -> paint(material, c.primary, 0.55f)
                Paint3.SECONDARY -> paint(material, c.secondary, 0.55f)
            }
        }
        for (slot in boxes + cylinders) slot.role = null
        dirty = true
    }

    fun setColors(colors: Figure3Colors) {
        if (this.colors == colors) return
        this.colors = colors
        applyColors()
    }

    fun setExercise(anim: ExerciseAnimation, primary: Set<BodyPart>, secondary: Set<BodyPart>) {
        if (this.anim === anim && this.primary == primary && this.secondary == secondary) return
        this.anim = anim
        this.primary = primary
        this.secondary = secondary
        applyColors()
    }

    /** Blickwinkel relativ zur Startansicht der Übung (Grad). */
    fun setView(yawOffset: Float, pitchOffset: Float) {
        val a = anim ?: return
        val base = Orbit.forAnimation(a, runtime.rig.lift)
        val next = base.with(yaw = base.yaw + yawOffset, pitch = base.pitch + pitchOffset)
        val old = orbit
        if (old == null || old.yaw != next.yaw || old.pitch != next.pitch || old.target != next.target || old.radius != next.radius) {
            orbit = next
            dirty = true
        }
    }

    /** Einmal pro Bildschirm-Frame aufrufen. Zeichnet nur, wenn sich etwas geändert hat. */
    fun frame(time: Float, frameTimeNanos: Long) {
        if (destroyed) return
        val a = anim ?: return
        val chain = swapChain ?: return
        if (!uiHelper.isReadyToRender) return
        if (!dirty && time == lastTime) return
        val o = orbit ?: Orbit.forAnimation(a, runtime.rig.lift).also { orbit = it }

        val rig = runtime.rig
        val solved = rig.solve(a, time)
        val tm = engine.transformManager
        tm.openLocalTransformTransaction()
        for (i in nodeInstances.indices) if (nodeInstances[i] != 0) tm.setTransform(nodeInstances[i], solved.local[i])
        var box = 0
        var cylinder = 0
        for (prop in rig.props(a, solved)) {
            val slot = if (prop.kind == Prop3Kind.BOX) boxes.getOrNull(box++) else cylinders.getOrNull(cylinder++)
            slot?.show(prop.matrix, prop.role)
        }
        for (i in box until boxes.size) boxes[i].hide()
        for (i in cylinder until cylinders.size) cylinders[i].hide()
        tm.commitLocalTransformTransaction()
        figure.instance.animator.updateBoneMatrices()

        val eye = o.eye(o.distance(Orbit.FOV, aspect.toFloat()))
        camera.setProjection(Orbit.FOV.toDouble(), aspect, 0.1, 40.0, Camera.Fov.VERTICAL)
        camera.lookAt(eye.x.toDouble(), eye.y.toDouble(), eye.z.toDouble(), o.target.x.toDouble(), o.target.y.toDouble(), o.target.z.toDouble(), 0.0, 1.0, 0.0)

        if (renderer.beginFrame(chain, frameTimeNanos)) {
            renderer.render(view)
            renderer.endFrame()
            dirty = false
            lastTime = time
            onFirstFrame?.let { it(); onFirstFrame = null }
        }
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        uiHelper.detach()
        displayHelper.detach()
        swapChain?.let { engine.destroySwapChain(it); swapChain = null }
        for (slot in boxes + cylinders) { slot.hide(); runtime.loader.destroyAsset(slot.asset) }
        scene.removeEntities(figure.entities)
        runtime.loader.destroyAsset(figure)
        scene.removeEntity(sun)
        engine.destroyEntity(sun)
        engine.destroyIndirectLight(indirect)
        engine.destroyRenderer(renderer)
        engine.destroyView(view)
        engine.destroyScene(scene)
        engine.destroyCameraComponent(cameraEntity)
        EntityManager.get().destroy(cameraEntity)
        EntityManager.get().destroy(sun)
        engine.flushAndWait()
    }
}
