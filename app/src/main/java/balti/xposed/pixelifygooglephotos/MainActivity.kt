package balti.xposed.pixelifygooglephotos

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import balti.xposed.pixelifygooglephotos.Constants.FIELD_LATEST_VERSION_CODE
import balti.xposed.pixelifygooglephotos.Constants.RELEASES_URL
import balti.xposed.pixelifygooglephotos.Constants.RELEASES_URL2
import balti.xposed.pixelifygooglephotos.Constants.UPDATE_INFO_URL
import balti.xposed.pixelifygooglephotos.Constants.UPDATE_INFO_URL2
import balti.xposed.pixelifygooglephotos.ui.*
import balti.xposed.pixelifygooglephotos.ui.screens.HomeScreen
import balti.xposed.pixelifygooglephotos.ui.screens.SettingScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class MainActivity : ComponentActivity() {

    // バージョン情報取得は頻繁に行うものではないので、ActivityのプロパティまたはUtilsへ
    private val appVersion: String
        get() = try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
            pInfo.versionName
        } catch (_: Exception) { "Unknown" }

    private val appVersionCode: Long
        get() = try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else pInfo.versionCode.toLong()
        } catch (_: Exception) { -1L }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val darkTheme = isSystemInDarkTheme()
            val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

            val colorScheme = when {
                dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
                dynamicColor && !darkTheme -> dynamicLightColorScheme(context)
                darkTheme -> darkColorScheme()
                else -> lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppStructure(appVersion, appVersionCode)
                }
            }
        }
    }

    @Composable
    fun MainAppStructure(appVersion: String, appVersionCode: Long) {
        val navController = rememberNavController()
        val items = listOf(Screen.Home, Screen.Settings)

        var showRebootSnack by remember { mutableStateOf(false) }
        var updateUrl by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            val url = withContext(Dispatchers.IO) { checkUpdateAvailable(appVersionCode) }
            updateUrl = url
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    items.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = null) },
                            label = { Text(screen.resourceId) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            },
            snackbarHost = {
                if (showRebootSnack) {
                    Snackbar(
                        modifier = Modifier.padding(16.dp),
                        action = { TextButton(onClick = { showRebootSnack = false }) { Text("OK") } }
                    ) { Text(stringResource(R.string.please_force_stop_google_photos)) }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        updateUrl = updateUrl,
                        appVersion = appVersion,
                        onOpenLink = { url -> openWebLink(url) }
                    )
                }
                composable(Screen.Settings.route) {
                    SettingScreen(onSettingChanged = { showRebootSnack = true })
                }
            }
        }
    }

    private fun checkUpdateAvailable(currentVersionCode: Long): String? {
        fun getUpdateStatus(url: String): Boolean = try {
            val jsonString = URL(url).readText()
            if (jsonString.isNotBlank()) {
                val json = JSONObject(jsonString)
                val remoteVersion = json.getInt(FIELD_LATEST_VERSION_CODE)
                currentVersionCode < remoteVersion
            } else false
        } catch (_: Exception) {
            false
        }

        return when {
            getUpdateStatus(UPDATE_INFO_URL) -> RELEASES_URL
            getUpdateStatus(UPDATE_INFO_URL2) -> RELEASES_URL2
            else -> null
        }
    }

    private fun openWebLink(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) })
    }

    sealed class Screen(val route: String, val resourceId: String, val icon: ImageVector) {
        object Home : Screen("home", "Home", Icons.Default.Home)
        object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    }
}