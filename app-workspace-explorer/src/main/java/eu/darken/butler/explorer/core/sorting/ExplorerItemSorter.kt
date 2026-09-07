package eu.darken.butler.explorer.core.sorting

import android.content.Context
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.butler.common.debug.logging.Logging.Priority.*
import eu.darken.butler.common.debug.logging.log
import eu.darken.butler.common.debug.logging.logTag
import eu.darken.butler.explorer.core.SortSettings
import eu.darken.butler.explorer.core.engine.ExplorerItem
import eu.darken.butler.workspace.core.Workspace
import kotlin.time.Instant

class ExplorerItemSorter @AssistedInject constructor(
    @Assisted private val workspaceId: Workspace.Id,
    @ApplicationContext private val context: Context,
) {

    private val tag = logTag("Explorer", "Workspace", workspaceId.shortTag, "Page", "ItemSorter")

    fun sortItems(
        items: List<ExplorerItem>,
        sortSettings: SortSettings,
    ): List<ExplorerItem> {
        log(tag, VERBOSE) { "sortItems: ${items.size} items with settings=$sortSettings" }

        val shortcuts = mutableListOf<ExplorerItem.Shortcut>()
        val storage = mutableListOf<ExplorerItem.Storage>()
        val pathItems = mutableListOf<ExplorerItem.Path>()
        val trashItems = mutableListOf<ExplorerItem.Trash.Root>()
        val trashNestedItems = mutableListOf<ExplorerItem.Trash.Nested>()

        items.forEach { item ->
            when (item) {
                is ExplorerItem.Shortcut -> shortcuts.add(item)
                is ExplorerItem.Storage -> storage.add(item)
                is ExplorerItem.Path -> pathItems.add(item)
                is ExplorerItem.Trash.Root -> trashItems.add(item)
                is ExplorerItem.Trash.Nested -> trashNestedItems.add(item)
            }
        }

        val sortedPathItems = sortPathItems(context, pathItems, sortSettings)
        val sortedTrashItems = sortTrashItems(context, trashItems, sortSettings)
        val sortedTrashNestedItems = sortTrashNestedItems(context, trashNestedItems, sortSettings)

        return if (sortSettings.reversed) {
            sortedTrashNestedItems + sortedTrashItems + sortedPathItems + storage + shortcuts
        } else {
            shortcuts + storage + sortedPathItems + sortedTrashItems + sortedTrashNestedItems
        }
    }

    private fun sortPathItems(
        context: Context,
        pathItems: List<ExplorerItem.Path>,
        sortSettings: SortSettings,
    ): List<ExplorerItem.Path> {
        if (pathItems.isEmpty()) return pathItems

        val peeks = pathItems.filterIsInstance<ExplorerItem.Peek>()
        val directories = pathItems.filterIsInstance<ExplorerItem.Directory>()
        val files = pathItems.filterIsInstance<ExplorerItem.File>()

        val sortedDirectories = when (sortSettings.mode) {
            SortSettings.Mode.SIZE -> sortDirectoriesBySize(context, directories, sortSettings.reversed)
            else -> applySortMode(context, directories, sortSettings)
                .let { if (sortSettings.reversed) it.reversed() else it }
        }
        val sortedFiles = applySortMode(context, files, sortSettings)
            .let { if (sortSettings.reversed) it.reversed() else it }

        return if (sortSettings.reversed) {
            peeks + sortedFiles + sortedDirectories
        } else {
            peeks + sortedDirectories + sortedFiles
        }
    }

    /**
     * Ranks folders by the size a "Calculate sizes" run produced for them. Folders without one have
     * nothing to rank by, so they keep their name order and stay last in both directions.
     */
    private fun sortDirectoriesBySize(
        context: Context,
        directories: List<ExplorerItem.Directory>,
        reversed: Boolean,
    ): List<ExplorerItem.Directory> {
        val byName = Comparator<ExplorerItem.Directory> { a, b ->
            NaturalSortComparator.compare(a.displayName.get(context), b.displayName.get(context))
        }
        val known = directories
            .filter { (it as? ExplorerItem.RegularDirectory)?.computedSize != null }
            .sortedWith(
                compareBy<ExplorerItem.Directory> { (it as ExplorerItem.RegularDirectory).computedSize!!.bytes }
                    .then(byName)
            )
            .let { if (reversed) it.reversed() else it }
        val unknown = directories
            .filter { (it as? ExplorerItem.RegularDirectory)?.computedSize == null }
            .sortedWith(byName)
        return known + unknown
    }

    private fun sortTrashItems(
        context: Context,
        items: List<ExplorerItem.Trash.Root>,
        sortSettings: SortSettings,
    ): List<ExplorerItem.Trash.Root> {
        if (items.isEmpty()) return items

        val sorted = when (sortSettings.mode) {
            SortSettings.Mode.NAME -> items.sortedWith { a, b ->
                NaturalSortComparator.compare(
                    a.originalLookup.userReadableName.get(context),
                    b.originalLookup.userReadableName.get(context)
                )
            }
            SortSettings.Mode.SIZE -> items.sortedBy { it.originalLookup.size ?: 0L }
            SortSettings.Mode.MODIFIED_AT -> items.sortedBy { it.deletedAt }
            SortSettings.Mode.CREATED_AT -> items.sortedBy {
                it.originalLookup.createdAt ?: Instant.DISTANT_PAST
            }
        }

        return if (sortSettings.reversed) sorted.reversed() else sorted
    }

    private fun sortTrashNestedItems(
        context: Context,
        items: List<ExplorerItem.Trash.Nested>,
        sortSettings: SortSettings,
    ): List<ExplorerItem.Trash.Nested> {
        if (items.isEmpty()) return items

        // Separate directories and files
        val directories = items.filter { it.isDirectory }
        val files = items.filter { it.isFile }

        val sortedDirectories = applySortModeNested(context, directories, sortSettings)
        val sortedFiles = applySortModeNested(context, files, sortSettings)

        return if (sortSettings.reversed) {
            sortedFiles.reversed() + sortedDirectories.reversed()
        } else {
            sortedDirectories + sortedFiles
        }
    }

    private fun applySortModeNested(
        context: Context,
        items: List<ExplorerItem.Trash.Nested>,
        sortSettings: SortSettings,
    ): List<ExplorerItem.Trash.Nested> {
        return when (sortSettings.mode) {
            SortSettings.Mode.NAME -> items.sortedWith { a, b ->
                NaturalSortComparator.compare(
                    a.displayName.get(context),
                    b.displayName.get(context)
                )
            }
            SortSettings.Mode.SIZE -> items.sortedBy { it.lookup.size ?: 0L }
            SortSettings.Mode.MODIFIED_AT -> items.sortedBy {
                it.lookup.modifiedAt ?: Instant.DISTANT_PAST
            }
            SortSettings.Mode.CREATED_AT -> items.sortedBy {
                it.lookup.createdAt ?: Instant.DISTANT_PAST
            }
        }
    }

    private fun <T : ExplorerItem.Path> applySortMode(
        context: Context,
        items: List<T>,
        sortSettings: SortSettings,
    ): List<T> {
        return when (sortSettings.mode) {
            SortSettings.Mode.NAME -> items.sortedWith { a, b ->
                NaturalSortComparator.compare(a.displayName.get(context), b.displayName.get(context))
            }
            SortSettings.Mode.SIZE -> items.sortedWith { a, b ->
                if (a is ExplorerItem.Lookup && b is ExplorerItem.Lookup) {
                    val sizeA = a.lookup.size ?: 0L
                    val sizeB = b.lookup.size ?: 0L
                    sizeA.compareTo(sizeB)
                } else {
                    0
                }
            }
            SortSettings.Mode.MODIFIED_AT -> items.sortedWith { a, b ->
                if (a is ExplorerItem.Lookup && b is ExplorerItem.Lookup) {
                    val timeA = a.lookup.modifiedAt ?: Instant.DISTANT_PAST
                    val timeB = b.lookup.modifiedAt ?: Instant.DISTANT_PAST
                    timeA.compareTo(timeB)
                } else {
                    0
                }
            }
            SortSettings.Mode.CREATED_AT -> {
                items.sortedWith { a, b ->
                    if (a is ExplorerItem.Lookup && b is ExplorerItem.Lookup) {
                        val timeA = a.createdAt
                        val timeB = b.createdAt
                        when {
                            timeA == null && timeB == null -> 0
                            timeA == null -> -1
                            timeB == null -> 1
                            else -> timeA.compareTo(timeB)
                        }
                    } else {
                        0
                    }
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(id: Workspace.Id): ExplorerItemSorter
    }
}