package eu.darken.butler.explorer.core.operations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.DataUsage
import androidx.compose.ui.graphics.vector.ImageVector
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import eu.darken.butler.common.ca.CaString
import eu.darken.butler.common.ca.caString
import eu.darken.butler.common.ca.toCaString
import eu.darken.butler.common.coroutine.DispatcherProvider
import eu.darken.butler.common.debug.logging.Logging.Priority.*
import eu.darken.butler.common.debug.logging.log
import eu.darken.butler.common.debug.logging.logTag
import eu.darken.butler.common.files.APath
import eu.darken.butler.common.files.APathGateway
import eu.darken.butler.common.files.APathLookup
import eu.darken.butler.common.files.GatewaySwitch
import eu.darken.butler.common.files.LookupOptions
import eu.darken.butler.common.getQuantityString2
import eu.darken.butler.common.progress.Progress
import eu.darken.butler.explorer.R
import eu.darken.butler.explorer.core.sizes.DirectorySizeAggregator
import eu.darken.butler.explorer.core.sizes.DirectorySizeStore
import eu.darken.butler.workspace.core.Workspace
import eu.darken.butler.workspace.core.operations.Operation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.time.Clock

class CalculateSizesOperation @AssistedInject constructor(
    @Assisted workspaceId: Workspace.Id,
    @Assisted private val command: ExplorerCommand.CalculateSizes,
    @Assisted store: DirectorySizeStore,
    private val gatewaySwitch: GatewaySwitch,
    private val dispatcherProvider: DispatcherProvider,
) : ExplorerOperation() {

    private val tag = logTag("Explorer", "Workspace", workspaceId.shortTag, "Operation", "CalculateSizes")

    /** Dropped once this operation terminates so a retained receipt can't keep the tab's sizes alive. */
    private var resultStore: DirectorySizeStore? = store

    override val metadata: Operation.Metadata = object : Operation.Metadata {
        override val origin = Operation.Metadata.Origin.Explorer(workspaceId)
        override val icon: ImageVector = Icons.TwoTone.DataUsage
        override val title = R.string.explorer_operation_calculate_sizes_title.toCaString()
        override val description = caString {
            it.getString(
                R.string.explorer_operation_calculate_sizes_description,
                command.directory.userReadablePath.get(it),
            )
        }
    }

    override fun perform(
        operationContext: Operation.Context
    ): Flow<State> = channelFlow {
        log(tag) { "perform(): $command" }
        val root = command.directory

        var active = State.Active(
            startedAt = operationContext.startedAt,
            primaryProgress = Progress.Data(
                primary = metadata.title,
                secondary = scannedLabel(0),
                count = Progress.Count.Indeterminate(),
            ),
        )
        send(active)

        val aggregator = DirectorySizeAggregator(root)
        // The walker invokes onError from its own context while entries are consumed in ours, so
        // failures are queued and folded in by the collector instead of racing it.
        val errors = ConcurrentLinkedQueue<APathLookup<*>>()

        val gateway = gatewaySwitch.getGateway(root)

        @Suppress("UNCHECKED_CAST")
        val typedGateway = gateway as APathGateway<APath<*>, APathLookup<APath<*>>>

        // No onFilter: every directory is traversed anyway, and its absence keeps escalated
        // subtrees on the host-side streaming walk instead of per-directory IPC.
        val walkOptions = APathGateway.WalkOptions<APath<*>, APathLookup<APath<*>>>(
            onError = { lookup, error ->
                log(tag, VERBOSE) { "Error accessing ${lookup.lookedUp}: $error" }
                errors.add(lookup)
                true
            },
        )

        typedGateway.walk(root, LOOKUP_PROJECTION, walkOptions)
            .cancellable()
            .flowOn(dispatcherProvider.IO)
            .collect { lookup ->
                aggregator.drain(errors)
                aggregator.onEntry(lookup)
                if (aggregator.itemCount % PROGRESS_INTERVAL == 0L) {
                    active = active.copy(
                        primaryProgress = active.primaryProgress.copy(
                            secondary = scannedLabel(aggregator.itemCount),
                        ),
                    )
                    send(active)
                }
            }
        aggregator.drain(errors)

        val scan = aggregator.result(Clock.System.now())
        log(tag, INFO) { "Scanned $root: ${scan.sizes.size} folders, ${scan.errorCount} errors" }
        val stored = checkNotNull(resultStore).publish(scan)

        send(
            State.Completed(
                startedAt = operationContext.startedAt,
                report = Report(
                    root = root,
                    directoryCount = scan.sizes.size,
                    itemCount = scan.itemCount,
                    errorCount = scan.errorCount,
                    wasDiscarded = !stored,
                ),
            )
        )
    }

    override fun onDiscarded() {
        resultStore = null
    }

    private fun scannedLabel(itemCount: Long): CaString = caString {
        it.getQuantityString2(R.plurals.explorer_operation_calculate_sizes_progress, itemCount.toInt())
    }

    private fun DirectorySizeAggregator.drain(errors: ConcurrentLinkedQueue<APathLookup<*>>) {
        while (true) onError(errors.poll() ?: break)
    }

    data class Report(
        val root: APath<*>,
        val directoryCount: Int,
        val itemCount: Long,
        val errorCount: Int,
        val wasDiscarded: Boolean,
    ) : ExplorerOperation.Report {

        override val summary: CaString = caString {
            if (wasDiscarded) {
                it.getString(
                    R.string.explorer_operation_calculate_sizes_summary_discarded,
                    root.userReadablePath.get(it),
                )
            } else if (errorCount == 0) {
                it.getString(R.string.explorer_operation_calculate_sizes_summary, directoryCount)
            } else {
                it.getQuantityString2(
                    R.plurals.explorer_operation_calculate_sizes_summary_partial,
                    errorCount,
                    directoryCount,
                    errorCount,
                )
            }
        }
        override val affectedPaths = emptyList<Operation.Report.PathChange>()
        override val subjectPath: APath<*> = root
        override val partialErrorCount: Int = errorCount
    }

    @AssistedFactory
    interface Factory {
        fun create(
            workspaceId: Workspace.Id,
            command: ExplorerCommand.CalculateSizes,
            store: DirectorySizeStore,
        ): CalculateSizesOperation
    }

    companion object {
        /** Sizes are the point; nothing else the walk could fetch is read. */
        private val LOOKUP_PROJECTION = LookupOptions(
            continueOnError = true,
            fetchSize = true,
        )
        private const val PROGRESS_INTERVAL = 500L
    }
}
