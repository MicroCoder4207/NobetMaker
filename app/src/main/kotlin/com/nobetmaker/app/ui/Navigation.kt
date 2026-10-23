package com.nobetmaker.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nobetmaker.app.ui.screens.CalendarScreen
import com.nobetmaker.app.ui.screens.GenerateScreen
import com.nobetmaker.app.ui.screens.HistoryScreen
import com.nobetmaker.app.ui.screens.PeopleScreen
import com.nobetmaker.app.ui.screens.PersonEditScreen
import com.nobetmaker.app.ui.screens.PrepareScreen
import com.nobetmaker.app.ui.screens.SettingsScreen
import com.nobetmaker.app.ui.screens.StatsScreen
import java.time.YearMonth

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("calendar", "Takvim", Icons.Default.DateRange),
    Tab("people", "Kişiler", Icons.Default.Person),
    Tab("stats", "İstatistik", Icons.AutoMirrored.Filled.List),
    Tab("settings", "Ayarlar", Icons.Default.Settings),
)

class Nav(private val controller: NavHostController) {
    fun back() = controller.popBackStack()
    fun prepare(ym: YearMonth) = controller.navigate("prepare/$ym")
    fun generate(ym: YearMonth) = controller.navigate("generate/$ym")
    fun person(id: String?) = controller.navigate("person/${id ?: "new"}")
    fun history() = controller.navigate("history")
    fun calendar() = controller.navigate("calendar") {
        popUpTo(controller.graph.findStartDestination().id) { inclusive = false }
        launchSingleTop = true
    }
}

@Composable
fun NobetNavHost() {
    // Uygulama nesnesi o anki etkinlikten alınır (varsayılan fabrika ilk uygulama nesnesini önbelleğe alıyor).
    val vm: AppViewModel = viewModel(factory = viewModelFactory { initializer { AppViewModel(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!) } })
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    val backStack by controller.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showTabs = tabs.any { it.route == route }
    // Tablette (geniş ekranda) sekmeler solda dikey şerit, telefonda altta.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    fun open(tab: Tab) = controller.navigate(tab.route) {
        popUpTo(controller.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showTabs && !wide) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { open(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.padding(padding).fillMaxSize()) {
            if (showTabs && wide) {
                NavigationRail(Modifier.fillMaxHeight()) {
                    Spacer(Modifier.height(12.dp))
                    tabs.forEach { tab ->
                        NavigationRailItem(
                            selected = route == tab.route,
                            onClick = { open(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
            NavHost(controller, startDestination = "calendar", modifier = Modifier.weight(1f).fillMaxHeight()) {
                composable("calendar") { Pane(1280.dp) { CalendarScreen(vm, nav) } }
                composable("people") { Pane { PeopleScreen(vm, nav) } }
                composable("stats") { Pane(960.dp) { StatsScreen(vm, nav) } }
                composable("settings") { Pane { SettingsScreen(vm, nav) } }
                composable("prepare/{ym}") { entry ->
                    Pane { PrepareScreen(vm, nav, YearMonth.parse(entry.arguments?.getString("ym"))) }
                }
                composable("generate/{ym}") { entry ->
                    Pane(960.dp) { GenerateScreen(vm, nav, YearMonth.parse(entry.arguments?.getString("ym"))) }
                }
                composable("person/{id}") { entry ->
                    Pane { PersonEditScreen(vm, nav, entry.arguments?.getString("id")?.takeIf { it != "new" }) }
                }
                composable("history") { Pane { HistoryScreen(vm, nav) } }
            }
        }
    }
}

/** Geniş ekranda içeriği okunur genişlikte ortalar; telefonda tam genişlik. */
@Composable
private fun Pane(maxWidth: Dp = 760.dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}

@Composable
fun rememberData(vm: AppViewModel) = vm.data.collectAsStateWithLifecycle()
