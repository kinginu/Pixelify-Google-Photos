package balti.xposed.pixelifygooglephotos

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import balti.xposed.pixelifygooglephotos.Constants.CONF_EXPORT_NAME
import balti.xposed.pixelifygooglephotos.Constants.PREF_DEVICE_TO_SPOOF
import balti.xposed.pixelifygooglephotos.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.Constants.PREF_OVERRIDE_ROM_FEATURE_LEVELS
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_ANDROID_VERSION_MANUAL
import balti.xposed.pixelifygooglephotos.Constants.PREF_SPOOF_FEATURES_LIST
import balti.xposed.pixelifygooglephotos.Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS
import balti.xposed.pixelifygooglephotos.Constants.SHARED_PREF_FILE_NAME
import balti.xposed.pixelifygooglephotos.Constants.TELEGRAM_GROUP
import java.io.File

class ActivityMain : ComponentActivity() {

    private val utils by lazy { Utils() }

    // モジュールが無効な場合は null になる可能性があります
    private val pref: SharedPreferences? by lazy {
        try {
            @Suppress("DEPRECATION")
            getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: Exception) {
            null
        }
    }

    private val childActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                showToast(getString(R.string.please_force_stop_google_photos))
            }
        }

    private val configCreateLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    utils.writeConfigFile(this, uri, pref)
                    showToast(getString(R.string.export_complete))
                }
            }
        }

    private val configOpenLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    utils.readConfigFile(this, uri, pref)
                    showToast(getString(R.string.import_complete))
                    finish()
                    startActivity(intent)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF6750A4),
                    onPrimary = Color.White,
                    secondary = Color(0xFF625B71),
                    background = Color(0xFFFFFBFE),
                    surface = Color(0xFFFFFBFE),
                    error = Color(0xFFB3261E),
                    onError = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // prefがnullでもMainScreenを表示する（null対応済み）
                    MainScreen(pref)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MainScreen(sharedPrefs: SharedPreferences?) {
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
                ModuleStatusCard(isModuleActive)

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

    /**
     * モジュールの状態を表示するカード
     */
    @Composable
    fun ModuleStatusCard(isActive: Boolean) {
        val containerColor = if (isActive) Color(0xFFE6F4EA) else MaterialTheme.colorScheme.errorContainer
        val contentColor = if (isActive) Color(0xFF1E8E3E) else MaterialTheme.colorScheme.onErrorContainer
        val icon = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning

        Card(
            colors = CardDefaults.cardColors(containerColor = containerColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = if (isActive) "Module Active" else "Module Not Active",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    if (!isActive) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.module_not_enabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor
                        )
                    }
                }
            }
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
        Row(modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }

    private fun showToast(message: String) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    private fun openWebLink(url: String) { startActivity(Intent(Intent.ACTION_VIEW).apply { data = Uri.parse(url) }) }

    private fun shareConfFile() {
        try {
            val confFile = File(cacheDir, CONF_EXPORT_NAME)
            val uriFromFile = Uri.fromFile(confFile)
            confFile.delete()
            utils.writeConfigFile(this, uriFromFile, pref)
            val confFileShareUri = FileProvider.getUriForFile(this, packageName, confFile)
            Intent().run {
                action = Intent.ACTION_SEND; type = "*/*"; flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_STREAM, confFileShareUri)
                startActivity(Intent.createChooser(this, getString(R.string.share_config_file)))
            }
        } catch (e: Exception) { e.printStackTrace(); showToast("${getString(R.string.share_error)}: ${e.message}") }
    }
    private fun saveConfFile() { val openIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_TITLE, CONF_EXPORT_NAME) }; showToast(getString(R.string.select_a_location)); configCreateLauncher.launch(openIntent) }
    private fun importConfFile() { val openIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }; configOpenLauncher.launch(openIntent) }
}