package balti.xposed.pixelifygooglephotos.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_ENABLE_VERBOSE_LOGS
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE
import balti.xposed.pixelifygooglephotos.ui.Constants.PREF_SPOOF_ANDROID_VERSION_MANUAL
import balti.xposed.pixelifygooglephotos.ui.Constants.SHARED_PREF_FILE_NAME
import com.google.android.material.snackbar.Snackbar
import balti.xposed.pixelifygooglephotos.R

class AdvancedOptionsActivity : ComponentActivity() {

    private val pref by lazy {
        @Suppress("DEPRECATION")
        getSharedPreferences(SHARED_PREF_FILE_NAME, Context.MODE_WORLD_READABLE)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF6750A4), onPrimary = Color.White,
                    secondary = Color(0xFF625B71), background = Color(0xFFFFFBFE), surface = Color(0xFFFFFBFE)
                )
            ) {
                var verboseLogs by remember { mutableStateOf(pref.getBoolean(PREF_ENABLE_VERBOSE_LOGS, false)) }
                var followDevice by remember { mutableStateOf(pref.getBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, false)) }
                var manualVersion by remember { mutableStateOf(pref.getString(PREF_SPOOF_ANDROID_VERSION_MANUAL, "") ?: "") }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.advanced_options)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Switch 1: Verbose Logs
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { verboseLogs = !verboseLogs; pref.edit().putBoolean(PREF_ENABLE_VERBOSE_LOGS, verboseLogs).apply() },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.enable_verbose_logs))
                            Switch(checked = verboseLogs, onCheckedChange = { verboseLogs = it; pref.edit().putBoolean(PREF_ENABLE_VERBOSE_LOGS, it).apply() })
                        }
                        Divider()

                        // Switch 2: Follow Device
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { followDevice = !followDevice; pref.edit().putBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, followDevice).apply() },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.spoof_android_version_follow_device))
                            Switch(checked = followDevice, onCheckedChange = { followDevice = it; pref.edit().putBoolean(PREF_SPOOF_ANDROID_VERSION_FOLLOW_DEVICE, it).apply() })
                        }

                        // Text Input: Manual Version
                        OutlinedTextField(
                            value = manualVersion,
                            onValueChange = { manualVersion = it },
                            label = { Text("Android Version (e.g. 8.1.0, 11, 12)") },
                            enabled = !followDevice,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Button: Apply Changes
                        Button(
                            onClick = {
                                if (manualVersion.isNotBlank() && DeviceProps.getAndroidVersionFromLabel(manualVersion) == null) {
                                    // Invalid version (Simple Toast fallback as Snackbar needs ScaffoldState in simple impl)
                                    android.widget.Toast.makeText(this@AdvancedOptionsActivity, "Invalid android version", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    pref.edit().putString(PREF_SPOOF_ANDROID_VERSION_MANUAL, if (manualVersion.isBlank()) null else manualVersion).apply()
                                    setResult(RESULT_OK)
                                    finish()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !followDevice
                        ) {
                            Text(stringResource(R.string.apply_changes))
                        }
                    }
                }
            }
        }
    }
}