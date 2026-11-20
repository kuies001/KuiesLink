package tw.kuies.link.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import tw.kuies.link.domain.RemovalReason
import tw.kuies.link.domain.UrlDiffAvailability
import tw.kuies.link.domain.UrlDiffSegment
import tw.kuies.link.domain.UrlParameterDetail
import tw.kuies.link.ui.CleaningOutcome
import tw.kuies.link.ui.CleaningReport
import tw.kuies.link.ui.theme.Spacing
import tw.kuies.link.ui.theme.UrlTextStyle

/**
 * URL box that keeps the original text intact and paints only the parameters that were actually
 * removed. The highlight comes from parsed parameters, never from substring replacement.
 */
@Composable
fun DiffUrlBox(
    label: String,
    segments: List<UrlDiffSegment>,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    emphasized: Boolean = false,
    onCopy: (() -> Unit)? = null,
    copyDescription: String = "複製網址",
) {
    val removedBackground = MaterialTheme.colorScheme.errorContainer
    val removedColor = MaterialTheme.colorScheme.onErrorContainer
    val annotated = remember(segments, removedBackground, removedColor) {
        buildAnnotatedString {
            segments.forEach { segment ->
                if (segment.removed) {
                    withStyle(
                        SpanStyle(
                            background = removedBackground,
                            color = removedColor,
                            fontWeight = FontWeight.SemiBold,
                        ),
                    ) {
                        append(segment.text)
                    }
                } else {
                    append(segment.text)
                }
            }
        }
    }
    val hasRemoved = segments.any { it.removed }

    Column(modifier = modifier.fillMaxWidth()) {
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
            border = if (emphasized) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
            } else {
                null
            },
        ) {
            Row(
                modifier = Modifier.padding(
                    start = Spacing.lg,
                    end = Spacing.sm,
                    top = Spacing.md,
                    bottom = Spacing.md,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SelectionContainer(modifier = Modifier.weight(1f)) {
                    Text(
                        text = annotated,
                        style = UrlTextStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        softWrap = true,
                    )
                }
                if (onCopy != null) {
                    IconButton(onClick = onCopy) {
                        KuiesIcon(
                            KuiesIconKind.Copy,
                            size = 20.dp,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            contentDescription = copyDescription,
                        )
                    }
                }
            }
        }
        if (supportingText != null || hasRemoved) {
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasRemoved) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.errorContainer),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                }
                Text(
                    text = supportingText ?: "紅色標示為已移除的追蹤參數",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Removed and kept parameters for the expandable details area. Values are decoded for reading but
 * the URLs above keep their original encoding.
 */
@Composable
fun CleaningDetailsContent(
    report: CleaningReport,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        when {
            report.outcome == CleaningOutcome.UNRESOLVED ||
                report.outcome == CleaningOutcome.INVALID ||
                report.outcome == CleaningOutcome.FAILED -> {
                InfoNote("目前沒有足夠資訊可比對清理前後的參數。")
            }

            report.outcome == CleaningOutcome.UNSUPPORTED -> {
                WarningNote(
                    "此網站不在目前的清理規則內，未移除的參數可能仍包含追蹤資訊。",
                )
                InfoNote("無法確認哪些參數是追蹤參數，也不宣稱任何參數已移除。")
            }

            report.diff.availability == UrlDiffAvailability.STRUCTURAL -> {
                InfoNote(
                    if (report.resolvedWrapper) {
                        "分享平台包裝連結已還原，無法逐項比對原始分享連結中的參數。"
                    } else {
                        "目標網址的網域或路徑已變更，無法逐項比對參數。"
                    },
                )
                if (report.diff.removed.isNotEmpty()) {
                    ParameterGroup(
                        title = "伺服器回報移除的參數",
                        emptyText = "伺服器未提供移除的參數名稱。",
                        parameters = report.diff.removed,
                        showReason = true,
                    )
                    val reported = report.serverReportedRemovedCount
                    if (reported != null && reported != report.diff.removed.size) {
                        InfoNote(
                            "伺服器回報移除 $reported 個參數，" +
                                "提供 ${report.diff.removed.size} 個名稱。",
                        )
                    }
                } else {
                    InfoNote("伺服器未提供可逐項確認的移除參數。")
                }
                InfoNote("保留的參數無法逐項比對，因此不列為已確認安全。")
            }

            report.diff.availability == UrlDiffAvailability.UNAVAILABLE -> {
                InfoNote("無法解析網址，因此無法比對參數。")
            }

            else -> {
                if (report.diff.fragmentChanged) {
                    InfoNote("Fragment 與原始網址不同；查詢參數仍已逐項比對。")
                }
                ParameterGroup(
                    title = "已移除的參數",
                    emptyText = "沒有移除任何參數。",
                    parameters = report.diff.removed,
                    showReason = true,
                )
                ParameterGroup(
                    title = "保留的參數",
                    emptyText = "沒有保留的查詢參數。",
                    parameters = report.diff.kept,
                    showReason = false,
                    note = "此參數未被目前的清理規則移除。未移除不代表已確認安全。",
                )
            }
        }

        if (report.countMismatch) {
            InfoNote(
                "伺服器回報移除 ${report.serverReportedRemovedCount} 個參數，" +
                    "本機比對結果為 ${report.removedCount} 個。",
            )
        }
        if (report.localResultDiffers) {
            InfoNote("伺服器清理結果與本機結果不同，上方網址以伺服器實際回傳的目標為準。")
        }
    }
}

@Composable
private fun ParameterGroup(
    title: String,
    emptyText: String,
    parameters: List<UrlParameterDetail>,
    showReason: Boolean,
    note: String? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "$title（${parameters.size}）",
            style = MaterialTheme.typography.titleSmall,
        )
        if (note != null && parameters.isNotEmpty()) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (parameters.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            parameters.forEach { parameter ->
                ParameterCard(parameter = parameter, showReason = showReason)
            }
        }
    }
}

@Composable
private fun ParameterCard(
    parameter: UrlParameterDetail,
    showReason: Boolean,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = parameter.name.ifEmpty { "（未命名參數）" },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (parameter.repeated) {
                    Text(
                        text = "重複",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = "值：${parameter.displayValue()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            if (showReason) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "原因：${parameter.removalReasonText()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

private fun UrlParameterDetail.displayValue(): String = when {
    !valueKnown -> "（伺服器未提供）"
    !hasValue || value == null -> "（無值）"
    value.isEmpty() -> "（空值）"
    else -> value
}

private fun UrlParameterDetail.removalReasonText(): String = when (removalReason) {
    RemovalReason.SERVER_REPORTED -> "無法確認（伺服器未提供個別原因）"
    RemovalReason.RULE -> ruleLabel(ruleSource)
    null -> "無法確認"
}

private fun ruleLabel(ruleSource: String?): String = when (ruleSource) {
    null, "" -> "符合目前的本機清理規則"
    "UTM" -> "符合 UTM 廣告參數規則"
    "Google Ads" -> "符合 Google Ads 點擊識別規則"
    "Microsoft Ads" -> "符合 Microsoft Ads 點擊識別規則"
    "Meta" -> "符合 Meta 分享識別規則"
    "Threads" -> "符合 Threads 分享識別規則"
    "Instagram" -> "符合 Instagram 分享識別規則"
    "Instagram Reel" -> "Instagram Reel 分享連結的查詢參數"
    "Instagram Post" -> "Instagram 貼文分享連結的查詢參數"
    "Facebook" -> "符合 Facebook 分享連結規則"
    else -> "符合 $ruleSource 清理規則"
}

@Composable
private fun InfoNote(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(Spacing.md),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WarningNote(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(Spacing.md),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
