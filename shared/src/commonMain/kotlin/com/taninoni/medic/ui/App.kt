package com.taninoni.medic.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
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
data class QuizConfig(val title: String, val kind: String, val instant: Boolean, val topicId: String? = null)

sealed interface Screen {
    data object Home : Screen
    data object Picker : Screen
    data class Topic(val id: String) : Screen
    data class Quiz(val items: List<QItem>, val config: QuizConfig) : Screen
    data class Results(val log: List<LogEntry>, val config: QuizConfig) : Screen
    data object Metronome : Screen
    data object Reference : Screen
    data object Account : Screen
    data object Settings : Screen
}

/** Simple back stack. [back] returns false when already at the home screen. */
class Nav {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    val current get() = stack.last()
    fun go(s: Screen) { stack.add(s) }
    fun replace(s: Screen) { stack.removeAt(stack.lastIndex); stack.add(s) }
    fun back(): Boolean = if (stack.size > 1) { stack.removeAt(stack.lastIndex); true } else false
    fun home() { while (stack.size > 1) stack.removeAt(stack.lastIndex) }

    /** Switches to a bottom-bar tab. Tabs sit directly on top of Home, so system back returns there. */
    fun tab(s: Screen) { home(); if (s != Screen.Home) stack.add(s) }
    val onTab get() = current in TABS.map { it.screen }
}

/** Bottom bar tabs, in RTL order (first is rightmost). */
data class Tab(val screen: Screen, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
val TABS = listOf(
    Tab(Screen.Home, "נושאים", Icons.basics),
    Tab(Screen.Picker, "תרגול", Icons.check),
    Tab(Screen.Metronome, "מטרונום", Icons.timer),
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
    AppBack.handler = { if (ViewerState.isOpen) { ViewerState.close(); true } else nav.back() }

    MedicTheme(model.themeMode) {
        val density = LocalDensity.current
        CompositionLocalProvider(
            LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalDensity provides Density(density.density, density.fontScale * model.textScale),
        ) {
            Box(Modifier.fillMaxSize().background(Pal.paper)) {
                if (model.content == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center), color = Pal.blue)
                } else CompositionLocalProvider(LocalBottomInset provides if (nav.onTab) TAB_BAR_SPACE else 0.dp) {
                    when (val s = nav.current) {
                        Screen.Home -> HomeScreen(model, nav)
                        Screen.Picker -> PickerScreen(model, nav)
                        is Screen.Topic -> TopicScreen(model, nav, s.id)
                        is Screen.Quiz -> QuizScreen(model, nav, s)
                        is Screen.Results -> ResultsScreen(model, nav, s)
                        Screen.Metronome -> MetronomeScreen(model, nav)
                        Screen.Reference -> ReferenceScreen(model, nav)
                        Screen.Account -> AccountScreen(model, nav)
                        Screen.Settings -> SettingsScreen(model, nav)
                    }
                    if (nav.onTab) TabBar(nav, Modifier.align(Alignment.BottomCenter))
                    ImageViewerHost()
                }
            }
        }
    }
}
