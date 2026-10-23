package com.nobetmaker.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
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
    val vm: AppViewModel = viewModel()
    val controller = rememberNavController()
    val nav = remember(controller) { Nav(controller) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }
    val backStack by controller.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (tabs.any { it.route == route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                controller.navigate(tab.route) {
                                    popUpTo(controller.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(controller, startDestination = "calendar", modifier = Modifier.padding(padding)) {
            composable("calendar") { CalendarScreen(vm, nav) }
            composable("people") { PeopleScreen(vm, nav) }
            composable("stats") { StatsScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("prepare/{ym}") { entry ->
                PrepareScreen(vm, nav, YearMonth.parse(entry.arguments?.getString("ym")))
            }
            composable("generate/{ym}") { entry ->
                GenerateScreen(vm, nav, YearMonth.parse(entry.arguments?.getString("ym")))
            }
            composable("person/{id}") { entry ->
                PersonEditScreen(vm, nav, entry.arguments?.getString("id")?.takeIf { it != "new" })
            }
            composable("history") { HistoryScreen(vm, nav) }
        }
    }
}

@Composable
fun rememberData(vm: AppViewModel) = vm.data.collectAsStateWithLifecycle()
