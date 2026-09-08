package eu.darken.butler.explorer.ui.explorer.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewWrapper as ComposePreviewWrapper
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.darken.butler.common.compose.ButlerPreviewWrapper
import eu.darken.butler.common.compose.Preview2
import eu.darken.butler.common.compose.PreviewWrapper
import eu.darken.butler.common.files.LocalPath
import eu.darken.butler.common.formatRelativeTime
import eu.darken.butler.explorer.R
import eu.darken.butler.workspace.ui.bottomsheet.PaneScopedBottomSheet
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * What the sizes currently shown in the listing are, and the two things that can be done with them.
 */
@Composable
fun CalculatedSizesSheet(
    modifier: Modifier = Modifier,
    state: ExplorerDialogState.CalculatedSizes,
    onRecalculate: () -> Unit,
    onDiscard: () -> Unit,
    onDismiss: () -> Unit,
    topInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
) {
    PaneScopedBottomSheet(
        modifier = modifier,
        visible = true,
        onDismiss = onDismiss,
        topInset = topInset,
        bottomInset = bottomInset,
    ) {
        CalculatedSizesContent(
            state = state,
            onRecalculate = onRecalculate,
            onDiscard = onDiscard,
        )
    }
}

@Composable
private fun CalculatedSizesContent(
    modifier: Modifier = Modifier,
    state: ExplorerDialogState.CalculatedSizes,
    onRecalculate: () -> Unit,
    onDiscard: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(R.string.explorer_sizes_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = state.root.userReadablePath.get(context),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = stringResource(
                    R.string.explorer_sizes_sheet_calculated_at,
                    formatRelativeTime(state.scannedAt),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.explorer_sizes_sheet_folders,
                    state.directoryCount,
                    state.directoryCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.errorCount > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.explorer_sizes_sheet_unreadable,
                        state.errorCount,
                        state.errorCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            TextButton(onClick = onDiscard) {
                Text(stringResource(R.string.explorer_sizes_sheet_discard))
            }
            TextButton(onClick = onRecalculate) {
                Text(stringResource(R.string.explorer_sizes_sheet_recalculate))
            }
        }
    }
}

private fun previewState(errorCount: Int) = ExplorerDialogState.CalculatedSizes(
    root = LocalPath.build("/storage/emulated/0/Download"),
    scannedAt = Clock.System.now() - 5.minutes,
    directoryCount = 128,
    errorCount = errorCount,
)

@Preview2
@ComposePreviewWrapper(ButlerPreviewWrapper::class)
@Composable
private fun CalculatedSizesSheetPreview() {
    PreviewWrapper {
        CalculatedSizesContent(
            state = previewState(errorCount = 0),
            onRecalculate = {},
            onDiscard = {},
        )
    }
}

@Preview2
@ComposePreviewWrapper(ButlerPreviewWrapper::class)
@Composable
private fun CalculatedSizesSheetPartialPreview() {
    PreviewWrapper {
        CalculatedSizesContent(
            state = previewState(errorCount = 7),
            onRecalculate = {},
            onDiscard = {},
        )
    }
}
