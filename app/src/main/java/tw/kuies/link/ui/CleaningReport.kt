package tw.kuies.link.ui

import tw.kuies.link.data.ShortenResponse
import tw.kuies.link.domain.CleaningResult
import tw.kuies.link.domain.CleaningStatus
import tw.kuies.link.domain.RemovalReason
import tw.kuies.link.domain.UrlDiff
import tw.kuies.link.domain.UrlDiffAnalyzer
import tw.kuies.link.domain.UrlDiffAvailability
import tw.kuies.link.domain.UrlParameterDetail

/**
 * Normalised view of a cleaning result. [CLEANED] is only chosen when a parameter removal can be
 * observed; a successful short URL on its own is never treated as cleaning evidence.
 */
enum class CleaningOutcome {
    CLEANED,
    RESOLVED,
    UNCHANGED,
    UNSUPPORTED,
    UNRESOLVED,
    INVALID,
    FAILED,
    INCONCLUSIVE,
}

data class CleaningReport(
    val outcome: CleaningOutcome,
    val originalUrl: String,
    /** Destination shown to the user; null when no trustworthy processed URL exists. */
    val targetUrl: String?,
    val diff: UrlDiff,
    val removedCount: Int,
    val serverResult: Boolean,
    val serverReportedRemovedCount: Int?,
    val countMismatch: Boolean,
    val localResultDiffers: Boolean,
    val resolvedWrapper: Boolean,
) {
    val canClaimRemoval: Boolean
        get() = outcome == CleaningOutcome.CLEANED && removedCount > 0
}

/**
 * Builds the display model from the local result and, when present, the server's short-URL
 * response. Server data is authoritative for the target URL and cleaning status because that is
 * what the short URL actually redirects to.
 */
fun buildCleaningReport(
    clean: CleaningResult,
    short: ShortenResponse?,
): CleaningReport = if (short != null) {
    buildServerReport(clean, short)
} else {
    buildLocalReport(clean)
}

private fun buildServerReport(
    clean: CleaningResult,
    short: ShortenResponse,
): CleaningReport {
    val parsedDiff = UrlDiffAnalyzer.compare(clean.originalUrl, short.targetUrl)
    val diff = parsedDiff.withServerRemovedNames(short.removedParams)
    val resolvedWrapper =
        short.resolutionStatus == ShortenResponse.RESOLVED &&
            diff.baseChanged
    val parameterLevel = diff.availability == UrlDiffAvailability.PARAMETER_LEVEL
    val removedCount = when {
        short.cleaningStatus == ShortenResponse.STATUS_UNSUPPORTED -> 0
        parameterLevel -> diff.removed.size
        else -> short.removedCount
    }

    val outcome = when (short.cleaningStatus) {
        ShortenResponse.STATUS_CLEANED -> when {
            parameterLevel && diff.removed.isNotEmpty() -> CleaningOutcome.CLEANED
            parameterLevel -> CleaningOutcome.INCONCLUSIVE
            resolvedWrapper && short.removedCount > 0 -> CleaningOutcome.CLEANED
            resolvedWrapper -> CleaningOutcome.RESOLVED
            short.removedCount > 0 -> CleaningOutcome.CLEANED
            else -> CleaningOutcome.INCONCLUSIVE
        }

        ShortenResponse.STATUS_UNCHANGED ->
            if (resolvedWrapper) CleaningOutcome.RESOLVED else CleaningOutcome.UNCHANGED

        ShortenResponse.STATUS_UNSUPPORTED -> CleaningOutcome.UNSUPPORTED
        else -> CleaningOutcome.INCONCLUSIVE
    }

    return CleaningReport(
        outcome = outcome,
        originalUrl = clean.originalUrl,
        targetUrl = short.targetUrl,
        diff = diff,
        removedCount = removedCount,
        serverResult = true,
        serverReportedRemovedCount = short.removedCount,
        countMismatch = parameterLevel && short.removedCount != diff.removed.size,
        localResultDiffers = clean.cleanedUrl != short.targetUrl,
        resolvedWrapper = resolvedWrapper,
    )
}

private fun buildLocalReport(clean: CleaningResult): CleaningReport {
    val diff = UrlDiffAnalyzer.compare(
        originalUrl = clean.originalUrl,
        cleanedUrl = clean.cleanedUrl,
        localRemoved = clean.removedParameters,
    )
    val outcome = when (clean.status) {
        CleaningStatus.CLEANED ->
            if (diff.removed.isNotEmpty()) CleaningOutcome.CLEANED else CleaningOutcome.INCONCLUSIVE

        CleaningStatus.UNCHANGED -> CleaningOutcome.UNCHANGED
        CleaningStatus.UNRESOLVED_WRAPPER -> CleaningOutcome.UNRESOLVED
        CleaningStatus.INVALID_URL -> CleaningOutcome.INVALID
        CleaningStatus.FAILED -> CleaningOutcome.FAILED
    }
    val hasTarget = outcome == CleaningOutcome.CLEANED ||
        outcome == CleaningOutcome.UNCHANGED ||
        outcome == CleaningOutcome.INCONCLUSIVE

    return CleaningReport(
        outcome = outcome,
        originalUrl = clean.originalUrl,
        targetUrl = if (hasTarget) clean.cleanedUrl else null,
        diff = diff,
        removedCount = diff.removed.size,
        serverResult = false,
        serverReportedRemovedCount = null,
        countMismatch = false,
        localResultDiffers = false,
        resolvedWrapper = clean.status == CleaningStatus.UNRESOLVED_WRAPPER,
    )
}

data class CleaningSummaryText(
    val title: String,
    val subtitle: String,
)

/** Shared wording so the dialog and the full result screen never contradict each other. */
fun CleaningReport.summaryText(shortUrlCreated: Boolean): CleaningSummaryText = when (outcome) {
    CleaningOutcome.CLEANED -> CleaningSummaryText(
        title = if (shortUrlCreated) "已建立乾淨短網址" else "已清理網址",
        subtitle = "已移除 $removedCount 個追蹤參數",
    )

    CleaningOutcome.RESOLVED -> CleaningSummaryText(
        title = if (shortUrlCreated) "已還原連結並建立短網址" else "已還原連結",
        subtitle = "包裝連結已還原；伺服器未回報移除追蹤參數",
    )

    CleaningOutcome.UNCHANGED -> CleaningSummaryText(
        title = if (shortUrlCreated) "無須清理，已建立短網址" else "未發現已知追蹤參數",
        subtitle = "目前沒有需要移除的已知參數",
    )

    CleaningOutcome.UNSUPPORTED -> CleaningSummaryText(
        title = if (shortUrlCreated) "已建立短網址" else "未支援此網站",
        subtitle = "這個網站不在目前的清理規則內，無法確認清理結果",
    )

    CleaningOutcome.UNRESOLVED -> CleaningSummaryText(
        title = "暫時無法還原連結",
        subtitle = "目前無法取得原始內容網址",
    )

    CleaningOutcome.INVALID -> CleaningSummaryText(
        title = "無法處理這個網址",
        subtitle = "請確認是完整的 HTTP 或 HTTPS 網址",
    )

    CleaningOutcome.FAILED -> CleaningSummaryText(
        title = "清理未完成",
        subtitle = "原始網址已保留",
    )

    CleaningOutcome.INCONCLUSIVE -> CleaningSummaryText(
        title = if (shortUrlCreated) "已建立短網址" else "無法確認清理結果",
        subtitle = "伺服器回報已清理，但沒有可確認的網址變更",
    )
}

fun CleaningReport.targetLabel(): String = when {
    outcome == CleaningOutcome.UNSUPPORTED -> "目標網址（未清理）"
    outcome == CleaningOutcome.INCONCLUSIVE -> "目標網址"
    resolvedWrapper -> "還原後的網址"
    else -> "清理後網址"
}

fun CleaningReport.targetNote(): String? = when {
    outcome == CleaningOutcome.UNSUPPORTED ->
        "此網站未支援清理，目標網址仍可能包含追蹤參數。"

    outcome == CleaningOutcome.UNCHANGED -> "與原始網址相同。"
    outcome == CleaningOutcome.RESOLVED -> "分享平台包裝連結已還原。"
    outcome == CleaningOutcome.INCONCLUSIVE -> "伺服器未回傳可逐項比對的變更。"
    diff.fragmentChanged -> "Fragment 與原始網址不同。"
    else -> null
}

/**
 * A resolved wrapper changes the host/path, so parameters cannot be matched against the original
 * share link. The backend still reports the names it removed; show those names without inventing
 * values or reasons.
 */
private fun UrlDiff.withServerRemovedNames(names: List<String>): UrlDiff {
    if (availability != UrlDiffAvailability.STRUCTURAL || names.isEmpty()) return this
    val counts = names.groupingBy { it }.eachCount()
    return copy(
        removed = names.map { name ->
            UrlParameterDetail(
                name = name,
                value = null,
                hasValue = false,
                valueKnown = false,
                repeated = (counts[name] ?: 0) > 1,
                ruleSource = null,
                removalReason = RemovalReason.SERVER_REPORTED,
            )
        },
    )
}
