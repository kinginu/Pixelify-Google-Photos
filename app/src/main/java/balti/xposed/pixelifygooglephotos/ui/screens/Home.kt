package balti.xposed.pixelifygooglephotos.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import balti.xposed.pixelifygooglephotos.Constants
import balti.xposed.pixelifygooglephotos.R
import balti.xposed.pixelifygooglephotos.ui.*

@Composable
fun HomeScreen(
    updateUrl: String?,
    appVersion: String,
    onOpenLink: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    // Module Active判定 (簡易的にPrefsの存在確認)
    val isModuleActive = try {
        @Suppress("DEPRECATION")
        context.getSharedPreferences(Constants.SHARED_PREF_FILE_NAME, android.content.Context.MODE_WORLD_READABLE) != null
    } catch (_: Exception) { false }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        .clickable { onOpenLink(updateUrl) }
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
                        onClick = { onOpenLink(Constants.TELEGRAM_GROUP) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    LinkItem(
                        title = "Learn KernelSU",
                        icon = Icons.Default.School,
                        onClick = { onOpenLink("https://kernelsu.org/") }
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}