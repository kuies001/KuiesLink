package tw.kuies.link.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import tw.kuies.link.data.ShortenResponse
import tw.kuies.link.domain.CleaningResult
import tw.kuies.link.domain.CleaningStatus
import tw.kuies.link.domain.RemovedParameter
import tw.kuies.link.ui.theme.KuiesLinkTheme

private val previewClean = CleaningResult(
    originalUrl = "https://shop.example.com/item?utm_source=newsletter&utm_medium=email&sku=42&color=red#details",
    cleanedUrl = "https://shop.example.com/item?sku=42&color=red#details",
    status = CleaningStatus.CLEANED,
    removedParameters = listOf(
        RemovedParameter(
            name = "utm_source",
            value = "newsletter",
            raw = "utm_source=newsletter",
            ruleSource = "UTM",
        ),
        RemovedParameter(
            name = "utm_medium",
            value = "email",
            raw = "utm_medium=email",
            ruleSource = "UTM",
        ),
    ),
    host = "shop.example.com",
)

private val previewShort = ShortenResponse(
    shortUrl = "https://u.kuies.tw/preview",
    targetUrl = previewClean.cleanedUrl,
    processingStatus = ShortenResponse.STATUS_CLEANED,
    resolutionStatus = "NOT_WRAPPER",
    cleaningStatus = ShortenResponse.STATUS_CLEANED,
    removedCount = 2,
    removedParams = listOf("utm_source", "utm_medium"),
    warnings = emptyList(),
    cleaningRulesVersion = "preview",
    cleaningScope = "known_params",
)

@Preview(name = "Result – light", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun ResultScreenPreviewLight() {
    KuiesLinkTheme(darkTheme = false) {
        ResultScreen(
            state = LinkUiState(
                result = previewClean,
                shortResult = previewShort,
                settingsLoaded = true,
            ),
            contentPadding = PaddingValues(),
            onBack = {},
            onCopyUrl = {},
            onShareUrl = {},
            onShorten = {},
            onRetryShorten = {},
            onRetryClean = {},
            onCopyShortUrl = {},
            onShareShortUrl = {},
        )
    }
}

@Preview(name = "Result – dark", showBackground = true, widthDp = 390, heightDp = 900)
@Composable
private fun ResultScreenPreviewDark() {
    KuiesLinkTheme(darkTheme = true) {
        ResultScreen(
            state = LinkUiState(
                result = previewClean,
                settingsLoaded = true,
            ),
            contentPadding = PaddingValues(),
            onBack = {},
            onCopyUrl = {},
            onShareUrl = {},
            onShorten = {},
            onRetryShorten = {},
            onRetryClean = {},
            onCopyShortUrl = {},
            onShareShortUrl = {},
        )
    }
}

@Preview(name = "Dialog – light", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun ShareDialogPreviewLight() {
    KuiesLinkTheme(darkTheme = false) {
        ShareDialogScreen(
            state = LinkUiState(
                result = previewClean,
                shortResult = previewShort,
                settingsLoaded = true,
            ),
            onCopyShortUrl = {},
            onShareShortUrl = {},
            onFinish = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "Dialog – dark", showBackground = true, widthDp = 390, heightDp = 780)
@Composable
private fun ShareDialogPreviewDark() {
    KuiesLinkTheme(darkTheme = true) {
        ShareDialogScreen(
            state = LinkUiState(
                result = previewClean,
                shortResult = previewShort.copy(
                    cleaningStatus = ShortenResponse.STATUS_UNSUPPORTED,
                    processingStatus = ShortenResponse.STATUS_UNSUPPORTED,
                    removedCount = 0,
                    removedParams = emptyList(),
                ),
                settingsLoaded = true,
            ),
            onCopyShortUrl = {},
            onShareShortUrl = {},
            onFinish = {},
            modifier = Modifier.fillMaxSize(),
        )
    }
}
