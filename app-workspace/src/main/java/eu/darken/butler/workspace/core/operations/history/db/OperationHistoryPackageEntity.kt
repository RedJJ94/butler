package eu.darken.butler.workspace.core.operations.history.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-app outcome of a package operation (enable, disable, force stop, uninstall, clear data,
 * component toggle). The path tables stay empty for those operations, so this is the only record of
 * what the operation actually touched.
 */
@Entity(
    tableName = "operation_history_packages",
    foreignKeys = [
        ForeignKey(
            entity = OperationHistoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["operationHistoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["operationHistoryId"]),
    ],
)
data class OperationHistoryPackageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationHistoryId: String,
    /** App label, or `label · component class` for a component. */
    val label: String,
    /** Package name of the app the outcome belongs to; for a component, its owning app. */
    val packageName: String,
    /** [eu.darken.butler.workspace.core.operations.Operation.Report.Packages.Outcome.Status] name. */
    val status: String,
    /** What the live operation details sheet displays for a failure. Null unless it failed. */
    val errorMessage: String?,
    val sortIndex: Int,
)
