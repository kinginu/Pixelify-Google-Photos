@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class
)

package balti.xposed.pixelifygooglephotos.ui


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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import balti.xposed.pixelifygooglephotos.R
import androidx.activity.enableEdgeToEdge

import balti.xposed.pixelifygooglephotos.ui.theme.KernelSUTheme
import balti.xposed.pixelifygooglephotos.ui.Constants // [Constantsをインポート]
import balti.xposed.pixelifygooglephotos.ui.DeviceProps // [DevicePropsをインポート]
import balti.xposed.pixelifygooglephotos.ui.Utils // [Utilsをインポート]
import balti.xposed.pixelifygooglephotos.ui.FeatureCustomize // [FeatureCustomizeをインポート]
import balti.xposed.pixelifygooglephotos.ui.AdvancedOptionsActivity // [AdvancedOptionsActivityをインポート]

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_DEVICE_TO_SPOOF
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_OVERRIDE_ROM_FEATURE_LEVELS
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_SPOOF_FEATURES_LIST
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_SPOOF_ANDROID_VERSION_MANUAL
import balti.xposed.pixelifygooglephotos.ui.Constants.FIELD_LATEST_VERSION_CODE
import balti.xposed.pixelifygooglephotos.ui.Constants.RELEASES_URL
import balti.xposed.pixelifygooglephotos.ui.Constants.RELEASES_URL2
import balti.xposed.pixelifygooglephotos.ui.Constants.TELEGRAM_GROUP
import balti.xposed.pixelifygooglephotos.ui.Constants.UPDATE_INFO_URL
import balti.xposed.pixelifygooglephotos.ui.Constants.UPDATE_INFO_URL2


class MainActivity : ComponentActivity() {

    private val utils by lazy { Utils() }

    private val pref: SharedPreferences? by lazy {
        try {
            @Suppress("DEPRECATION")
            getSharedPreferences(Constants.SHARED_PREF_FILE_NAME, MODE_WORLD_READABLE)
        } catch (_: Exception) { null }
    }

    // PixelifyのSharedPreference参照を修正 (Context.MODE_WORLD_READABLEを直接使用)
    private val sharedPrefs: SharedPreferences? by lazy { pref }


    private val appVersion: String
        get() {
            return try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    packageManager.getPackageInfo(packageName, 0)
                }
                pInfo.versionName
            } catch (e: Exception) { "Unknown" }
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


    private val childActivityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) showToast(getString(R.string.please_force_stop_google_photos))
    }
    private val configCreateLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) it.data?.data?.let { uri -> utils.writeConfigFile(this, uri, pref); showToast(getString(
            R.string.export_complete)) }
    }
    private val configOpenLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) it.data?.data?.let { uri -> utils.readConfigFile(this, uri, pref); showToast(getString(
            R.string.import_complete)); finish(); startActivity(intent) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // KSU Nextに合わせて Edge-to-Edge をActivityレベルで有効化
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        super.onCreate(savedInstanceState)

        setContent {
            // KSU Nextと同様にSharedPreferencesからamoledModeを読み込み
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val amoledMode = prefs.getBoolean("enable_amoled", false)

            // KernelSU Themeでラップ
            KernelSUTheme(amoledMode = amoledMode) { // <--- KSU Nextのテーマ適用
                MainAppStructure()
            }
        }
    }

    @Composable
    fun MainAppStructure() {
        val navController = rememberNavController()
        // ScreenクラスはMainActivity直下に定義されているため、ここでは参照可能
        val items = listOf(Screen.Home, Screen.Settings)

        var showRebootSnack by remember { mutableStateOf(false) }

        // アップデートチェック (Pixelfyのロジックを維持)
        var updateUrl by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                updateUrl = checkUpdateAvailable()
            }
        }

        Scaffold(
            // KSU Nextの Scafflod と同様に WindowInsets を制御
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar {
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    // L142 エラー対応: 型を明示するか、コンパイラが推論できるよう Screen クラスを内部化
                    items.forEach { screen: Screen ->
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
                // KSU Nextのスタイルに合わせて NavigationBarsのインセット処理を適用 (Edge-to-Edge対応)
                modifier = Modifier
                    .padding(innerPadding)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                composable(Screen.Home.route) {
                    // L175 エラー対応: HomeScreenの引数にupdateUrlを渡す
                    HomeScreen(updateUrl)
                }
                composable(Screen.Settings.route) {
                    // L178 エラー対応: SettingScreenの引数にonSettingChangedを渡す
                    // ただし、SettingScreenはThemeの設定も扱うため、AMOLED ModeのStateを渡す
                    val prefs = getSharedPreferences("settings", MODE_PRIVATE)
                    var amoledMode by remember { mutableStateOf(prefs.getBoolean("enable_amoled", false)) }

                    SettingScreen(
                        isAmoledMode = amoledMode,
                        onAmoledChange = { isEnabled ->
                            amoledMode = isEnabled
                            prefs.edit().putBoolean("enable_amoled", isEnabled).apply()
                        },
                        onSettingChanged = { showRebootSnack = true }
                    )
                }
            }
        }
    }


    // --- Screens ---

    @Composable
    fun HomeScreen(updateUrl: String?) {
        val scrollState = rememberScrollState()
        val context = LocalContext.current

        // モジュールが有効かどうか判定
        val isModuleActive = sharedPrefs != null

        // 設定値の読み込み (nullの場合はデフォルト値を使用)
        var deviceToSpoof by remember { mutableStateOf(sharedPrefs?.getString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName) ?: DeviceProps.defaultDeviceName) }
        var overrideRomFeatures by remember { mutableStateOf(sharedPrefs?.getBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, true) ?: true) }
        var spoofOnlyPhotos by remember { mutableStateOf(sharedPrefs?.getBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true) ?: true) }
        var showRebootSnack by remember { mutableStateOf(false) }

        // 保存用関数 (sharedPrefsがnullでない場合のみ実行)
        fun saveString(key: String, value: String) {
            sharedPrefs?.edit()?.putString(key, value)?.apply()
            if (isModuleActive) showRebootSnack = true
        }
        fun saveBoolean(key: String, value: Boolean) {
            sharedPrefs?.edit()?.putBoolean(key, value)?.apply()
            if (isModuleActive) showRebootSnack = true
        }

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
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

            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                // --- ステータスカード (一番上に表示) ---
                StatusCard(isModuleActive) // 修正: ModuleStatusCard -> StatusCard

                // --- 設定 UI (以下は以前と同じ) ---

                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Target Device", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))

                        // モジュール無効時は操作できないようにする場合は enabled = isModuleActive を追加できます
                        DeviceSelector(
                            currentDevice = deviceToSpoof,
                            onDeviceSelected = { newDevice ->
                                deviceToSpoof = newDevice
                                saveString(PREF_DEVICE_TO_SPOOF, newDevice)
                                sharedPrefs?.edit()?.putStringSet(PREF_SPOOF_FEATURES_LIST, DeviceProps.getFeaturesUpToFromDeviceName(newDevice))?.apply()
                            }
                        )
                    }
                }

                Card(elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column {
                        SettingSwitchItem(
                            title = stringResource(R.string.override_rom_feature_levels),
                            checked = overrideRomFeatures,
                            onCheckedChange = { overrideRomFeatures = it; saveBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, it) }
                        )
                        Divider()
                        SettingSwitchItem(
                            title = stringResource(R.string.spoof_only_in_google_photos),
                            checked = spoofOnlyPhotos,
                            onCheckedChange = { spoofOnlyPhotos = it; saveBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, it) }
                        )
                        Divider()
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                // モジュール無効でも画面遷移は許可するが、保存はできない旨を考慮
                                childActivityLauncher.launch(Intent(context, FeatureCustomize::class.java))
                            }.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = stringResource(R.string.customize_feature_flags))
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }

                Text("Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { utils.forceStopPackage(Constants.PACKAGE_NAME_GOOGLE_PHOTOS, context) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.filledTonalButtonColors()) {
                        Text(stringResource(R.string.force_stop_google_photos))
                    }
                    FilledTonalIconButton(onClick = { utils.openApplication(Constants.PACKAGE_NAME_GOOGLE_PHOTOS, context) }) {
                        Icon(painter = painterResource(android.R.drawable.ic_menu_gallery), contentDescription = "Open")
                    }
                }

                OutlinedButton(
                    onClick = {
                        sharedPrefs?.edit()?.apply {
                            putString(PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
                            putBoolean(PREF_OVERRIDE_ROM_FEATURE_LEVELS, true)
                            putBoolean(PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true)
                            putStringSet(PREF_SPOOF_FEATURES_LIST, DeviceProps.defaultFeatures.map { it.displayName }.toSet())
                            putBoolean(PREF_ENABLE_VERBOSE_LOGS, false)
                            putBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, false)
                            putString(PREF_SPOOF_ANDROID_VERSION_MANUAL, null)
                            apply()
                        }
                        finish()
                        startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.reset_settings))
                }

                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    TextButton(onClick = { childActivityLauncher.launch(Intent(context, AdvancedOptionsActivity::class.java)) }) {
                        Text(stringResource(R.string.advanced_options))
                    }
                    TextButton(onClick = { openWebLink(TELEGRAM_GROUP) }) {
                        Text(stringResource(R.string.telegram_group))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp), modifier = Modifier.padding(top = 16.dp)) {
                        IconButton(onClick = {
                            android.app.AlertDialog.Builder(context)
                                .setTitle(R.string.export_config)
                                .setMessage(R.string.export_config_desc)
                                .setPositiveButton(R.string.share) { _, _ -> shareConfFile() }
                                .setNegativeButton(R.string.save) { _, _ -> saveConfFile() }
                                .setNeutralButton(android.R.string.cancel, null)
                                .show()
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "Export")
                        }

                        IconButton(onClick = {
                            android.app.AlertDialog.Builder(context)
                                .setTitle(R.string.import_config)
                                .setMessage(R.string.import_config_desc)
                                .setPositiveButton(android.R.string.ok) { _, _ -> importConfFile() }
                                .setNegativeButton(android.R.string.cancel, null)
                                .show()
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Import")
                        }
                    }
                }
            }
        }
    }

    // SettingScreenの引数リストと中身を修正・統合
    @Composable
    fun SettingScreen(
        isAmoledMode: Boolean,
        onAmoledChange: (Boolean) -> Unit,
        onSettingChanged: () -> Unit // 設定変更時にリブートスナックバーを表示するためのコールバック
    ) {
        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                // ステータスバーとナビゲーションバーの分だけ余白を空ける
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            // タイトル
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // 設定項目カード
            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // AMOLEDモード切り替えスイッチ
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onAmoledChange(!isAmoledMode) }, // クリック領域を広くする
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AMOLED Black Mode",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Save battery on OLED screens",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isAmoledMode,
                            onCheckedChange = onAmoledChange // 親から渡されたコールバック
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 例として、元のSettingScreenのロジックの一部を再利用できます (ただし、この例ではテーマ設定のみに絞っています)
        }
    }


    // --- Components ---
    // ここから下の Composable および Helpers は、元のコードからそのままコピーします。

    @Composable
    fun StatusCard(isActive: Boolean) {
        val color = if (isActive) Color(0xFFE6F4EA) else MaterialTheme.colorScheme.errorContainer
        val contentColor = if (isActive) Color(0xFF1E8E3E) else MaterialTheme.colorScheme.onErrorContainer
        val icon = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning

        Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(if (isActive) "Module Active" else "Module Not Active", fontWeight = FontWeight.Bold, color = contentColor)
                    if (!isActive) Text(stringResource(R.string.module_not_enabled), style = MaterialTheme.typography.bodySmall, color = contentColor)
                }
            }
        }
    }

    @Composable
    fun InfoCard(items: List<Pair<String, String>>) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                items.forEachIndexed { index, (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, color = Color.Gray)
                        Text(value, fontWeight = FontWeight.SemiBold)
                    }
                    if (index < items.size - 1) Divider(modifier = Modifier.padding(vertical = 4.dp), color = Color.LightGray.copy(alpha = 0.3f))
                }
            }
        }
    }

    @Composable
    fun LinkItem(title: String, icon: ImageVector, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun DeviceSelector(currentDevice: String, onDeviceSelected: (String) -> Unit) {
        var expanded by remember { mutableStateOf(false) }
        val devices = DeviceProps.allDevices.map { it.deviceName }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                readOnly = true,
                value = currentDevice,
                onValueChange = {},
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                devices.forEach { selectionOption ->
                    DropdownMenuItem(text = { Text(selectionOption) }, onClick = { onDeviceSelected(selectionOption); expanded = false }, contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                }
            }
        }
    }

    @Composable
    fun SettingSwitchItem(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    // --- Helpers ---
    // L123 エラー対策: ScreenクラスをMainActivity内に配置
    sealed class Screen(val route: String, val resourceId: String, val icon: ImageVector) {
        object Home : Screen("home", "Home", Icons.Default.Home)
        object Settings : Screen("settings", "Settings", Icons.Default.Settings)
    }

    // L238 エラー対策: return文を明示し、戻り値の型を確保
    private fun checkUpdateAvailable(): String? {
        fun getUpdateStatus(url: String): Boolean {
            return try {
                // Constantsがない場合はURLを直接使用（例として）
                val jsonString = URL(url).readText()
                if (jsonString.isNotBlank()) {
                    val json = JSONObject(jsonString)
                    // Constantsがない場合は定数を直接指定（例として）
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

    // Config File Helpers (Constantsに依存)
    private fun shareConfFile() {
        try {
            val confFile = File(cacheDir, Constants.CONF_EXPORT_NAME); val uriFromFile = Uri.fromFile(confFile); confFile.delete()
            utils.writeConfigFile(this, uriFromFile, pref); val confFileShareUri = FileProvider.getUriForFile(this, packageName, confFile)
            Intent().run { action = Intent.ACTION_SEND; type = "*/*"; flags = Intent.FLAG_GRANT_READ_URI_PERMISSION; putExtra(Intent.EXTRA_STREAM, confFileShareUri); startActivity(Intent.createChooser(this, getString(
                R.string.share_config_file))) }
        } catch (e: Exception) { e.printStackTrace(); showToast("${getString(R.string.share_error)}: ${e.message}") }
    }
    private fun saveConfFile() { val openIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_TITLE, Constants.CONF_EXPORT_NAME) }; showToast(getString(
        R.string.select_a_location)); configCreateLauncher.launch(openIntent) }
    private fun importConfFile() { val openIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }; configOpenLauncher.launch(openIntent) }
}