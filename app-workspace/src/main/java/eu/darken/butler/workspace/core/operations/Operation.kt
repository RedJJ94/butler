package eu.darken.butler.workspace.core.operations

import android.os.Parcelable
import androidx.compose.ui.graphics.vector.ImageVector
import eu.darken.butler.common.ca.CaString
import eu.darken.butler.common.files.APath
import eu.darken.butler.common.files.local.operations.core.PerformanceHistory
import eu.darken.butler.common.issue.Issue
import eu.darken.butler.common.parcel.UuidParceler
import eu.darken.butler.common.progress.Progress
import eu.darken.butler.workspace.core.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface Operation {

    @Parcelize
    @TypeParceler<Uuid, UuidParceler>
    data class Id(
        val id: Uuid = Uuid.random(),
    ) : Parcelable {
        val shortTag: String
            get() = id.toString().take(4)
        val longTag: String
            get() = id.toString()

        override fun toString(): String = "Operation.Id($shortTag)"
    }

    interface Metadata {
        val origin: Origin
        val icon: ImageVector
        val title: CaString
        val description: CaString

        /**
         * File-op classification used by the global Operation History to filter and label entries.
         * `null` means this operation is not a file op and should NOT be persisted in history
         * (e.g., Developer test-data generators, Searcher search execution).
         */
        val kind: Kind? get() = null

        /**
         * Semantic intent override that refines the displayed label in history.
         * For example, a `MoveOperation` invoked as a rename should set `intent = RENAME` so the
         * history row reads "Renamed" instead of "Moved". Null = use the default label for `kind`.
         * A [Kind.COMPONENTS] operation sets [Intent.ENABLE_COMPONENTS] or
         * [Intent.DISABLE_COMPONENTS] so the history headline names the verb.
         */
        val intent: Intent? get() = null

        /**
         * What the operation set out to do, in paths: its targets, an optional destination, and the
         * per-consumer views ([OperationPathPlan.scopePaths], [OperationPathPlan.representativePath])
         * derived from them. Captured at submit time so failed/cancelled ops are still queryable by
         * path scope, even when [Report.Paths.affectedPaths] is null/empty (because nothing was actually
         * completed). Persistence stores the union of planned + actually-affected paths.
         */
        val pathPlan: OperationPathPlan? get() = null

        /**
         * Whether the USER may ask for this operation to stop: the operations bar/sheet cancel
         * button, the notification's Cancel action, and [OperationsManager.cancel] itself.
         * False for work that cannot be interrupted once dispatched, e.g. a blocking IPC call whose
         * peer keeps going regardless.
         *
         * Independent of [closePolicy]: this is about the user asking, that one is about the owner
         * workspace going away.
         */
        val isCancellable: Boolean get() = true

        val closePolicy: ClosePolicy get() = ClosePolicy.CANCEL_WITH_ORIGIN

        /** What closing the origin workspace does to an unfinished operation. */
        enum class ClosePolicy {
            /** Closing the origin workspace cancels the operation and drops its receipt. */
            CANCEL_WITH_ORIGIN,

            /**
             * The origin workspace - and any tab whose close would take it down - cannot be closed
             * while the operation is unfinished: the close is refused and the user is told.
             *
             * Never detaches. An operation always keeps the workspace that can show its progress and
             * answer its [State.Waiting] state.
             */
            REQUIRE_ORIGIN,
        }

        enum class Kind {
            COPY, MOVE, DELETE, RESTORE, CREATE_FOLDER, CREATE_FILE, SAVE, COMPRESS, EXTRACT, INSTALL,
            ENABLE, DISABLE, FORCE_STOP, UNINSTALL, CLEAR_DATA, COMPONENTS,
        }

        enum class Intent {
            RENAME, PASTE_COPY, PASTE_MOVE, DROP_COPY, DROP_MOVE,
            ENABLE_COMPONENTS, DISABLE_COMPONENTS,
        }

        sealed interface Origin {
            val workspaceId: Workspace.Id

            data class Explorer(override val workspaceId: Workspace.Id) : Origin
            data class Searcher(override val workspaceId: Workspace.Id) : Origin
            data class Saver(override val workspaceId: Workspace.Id) : Origin
            data class Developer(override val workspaceId: Workspace.Id) : Origin
            data class Viewer(override val workspaceId: Workspace.Id) : Origin
            data class Apps(override val workspaceId: Workspace.Id) : Origin
        }
    }

    val metadata: Metadata

    interface State {
        val startedAt: Instant

        data class Queued(
            override val startedAt: Instant,
        ) : State

        interface Active : State {
            val primaryProgress: Progress.Data
            val secondaryProgress: Progress.Data?
        }

        interface Waiting : State {
            val waitingSince: Instant
            val reason: CaString
            val issue: Issue
        }

        interface Completed : State {
            val completedAt: Instant
            val summary: CaString
            val report: Report?
            val error: Throwable?
        }
    }

    /**
     * What an operation has to say about itself once it finished.
     *
     * Sealed so consumers can switch exhaustively over the report SHAPES. [Paths] is the
     * file-operation shape and is open, because every file operation refines it with its own
     * counters. [Packages] is the package-operation shape. Further shapes are added beside them,
     * in this file.
     */
    sealed interface Report {
        val summary: CaString

        /**
         * Number of sub-items that DIDN'T complete as intended even though the operation as a whole
         * didn't fail (e.g., save with mixed permissions: some files succeed, some fail per-file
         * with the top-level [Operation.State.Completed.error] still null). Drives the
         * [eu.darken.butler.workspace.core.operations.history.HistoryOutcome.PARTIAL] outcome in history.
         * Default 0 means "not partial" — only reports that can produce per-item failures override.
         */
        val partialErrorCount: Int get() = 0

        /** The shape of a report about paths: what changed on disk, and what it was about. */
        interface Paths : Report {
            val affectedPaths: Collection<PathChange>

            /**
             * The path this operation was ABOUT, shown as its history row label.
             *
             * The conflict-resolved path of what the user selected, or - for operations that fan out -
             * the container or archive the user acted on. Never a path whose name the user did not
             * choose: an extraction's entries and a recursive delete's descendants are audit records,
             * not subjects.
             *
             * Null when THIS REPORT cannot name one (nothing completed, or the operation is not
             * history-eligible). The history then falls back to the path plan's representative path.
             */
            val subjectPath: APath<*>?

            data class PathChange(
                val path: APath<*>,
                val change: Change,
                /**
                 * For [Change.MOVED]: the source path before the move (i.e., the rename source).
                 * History details show as `previousPath → path`. Null for non-move changes or when
                 * the source isn't tracked.
                 */
                val previousPath: APath<*>? = null,
            ) {
                enum class Change {
                    ADDED, REMOVED, MODIFIED, TRASHED, MOVED,
                }
            }
        }

        /** The shape of a report about packages or their components: one outcome per target, in order. */
        data class Packages(
            override val summary: CaString,
            val outcomes: List<Outcome>,
        ) : Report {
            override val partialErrorCount: Int get() = outcomes.count { it.status == Outcome.Status.FAILED }

            data class Outcome(
                /** App label, or `label · component class` for a component. */
                val label: CaString,
                /** Package name of the app the outcome belongs to; for a component, its owning app. */
                val packageName: String,
                val status: Status,
                val error: Throwable? = null,
            ) {
                enum class Status { DONE, FAILED, DECLINED }
            }
        }
    }

    interface HasPerformanceHistory {
        val performanceHistory: PerformanceHistory?
    }

    data class Context(
        val id: Id,
        val startedAt: Instant,
    )

    fun perform(operationContext: Context): Flow<State>

    /**
     * Releases sensitive or transient data held by this operation (e.g. wiping a password buffer).
     * Invoked by [ManagedOperation] when the operation reaches a terminal state, and also when it is
     * cancelled before [perform] ever begins. Must be idempotent — it can be called more than once.
     * Default: nothing to release.
     */
    fun onDiscarded() {}
}