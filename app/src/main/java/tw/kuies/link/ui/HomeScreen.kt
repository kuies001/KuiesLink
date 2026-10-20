package tw.kuies.link.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import tw.kuies.link.R
import tw.kuies.link.domain.CleaningResult
import tw.kuies.link.domain.CleaningStatus
import tw.kuies.link.domain.RemovedParameter
import tw.kuies.link.settings.ShareMode
import tw.kuies.link.ui.theme.KuiesLinkTheme

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
    onCopyUrl: (String) -> Unit,
    onShareUrl: (String) -> Unit,
    onRestoreOriginal: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            actions = {
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.home_settings))
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            // The Scaffold already applies the status-bar inset around its content.
            windowInsets = WindowInsets(0, 0, 0, 0),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.home_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            ModeBadge(mode = state.shareMode, onOpenSettings = onOpenSettings)
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = state.input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.home_input_label)) },
                placeholder = {
                    Text(
                        text = stringResource(R.string.home_input_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                minLines = 2,
                maxLines = 5,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onClean() }),
                trailingIcon = {
                    if (state.input.isNotEmpty()) {
                        TextButton(onClick = onClearInput) {
                            Text(stringResource(R.string.home_clear))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onPaste,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.home_paste))
                }
                Button(
                    onClick = onClean,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = !state.isCleaning,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    if (state.isCleaning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_cleaning))
                    } else {
                        Text(stringResource(R.string.home_clean))
                    }
                }
            }

            if (state.candidates.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                CandidateList(urls = state.candidates, onSelect = onSelectCandidate)
            }

            state.result?.let { result ->
                Spacer(Modifier.height(22.dp))
                ResultCard(
                    result = result,
                    onCopy = onCopyUrl,
                    onShare = onShareUrl,
                    onRestore = onRestoreOriginal,
                )
            }
        }
    }
}

@Composable
private fun ModeBadge(mode: ShareMode, onOpenSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = stringResource(
                    if (mode == ShareMode.QUICK) R.string.home_mode_quick else R.string.home_mode_confirm,
                ),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        TextButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.home_mode_change))
        }
    }
}

@Composable
private fun CandidateList(urls: List<String>, onSelect: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.home_candidates_title, urls.size),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = stringResource(R.string.home_candidates_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            urls.forEach { url ->
                Surface(
                    onClick = { onSelect(url) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Text(
                        text = url,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    result: CleaningResult,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    onRestore: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = result.host ?: stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(8.dp))
            StatusPill(result)
            Spacer(Modifier.height(16.dp))

            UrlBox(
                label = stringResource(R.string.result_original_label),
                url = result.originalUrl,
                emphasized = false,
            )
            Spacer(Modifier.height(12.dp))
            UrlBox(
                label = stringResource(R.string.result_cleaned_label),
                url = result.cleanedUrl,
                emphasized = result.hasChange,
            )

            if (result.removedCount > 0) {
                Spacer(Modifier.height(4.dp))
                RemovedParameterList(result)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onCopy(result.cleanedUrl) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = result.isShareable,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.result_copy))
                }
                FilledTonalButton(
                    onClick = { onShare(result.cleanedUrl) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    enabled = result.isShareable,
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.result_share))
                }
            }

            if (result.hasChange) {
                TextButton(onClick = onRestore) {
                    Text(stringResource(R.string.result_restore))
                }
            }
        }
    }
}

@Composable
private fun StatusPill(result: CleaningResult) {
    val container: Color
    val content: Color
    when (result.status) {
        CleaningStatus.CLEANED -> {
            container = MaterialTheme.colorScheme.primaryContainer
            content = MaterialTheme.colorScheme.onPrimaryContainer
        }

        CleaningStatus.UNCHANGED -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
        }

        CleaningStatus.UNRESOLVED_WRAPPER -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
        }

        CleaningStatus.INVALID_URL, CleaningStatus.FAILED -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
        }
    }

    Surface(shape = RoundedCornerShape(50), color = container, contentColor = content) {
        Text(
            text = when (result.status) {
                CleaningStatus.CLEANED -> stringResource(R.string.result_removed_count, result.removedCount)
                CleaningStatus.UNCHANGED -> stringResource(R.string.result_already_clean)
                CleaningStatus.UNRESOLVED_WRAPPER -> stringResource(R.string.result_unresolved_wrapper)
                CleaningStatus.INVALID_URL -> stringResource(R.string.result_invalid_url)
                CleaningStatus.FAILED -> stringResource(R.string.result_failed)
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun UrlBox(label: String, url: String, emphasized: Boolean) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = if (emphasized) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            },
        ) {
            SelectionContainer {
                Text(
                    text = url,
                    modifier = Modifier.padding(12.dp),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    ),
                )
            }
        }
    }
}

@Composable
private fun RemovedParameterList(result: CleaningResult) {
    var expanded by rememberSaveable(result.cleanedUrl) { mutableStateOf(false) }

    TextButton(
        onClick = { expanded = !expanded },
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Text(stringResource(R.string.result_removed_title) + "（${result.removedCount}）")
        Text(
            text = if (expanded) "▴" else "▾",
            modifier = Modifier.padding(start = 4.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }

    AnimatedVisibility(visible = expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            result.removedParameters.forEach { parameter ->
                RemovedParameterRow(parameter)
            }
        }
    }
}

@Composable
private fun RemovedParameterRow(parameter: RemovedParameter) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = parameter.name,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = " = ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = parameter.value?.ifEmpty { stringResource(R.string.result_empty_value) }
                ?: stringResource(R.string.result_empty_value),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(name = "首頁", showBackground = true)
@Composable
private fun HomeScreenPreview() {
    KuiesLinkTheme {
        HomeScreen(
            state = LinkUiState(
                input = "https://www.instagram.com/p/CxYz123/?igsh=MW9xZ2F0",
                shareMode = ShareMode.CONFIRM,
                result = CleaningResult(
                    originalUrl = "https://www.instagram.com/p/CxYz123/?igsh=MW9xZ2F0&utm_source=ig_web",
                    cleanedUrl = "https://www.instagram.com/p/CxYz123/",
                    status = CleaningStatus.CLEANED,
                    removedParameters = listOf(
                        RemovedParameter("igsh", "MW9xZ2F0", "igsh=MW9xZ2F0", "Instagram"),
                        RemovedParameter("utm_source", "ig_web", "utm_source=ig_web", "UTM"),
                    ),
                    host = "instagram.com",
                ),
            ),
            contentPadding = PaddingValues(0.dp),
            onInputChange = {},
            onPaste = {},
            onClean = {},
            onClearInput = {},
            onSelectCandidate = {},
            onCopyUrl = {},
            onShareUrl = {},
            onRestoreOriginal = {},
            onOpenSettings = {},
        )
    }
}
