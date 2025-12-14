package balti.xposed.pixelifygooglephotos.ui.screens

import android.app.Activity
import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import balti.xposed.pixelifygooglephotos.Constants
import balti.xposed.pixelifygooglephotos.R
import balti.xposed.pixelifygooglephotos.spoof.DeviceProps
import balti.xposed.pixelifygooglephotos.ui.SettingSwitchItem
import balti.xposed.pixelifygooglephotos.utils.Utils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingScreen(onSettingChanged: () -> Unit) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val utils = remember { Utils() }

    val pref = remember {
        try {
            @Suppress("DEPRECATION")
            context.getSharedPreferences(Constants.SHARED_PREF_FILE_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: Exception) { null }
    }

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

    var moduleEnabled by remember {
        mutableStateOf(pref?.getBoolean(Constants.PREF_MODULE_ENABLED, true) ?: true)
    }

    var deviceToSpoof by remember {
        mutableStateOf(
            pref?.getString(Constants.PREF_DEVICE_TO_SPOOF, DeviceProps.defaultDeviceName)
                ?: DeviceProps.defaultDeviceName
        )
    }
    var spoofOnlyPhotos by remember {
        mutableStateOf(pref?.getBoolean(Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, true) ?: true)
    }
    var verboseLogs by remember {
        mutableStateOf(pref?.getBoolean(Constants.PREF_ENABLE_VERBOSE_LOGS, false) ?: false)
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
                contentPadding = PaddingValues(bottom = 48.dp)
            ) {
                items(DeviceProps.allDevices.map { it.deviceName }) { device ->
                    val isSelected = device == deviceToSpoof
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                deviceToSpoof = device
                                pref?.edit()?.apply {
                                    putString(Constants.PREF_DEVICE_TO_SPOOF, device)
                                    putStringSet(
                                        Constants.PREF_SPOOF_FEATURES_LIST,
                                        DeviceProps.getFeaturesUpToFromDeviceName(device)
                                    )
                                    putBoolean(Constants.PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, true)
                                    putBoolean(Constants.PREF_OVERRIDE_ROM_FEATURE_LEVELS, true)
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

        // Master Switch
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (moduleEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val newState = !moduleEnabled
                        moduleEnabled = newState
                        saveBoolean(Constants.PREF_MODULE_ENABLED, newState)
                    }
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Enable Pixelify",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (moduleEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (moduleEnabled) "Module is active" else "Module is disabled",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (moduleEnabled) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = moduleEnabled,
                    onCheckedChange = { newState ->
                        moduleEnabled = newState
                        saveBoolean(Constants.PREF_MODULE_ENABLED, newState)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedBorderColor = Color.Transparent
                    )
                )
            }
        }

        // --- Configuration Card ---
        Column(
            modifier = Modifier
                .alpha(if (moduleEnabled) 1f else 0.5f)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = moduleEnabled) { showDeviceSheet = true }
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

                    SettingSwitchItem(
                        stringResource(R.string.spoof_only_in_google_photos),
                        spoofOnlyPhotos
                    ) {
                        if (moduleEnabled) {
                            spoofOnlyPhotos = it
                            saveBoolean(Constants.PREF_STRICTLY_CHECK_GOOGLE_PHOTOS, it)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingSwitchItem(
                        stringResource(R.string.enable_verbose_logs),
                        verboseLogs
                    ) {
                        if (moduleEnabled) {
                            verboseLogs = it
                            saveBoolean(Constants.PREF_ENABLE_VERBOSE_LOGS, it)
                        }
                    }
                }
            }
        }

        // --- Actions ---
        Text("Actions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 8.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
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