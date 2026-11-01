package com.aleb.aletracr.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aleb.aletracr.data.Meal
import com.aleb.aletracr.data.Repository
import com.aleb.aletracr.ui.add.AddFoodScreen
import com.aleb.aletracr.ui.add.QUICK_ADD_TAB
import java.time.LocalDate
import com.aleb.aletracr.ui.diary.DiaryScreen
import com.aleb.aletracr.ui.settings.SettingsScreen
import com.aleb.aletracr.ui.stats.StatsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("diary", "Tagebuch", Icons.Filled.Book),
    Tab("stats", "Fortschritt", Icons.Filled.Insights),
    Tab("settings", "Einstellungen", Icons.Filled.Settings),
)

@Composable
fun AppNav(repo: Repository, quickAddRequest: Int = 0, onEnableNotification: () -> Unit = {}) {
    val nav = rememberNavController()

    // "Mehr…" in the notification opens the quick-add tab for the current meal.
    LaunchedEffect(quickAddRequest) {
        if (quickAddRequest > 0) {
            nav.navigate("add/${LocalDate.now()}/${Meal.forTime().name}?tab=$QUICK_ADD_TAB") { launchSingleTop = true }
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
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
        NavHost(nav, startDestination = "diary", modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable("diary") {
                DiaryScreen(repo, onAddFood = { date, meal -> nav.navigate("add/$date/${meal.name}") })
            }
            composable("stats") { StatsScreen(repo) }
            composable("settings") { SettingsScreen(repo, onEnableNotification) }
            composable(
                "add/{date}/{meal}?tab={tab}",
                arguments = listOf(
                    navArgument("date") { type = NavType.StringType },
                    navArgument("meal") { type = NavType.StringType },
                    navArgument("tab") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) { entry ->
                val date = entry.arguments?.getString("date").orEmpty()
                val meal = Meal.valueOf(entry.arguments?.getString("meal") ?: Meal.SNACK.name)
                val tab = entry.arguments?.getInt("tab") ?: 0
                AddFoodScreen(repo, date, meal, initialTab = tab, onDone = { nav.popBackStack() })
            }
        }
    }
}
