package balti.xposed.pixelifygooglephotos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
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
                    MainAppStructure()
                }
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
                verticalArrangement = Arrangement.spacedBy(16.dp) // 間隔を少し広げて見やすく
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
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column {
                        LinkItem(
                            title = "Support Us",
                            icon = Icons.Default.Favorite,
                            onClick = { openWebLink(TELEGRAM_GROUP) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        LinkItem(
                            title = "Learn KernelSU",
                            icon = Icons.Default.School,
                            onClick = { openWebLink("https://kernelsu.org/") }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp)) // BottomBar分の余裕
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun SettingScreen(onSettingChanged: () -> Unit) {
        val scrollState = rememberScrollState()
        val context = LocalContext.current
        val isModuleActive = pref != null

        // BottomSheetの状態
        var showDeviceSheet by remember { mutableStateOf(false) }
        val sheetState = rememberModalBottomSheetState()

        fun notifyChangedIfActive() {
            if (isModuleActive) onSettingChanged()
        }

        fun saveBoolean(key: String, value: Boolean) {
            pref?.edit()?.putBoolean(key, value)?.apply()
            notifyChangedIfActive()
        }

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

        // --- Bottom Sheet Logic ---
        if (showDeviceSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDeviceSheet = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Text(
                    "Select Device",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 48.dp) // NavigationBar回避
                ) {
                    items(DeviceProps.allDevices.map { it.deviceName }) { device ->
                        val isSelected = device == deviceToSpoof
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // 選択処理
                                    deviceToSpoof = device
                                    pref?.edit()?.apply {
                                        putString(PREF_DEVICE_TO_SPOOF, device)
                                        putStringSet(
                                            PREF_SPOOF_FEATURES_LIST,
                                            DeviceProps.getFeaturesUpToFromDeviceName(device)
                                        )
                                        putBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, true)
                                        putBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, true)
                                    }?.apply()
                                    notifyChangedIfActive()
                                    showDeviceSheet = false
                                }
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = device,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // --- Main Settings UI ---
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

            // --- Configuration Card (Device + Switches) ---
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column {
                    // 1. Target Device Row (Clickable)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDeviceSheet = true }
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Target Device",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = deviceToSpoof,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Select",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 2. Switches
                    SettingSwitchItem(
                        stringResource(R.string.spoof_only_in_google_photos),
                        spoofOnlyPhotos
                    ) {
                        spoofOnlyPhotos = it
                        saveBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, it)
                    }

                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingSwitchItem(
                        stringResource(R.string.enable_verbose_logs),
                        verboseLogs
                    ) {
                        verboseLogs = it
                        saveBoolean(PREF_ENABLE_VERBOSE_LOGS, it)
                    }
                }
            }

            // --- Actions ---
            Text("Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 8.dp))

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column {
                    val rowPadding = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)

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
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { utils.openApplication(Constants.PACKAGE_NAME_GOOGLE_PHOTOS, context) }
                            .then(rowPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Open Google Photos", color = MaterialTheme.colorScheme.onSurface)
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

            Spacer(modifier = Modifier.height(32.dp))
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