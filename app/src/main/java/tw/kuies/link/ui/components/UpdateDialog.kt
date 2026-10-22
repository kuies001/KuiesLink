package tw.kuies.link.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import tw.kuies.link.ui.UpdatePhase
import tw.kuies.link.ui.UpdateUiState
import tw.kuies.link.ui.userMessage

@Composable
fun UpdateDialog(
    state: UpdateUiState,
    currentVersion: String,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    onCancelDownload: () -> Unit,
) {
    val phase = state.phase
    val update = state.available
    val title = when (phase) {
        UpdatePhase.Idle, UpdatePhase.Checking -> "檢查更新"
        UpdatePhase.UpToDate -> "目前已是最新版本"
        UpdatePhase.Available -> "有新版本 v${update?.versionName.orEmpty()}"
        UpdatePhase.Downloading -> "正在下載更新"
        UpdatePhase.ReadyToInstall -> "已下載完成"
        UpdatePhase.NeedsPermission -> "需要安裝權限"
        UpdatePhase.Failed -> "無法完成更新"
    }

    AlertDialog(
        onDismissRequest = {
            if (phase == UpdatePhase.Downloading) onCancelDownload() else onDismiss()
        },
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (phase) {
                    UpdatePhase.Idle, UpdatePhase.Checking -> {
                        Text("正在取得最新版本資訊…", style = MaterialTheme.typography.bodyMedium)
                    }

                    UpdatePhase.UpToDate -> {
                        Text(
                            "目前版本 $currentVersion 已是最新版本。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    UpdatePhase.Available -> {
                        if (update != null) {
                            Text(
                                "版本：v${update.versionName} (${update.versionCode})",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "APK 大小：${formatBytes(update.apkSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (update.changelog.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = 160.dp)
                                        .verticalScroll(rememberScrollState()),
                                ) {
                                    Text(
                                        text = update.changelog,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    UpdatePhase.Downloading -> {
                        val progress = (state.progress ?: 0).coerceIn(0, 100)
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "$progress%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    UpdatePhase.ReadyToInstall -> {
                        Text(
                            "檔案已通過雜湊、套件與簽章檢查。Android 會再顯示一次安裝確認；" +
                                "取消安裝不會影響目前版本。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    UpdatePhase.NeedsPermission -> {
                        Text(
                            "Android 需要你允許 KuiesLink 安裝應用程式，才能完成覆蓋更新。",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    UpdatePhase.Failed -> {
                        Text(
                            text = state.failure?.userMessage() ?: "更新失敗，請稍後再試。",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when (phase) {
                UpdatePhase.Available -> TextButton(onClick = onDownload) { Text("下載並安裝") }
                UpdatePhase.ReadyToInstall -> TextButton(onClick = onInstall) { Text("安裝") }
                UpdatePhase.NeedsPermission -> {
                    TextButton(onClick = onOpenInstallSettings) { Text("前往設定") }
                }

                UpdatePhase.UpToDate, UpdatePhase.Failed -> {
                    TextButton(onClick = onDismiss) { Text("關閉") }
                }

                else -> Unit
            }
        },
        dismissButton = {
            when (phase) {
                UpdatePhase.Available, UpdatePhase.ReadyToInstall, UpdatePhase.NeedsPermission -> {
                    TextButton(onClick = onDismiss) { Text("稍後") }
                }

                UpdatePhase.Checking, UpdatePhase.Downloading -> {
                    TextButton(onClick = onCancelDownload) { Text("取消") }
                }

                else -> Unit
            }
        },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L ->
        String.format(Locale.TAIWAN, "%.1f MB", bytes / 1024.0 / 1024.0)

    bytes >= 1024L -> String.format(Locale.TAIWAN, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
