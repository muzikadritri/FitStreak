package preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.leon.fitstreak.testing.World
import de.leon.fitstreak.ui.AppViewModel
import de.leon.fitstreak.ui.theme.FitTheme
import de.leon.fitstreak.ui.today.TodayScreen
import de.leon.fitstreak.ui.workout.WorkoutScreen
import de.leon.fitstreak.ui.workout.WorkoutViewModel
import kotlinx.coroutines.runBlocking
import java.io.File

private const val W = 412
private const val H = 915

private fun shot(out: File, name: String, dark: Boolean = false, height: Int = H, frames: Int = 6, content: @Composable () -> Unit) {
    val file = File(out, "$name.png")
    renderPng(file, (W * 1.5f).toInt(), (height * 1.5f).toInt(), density = 1.5f, frames = frames) {
        FitTheme(dark = dark) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
        }
    }
    println("geschrieben: $file")
}

fun screens(out: File, only: Set<String>) = runBlocking {
    fun want(name: String) = only.isEmpty() || name in only
    val world = World()
    world.onboard()
    world.simulate(16, skip = setOf(8))
    val vm = AppViewModel(world.repo)
    vm.refresh()

    if (want("today")) shot(out, "today", height = 1500) { TodayScreen(vm, {}, {}, {}) }
    if (want("today_dark")) shot(out, "today_dark", dark = true, height = 1500) { TodayScreen(vm, {}, {}, {}) }

    if (want("library")) shot(out, "library", height = 1000) { de.leon.fitstreak.ui.library.LibraryScreen {} }
    if (want("detail")) shot(out, "detail", height = 2500) { de.leon.fitstreak.ui.library.ExerciseDetailScreen("liegestuetze", vm.data.value, {}, {}) }
    if (want("progress")) shot(out, "progress", height = 3300) { de.leon.fitstreak.ui.progress.ProgressScreen(vm) {} }
    if (want("profile")) shot(out, "profile", dark = true, height = 1250) { de.leon.fitstreak.ui.profile.ProfileScreen(vm, {}, {}, {}) }
    if (want("settings")) shot(out, "settings", height = 2600) { de.leon.fitstreak.ui.settings.SettingsScreen(vm) {} }
    if (want("plan")) shot(out, "plan", height = 1700) { de.leon.fitstreak.ui.plan.PlanScreen(vm, {}, {}, {}) }
    if (want("planedit")) shot(out, "planedit", height = 1500) { de.leon.fitstreak.ui.plan.PlanEditScreen(vm, vm.data.value.rotation.first().id, {}, {}) }
    if (want("tips")) shot(out, "tips", height = 1400) { de.leon.fitstreak.ui.tips.TipsScreen {} }
    if (want("onboarding")) shot(out, "onboarding") { de.leon.fitstreak.ui.onboarding.OnboardingScreen {} }

    if (want("workout")) {
        val w = world.repo.snapshot().rotation.first()
        val wvm = WorkoutViewModel(world.repo, w.id)
        wvm.finishWarmup()
        shot(out, "workout_warmup_skipped", height = 1100) { WorkoutScreen(wvm, {}, {}) }
        wvm.completeSet(0, 0)
        wvm.completeSet(0, 1)
        shot(out, "workout_rest", height = 1100) { WorkoutScreen(wvm, {}, {}) }
        // alles abhaken und abschließen
        val ui = wvm.ui.value
        ui.exercises.forEachIndexed { i, ex -> ex.sets.forEach { s -> if (!s.done) wvm.completeSet(i, s.index) } }
        shot(out, "workout_done", height = 1100) { WorkoutScreen(wvm, {}, {}) }
        wvm.finish()
        shot(out, "workout_summary", height = 1500, frames = 22) { WorkoutScreen(wvm, {}, {}) }
    }
    if (want("warmup")) {
        world.advanceDays(1)
        val w = world.repo.snapshot().rotation[1]
        val wvm = WorkoutViewModel(world.repo, w.id)
        shot(out, "workout_warmup") { WorkoutScreen(wvm, {}, {}) }
    }
}
