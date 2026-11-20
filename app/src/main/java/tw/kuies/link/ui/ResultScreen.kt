package tw.kuies.link.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import tw.kuies.link.R
import tw.kuies.link.data.ShortenError
import tw.kuies.link.data.ShortenResponse
import tw.kuies.link.domain.UrlDiffSegment
import tw.kuies.link.ui.components.CleaningDetailsContent
import tw.kuies.link.ui.components.DetailRow
import tw.kuies.link.ui.components.DetailsDisclosure
import tw.kuies.link.ui.components.DiffUrlBox
import tw.kuies.link.ui.components.KuiesIconKind
import tw.kuies.link.ui.components.KuiesPrimaryButton
import tw.kuies.link.ui.components.KuiesSecondaryButton
import tw.kuies.link.ui.components.KuiesTopBar
import tw.kuies.link.ui.components.StatusVisual
import tw.kuies.link.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    state: LinkUiState,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onCopyUrl: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onShorten: () -> Unit,
    onRetryShorten: () -> Unit,
    onRetryClean: () -> Unit,
    onCopyShortUrl: (String) -> Unit,
    onShareShortUrl: (String) -> Unit,
) {
    val clean = state.result ?: return
    val short = state.shortResult
    val report = buildCleaningReport(clean, short)
    val summary = report.summaryText(shortUrlCreated = short != null)
    var detailsExpanded by rememberSaveable(clean.originalUrl, short?.shortUrl) {
        mutableStateOf(state.expandDetailsByDefault)
    }

    val icon = when (report.outcome) {
        CleaningOutcome.CLEANED -> KuiesIconKind.CheckCircle
        CleaningOutcome.UNRESOLVED -> KuiesIconKind.LinkOff
        CleaningOutcome.RESOLVED -> KuiesIconKind.Link
        else -> KuiesIconKind.Info
    }
    val success = report.outcome == CleaningOutcome.CLEANED
    val targetUrl = report.targetUrl

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        KuiesTopBar(title = "處理結果", onBack = onBack)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = Spacing.xxl),
        ) {
            Spacer(Modifier.height(Spacing.lg))
            StatusVisual(
                icon = icon,
                title = summary.title,
                subtitle = summary.subtitle,
                success = success,
            )

            Spacer(Modifier.height(Spacing.xl))
            DiffUrlBox(
                label = if (report.resolvedWrapper) "原始分享網址" else "原始網址",
                segments = report.diff.originalSegments,
                supportingText = when {
                    report.diff.hasRemoved -> "紅色標示為已移除的追蹤參數"
                    report.resolvedWrapper -> "這是分享平台的包裝連結"
                    else -> null
                },
            )

            if (targetUrl != null) {
                Spacer(Modifier.height(Spacing.md))
                DiffUrlBox(
                    label = report.targetLabel(),
                    segments = listOf(UrlDiffSegment(targetUrl, removed = false)),
                    supportingText = report.targetNote(),
                    emphasized = short != null || success,
                    onCopy = { onCopyUrl(targetUrl) },
                    copyDescription = "複製清理後網址",
                )
            }

            if (state.shortenError != null) {
                Spacer(Modifier.height(Spacing.md))
                ShortenErrorCard(
                    error = state.shortenError,
                    onRetryShorten = onRetryShorten,
                )
            }

            Spacer(Modifier.height(Spacing.md))
            DetailsDisclosure(
                expanded = detailsExpanded,
                onToggle = { detailsExpanded = !detailsExpanded },
                title = "清理詳細資訊",
            ) {
                CleaningDetailsContent(report)
                if (short != null) {
                    TechnicalDetails(short = short, showTechnicalInfo = state.showTechnicalInfo)
                }
            }

            if (short != null) {
                Spacer(Modifier.height(Spacing.xl))
                DiffUrlBox(
                    label = "短網址",
                    segments = listOf(UrlDiffSegment(short.shortUrl, removed = false)),
                    supportingText = "此短網址會導向${report.targetLabel()}。",
                    emphasized = true,
                    onCopy = { onCopyShortUrl(short.shortUrl) },
                    copyDescription = "複製短網址",
                )
            }

            Spacer(Modifier.height(Spacing.xl))
            when {
                short != null -> {
                    KuiesPrimaryButton(
                        text = "分享短網址",
                        onClick = { onShareShortUrl(short.shortUrl) },
                        icon = KuiesIconKind.Share,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    KuiesSecondaryButton(
                        text = "複製短網址",
                        onClick = { onCopyShortUrl(short.shortUrl) },
                        icon = KuiesIconKind.Copy,
                    )
                }

                report.outcome == CleaningOutcome.UNRESOLVED ||
                    report.outcome == CleaningOutcome.INVALID ||
                    report.outcome == CleaningOutcome.FAILED -> {
                    KuiesSecondaryButton(
                        text = "複製原始網址",
                        onClick = { onCopyUrl(clean.originalUrl) },
                        icon = KuiesIconKind.Copy,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    KuiesSecondaryButton(
                        text = "重新處理",
                        onClick = onRetryClean,
                        icon = KuiesIconKind.Refresh,
                    )
                    if (report.outcome == CleaningOutcome.UNRESOLVED && clean.isShareable) {
                        TextButton(onClick = { onShareUrl(clean.originalUrl) }) {
                            Text("分享原始網址")
                        }
                    }
                }

                else -> {
                    KuiesPrimaryButton(
                        text = if (state.isShortening) "建立中…" else "建立短網址",
                        onClick = onShorten,
                        icon = if (state.isShortening) null else KuiesIconKind.Link,
                        loading = state.isShortening,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    KuiesSecondaryButton(
                        text = "複製清理後網址",
                        onClick = { onCopyUrl(targetUrl ?: clean.cleanedUrl) },
                        icon = KuiesIconKind.Copy,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShortenErrorCard(
    error: ShortenError,
    onRetryShorten: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(
                text = stringResource(error.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (error.isRetryable()) {
                TextButton(onClick = onRetryShorten) { Text("重試") }
            }
        }
    }
}

@Composable
private fun TechnicalDetails(
    short: ShortenResponse,
    showTechnicalInfo: Boolean,
) {
    DetailRow("解析狀態", resolutionLabel(short))
    DetailRow("清理狀態", cleaningLabel(short))
    if (showTechnicalInfo) {
        DetailRow("processing_status", short.processingStatus)
        DetailRow("resolution_status", short.resolutionStatus)
        DetailRow("cleaning_status", short.cleaningStatus)
        short.cleaningRulesVersion?.let { DetailRow("清理規則版本", it) }
        short.cleaningScope?.let { DetailRow("清理範圍", it) }
    }
    val warningLabels = short.warnings.mapNotNull { warning -> warningLabel(warning) }
    if (warningLabels.isNotEmpty()) {
        DetailRow("提醒", warningLabels.joinToString("、"))
    }
}

private fun resolutionLabel(response: ShortenResponse): String = when (response.resolutionStatus) {
    "RESOLVED" -> "已還原包裝連結"
    "NOT_WRAPPER" -> "不需要還原"
    "UNRESOLVED_WRAPPER" -> "無法還原包裝連結"
    else -> "處理失敗"
}

private fun cleaningLabel(response: ShortenResponse): String = when (response.cleaningStatus) {
    ShortenResponse.STATUS_CLEANED -> "已清理"
    ShortenResponse.STATUS_UNCHANGED -> "未發現已知追蹤參數"
    ShortenResponse.STATUS_UNSUPPORTED -> "未支援此網站"
    else -> response.cleaningStatus
}

private fun warningLabel(warning: String): String? = when (warning) {
    "social_share_wrapper_unresolved_original_kept" -> "包裝連結未還原，已保留原始網址"
    "cleaning_rules_unavailable" -> "沒有可用的清理規則"
    "cleaning_scope_supported_params_only" -> "只清理已知的追蹤參數"
    else -> null
}
