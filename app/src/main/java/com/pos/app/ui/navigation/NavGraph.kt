package com.pos.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pos.app.ui.login.LoginScreen
import com.pos.app.ui.menu.MenuManagementScreen
import com.pos.app.ui.order.OrderScreen
import com.pos.app.ui.report.ReportScreen
import com.pos.app.ui.settings.SettingsScreen
import com.pos.app.ui.table.TableSettingScreen
import com.pos.app.ui.theme.Red700

sealed class Screen(val route: String) {
    object Login : Screen("login")
    object Home : Screen("home")
    object Order : Screen("order")
    object ItemSetting : Screen("item_setting")
    object TableSetting : Screen("table_setting")
    object Report : Screen("report")
    object Settings : Screen("settings")
}

data class BottomTab(val screen: Screen, val label: String, val icon: ImageVector)

val bottomTabs = listOf(
    BottomTab(Screen.Order, "記帳", Icons.Default.ShoppingCart),
    BottomTab(Screen.ItemSetting, "品項設定", Icons.Default.MenuBook),
    BottomTab(Screen.TableSetting, "桌號設定", Icons.Default.TableRestaurant),
    BottomTab(Screen.Report, "報表", Icons.Default.Assessment)
)

@Composable
fun NavGraph() {
    val rootNav = rememberNavController()

    NavHost(navController = rootNav, startDestination = Screen.Login.route) {
        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    rootNav.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Home.route) {
            HomeWithBottomNav(onGoSettings = { rootNav.navigate(Screen.Settings.route) })
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { rootNav.popBackStack() })
        }
    }
}

@Composable
private fun HomeWithBottomNav(onGoSettings: () -> Unit) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Red700) {
                bottomTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.screen.route,
                        onClick = {
                            navController.navigate(tab.screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Red700,
                            selectedTextColor = Color.White,
                            indicatorColor = Color.White,
                            unselectedIconColor = Color.White.copy(alpha = 0.7f),
                            unselectedTextColor = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Order.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(Screen.Order.route) { OrderScreen(onGoSettings = onGoSettings) }
            composable(Screen.ItemSetting.route) { MenuManagementScreen() }
            composable(Screen.TableSetting.route) { TableSettingScreen() }
            composable(Screen.Report.route) { ReportScreen(onGoSettings = onGoSettings) }
        }
    }
}
