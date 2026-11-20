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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import tw.kuies.link.R
import tw.kuies.link.data.ShortenResponse
import tw.kuies.link.domain.CleaningResult
import tw.kuies.link.domain.UrlDiffSegment
import tw.kuies.link.ui.components.CleaningDetailsContent
import tw.kuies.link.ui.components.DetailsDisclosure
import tw.kuies.link.ui.components.DiffUrlBox
import tw.kuies.link.ui.components.KuiesIcon
import tw.kuies.link.ui.components.KuiesIconKind
import tw.kuies.link.ui.components.KuiesPrimaryButton
import tw.kuies.link.ui.components.KuiesSecondaryButton
import tw.kuies.link.ui.theme.Spacing

private enum class DialogPhase { Processing, Success, Failure }

/**
 * The compact external-share result. It deliberately reuses the same dark palette and components
 * as the full result screen, but shows only what is needed to compare before/after and hand the
 * short URL on. Detailed parameters stay collapsed until the user asks for them.
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
            DialogScaffold(
                content = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                    ) {
                        DialogHeader(phase)
                        Spacer(Modifier.height(Spacing.md))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(Spacing.md))

                        when (phase) {
                            DialogPhase.Processing -> ProcessingBody(state, clean)
                            DialogPhase.Success -> SuccessBody(clean, short)
                            DialogPhase.Failure -> FailureBody(state, clean)
                        }
                    }
                },
                actions = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(start = 20.dp, end = 20.dp, top = Spacing.md, bottom = 18.dp),
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(Spacing.md))
                        DialogActions(
                            phase = phase,
                            clean = clean,
                            short = short,
                            onCopyShortUrl = onCopyShortUrl,
                            onShareShortUrl = onShareShortUrl,
                            onFinish = onFinish,
                        )
                    }
                },
            )
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
        DialogPhase.Success -> "已完成"
        DialogPhase.Failure -> "未完成"
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
        DiffUrlBox(
            label = "原始網址",
            segments = listOf(UrlDiffSegment(original, removed = false)),
        )
    }
}

@Composable
private fun SuccessBody(clean: CleaningResult?, short: ShortenResponse?) {
    if (short == null) return
    val result = clean ?: return
    val report = buildCleaningReport(result, short)
    val summary = report.summaryText(shortUrlCreated = true)
    var detailsExpanded by rememberSaveable(report.originalUrl, short.shortUrl) {
        mutableStateOf(false)
    }

    Text(
        text = summary.title,
        style = MaterialTheme.typography.titleMedium,
    )
    Spacer(Modifier.height(Spacing.sm))
    Text(
        text = summary.subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(Modifier.height(Spacing.md))
    DetailsDisclosure(
        expanded = detailsExpanded,
        onToggle = { detailsExpanded = !detailsExpanded },
        title = "清理詳細資訊",
    ) {
        CleaningDetailsContent(report)
    }

    Spacer(Modifier.height(Spacing.md))
    DiffUrlBox(
        label = if (report.resolvedWrapper) "原始分享網址" else "原始網址",
        segments = report.diff.originalSegments,
        supportingText = when {
            report.diff.hasRemoved -> "紅色標示為已移除的追蹤參數"
            report.resolvedWrapper -> "這是分享平台的包裝連結"
            else -> null
        },
    )
    report.targetUrl?.let { target ->
        Spacer(Modifier.height(Spacing.sm))
        DiffUrlBox(
            label = report.targetLabel(),
            segments = listOf(UrlDiffSegment(target, removed = false)),
            supportingText = report.targetNote(),
        )
    }

    Spacer(Modifier.height(Spacing.sm))
    DiffUrlBox(
        label = "短網址",
        segments = listOf(UrlDiffSegment(short.shortUrl, removed = false)),
        supportingText = "此短網址會導向${report.targetLabel()}。",
        emphasized = true,
    )
}

@Composable
private fun FailureBody(state: LinkUiState, clean: CleaningResult?) {
    val report = clean?.let { buildCleaningReport(it, short = null) }
    val title = when {
        clean == null && state.notice == LinkNotice.EmptyShare -> "沒有收到分享內容"
        clean == null && state.notice == LinkNotice.NoUrlFound -> "找不到可清理的網址"
        clean == null && state.notice == LinkNotice.MultipleUrls -> "這段文字有多個網址"
        clean == null -> "無法處理這個網址"
        state.shortenError != null -> "短網址建立失敗"
        report?.outcome == CleaningOutcome.INVALID -> "這不是有效的網址"
        report?.outcome == CleaningOutcome.FAILED -> "清理未完成"
        report?.outcome == CleaningOutcome.UNRESOLVED -> "暫時無法還原連結"
        else -> "無法處理這個網址"
    }
    val message = when {
        state.shortenError != null -> stringResource(state.shortenError.messageRes())
        clean == null && state.notice == LinkNotice.MultipleUrls ->
            "KuiesLink 不會自行猜測，請回到 App 後再選擇其中一個連結。"
        clean == null && state.notice == LinkNotice.EmptyShare -> "分享內容是空的，沒有網址可以清理。"
        clean == null -> "找不到可清理的 HTTP 或 HTTPS 網址。"
        report?.outcome == CleaningOutcome.INVALID -> "請確認分享內容包含完整的 HTTP 或 HTTPS 網址。"
        report?.outcome == CleaningOutcome.FAILED -> "原始網址已保留，稍後可以再試一次。"
        else -> "請稍後再試一次。"
    }
    val cleaningNote = if (state.shortenError != null) {
        when (report?.outcome) {
            CleaningOutcome.CLEANED -> "本機已清理網址，但短網址未建立。"
            CleaningOutcome.UNCHANGED -> "本機未發現需要移除的參數，原始網址已保留。"
            CleaningOutcome.UNRESOLVED -> "包裝連結尚未還原，原始網址已保留。"
            CleaningOutcome.INCONCLUSIVE -> "本機清理結果無法確認，原始網址已保留。"
            CleaningOutcome.INVALID, CleaningOutcome.FAILED -> "原始網址已保留。"
            else -> null
        }
    } else {
        null
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
    if (cleaningNote != null) {
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = cleaningNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (report != null) {
        var detailsExpanded by rememberSaveable(report.originalUrl) { mutableStateOf(false) }
        Spacer(Modifier.height(Spacing.md))
        DetailsDisclosure(
            expanded = detailsExpanded,
            onToggle = { detailsExpanded = !detailsExpanded },
            title = "清理詳細資訊",
        ) {
            CleaningDetailsContent(report)
        }

        Spacer(Modifier.height(Spacing.md))
        DiffUrlBox(
            label = if (report.resolvedWrapper) "原始分享網址" else "原始網址",
            segments = report.diff.originalSegments,
            supportingText = if (report.diff.hasRemoved) {
                "紅色標示為已移除的追蹤參數"
            } else {
                null
            },
        )
        report.targetUrl?.let { target ->
            Spacer(Modifier.height(Spacing.sm))
            DiffUrlBox(
                label = report.targetLabel(),
                segments = listOf(UrlDiffSegment(target, removed = false)),
            )
        }
    }
}

@Composable
private fun DialogActions(
    phase: DialogPhase,
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
            val report = clean?.let { buildCleaningReport(it, short = null) }
            val outcome = report?.outcome
            val fallback = if (outcome == CleaningOutcome.CLEANED || outcome == CleaningOutcome.RESOLVED) {
                report?.targetUrl ?: clean?.originalUrl
            } else {
                clean?.originalUrl
            }
            val copyLabel = when (outcome) {
                CleaningOutcome.CLEANED -> "複製清理後網址"
                CleaningOutcome.RESOLVED -> "複製還原後網址"
                CleaningOutcome.UNCHANGED -> "複製原網址"
                else -> "複製原始網址"
            }
            val shareLabel = when (outcome) {
                CleaningOutcome.CLEANED -> "分享清理後網址"
                CleaningOutcome.RESOLVED -> "分享還原後網址"
                CleaningOutcome.UNCHANGED -> "分享原網址"
                else -> "分享原始網址"
            }
            if (fallback != null) {
                KuiesPrimaryButton(
                    text = copyLabel,
                    onClick = { onCopyShortUrl(fallback) },
                    icon = KuiesIconKind.Copy,
                )
                if (clean?.isShareable == true) {
                    Spacer(Modifier.height(Spacing.sm))
                    KuiesSecondaryButton(
                        text = shareLabel,
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

/**
 * Measures the fixed actions first, then gives the remaining height to the scrollable content.
 * This keeps the actions on screen even when the details are expanded on a short window.
 */
@Composable
private fun DialogScaffold(
    content: @Composable () -> Unit,
    actions: @Composable () -> Unit,
) {
    Layout(
        contents = listOf(content, actions),
        modifier = Modifier.fillMaxWidth(),
    ) { (contentMeasurables, actionsMeasurables), constraints ->
        val actionsPlaceable = actionsMeasurables.single().measure(
            Constraints(
                minWidth = constraints.minWidth,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = constraints.maxHeight,
            ),
        )
        val contentMaxHeight = (constraints.maxHeight - actionsPlaceable.height).coerceAtLeast(0)
        val contentPlaceable = contentMeasurables.single().measure(
            Constraints(
                minWidth = constraints.minWidth,
                maxWidth = constraints.maxWidth,
                minHeight = 0,
                maxHeight = contentMaxHeight,
            ),
        )
        val width = maxOf(contentPlaceable.width, actionsPlaceable.width)
        val height = contentPlaceable.height + actionsPlaceable.height
        layout(width, height) {
            contentPlaceable.placeRelative(0, 0)
            actionsPlaceable.placeRelative(0, contentPlaceable.height)
        }
    }
}
