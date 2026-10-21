package tw.kuies.link.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tw.kuies.link.ui.components.KuiesIcon
import tw.kuies.link.ui.components.KuiesIconKind
import tw.kuies.link.ui.components.KuiesPrimaryButton
import tw.kuies.link.ui.components.KuiesSecondaryButton
import tw.kuies.link.ui.components.KuiesTopBar
import tw.kuies.link.ui.components.PlatformSourceRow
import tw.kuies.link.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: LinkUiState,
    contentPadding: PaddingValues,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onClean: () -> Unit,
    onClearInput: () -> Unit,
    onSelectCandidate: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var showShareHelp by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        KuiesTopBar(title = "KuiesLink", onSettings = onOpenSettings)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(Spacing.xl))
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                KuiesIcon(
                    KuiesIconKind.Link,
                    size = 40.dp,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = "清理並縮短網址",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "移除追蹤參數，分享更安心",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KuiesIcon(
                            KuiesIconKind.Link,
                            size = 18.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text("貼上網址", style = MaterialTheme.typography.titleSmall)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = onInputChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                text = "請輸入或貼上網址…",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        minLines = 3,
                        maxLines = 6,
                        shape = MaterialTheme.shapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        ),
                        trailingIcon = {
                            if (state.input.isNotEmpty()) {
                                TextButton(onClick = onClearInput) { Text("清除") }
                            }
                        },
                    )
                    if (state.notice == LinkNotice.NoUrlFound) {
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = "找不到可清理的 HTTP 或 HTTPS 網址",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = onPaste) { Text("貼上") }
                    }
                }
            }

            if (state.candidates.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                CandidateList(urls = state.candidates, onSelect = onSelectCandidate)
            }

            Spacer(Modifier.height(Spacing.lg))
            KuiesPrimaryButton(
                text = if (state.isCleaning) "清理中…" else "清理並縮短網址",
                onClick = onClean,
                icon = if (state.isCleaning) null else KuiesIconKind.Link,
                loading = state.isCleaning,
            )

            Spacer(Modifier.height(Spacing.xl))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    text = "或",
                    modifier = Modifier.padding(horizontal = Spacing.md),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.lg))
            KuiesSecondaryButton(
                text = "從其他 App 分享",
                onClick = { showShareHelp = true },
                icon = KuiesIconKind.Share,
            )

            Spacer(Modifier.height(Spacing.xl))
            PlatformSourceRow()
        }
    }

    if (showShareHelp) {
        AlertDialog(
            onDismissRequest = { showShareHelp = false },
            title = { Text("從其他 App 分享") },
            text = {
                Text(
                    "在 Instagram、Threads 或 Facebook 的分享選單中選擇 KuiesLink，" +
                        "會先清理網址並建立短網址，再讓你分享出去。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { showShareHelp = false }) { Text("了解") }
            },
        )
    }
}

@Composable
private fun CandidateList(urls: List<String>, onSelect: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(Spacing.lg)) {
            Text(
                text = "找到 ${urls.size} 個網址",
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "請選擇要處理的連結，KuiesLink 不會自行猜測。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(Spacing.sm))
            urls.forEach { url ->
                Text(
                    text = url,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onSelect(url) }
                        .padding(Spacing.md),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
