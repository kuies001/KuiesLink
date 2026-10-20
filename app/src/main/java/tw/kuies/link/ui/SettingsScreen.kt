package tw.kuies.link.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tw.kuies.link.R
import tw.kuies.link.settings.PreferredShareTarget
import tw.kuies.link.settings.ShareMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    shareMode: ShareMode,
    preferredShareTarget: PreferredShareTarget,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onShareModeChange: (ShareMode) -> Unit,
    onPreferredShareTargetChange: (PreferredShareTarget) -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val versionName = remember(context) { context.appVersionName() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        TopAppBar(
            title = { Text(stringResource(R.string.settings_title)) },
            navigationIcon = {
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.settings_back))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.settings_mode_title))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    ModeOption(
                        title = stringResource(R.string.settings_mode_confirm_title),
                        description = stringResource(R.string.settings_mode_confirm_body),
                        selected = shareMode == ShareMode.CONFIRM,
                        onSelect = { onShareModeChange(ShareMode.CONFIRM) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ModeOption(
                        title = stringResource(R.string.settings_mode_quick_title),
                        description = stringResource(R.string.settings_mode_quick_body),
                        selected = shareMode == ShareMode.QUICK,
                        onSelect = { onShareModeChange(ShareMode.QUICK) },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.preferred_target_title))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column {
                    ModeOption(
                        title = stringResource(R.string.preferred_target_clean_title),
                        description = stringResource(R.string.preferred_target_clean_body),
                        selected = preferredShareTarget == PreferredShareTarget.CLEAN_URL,
                        onSelect = { onPreferredShareTargetChange(PreferredShareTarget.CLEAN_URL) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ModeOption(
                        title = stringResource(R.string.preferred_target_short_title),
                        description = stringResource(R.string.preferred_target_short_body),
                        selected = preferredShareTarget == PreferredShareTarget.KUIES_SHORT_URL,
                        onSelect = {
                            onPreferredShareTargetChange(PreferredShareTarget.KUIES_SHORT_URL)
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.settings_privacy_title))
            Text(
                text = stringResource(R.string.settings_privacy_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            SectionTitle(stringResource(R.string.settings_about_title))
            Text(
                text = stringResource(R.string.settings_version, versionName.ifEmpty { "—" }),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_v010_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_github_link),
                modifier = Modifier
                    .clickable { uriHandler.openUri(GITHUB_URL) }
                    .padding(vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ModeOption(
    title: String,
    description: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onSelect)
    }
}

private fun Context.appVersionName(): String = runCatching {
    val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    packageInfo.versionName
}.getOrNull().orEmpty()

private const val GITHUB_URL = "https://github.com/kuies001/kuies-short-url"
