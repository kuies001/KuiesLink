package tw.kuies.link.ui

import androidx.annotation.StringRes
import tw.kuies.link.R
import tw.kuies.link.data.ShortenError

/** Shared by the full result screen and the external-share dialog. */
@StringRes
internal fun ShortenError.messageRes(): Int = when (this) {
    ShortenError.INVALID_REQUEST -> R.string.error_invalid_request
    ShortenError.WRAPPER_UNRESOLVED -> R.string.error_wrapper_unresolved
    ShortenError.RATE_LIMITED -> R.string.error_rate_limited
    ShortenError.SERVER_ERROR -> R.string.error_server
    ShortenError.NETWORK -> R.string.error_network
    ShortenError.TIMEOUT -> R.string.error_timeout
    ShortenError.TLS -> R.string.error_tls
    ShortenError.INVALID_RESPONSE -> R.string.error_invalid_response
    ShortenError.UNVERIFIED -> R.string.error_unverified
    ShortenError.CANCELLED -> R.string.error_cancelled
}

internal fun ShortenError.isRetryable(): Boolean =
    this != ShortenError.INVALID_REQUEST && this != ShortenError.WRAPPER_UNRESOLVED
