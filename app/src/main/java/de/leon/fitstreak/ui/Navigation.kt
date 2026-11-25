package de.leon.fitstreak.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.leon.fitstreak.AppContainer
import de.leon.fitstreak.platform.AndroidPlatform
import de.leon.fitstreak.ui.components.FitIcon
import de.leon.fitstreak.ui.components.FitIconView
import de.leon.fitstreak.ui.library.ExerciseDetailScreen
import de.leon.fitstreak.ui.library.LibraryScreen
import de.leon.fitstreak.ui.onboarding.OnboardingScreen
import de.leon.fitstreak.ui.plan.PlanEditScreen
import de.leon.fitstreak.ui.plan.PlanScreen
import de.leon.fitstreak.ui.profile.ProfileScreen
import de.leon.fitstreak.ui.progress.ProgressScreen
import de.leon.fitstreak.ui.settings.SettingsScreen
import de.leon.fitstreak.ui.tips.TipsScreen
import de.leon.fitstreak.ui.today.TodayScreen
import de.leon.fitstreak.ui.workout.WorkoutScreen
import de.leon.fitstreak.ui.workout.WorkoutViewModel

private object Routes {
    const val TODAY = "today"
    const val LIBRARY = "library"
    const val PROGRESS = "progress"
    const val PROFILE = "profile"
    const val PLAN = "plan"
    const val SETTINGS = "settings"
    const val TIPS = "tips"
    fun exercise(id: String) = "exercise/$id"
    fun workout(templateId: Long) = "workout/$templateId"
    fun planEdit(templateId: Long) = "planEdit/$templateId"
}

private class Tab(val route: String, val label: String, val icon: FitIcon)

private val tabs = listOf(
    Tab(Routes.TODAY, "Heute", FitIcon.TODAY),
    Tab(Routes.LIBRARY, "Übungen", FitIcon.DUMBBELL),
    Tab(Routes.PROGRESS, "Fortschritt", FitIcon.CHART),
    Tab(Routes.PROFILE, "Profil", FitIcon.PERSON),
)

@Composable
fun FitStreakRoot(container: AppContainer, openTodaySignal: Int) {
    val context = LocalContext.current
    val vm: AppViewModel = viewModel(factory = viewModelFactory { initializer { AppViewModel(container.repo) } })
    val data by vm.data.collectAsStateWithLifecycle()
    val platform = remember { AndroidPlatform(context.applicationContext, container) }

    // Beim Zurückkehren in die App: Datum und Streak aktualisieren (z. B. nach Mitternacht).
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    CompositionLocalProvider(LocalPlatform provides platform) {
        when {
            !data.loaded -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            !data.settings.onboardingDone -> OnboardingScreen(onFinish = { vm.completeOnboarding(it) })
            else -> MainNavigation(vm, container, openTodaySignal)
        }
    }
}

private fun NavHostController.openTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun MainNavigation(vm: AppViewModel, container: AppContainer, openTodaySignal: Int) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val showTabs = tabs.any { it.route == currentRoute }

    LaunchedEffect(openTodaySignal) {
        if (openTodaySignal > 0) {
            nav.navigate(Routes.TODAY) {
                popUpTo(nav.graph.findStartDestination().id) { inclusive = false }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showTabs) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    for (tab in tabs) {
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { nav.openTab(tab.route) },
                            icon = { FitIconView(tab.icon) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(nav, startDestination = Routes.TODAY, modifier = Modifier.padding(bottom = inner.calculateBottomPadding())) {
            composable(Routes.TODAY) {
                TodayScreen(
                    vm,
                    onStartWorkout = { nav.navigate(Routes.workout(it)) },
                    onOpenPlan = { nav.navigate(Routes.PLAN) },
                    onExercise = { nav.navigate(Routes.exercise(it)) },
                )
            }
            composable(Routes.LIBRARY) { LibraryScreen(onExercise = { nav.navigate(Routes.exercise(it)) }) }
            composable(Routes.PROGRESS) { ProgressScreen(vm, onExercise = { nav.navigate(Routes.exercise(it)) }) }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    vm,
                    onPlan = { nav.navigate(Routes.PLAN) },
                    onTips = { nav.navigate(Routes.TIPS) },
                    onSettings = { nav.navigate(Routes.SETTINGS) },
                )
            }
            composable("exercise/{id}") { backStack ->
                val data by vm.data.collectAsStateWithLifecycle()
                ExerciseDetailScreen(
                    exerciseId = backStack.arguments?.getString("id").orEmpty(),
                    data = data,
                    onBack = { nav.popBackStack() },
                    onExercise = { nav.navigate(Routes.exercise(it)) },
                )
            }
            composable("workout/{templateId}", arguments = listOf(navArgument("templateId") { type = NavType.LongType })) { backStack ->
                val templateId = backStack.arguments?.getLong("templateId") ?: 0L
                val workoutVm: WorkoutViewModel = viewModel(
                    factory = viewModelFactory { initializer { WorkoutViewModel(container.repo, templateId) } },
                )
                WorkoutScreen(
                    workoutVm,
                    onClose = {
                        vm.refresh()
                        nav.popBackStack(Routes.TODAY, inclusive = false)
                    },
                    onExercise = { nav.navigate(Routes.exercise(it)) },
                )
            }
            composable(Routes.PLAN) {
                PlanScreen(
                    vm,
                    onBack = { nav.popBackStack() },
                    onStart = { nav.navigate(Routes.workout(it)) },
                    onEdit = { nav.navigate(Routes.planEdit(it)) },
                )
            }
            composable("planEdit/{templateId}", arguments = listOf(navArgument("templateId") { type = NavType.LongType })) { backStack ->
                PlanEditScreen(
                    vm,
                    templateId = backStack.arguments?.getLong("templateId") ?: 0L,
                    onBack = { nav.popBackStack() },
                    onExercise = { nav.navigate(Routes.exercise(it)) },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen(vm, onBack = { nav.popBackStack() }) }
            composable(Routes.TIPS) { TipsScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
