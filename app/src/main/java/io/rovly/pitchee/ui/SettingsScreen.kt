package io.rovly.pitchee.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.rovly.pitchee.update.UpdateUiState

@Composable
internal fun SettingsScreen(
    updateState: UpdateUiState,
    autoCheckUpdates: Boolean,
    onAutoCheckChange: (Boolean) -> Unit,
    onCheckUpdates: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { PassagePreferences(context) }
    val versionName = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "1.0" }
    }
    var source by remember { mutableStateOf(preferences.source()) }
    var officialIndex by remember { mutableIntStateOf(preferences.officialIndex()) }
    var customText by remember { mutableStateOf(preferences.customText()) }
    var customSaved by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(
            text = "设置",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "语料",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FilterChip(
                selected = source == PassageSource.OFFICIAL,
                onClick = {
                    source = PassageSource.OFFICIAL
                    preferences.setSource(source)
                },
                label = { Text("官方语料") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = source == PassageSource.CUSTOM,
                onClick = {
                    source = PassageSource.CUSTOM
                    preferences.setSource(source)
                    customSaved = false
                },
                label = { Text("自定义语料") },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (source == PassageSource.OFFICIAL) {
            readingPassages.forEachIndexed { index, passage ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            officialIndex = index
                            preferences.setOfficialIndex(index)
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = officialIndex == index,
                        onClick = {
                            officialIndex = index
                            preferences.setOfficialIndex(index)
                        },
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(
                            text = passage.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = passage.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = customText,
                onValueChange = {
                    customText = it
                    customSaved = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("自定义语料") },
                minLines = 4,
                maxLines = 10,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    preferences.setCustomText(customText)
                    customText = preferences.customText()
                    customSaved = true
                },
                enabled = customText.trim().isNotEmpty(),
            ) {
                Text("保存语料")
            }
            val savedCustomText = preferences.customText()
            if (customSaved || (savedCustomText.isNotBlank() && customText == savedCustomText)) {
                Text(
                    text = "已使用自定义语料",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(20.dp))
        Text(
            text = "应用",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("当前版本", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = versionName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = onCheckUpdates,
                enabled = updateState !is UpdateUiState.Checking &&
                    updateState !is UpdateUiState.Downloading,
            ) {
                if (updateState is UpdateUiState.Checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("检查更新")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("自动检查更新", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "启动应用时检查 GitHub 上的新版本",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = autoCheckUpdates,
                onCheckedChange = onAutoCheckChange,
            )
        }
        when (val state = updateState) {
            is UpdateUiState.UpToDate -> UpdateStatusText("当前已是最新版本")
            is UpdateUiState.Available -> UpdateStatusText("发现新版本 ${state.release.displayName}")
            is UpdateUiState.Downloading -> UpdateStatusText("正在下载更新")
            is UpdateUiState.ReadyToInstall -> UpdateStatusText("更新已下载，正在打开安装器")
            is UpdateUiState.Error -> UpdateStatusText(
                text = state.message,
                isError = true,
            )
            UpdateUiState.Idle,
            UpdateUiState.Checking,
            -> Unit
        }
    }
}

@Composable
private fun UpdateStatusText(
    text: String,
    isError: Boolean = false,
) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    )
}
