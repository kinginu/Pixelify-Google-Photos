package balti.xposed.pixelifygooglephotos.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import balti.xposed.pixelifygooglephotos.Constants
import balti.xposed.pixelifygooglephotos.spoof.DeviceProps
import balti.xposed.pixelifygooglephotos.R

class FeatureCustomize : ComponentActivity() {

    private val pref by lazy {
        @Suppress("DEPRECATION")
        getSharedPreferences(Constants.SHARED_PREF_FILE_NAME, MODE_WORLD_READABLE)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF6750A4),
                    onPrimary = Color.Companion.White,
                    secondary = Color(0xFF625B71),
                    background = Color(0xFFFFFBFE),
                    surface = Color(0xFFFFFBFE)
                )
            ) {
                // 初期データの読み込み
                val allFeatures = DeviceProps.defaultFeatures.map { it.displayName }
                // 選択状態をSetで管理
                var selectedFeatures by remember {
                    mutableStateOf(
                        pref.getStringSet(
                            Constants.PREF_SPOOF_FEATURES_LIST,
                            allFeatures.toSet()
                        )?.toSet() ?: emptySet()
                    )
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.customize_feature_flags)) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                TextButton(onClick = {
                                    pref.edit().putStringSet(
                                        Constants.PREF_SPOOF_FEATURES_LIST,
                                        selectedFeatures
                                    ).apply()
                                    setResult(RESULT_OK)
                                    finish()
                                }) {
                                    Text("SAVE", fontWeight = FontWeight.Bold)
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier.Companion
                            .padding(innerPadding)
                            .fillMaxSize(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        items(allFeatures) { feature ->
                            Row(
                                modifier = Modifier.Companion
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedFeatures = if (selectedFeatures.contains(feature)) {
                                            selectedFeatures - feature
                                        } else {
                                            selectedFeatures + feature
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.Companion.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selectedFeatures.contains(feature),
                                    onCheckedChange = { isChecked ->
                                        selectedFeatures = if (isChecked) {
                                            selectedFeatures + feature
                                        } else {
                                            selectedFeatures - feature
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.Companion.width(16.dp))
                                Text(text = feature, style = MaterialTheme.typography.bodyLarge)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}