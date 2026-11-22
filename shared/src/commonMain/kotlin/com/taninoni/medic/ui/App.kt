package com.taninoni.medic.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.rememberHazeState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.taninoni.medic.data.AppModel
import com.taninoni.medic.data.LogEntry
import com.taninoni.medic.data.QItem

/** A quiz run. [instant] shows the answer after each question; exams show results only at the end. */
data class QuizConfig(val title: String, val kind: String, val instant: Boolean, val topicId: String? = null, val timeLimitSec: Int = 0)

sealed interface Screen {
    data object Home : Screen
    data object Picker : Screen
    data class Topic(val id: String) : Screen
    data class Quiz(val items: List<QItem>, val config: QuizConfig) : Screen
    data class Results(val log: List<LogEntry>, val config: QuizConfig, val bestStreak: Int = 0) : Screen
    data class Blitz(val items: List<QItem>) : Screen
    data object AnatomyQuiz : Screen
    data object Metronome : Screen
    data object Aed : Screen
    data object Reference : Screen
    data object Protocols : Screen
    data class ProtocolView(val id: String) : Screen
    data object Streak : Screen
    data object Account : Screen
    data object Settings : Screen
    data object Instructor : Screen
    data class Student(val id: Long) : Screen
}

/** Simple back stack. [back] returns false when already at the home screen. */
class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current get() = stack.last()
    /** Direction of the last move, for the screen transition: 1 push, -1 pop, 0 tab switch or replace. */
    var lastMove = 0
        private set
    fun go(s: Screen) { lastMove = 1; stack.add(s) }
    fun replace(s: Screen) { lastMove = 0; stack.removeAt(stack.lastIndex); stack.add(s) }
    fun back(): Boolean = if (stack.size > 1) { lastMove = -1; stack.removeAt(stack.lastIndex); true } else false
    fun home() { lastMove = -1; while (stack.size > 1) stack.removeAt(stack.lastIndex) }

    /** Switches to a bottom-bar tab. Tabs sit directly on top of Home, so system back returns there. */
    fun tab(s: Screen) { home(); if (s != Screen.Home) stack.add(s); lastMove = 0 }
    val onTab get() = current in TABS.map { it.screen }
}

/** Bottom bar tabs, in RTL order (first is rightmost). */
data class Tab(val screen: Screen, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
val TABS = listOf(
    Tab(Screen.Home, "נושאים", Icons.basics),
    Tab(Screen.Picker, "תרגול", Icons.check),
    Tab(Screen.Aed, "דפיברילטור", Icons.dev),
    Tab(Screen.Reference, "מספרים", Icons.hash),
    Tab(Screen.Settings, "הגדרות", Icons.gear),
)

/** Lets the Android activity route the system back button into the app. */
object AppBack {
    var handler: (() -> Boolean)? = null
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val model = remember { AppModel(scope) }
    val nav = remember { Nav() }
    LaunchedEffect(Unit) { model.load() }
    LaunchedEffect(Unit) { AnswerSound.preload() }
    // Roll the streak over at midnight while the app stays open.
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(60_000); if (model.content != null) model.refreshDay() } }
    LaunchedEffect(model.content) { AnimatedImages.byFile = model.content?.topics.orEmpty().flatMap { it.images }.associateBy { it.file } }
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(model.user == null) { if (model.user == null) nav.home() }
    FeedbackPrefs.sound = model.soundOn
    FeedbackPrefs.haptics = model.hapticsOn
    AppBack.handler = { if (ViewerState.isOpen) { ViewerState.close(); true } else nav.back() }

    MedicTheme(model.themeMode) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalDensity provides Density(density.density, density.fontScale * model.textScale),
            LocalSoundOn provides model.soundOn,
        ) {
            val haze = rememberHazeState()
            CompositionLocalProvider(LocalHaze provides haze, LocalHazePerformanceMode provides HazePerformanceMode.Performance, LocalReduceEffects provides model.reduceEffects) {
            Box(Modifier.fillMaxSize().background(Pal.baseTop)) {
                AmbientBackdrop(Modifier.hazeSource(haze, zIndex = 0f))
                if (model.content == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Pal.blue)
                } else if (model.user == null) {
                    // Sign-in is required: instructors follow each student's progress.
                    LoginScreen(model)
                } else CompositionLocalProvider(LocalBottomInset provides if (nav.onTab) TAB_BAR_SPACE else 0.dp) {
                    // Pushes slide in from the start edge over a fade; going back reverses it; tabs cross-fade.
                    AnimatedContent(
                        targetState = nav.current,
                        transitionSpec = {
                            val depth = nav.lastMove
                            if (depth == 0) {
                                (fadeIn(tween(220)) + scaleIn(tween(260), initialScale = .985f)) togetherWith fadeOut(tween(160))
                            } else {
                                val dir = if (depth > 0) 1 else -1
                                (slideInHorizontally(spring(dampingRatio = .9f, stiffness = 420f)) { w -> -dir * w / 3 } + fadeIn(tween(220))) togetherWith
                                    (slideOutHorizontally(tween(260)) { w -> dir * w / 5 } + fadeOut(tween(180)))
                            }
                        },
                        contentKey = { it },
                        label = "screen",
                    ) { s ->
                        when (s) {
                            Screen.Home -> HomeScreen(model, nav)
                            Screen.Picker -> PickerScreen(model, nav)
                            is Screen.Topic -> TopicScreen(model, nav, s.id)
                            is Screen.Quiz -> QuizScreen(model, nav, s)
                            is Screen.Results -> ResultsScreen(model, nav, s)
                            is Screen.Blitz -> BlitzScreen(model, nav, s)
                            Screen.AnatomyQuiz -> AnatomyQuizScreen(model, nav)
                            Screen.Metronome -> MetronomeScreen(model, nav)
                            Screen.Aed -> AedScreen(model, nav)
                            Screen.Reference -> ReferenceScreen(model, nav)
                            Screen.Protocols -> ProtocolsScreen(model, nav)
                            is Screen.ProtocolView -> ProtocolScreen(model, nav, s.id)
                            Screen.Streak -> StreakScreen(model, nav)
                            Screen.Account -> AccountScreen(model, nav)
                            Screen.Settings -> SettingsScreen(model, nav)
                            Screen.Instructor -> InstructorScreen(model, nav)
                            is Screen.Student -> StudentScreen(model, nav, s.id)
                        }
                    }
                    AnimatedVisibility(
                        nav.onTab, Modifier.align(Alignment.BottomCenter),
                        enter = slideInVertically(spring(dampingRatio = .75f, stiffness = 380f)) { it } + fadeIn(),
                        exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(150)),
                    ) { TabBar(nav) }
                    ImageViewerHost()
                    StreakOverlays(model, nav)
                }
                if (splash) SplashScreen(ready = { model.content != null }) { splash = false }
            }
            }
        }
    }
}

