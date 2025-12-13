package balti.xposed.pixelifygooglephotos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import balti.xposed.pixelifygooglephotos.Constants.FIELD_LATEST_VERSION_CODE
import balti.xposed.pixelifygooglephotos.Constants.PREF_DEVICE_TO_SPOOF
import balti.xposed.pixelifygooglephotos.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.Constants.PREF_OVERRIDE_ROM_FEATURE_LEVELS
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_FEATURES_LIST
import balti.xposed.pixelifygooglephotos.Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS
import balti.xposed.pixelifygooglephotos.Constants.RELEASES_URL
import balti.xposed.pixelifygooglephotos.Constants.RELEASES_URL2
import balti.xposed.pixelifygooglephotos.Constants.SHARED_PREF_FILE_NAME
import balti.xposed.pixelifygooglephotos.Constants.TELEGRAM_GROUP
import balti.xposed.pixelifygooglephotos.Constants.UPDATE_INFO_URL
import balti.xposed.pixelifygooglephotos.Constants.UPDATE_INFO_URL2
import balti.xposed.pixelifygooglephotos.spoof.DeviceProps
import balti.xposed.pixelifygooglephotos.ui.*
import balti.xposed.pixelifygooglephotos.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class MainActivity : ComponentActivity() {

    private val utils by lazy { Utils() }

    private val pref: SharedPreferences? by lazy {
        try {
            @Suppress("DEPRECATION")
            getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: Exception) { null }
    }

    // アプリバージョン取得ロジック（変更なし）
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
        val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val context = this
        setContent {
            val colorScheme = when {
                dynamicSupported -> dynamicLightColorScheme(context)
                else -> lightColorScheme()
            }
            MaterialTheme(colorScheme = colorScheme) {
                MainAppStructure()
            }
        }
    }

    @Composable
    fun MainAppStructure() {
        val navController = rememberNavController()
        val items = listOf(Screen.Home, Screen.Settings)

        var showRebootSnack by remember { mutableStateOf(false) }
        var updateUrl by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            val url = withContext(Dispatchers.IO) { checkUpdateAvailable() }
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
                composable(Screen.Home.route) { HomeScreen(updateUrl) }
                composable(Screen.Settings.route) {
                    SettingScreen(onSettingChanged = { showRebootSnack = true })
                }
            }
        }
    }

    // --- Screens ---

    @Composable
    fun HomeScreen(updateUrl: String?) {
        val scrollState = rememberScrollState()
        val isModuleActive = pref != null

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                StatusCard(isActive = isModuleActive)

                if (updateUrl != null) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openWebLink(updateUrl) }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Update, contentDescription = null)
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("New version available! Tap to download.", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                InfoCard(
                    items = listOf(
                        "Android Version" to Build.VERSION.RELEASE,
                        "Device Model" to Build.MODEL,
                        "App Version" to appVersion
                    )
                )

                Text(
                    "Info & Links",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp)
                )

                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column {
                        LinkItem(
                            title = "Support Us",
                            icon = Icons.Default.Favorite,
                            onClick = { openWebLink(TELEGRAM_GROUP) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Color.LightGray.copy(alpha = 0.3f)
                        )
                        LinkItem(
                            title = "Learn KernelSU",
                            icon = Icons.Default.School,
                            onClick = { openWebLink("https://kernelsu.org/") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    @Composable
    fun SettingScreen(onSettingChanged: () -> Unit) {
        val scrollState = rememberScrollState()
        val context = LocalContext.current
        val isModuleActive = pref != null

        // 設定保存用ヘルパー関数
        fun notifyChangedIfActive() {
            if (isModuleActive) onSettingChanged()
        }

        fun saveBoolean(key: String, value: Boolean) {
            pref?.edit()?.putBoolean(key, value)?.apply()
            notifyChangedIfActive()
        }

        // 状態管理
        var deviceToSpoof by remember {
            mutableStateOf(
                pref?.getString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
                    ?: DeviceProps.defaultDeviceName
            )
        }
        var spoofOnlyPhotos by remember {
            mutableStateOf(pref?.getBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true) ?: true)
        }
        var verboseLogs by remember {
            mutableStateOf(pref?.getBoolean(PREF_ENABLE_VERBOSE_LOGS, false) ?: false)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // --- Target Device ---
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "Target Device",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Features and Android version will be automatically configured based on the selected device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    DeviceSelector(
                        currentDevice = deviceToSpoof,
                        onDeviceSelected = { newDevice ->
                            deviceToSpoof = newDevice

                            // ★ここを簡略化しました★
                            pref?.edit()?.apply {
                                // 1. デバイス名を保存
                                putString(PREF_DEVICE_TO_SPOOF, newDevice)

                                // 2. 機能フラグ（Features）を自動設定
                                putStringSet(
                                    PREF_SPOOF_FEATURES_LIST,
                                    DeviceProps.getFeaturesUpToFromDeviceName(newDevice)
                                )

                                // 3. Androidバージョンは「デバイスに従う」をONにする
                                // DeviceSpoofer側で自動的にマップされます
                                putBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, true)

                                // 4. ROM機能の上書きを強制ON
                                putBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, true)
                            }?.apply()

                            notifyChangedIfActive()
                        }
                    )
                }
            }

            HorizontalDivider()

            // --- Toggle Switches ---
            Column {
                SettingSwitchItem(
                    stringResource(R.string.spoof_only_in_google_photos),
                    spoofOnlyPhotos
                ) {
                    spoofOnlyPhotos = it
                    saveBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, it)
                }
                HorizontalDivider()

                SettingSwitchItem(
                    stringResource(R.string.enable_verbose_logs),
                    verboseLogs
                ) {
                    verboseLogs = it
                    saveBoolean(PREF_ENABLE_VERBOSE_LOGS, it)
                }
            }

            // --- Actions ---
            Text("Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 8.dp))

            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column {
                    val rowPadding = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                utils.forceStopPackage(Constants.PACKAGE_NAME_GOOGLE_PHOTOS, context)
                                notifyChangedIfActive()
                            }
                            .then(rowPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.force_stop_google_photos), color = MaterialTheme.colorScheme.error)
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.Gray)
                    }

                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = Color.LightGray.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { utils.openApplication(Constants.PACKAGE_NAME_GOOGLE_PHOTOS, context) }
                            .then(rowPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Open Google Photos", color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.Gray)
                    }
                }
            }

            // --- Reset ---
            OutlinedButton(
                onClick = {
                    pref?.edit()?.clear()?.apply()
                    val activity = context as? Activity
                    activity?.finish()
                    activity?.intent?.let { activity.startActivity(it) }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.reset_settings))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    sealed class Screen(val route: String, val resourceId: String, val icon: ImageVector) {
        object Home : Screen("home", "Home", Icons.Default.Home)
        object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    }

    private fun checkUpdateAvailable(): String? {
        fun getUpdateStatus(url: String): Boolean = try {
            val jsonString = URL(url).readText()
            if (jsonString.isNotBlank()) {
                val json = JSONObject(jsonString)
                val remoteVersion = json.getInt(FIELD_LATEST_VERSION_CODE)
                appVersionCode < remoteVersion
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
}