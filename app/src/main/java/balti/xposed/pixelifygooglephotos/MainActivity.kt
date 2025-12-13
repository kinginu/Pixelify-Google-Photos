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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.OpenInNew
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
import androidx.core.content.FileProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import balti.xposed.pixelifygooglephotos.Constants.CONF_EXPORT_NAME
import balti.xposed.pixelifygooglephotos.Constants.FIELD_LATEST_VERSION_CODE
import balti.xposed.pixelifygooglephotos.Constants.PREF_DEVICE_TO_SPOOF
import balti.xposed.pixelifygooglephotos.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.Constants.PREF_OVERRIDE_ROM_FEATURE_LEVELS
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_MANUAL
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_FEATURES_LIST
import balti.xposed.pixelifygooglephotos.Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS
import balti.xposed.pixelifygooglephotos.Constants.RELEASES_URL
import balti.xposed.pixelifygooglephotos.Constants.RELEASES_URL2
import balti.xposed.pixelifygooglephotos.Constants.SHARED_PREF_FILE_NAME
import balti.xposed.pixelifygooglephotos.Constants.TELEGRAM_GROUP
import balti.xposed.pixelifygooglephotos.Constants.UPDATE_INFO_URL
import balti.xposed.pixelifygooglephotos.Constants.UPDATE_INFO_URL2
import balti.xposed.pixelifygooglephotos.spoof.DeviceProps
import balti.xposed.pixelifygooglephotos.ui.* // ComposablesとActivityをインポート
import balti.xposed.pixelifygooglephotos.utils.Utils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
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
        get() {
            return try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    packageManager.getPackageInfo(packageName, 0)
                }
                pInfo.versionName
            } catch (e: Exception) {
                "Unknown"
            }
        }

    private val appVersionCode: Long
        get() {
            return try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    packageManager.getPackageInfo(packageName, 0)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode else pInfo.versionCode.toLong()
            } catch (e: Exception) { -1L }
        }


    // Launchers
    private val childActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) showToast(getString(R.string.please_force_stop_google_photos))
    }
    private val configCreateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) it.data?.data?.let { uri -> utils.writeConfigFile(this, uri, pref); showToast(getString(R.string.export_complete)) }
    }
    private val configOpenLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == Activity.RESULT_OK) it.data?.data?.let { uri -> utils.readConfigFile(this, uri, pref); showToast(getString(R.string.import_complete)); finish(); startActivity(intent) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val context = this
        setContent {
            val colorScheme = when {
                dynamicSupported -> dynamicLightColorScheme(context)
                else -> lightColorScheme()
            }
            MaterialTheme(
                colorScheme = colorScheme
            ) {
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
                composable(Screen.Home.route) {
                    HomeScreen(updateUrl)
                }
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
        val context = LocalContext.current
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

                // UI Components from ui/Composables.kt
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

                Text("Info & Links", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 16.dp))

                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column {
                        LinkItem(
                            title = "Look forward to...",
                            icon = Icons.Default.Upcoming,
                            onClick = { Toast.makeText(context, "Coming soon!", Toast.LENGTH_SHORT).show() }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.LightGray.copy(alpha = 0.3f))

                        LinkItem(
                            title = "Support Us",
                            icon = Icons.Default.Favorite,
                            onClick = { openWebLink(TELEGRAM_GROUP) }
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.LightGray.copy(alpha = 0.3f))

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

        fun saveString(key: String, value: String) {
            pref?.edit()?.putString(key, value)?.apply()
            if(isModuleActive) onSettingChanged()
        }
        fun saveBoolean(key: String, value: Boolean) {
            pref?.edit()?.putBoolean(key, value)?.apply()
            if(isModuleActive) onSettingChanged()
        }

        var deviceToSpoof by remember { mutableStateOf(pref?.getString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName) ?: DeviceProps.defaultDeviceName) }
        var overrideRomFeatures by remember { mutableStateOf(pref?.getBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, true) ?: true) }
        var spoofOnlyPhotos by remember { mutableStateOf(pref?.getBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true) ?: true) }
        var verboseLogs by remember { mutableStateOf(pref?.getBoolean(PREF_ENABLE_VERBOSE_LOGS, false) ?: false) }
        var followDevice by remember { mutableStateOf(pref?.getBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, false) ?: false) }
        var manualVersion by remember { mutableStateOf(pref?.getString(PREF_SPOOF_ANDROID_VERSION_MANUAL, "") ?: "") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Target Device", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    DeviceSelector(currentDevice = deviceToSpoof, onDeviceSelected = { newDevice ->
                        deviceToSpoof = newDevice
                        saveString(PREF_DEVICE_TO_SPOOF, newDevice)
                        pref?.edit()?.putStringSet(PREF_SPOOF_FEATURES_LIST, DeviceProps.getFeaturesUpToFromDeviceName(newDevice))?.apply()
                    })
                }

                HorizontalDivider()

                Column {
                    SettingSwitchItem(
                        stringResource(R.string.override_rom_feature_levels),
                        overrideRomFeatures
                    ) {
                        overrideRomFeatures = it; saveBoolean(
                        PREF_OVERRIDE_ROM_FEATURE_LEVELS,
                        it
                    )
                    }
                    HorizontalDivider()
                    SettingSwitchItem(stringResource(R.string.spoof_only_in_google_photos), spoofOnlyPhotos) { spoofOnlyPhotos = it; saveBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, it) }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                childActivityLauncher.launch(
                                    Intent(
                                        context,
                                        FeatureCustomize::class.java
                                    )
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.customize_feature_flags), style = MaterialTheme.typography.bodyLarge)
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            Text("Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 8.dp))

            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column {
                    val rowPadding = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                utils.forceStopPackage(
                                    Constants.PACKAGE_NAME_GOOGLE_PHOTOS,
                                    context
                                )
                            }
                            .then(rowPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.force_stop_google_photos), color = MaterialTheme.colorScheme.error)
                        }
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.Gray)
                    }
                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = Color.LightGray.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                utils.openApplication(
                                    Constants.PACKAGE_NAME_GOOGLE_PHOTOS,
                                    context
                                )
                            }
                            .then(rowPadding),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Open Google Photos", color = MaterialTheme.colorScheme.onSurface)
                        }
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = Color.Gray)
                    }
                }
            }

            Text("Advanced", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 8.dp))

            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column {
                    SettingSwitchItem(stringResource(R.string.enable_verbose_logs), verboseLogs) { verboseLogs = it; saveBoolean(PREF_ENABLE_VERBOSE_LOGS, it) }
                    HorizontalDivider()
                    SettingSwitchItem(stringResource(R.string.spoof_android_version_follow_device), followDevice) { followDevice = it; saveBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, it) }
                }
            }

            if (!followDevice) {
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Manual Android Version", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = manualVersion,
                            onValueChange = { manualVersion = it },
                            label = { Text("e.g. 8.1.0, 11, 12") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (manualVersion.isNotBlank() && DeviceProps.getAndroidVersionFromLabel(manualVersion) == null) {
                                    Toast.makeText(context, "Invalid version", Toast.LENGTH_SHORT).show()
                                } else {
                                    pref?.edit()?.putString(PREF_SPOOF_ANDROID_VERSION_MANUAL, if (manualVersion.isBlank()) null else manualVersion)?.apply()
                                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                    if(isModuleActive) onSettingChanged()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.apply_changes))
                        }
                    }
                }
            }

            Text("Configuration File", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = {
                    android.app.AlertDialog.Builder(context)
                        .setTitle(R.string.export_config)
                        .setMessage(R.string.export_config_desc)
                        .setPositiveButton(R.string.share) { _, _ -> shareConfFile() }
                        .setNegativeButton(R.string.save) { _, _ -> saveConfFile() }
                        .setNeutralButton(android.R.string.cancel, null)
                        .show()
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export")
                }

                Button(onClick = {
                    android.app.AlertDialog.Builder(context)
                        .setTitle(R.string.import_config)
                        .setMessage(R.string.import_config_desc)
                        .setPositiveButton(android.R.string.ok) { _, _ -> importConfFile() }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import")
                }
            }

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
        fun getUpdateStatus(url: String): Boolean {
            return try {
                val jsonString = URL(url).readText()
                if (jsonString.isNotBlank()) {
                    val json = JSONObject(jsonString)
                    val remoteVersion = json.getInt(FIELD_LATEST_VERSION_CODE)
                    appVersionCode < remoteVersion
                } else false
            } catch (_: Exception) { false }
        }
        return when {
            getUpdateStatus(UPDATE_INFO_URL) -> RELEASES_URL
            getUpdateStatus(UPDATE_INFO_URL2) -> RELEASES_URL2
            else -> null
        }
    }

    private fun showToast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    private fun openWebLink(url: String) { startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }) }
    private fun shareConfFile() {
        try {
            val confFile = File(cacheDir, CONF_EXPORT_NAME); val uriFromFile = Uri.fromFile(confFile); confFile.delete()
            utils.writeConfigFile(this, uriFromFile, pref); val confFileShareUri = FileProvider.getUriForFile(this, packageName, confFile)
            Intent().run { action = Intent.ACTION_SEND; type = "*/*"; flags = Intent.FLAG_GRANT_READ_URI_PERMISSION; putExtra(Intent.EXTRA_STREAM, confFileShareUri); startActivity(Intent.createChooser(this, getString(R.string.share_config_file))) }
        } catch (e: Exception) { e.printStackTrace(); showToast("${getString(R.string.share_error)}: ${e.message}") }
    }
    private fun saveConfFile() { val openIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_TITLE, CONF_EXPORT_NAME) }; showToast(getString(R.string.select_a_location)); configCreateLauncher.launch(openIntent) }
    private fun importConfFile() { val openIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }; configOpenLauncher.launch(openIntent) }
}