package tw.kuies.link.ui

import androidx.annotation.StringRes
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
import tw.kuies.link.domain.CleaningStatus
import tw.kuies.link.ui.components.DetailRow
import tw.kuies.link.ui.components.DetailsDisclosure
import tw.kuies.link.ui.components.KuiesIconKind
import tw.kuies.link.ui.components.KuiesPrimaryButton
import tw.kuies.link.ui.components.KuiesSecondaryButton
import tw.kuies.link.ui.components.KuiesTopBar
import tw.kuies.link.ui.components.StatusSummaryCard
import tw.kuies.link.ui.components.StatusVisual
import tw.kuies.link.ui.components.UrlBox
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
    var detailsExpanded by rememberSaveable(clean.cleanedUrl, short?.shortUrl) {
        mutableStateOf(state.expandDetailsByDefault)
    }

    val unresolved = clean.status == CleaningStatus.UNRESOLVED_WRAPPER
    val failed = clean.status == CleaningStatus.INVALID_URL || clean.status == CleaningStatus.FAILED
    val success = when {
        short != null -> short.cleaningStatus == ShortenResponse.STATUS_CLEANED
        else -> clean.status == CleaningStatus.CLEANED
    }
    val icon = when {
        success -> KuiesIconKind.CheckCircle
        unresolved -> KuiesIconKind.LinkOff
        else -> KuiesIconKind.Info
    }
    val title = when {
        unresolved -> "暫時無法還原連結"
        failed -> "無法處理這個網址"
        short != null && success -> "已建立乾淨短網址"
        short != null -> "已建立短網址"
        success -> "已清理網址"
        else -> "未發現已知追蹤參數"
    }
    val subtitle = when {
        unresolved -> "目前無法還原這個分享連結，你可以使用原始網址，或稍後再試。"
        failed -> "這個網址目前無法處理，請確認格式後再試一次。"
        short != null && success -> "已依目前支援規則完成清理，可以直接分享這個連結。"
        short != null && short.cleaningStatus == ShortenResponse.STATUS_UNSUPPORTED ->
            "這個網站不在目前的清理規則內，網址可能仍含追蹤參數。"
        short != null -> "目前沒有需要移除的已知參數。"
        success -> "已依目前支援規則完成清理，可以建立短網址分享。"
        else -> "目前沒有需要移除的已知參數。"
    }
    val shownUrl = when {
        short != null -> short.shortUrl
        unresolved -> clean.originalUrl
        else -> clean.cleanedUrl
    }
    val removedCount = short?.removedCount ?: clean.removedCount
    val removedNames = short?.removedParams ?: clean.removedParameters.map { it.name }
    val statusTitle = when {
        unresolved -> "無法還原包裝連結"
        failed -> "無法處理這個網址"
        short?.cleaningStatus == ShortenResponse.STATUS_CLEANED -> "已完成清理"
        short?.cleaningStatus == ShortenResponse.STATUS_UNSUPPORTED -> "未支援此網站"
        short != null -> "未發現已知追蹤參數"
        clean.status == CleaningStatus.CLEANED -> "已完成清理"
        else -> "未發現已知追蹤參數"
    }
    val statusSubtitle = when {
        unresolved -> "目前無法取得原始內容網址"
        failed -> "原始網址已保留"
        short?.cleaningStatus == ShortenResponse.STATUS_CLEANED -> "已移除 $removedCount 個追蹤參數"
        short?.cleaningStatus == ShortenResponse.STATUS_UNSUPPORTED -> "只建立短網址，未變更內容"
        clean.status == CleaningStatus.CLEANED -> "已移除 $removedCount 個追蹤參數"
        else -> "目前沒有需要移除的已知參數"
    }

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
                title = title,
                subtitle = subtitle,
                success = success,
            )
            Spacer(Modifier.height(Spacing.xl))
            UrlBox(
                url = shownUrl,
                label = if (short != null) "短網址" else if (unresolved) "原始網址" else "清理後的網址",
                emphasized = short != null,
                onCopy = {
                    if (short != null) onCopyShortUrl(short.shortUrl) else onCopyUrl(shownUrl)
                },
            )

            if (state.shortenError != null) {
                Spacer(Modifier.height(Spacing.md))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        Text(
                            text = stringResource(state.shortenError.messageRes()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        if (state.shortenError.isRetryable()) {
                            TextButton(onClick = onRetryShorten) { Text("重試") }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            when {
                unresolved || failed -> {
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
                    if (unresolved && clean.isShareable) {
                        TextButton(onClick = { onShareUrl(clean.originalUrl) }) {
                            Text("分享原始網址")
                        }
                    }
                }

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

                else -> {
                    KuiesPrimaryButton(
                        text = if (state.isShortening) "建立中…" else "建立短網址",
                        onClick = onShorten,
                        icon = if (state.isShortening) null else KuiesIconKind.Link,
                        loading = state.isShortening,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    KuiesSecondaryButton(
                        text = "複製此網址",
                        onClick = { onCopyUrl(clean.cleanedUrl) },
                        icon = KuiesIconKind.Copy,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xl))
            StatusSummaryCard(
                icon = icon,
                label = "清理狀態",
                title = statusTitle,
                subtitle = statusSubtitle,
                onClick = { detailsExpanded = !detailsExpanded },
            )

            DetailsDisclosure(
                expanded = detailsExpanded,
                onToggle = { detailsExpanded = !detailsExpanded },
            ) {
                DetailRow("原始網址", clean.originalUrl)
                DetailRow("清理後網址", clean.cleanedUrl)
                DetailRow(
                    "移除的參數 ($removedCount)",
                    removedNames.ifEmpty { listOf("無") }.joinToString("、"),
                )
                if (short != null) {
                    DetailRow("解析狀態", resolutionLabel(short))
                    DetailRow("清理狀態", cleaningLabel(short))
                    if (state.showTechnicalInfo) {
                        DetailRow("processing_status", short.processingStatus)
                        DetailRow("resolution_status", short.resolutionStatus)
                        DetailRow("cleaning_status", short.cleaningStatus)
                        short.cleaningRulesVersion?.let { DetailRow("清理規則版本", it) }
                        short.cleaningScope?.let { DetailRow("清理範圍", it) }
                    }
                    val warningLabels = short.warnings.mapNotNull { warning ->
                        warningLabel(warning)
                    }
                    if (warningLabels.isNotEmpty()) {
                        DetailRow("提醒", warningLabels.joinToString("、"))
                    }
                }
            }
        }
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
