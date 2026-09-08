package eu.darken.butler.workspace.ui.operations.details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Block
import androidx.compose.material.icons.twotone.CheckCircle
import androidx.compose.material.icons.twotone.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewWrapper as ComposePreviewWrapper
import androidx.compose.ui.unit.dp
import eu.darken.butler.common.ca.toCaString
import eu.darken.butler.common.compose.ButlerPreviewWrapper
import eu.darken.butler.common.compose.Preview2
import eu.darken.butler.common.compose.asComposable
import eu.darken.butler.workspace.R
import eu.darken.butler.workspace.core.operations.Operation
import java.io.IOException

@Composable
internal fun OperationPackagesSection(
    outcomes: List<Operation.Report.Packages.Outcome>,
) {
    OperationSection(
        title = stringResource(R.string.workspace_operation_packages_section_title),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(
                items = outcomes,
                key = { index, _ -> index },
            ) { _, outcome ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        imageVector = when (outcome.status) {
                            Operation.Report.Packages.Outcome.Status.DONE -> Icons.TwoTone.CheckCircle
                            Operation.Report.Packages.Outcome.Status.FAILED -> Icons.TwoTone.Error
                            Operation.Report.Packages.Outcome.Status.DECLINED -> Icons.TwoTone.Block
                        },
                        contentDescription = stringResource(outcome.status.labelRes),
                        tint = when (outcome.status) {
                            Operation.Report.Packages.Outcome.Status.DONE -> MaterialTheme.colorScheme.primary
                            Operation.Report.Packages.Outcome.Status.FAILED -> MaterialTheme.colorScheme.error
                            Operation.Report.Packages.Outcome.Status.DECLINED ->
                                MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(16.dp),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = outcome.label.asComposable(),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                        Text(
                            text = outcome.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.MiddleEllipsis,
                        )
                        outcome.error?.let { error ->
                            Text(
                                text = error.localizedMessage ?: error.javaClass.simpleName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview2
@ComposePreviewWrapper(ButlerPreviewWrapper::class)
@Composable
private fun OperationPackagesSectionPreview() {
    OperationPackagesSection(
        outcomes = listOf(
            Operation.Report.Packages.Outcome(
                label = "Chrome".toCaString(),
                packageName = "com.android.chrome",
                status = Operation.Report.Packages.Outcome.Status.DONE,
            ),
            Operation.Report.Packages.Outcome(
                label = "System UI".toCaString(),
                packageName = "com.android.systemui",
                status = Operation.Report.Packages.Outcome.Status.FAILED,
                error = IOException("Operation not permitted"),
            ),
            Operation.Report.Packages.Outcome(
                label = "Notes".toCaString(),
                packageName = "com.example.notes",
                status = Operation.Report.Packages.Outcome.Status.DECLINED,
            ),
        ),
    )
}
