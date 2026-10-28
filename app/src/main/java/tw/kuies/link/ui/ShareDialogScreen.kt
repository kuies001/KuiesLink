package tw.kuies.link.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tw.kuies.link.R
import tw.kuies.link.data.ShortenResponse
import tw.kuies.link.domain.CleaningResult
import tw.kuies.link.domain.CleaningStatus
import tw.kuies.link.ui.components.KuiesIcon
import tw.kuies.link.ui.components.KuiesIconKind
import tw.kuies.link.ui.components.KuiesPrimaryButton
import tw.kuies.link.ui.components.KuiesSecondaryButton
import tw.kuies.link.ui.theme.Spacing
import tw.kuies.link.ui.theme.UrlTextStyle

private enum class DialogPhase { Processing, Success, Failure }

/**
 * The compact external-share result. It deliberately reuses the same dark palette and components
 * as the full result screen, but shows only what is needed to compare before/after and hand the
 * short URL on.
 */
@Composable
fun ShareDialogScreen(
    state: LinkUiState,
    onCopyShortUrl: (String) -> Unit,
    onShareShortUrl: (String) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clean = state.result
    val short = state.shortResult
    val phase = when {
        state.isCleaning || state.isShortening -> DialogPhase.Processing
        short != null -> DialogPhase.Success
        clean != null && clean.isShareable && state.shortenError == null -> DialogPhase.Processing
        else -> DialogPhase.Failure
    }

    val scrimInteraction = remember { MutableInteractionSource() }
    BackHandler(onBack = onFinish)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ScrimColor)
            // Tapping the dimmed source app dismisses the dialog, like a modal sheet.
            .clickable(
                interactionSource = scrimInteraction,
                indication = null,
                onClick = onFinish,
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(maxWidth * 0.88f)
                .heightIn(max = maxHeight * 0.86f)
                // Consume taps so pressing inside the card never dismisses it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 16.dp,
        ) {
            var actionsHeightPx by remember { mutableStateOf(0) }
            val actionsPadding = with(LocalDensity.current) { actionsHeightPx.toDp() }

            Box {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                        // Keep the scrollable content clear of the pinned actions.
                        .padding(bottom = actionsPadding),
                ) {
                    DialogHeader(phase)
                    Spacer(Modifier.height(Spacing.md))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(Spacing.md))

                    when (phase) {
                        DialogPhase.Processing -> ProcessingBody(state, clean)
                        DialogPhase.Success -> SuccessBody(state, clean, short)
                        DialogPhase.Failure -> FailureBody(state, clean)
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .onSizeChanged { actionsHeightPx = it.height }
                        .padding(start = 20.dp, end = 20.dp, top = Spacing.md, bottom = 18.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(Spacing.md))
                    DialogActions(
                        phase = phase,
                        state = state,
                        clean = clean,
                        short = short,
                        onCopyShortUrl = onCopyShortUrl,
                        onShareShortUrl = onShareShortUrl,
                        onFinish = onFinish,
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogHeader(phase: DialogPhase) {
    val chipContainer = when (phase) {
        DialogPhase.Processing -> MaterialTheme.colorScheme.secondaryContainer
        DialogPhase.Success -> MaterialTheme.colorScheme.primaryContainer
        DialogPhase.Failure -> MaterialTheme.colorScheme.errorContainer
    }
    val chipContent = when (phase) {
        DialogPhase.Processing -> MaterialTheme.colorScheme.onSecondaryContainer
        DialogPhase.Success -> MaterialTheme.colorScheme.onPrimaryContainer
        DialogPhase.Failure -> MaterialTheme.colorScheme.onErrorContainer
    }
    val chipLabel = when (phase) {
        DialogPhase.Processing -> "處理中"
        DialogPhase.Success -> "成功"
        DialogPhase.Failure -> "失敗"
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            KuiesIcon(
                KuiesIconKind.Link,
                size = 20.dp,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text("KuiesLink", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "網址清理與短網址",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = chipContainer,
        ) {
            Text(
                text = chipLabel,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
                color = chipContent,
            )
        }
    }
}

@Composable
private fun ProcessingBody(state: LinkUiState, clean: CleaningResult?) {
    Text(
        text = if (state.isCleaning) "正在清理網址…" else "正在建立短網址…",
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(Spacing.lg))
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = "處理完成後會顯示清理前後的網址。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    val original = clean?.originalUrl ?: state.input.takeIf { it.isNotBlank() }
    if (original != null) {
        Spacer(Modifier.height(Spacing.lg))
        DialogUrl("原始網址", original)
    }
}

@Composable
private fun SuccessBody(
    state: LinkUiState,
    clean: CleaningResult?,
    short: ShortenResponse?,
) {
    if (short == null) return
    val unsupported = short.cleaningStatus == ShortenResponse.STATUS_UNSUPPORTED
    val cleanedNothing = short.cleaningStatus == ShortenResponse.STATUS_UNCHANGED
    val removedCount = short.removedCount

    Text(
        text = when {
            unsupported -> "已建立短網址"
            cleanedNothing -> "無須清理，已建立短網址"
            else -> "已清理並建立短網址"
        },
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(Spacing.sm))
    Text(
        text = when {
            unsupported -> "未支援此網站的清理規則"
            removedCount > 0 -> "已依目前支援規則完成清理"
            else -> "沒有需要移除的追蹤參數"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (!unsupported && removedCount > 0) {
        Spacer(Modifier.height(Spacing.xs))
        RemovedParamsRow(count = removedCount, names = short.removedParams)
    }

    Spacer(Modifier.height(Spacing.md))
    DialogUrl("原始網址", clean?.originalUrl ?: short.targetUrl)
    Spacer(Modifier.height(Spacing.sm))
    DialogUrl("清理後網址", short.targetUrl)
    Spacer(Modifier.height(Spacing.sm))
    DialogUrl("短網址", short.shortUrl, emphasized = true)
}

@Composable
private fun FailureBody(state: LinkUiState, clean: CleaningResult?) {
    val title = when {
        clean == null && state.notice == LinkNotice.EmptyShare -> "沒有收到分享內容"
        clean == null && state.notice == LinkNotice.NoUrlFound -> "找不到可清理的網址"
        clean == null && state.notice == LinkNotice.MultipleUrls -> "這段文字有多個網址"
        clean == null -> "無法處理這個網址"
        clean.status == CleaningStatus.INVALID_URL -> "這不是有效的網址"
        clean.status == CleaningStatus.FAILED -> "清理未完成"
        state.shortenError != null && clean.status == CleaningStatus.UNCHANGED ->
            "網址無須清理，但短網址建立失敗"
        state.shortenError != null -> "已清理，但短網址建立失敗"
        else -> "無法處理這個網址"
    }
    val message = when {
        state.shortenError != null -> stringResource(state.shortenError.messageRes())
        clean == null && state.notice == LinkNotice.MultipleUrls ->
            "KuiesLink 不會自行猜測，請回到 App 後再選擇其中一個連結。"
        clean == null && state.notice == LinkNotice.EmptyShare -> "分享內容是空的，沒有網址可以清理。"
        clean == null -> "找不到可清理的 HTTP 或 HTTPS 網址。"
        clean.status == CleaningStatus.INVALID_URL -> "請確認分享內容包含完整的 HTTP 或 HTTPS 網址。"
        clean.status == CleaningStatus.FAILED -> "原始網址已保留，稍後可以再試一次。"
        else -> "請稍後再試一次。"
    }

    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(Spacing.sm))
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    clean?.let { result ->
        Spacer(Modifier.height(Spacing.lg))
        DialogUrl("原始網址", result.originalUrl)
        if (result.isShareable && result.cleanedUrl != result.originalUrl) {
            Spacer(Modifier.height(Spacing.md))
            DialogUrl("清理後網址", result.cleanedUrl)
        }
    }
}

@Composable
private fun RemovedParamsRow(count: Int, names: List<String>) {
    var expanded by rememberSaveable(names) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已移除 $count 個追蹤參數",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            KuiesIcon(
                if (expanded) KuiesIconKind.ChevronUp else KuiesIconKind.ChevronDown,
                size = 18.dp,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) {
            Text(
                text = names.ifEmpty { listOf("未提供名稱") }.joinToString("、"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DialogUrl(label: String, url: String, emphasized: Boolean = false) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.xs))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            SelectionContainer {
                Text(
                    text = url,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = 10.dp),
                    style = UrlTextStyle,
                    color = if (emphasized) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@Composable
private fun DialogActions(
    phase: DialogPhase,
    state: LinkUiState,
    clean: CleaningResult?,
    short: ShortenResponse?,
    onCopyShortUrl: (String) -> Unit,
    onShareShortUrl: (String) -> Unit,
    onFinish: () -> Unit,
) {
    when (phase) {
        DialogPhase.Processing -> {
            KuiesPrimaryButton(
                text = "處理中…",
                onClick = {},
                icon = null,
                enabled = false,
                loading = true,
            )
            Spacer(Modifier.height(Spacing.xs))
            TextButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("取消")
            }
        }

        DialogPhase.Success -> {
            val shortUrl = short?.shortUrl
            if (shortUrl != null) {
                KuiesPrimaryButton(
                    text = "複製短網址",
                    onClick = { onCopyShortUrl(shortUrl) },
                    icon = KuiesIconKind.Copy,
                )
                Spacer(Modifier.height(Spacing.sm))
                KuiesSecondaryButton(
                    text = "分享短網址",
                    onClick = { onShareShortUrl(shortUrl) },
                    icon = KuiesIconKind.Share,
                )
            }
            Spacer(Modifier.height(Spacing.xs))
            TextButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("完成")
            }
        }

        DialogPhase.Failure -> {
            // The short URL is unavailable, so fall back to the best URL that still exists.
            val fallback = clean?.let { if (it.isShareable) it.cleanedUrl else it.originalUrl }
            if (fallback != null) {
                KuiesPrimaryButton(
                    text = if (clean?.isShareable == true) "複製清理後網址" else "複製原始網址",
                    onClick = { onCopyShortUrl(fallback) },
                    icon = KuiesIconKind.Copy,
                )
                if (clean?.isShareable == true) {
                    Spacer(Modifier.height(Spacing.sm))
                    KuiesSecondaryButton(
                        text = "分享網址",
                        onClick = { onShareShortUrl(fallback) },
                        icon = KuiesIconKind.Share,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            TextButton(
                onClick = onFinish,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("完成")
            }
        }
    }
}

private val ScrimColor = Color.Black.copy(alpha = 0.45f)
