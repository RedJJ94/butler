package eu.darken.butler.workspace.core.operations.history.db

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Room projection joining one [OperationHistoryEntity] with its [OperationHistoryPathEntity] and
 * [OperationHistoryPackageEntity] children. Used by DAO queries that need both the operation row and
 * what it affected. An operation has one or the other: a path operation reports paths, a package
 * operation reports per-app outcomes.
 */
data class OperationHistoryWithPaths(
    @Embedded val entry: OperationHistoryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "operationHistoryId",
    )
    val paths: List<OperationHistoryPathEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "operationHistoryId",
    )
    val packages: List<OperationHistoryPackageEntity>,
)
