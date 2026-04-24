package com.pos.app.ui.navigation

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.pos.app.ui.login.LoginScreen
import com.pos.app.ui.menu.MenuManagementScreen
import com.pos.app.ui.order.OrderScreen
import com.pos.app.ui.report.ReportScreen
import com.pos.app.ui.reservation.ReservationScreen
import com.pos.app.ui.settings.SettingsScreen
import com.pos.app.ui.settings.SettingsViewModel
import com.pos.app.ui.table.TableSettingScreen
import com.pos.app.ui.theme.LocalPosColors
import androidx.compose.material3.Text

sealed class Screen(val route: String) {
    object Login       : Screen("login")
    object Home        : Screen("home")
    object Order       : Screen("order")
    object Reservation : Screen("reservation")
    object ItemSetting : Screen("item_setting")
    object TableSetting: Screen("table_setting")
    object Report      : Screen("report")
    object Settings    : Screen("settings")
}

data class BottomTab(val screen: Screen, val label: String, val emoji: String)

val bottomTabs = listOf(
    BottomTab(Screen.Order,        "記帳",    "🛒"),
    BottomTab(Screen.Reservation,  "訂位",    "📅"),
    BottomTab(Screen.ItemSetting,  "菜單管理", "🥩"),
    BottomTab(Screen.TableSetting, "桌號設定", "🪑"),
    BottomTab(Screen.Report,       "報表",    "📊"),
    BottomTab(Screen.Settings,     "設定",    "⚙️"),
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
            HomeWithBottomNav()
        }
    }
}

@Composable
private fun HomeWithBottomNav(
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val settingsUiState by settingsViewModel.uiState.collectAsState()

    val visibleTabs = remember(
        settingsUiState.tabMenuEnabled,
        settingsUiState.tabTableEnabled,
        settingsUiState.tabReportEnabled,
        settingsUiState.tabReservationEnabled
    ) {
        bottomTabs.filter { tab ->
            when (tab.screen) {
                Screen.Reservation  -> settingsUiState.tabReservationEnabled
                Screen.ItemSetting  -> settingsUiState.tabMenuEnabled
                Screen.TableSetting -> settingsUiState.tabTableEnabled
                Screen.Report       -> settingsUiState.tabReportEnabled
                else                -> true  // Order 與 Settings 永遠顯示
            }
        }
    }

    // If current tab is disabled, navigate back to Order
    LaunchedEffect(visibleTabs) {
        val tabRoutes = visibleTabs.map { it.screen.route }
        if (currentRoute != null && currentRoute !in tabRoutes) {
            navController.navigate(Screen.Order.route) {
                popUpTo(Screen.Order.route) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val context = LocalContext.current
    val versionName = remember {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }
        }.getOrNull() ?: "N/A"
    }
    val t = LocalPosColors.current
    val onGoSettings = { navController.navigate(Screen.Settings.route) { launchSingleTop = true } }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 900.dp || maxWidth > maxHeight
        val navHeight = if (compact) 54.dp else 62.dp
        val navLabelSize = if (compact) 10.sp else 11.sp
        val navEmojiSize = if (compact) 18.sp else 20.sp
        val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val navContainerHeight: Dp = navHeight + navBottomInset

        Column(modifier = Modifier.fillMaxSize()) {
            // Content area
            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Order.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Screen.Order.route) {
                        OrderScreen(onGoSettings = onGoSettings, appVersion = versionName)
                    }
                    composable(Screen.Reservation.route) { ReservationScreen() }
                    composable(Screen.ItemSetting.route) { MenuManagementScreen() }
                    composable(Screen.TableSetting.route) { TableSettingScreen() }
                    composable(Screen.Report.route) {
                        ReportScreen(onGoSettings = onGoSettings, appVersion = versionName)
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen()
                    }
                }
            }

            // Custom bottom navigation
            HorizontalDivider(color = t.border, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(navContainerHeight)
                    .padding(bottom = navBottomInset)
                    .background(t.topbar),
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleTabs.forEach { tab ->
                    val selected = currentRoute == tab.screen.route
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable {
                                navController.navigate(tab.screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Top accent indicator
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth(0.6f)
                                    .height(3.dp)
                                    .background(t.accent)
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (compact) 2.dp else 3.dp)
                        ) {
                            Text(tab.emoji, fontSize = navEmojiSize)
                            Text(
                                tab.label,
                                fontSize = navLabelSize,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) t.accent else t.textMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "v$versionName",
            color = t.textMuted,
            fontSize = if (compact) 10.sp else 11.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = navBottomInset + 2.dp)
        )
    }
}
