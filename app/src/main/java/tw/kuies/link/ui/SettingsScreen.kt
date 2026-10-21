package tw.kuies.link.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import tw.kuies.link.settings.PreferredShareTarget
import tw.kuies.link.settings.ShareMode
import tw.kuies.link.settings.ThemeMode
import tw.kuies.link.ui.components.KuiesIconKind
import tw.kuies.link.ui.components.KuiesTopBar
import tw.kuies.link.ui.components.SettingsRow
import tw.kuies.link.ui.components.SettingsRowDivider
import tw.kuies.link.ui.components.SettingsSection
import tw.kuies.link.ui.theme.Spacing

private enum class SettingsDialog {
    ShareMode,
    Target,
    Theme,
    Privacy,
    Verified,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: LinkUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onShareModeChange: (ShareMode) -> Unit,
    onPreferredShareTargetChange: (PreferredShareTarget) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onExpandDetailsChange: (Boolean) -> Unit,
    onShowTechnicalInfoChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val versionLabel = remember(context) { context.versionLabel() }
    var dialog by remember { mutableStateOf<SettingsDialog?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        KuiesTopBar(title = "設定", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Spacer(Modifier.height(Spacing.xs))
            SettingsSection(title = "分享設定") {
                SettingsRow(
                    icon = KuiesIconKind.Share,
                    title = "分享模式",
                    subtitle = "選擇處理後的分享流程",
                    value = if (state.shareMode == ShareMode.QUICK) "快速模式" else "確認模式",
                    onClick = { dialog = SettingsDialog.ShareMode },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = KuiesIconKind.Link,
                    title = "預設分享目標",
                    subtitle = "選擇要分享的內容",
                    value = if (state.preferredShareTarget == PreferredShareTarget.CLEAN_URL) {
                        "乾淨原網址"
                    } else {
                        "Kuies 短網址"
                    },
                    onClick = { dialog = SettingsDialog.Target },
                )
            }

            SettingsSection(title = "顯示與體驗") {
                SettingsRow(
                    icon = KuiesIconKind.Moon,
                    title = "深色模式",
                    value = when (state.themeMode) {
                        ThemeMode.SYSTEM -> "跟隨系統"
                        ThemeMode.LIGHT -> "淺色"
                        ThemeMode.DARK -> "深色"
                    },
                    onClick = { dialog = SettingsDialog.Theme },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = KuiesIconKind.Info,
                    title = "預設展開詳細資訊",
                    checked = state.expandDetailsByDefault,
                    onCheckedChange = onExpandDetailsChange,
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = KuiesIconKind.Settings,
                    title = "顯示技術資訊",
                    subtitle = "在詳細資訊中顯示處理狀態等技術欄位",
                    checked = state.showTechnicalInfo,
                    onCheckedChange = onShowTechnicalInfoChange,
                )
            }

            SettingsSection(title = "關於與隱私") {
                SettingsRow(
                    icon = KuiesIconKind.Info,
                    title = "App 版本",
                    value = versionLabel,
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = KuiesIconKind.Shield,
                    title = "隱私說明",
                    subtitle = "了解我們如何處理你的資料",
                    onClick = { dialog = SettingsDialog.Privacy },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = KuiesIconKind.Copy,
                    title = "GitHub",
                    subtitle = "查看專案原始碼",
                    onClick = { uriHandler.openUri(GITHUB_URL) },
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = KuiesIconKind.CheckCircle,
                    title = "已驗證清理是什麼？",
                    subtitle = "了解清理規則與限制",
                    onClick = { dialog = SettingsDialog.Verified },
                )
            }
        }
    }

    when (dialog) {
        SettingsDialog.ShareMode -> ChoiceDialog(
            title = "分享模式",
            options = listOf(
                Choice(ShareMode.CONFIRM, "確認模式", "先看清結果再分享"),
                Choice(ShareMode.QUICK, "快速模式", "清理後直接開啟分享選單"),
            ),
            selected = state.shareMode,
            onSelect = onShareModeChange,
            onDismiss = { dialog = null },
        )

        SettingsDialog.Target -> ChoiceDialog(
            title = "預設分享目標",
            options = listOf(
                Choice(PreferredShareTarget.KUIES_SHORT_URL, "Kuies 短網址", "建立 u.kuies.tw 短網址"),
                Choice(PreferredShareTarget.CLEAN_URL, "乾淨原網址", "只分享清理後的原網址"),
            ),
            selected = state.preferredShareTarget,
            onSelect = onPreferredShareTargetChange,
            onDismiss = { dialog = null },
        )

        SettingsDialog.Theme -> ChoiceDialog(
            title = "深色模式",
            options = listOf(
                Choice(ThemeMode.SYSTEM, "跟隨系統"),
                Choice(ThemeMode.LIGHT, "淺色"),
                Choice(ThemeMode.DARK, "深色"),
            ),
            selected = state.themeMode,
            onSelect = onThemeModeChange,
            onDismiss = { dialog = null },
        )

        SettingsDialog.Privacy -> InfoDialog(
            title = "隱私說明",
            body = "一般清理都在這支手機上完成。只有在你按下「建立短網址」，" +
                "或快速模式預設分享短網址時，原始網址才會送到 u.kuies.tw 建立短網址；" +
                "伺服器不會保存你貼上的其他內容。App 只在裝置上保存你的偏好設定。",
            onDismiss = { dialog = null },
        )

        SettingsDialog.Verified -> InfoDialog(
            title = "已驗證清理是什麼？",
            body = "KuiesLink 會依目前支援的清理規則處理網址，但不保證第三方平台完全無法追蹤。" +
                "只有後端回報 CLEANED 或 UNCHANGED 時，畫面才會顯示對應狀態；" +
                "UNSUPPORTED 代表這個網站不在目前的規則內，短網址仍會建立，但不會宣稱已清理。",
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}

private data class Choice<T>(
    val value: T,
    val label: String,
    val description: String? = null,
)

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Choice<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(Modifier.selectableGroup()) {
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp)
                            .selectable(
                                selected = option.value == selected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelect(option.value)
                                    onDismiss()
                                },
                            )
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option.value == selected,
                            onClick = null,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = option.label, style = MaterialTheme.typography.titleSmall)
                            option.description?.let { description ->
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("了解") }
        },
    )
}

private fun Context.versionLabel(): String = runCatching {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    "v${info.versionName} (${info.longVersionCode})"
}.getOrDefault("—")

private const val GITHUB_URL = "https://github.com/kuies001/kuies-short-url"
