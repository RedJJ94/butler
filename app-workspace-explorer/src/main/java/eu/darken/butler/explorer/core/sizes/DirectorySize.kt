package eu.darken.butler.explorer.core.sizes

import eu.darken.butler.common.files.APath
import kotlin.time.Instant

/** The recursive apparent size of a directory; [isComplete] is false when part of it could not be read. */
data class DirectorySize(
    val bytes: Long,
    val isComplete: Boolean,
)

/**
 * One completed walk of [root].
 *
 * [sizes] is keyed by [APath.path] and holds every directory the walk saw below [root], [root]
 * itself included.
 */
data class DirectoryScan(
    val root: APath<*>,
    val scannedAt: Instant,
    val sizes: Map<String, DirectorySize>,
    /** Entries the walk emitted. */
    val itemCount: Long,
    /** Failed locations plus entries the walk could not size. */
    val errorCount: Int,
)
