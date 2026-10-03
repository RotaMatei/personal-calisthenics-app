package com.personal.calisthenicsguide.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personal.calisthenicsguide.CalisthenicsApp
import com.personal.calisthenicsguide.ui.dashboard.DashboardScreen
import com.personal.calisthenicsguide.ui.guide.GuideScreen
import com.personal.calisthenicsguide.ui.dashboard.DashboardViewModel
import com.personal.calisthenicsguide.ui.theme.AppColors
import com.personal.calisthenicsguide.ui.workout.WorkoutScreen
import com.personal.calisthenicsguide.ui.theme.CalisthenicsTheme

enum class Tab(val title: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    WORKOUT("Workout", Icons.Filled.FitnessCenter),
    GUIDE("Guide", Icons.Filled.MenuBook),
    STATS("Stats", Icons.Filled.BarChart),
}

@Composable
fun AppRoot() {
    CalisthenicsTheme {
        val context = LocalContext.current
        val app = context.applicationContext as CalisthenicsApp
        val runnerState by app.sessionRunner.state.collectAsState()
        var tabIndex by rememberSaveable { mutableIntStateOf(0) }
        val dashboard: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(app.repository))
        val dashboardState by dashboard.state.collectAsState()

        fun startNow() {
            dashboardState?.let { app.sessionRunner.start(it.options) }
            tabIndex = Tab.WORKOUT.ordinal
        }

        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            // The workout works either way; without the permission only the lock-screen notification is hidden.
            startNow()
        }

        fun beginWorkout() {
            val needsPermission = Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            if (needsPermission) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else startNow()
        }

        Scaffold(
            containerColor = AppColors.Background,
            bottomBar = {
                NavigationBar(containerColor = AppColors.Surface) {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = tabIndex == tab.ordinal,
                            onClick = { tabIndex = tab.ordinal },
                            icon = { Icon(tab.icon, contentDescription = tab.title) },
                            label = { Text(tab.title) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AppColors.AccentOn,
                                selectedTextColor = AppColors.Accent,
                                indicatorColor = AppColors.Accent,
                                unselectedIconColor = AppColors.TextSecondary,
                                unselectedTextColor = AppColors.TextSecondary,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Surface(modifier = Modifier.fillMaxSize().padding(padding), color = AppColors.Background) {
                when (Tab.entries[tabIndex]) {
                    Tab.HOME -> DashboardScreen(
                        state = dashboardState,
                        sessionActive = runnerState.active,
                        onStartWorkout = ::beginWorkout,
                        onResumeWorkout = { tabIndex = Tab.WORKOUT.ordinal },
                        onChecklist = dashboard::setChecklist,
                        onColdMode = dashboard::setColdMode,
                        onPickDay = dashboard::overrideDay,
                    )
                    Tab.WORKOUT -> {
                        val progression by app.repository.progression.collectAsState(initial = emptyMap())
                        WorkoutScreen(
                            runnerState = runnerState,
                            runner = app.sessionRunner,
                            progression = progression,
                            coldModeSetting = dashboardState?.coldMode ?: false,
                            onColdMode = dashboard::setColdMode,
                            onStart = ::beginWorkout,
                        )
                    }
                    Tab.GUIDE -> GuideScreen(repository = app.repository)
                    Tab.STATS -> Placeholder("Stats", "Heatmap, safety guard and charts arrive with milestone M12.")
                }
            }
        }
    }
}

@Composable
private fun Placeholder(title: String, text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("$title\n\n$text", modifier = Modifier.padding(24.dp))
    }
}
