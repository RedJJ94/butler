package eu.darken.butler.explorer.core.sizes

import eu.darken.butler.common.files.APath
import eu.darken.butler.common.files.extensions.isAncestorOf
import eu.darken.butler.common.files.extensions.isAncestorOfOrSelf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

/**
 * The scans one Explorer tab has in memory. Results are tab-local and die with it.
 */
class DirectorySizeStore {

    data class Snapshot(
        /** Keyed by the scan root's [APath.path]. */
        val scans: Map<String, DirectoryScan> = emptyMap(),
        /** Roots with a scan in flight. */
        val running: Map<String, APath<*>> = emptyMap(),
        /** Running roots that saw an overlapping change; their results are dropped at publish. */
        val stale: Set<String> = emptySet(),
    ) {
        /** The deepest scan whose root is [directory] or an ancestor of it. */
        fun scanFor(directory: APath<*>): DirectoryScan? = scans.values
            .filter { it.root.isAncestorOfOrSelf(directory) }
            .maxByOrNull { it.root.path.length }

        fun isRunning(directory: APath<*>): Boolean = directory.path in running
    }

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    /** Reserves [root]; false when a scan of it is already running (the caller must not start another). */
    fun markRunning(root: APath<*>): Boolean {
        val key = root.path
        val previous = _snapshot.getAndUpdate { current ->
            if (key in current.running) current else current.copy(running = current.running + (key to root))
        }
        return key !in previous.running
    }

    fun markFinished(root: APath<*>) {
        val key = root.path
        _snapshot.update { current ->
            current.copy(running = current.running - key, stale = current.stale - key)
        }
    }

    /**
     * Stores [scan], unless it was marked stale while it ran - publishing it then would overwrite
     * the store with a pre-change total stamped with a post-change time.
     *
     * @return true when [scan] was stored, false when it was dropped as stale.
     */
    fun publish(scan: DirectoryScan): Boolean {
        val key = scan.root.path
        var stored = false
        _snapshot.update { current ->
            if (key in current.stale) {
                stored = false
                return@update current
            }
            // A fresh parent covers everything below it.
            val retained = current.scans.filterValues { !scan.root.isAncestorOf(it.root) }
            stored = true
            current.copy(scans = retained + (key to scan))
        }
        return stored
    }

    /**
     * Drops every scan that overlaps one of [paths] in either direction: a scan of `/a/b/c` is just
     * as dead when `/a/b` is moved away as when something below `/a/b/c` changes.
     */
    fun invalidate(paths: Collection<APath<*>>) {
        if (paths.isEmpty()) return
        _snapshot.update { current ->
            val retained = current.scans.filterValues { scan -> paths.none { it.overlaps(scan.root) } }
            val nowStale = current.running.filterValues { root -> paths.any { it.overlaps(root) } }.keys
            current.copy(scans = retained, stale = current.stale + nowStale)
        }
    }
}

private fun APath<*>.overlaps(root: APath<*>): Boolean =
    root.isAncestorOfOrSelf(this) || this.isAncestorOf(root)
