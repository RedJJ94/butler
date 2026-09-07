package eu.darken.butler.explorer.core.sizes

import eu.darken.butler.common.files.LocalPath
import eu.darken.butler.common.files.local.LocalPathLookup
import eu.darken.butler.common.files.metadata.FileType
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import kotlin.time.Instant

class DirectorySizeAggregatorTest : BaseTest() {

    private val scannedAt = Instant.parse("2026-09-07T14:32:00Z")

    private fun dir(path: String, error: String? = null) = LocalPathLookup(
        lookedUp = LocalPath.build(path),
        fileType = FileType.DIRECTORY,
        size = null,
        modifiedAt = null,
        error = error,
    )

    private fun file(path: String, size: Long?) = LocalPathLookup(
        lookedUp = LocalPath.build(path),
        fileType = FileType.FILE,
        size = size,
        modifiedAt = null,
    )

    private fun unknown(path: String) = LocalPathLookup.unknown(LocalPath.build(path), "unreadable")

    private fun aggregator(root: String) = DirectorySizeAggregator(LocalPath.build(root))

    @Test
    fun `a file counts towards every directory above it`() {
        val scan = aggregator("/a").apply {
            onEntry(dir("/a/b"))
            onEntry(dir("/a/b/c"))
            onEntry(file("/a/b/c/file", 100L))
            onEntry(file("/a/other", 5L))
        }.result(scannedAt)

        scan.sizes.getValue("/a") shouldBe DirectorySize(105L, true)
        scan.sizes.getValue("/a/b") shouldBe DirectorySize(100L, true)
        scan.sizes.getValue("/a/b/c") shouldBe DirectorySize(100L, true)
    }

    @Test
    fun `a directory without files is recorded at zero`() {
        val scan = aggregator("/a").apply { onEntry(dir("/a/empty")) }.result(scannedAt)

        scan.sizes.getValue("/a/empty") shouldBe DirectorySize(0L, true)
        scan.sizes.getValue("/a") shouldBe DirectorySize(0L, true)
    }

    @Test
    fun `an unreadable directory marks itself and its ancestors, not its siblings`() {
        val scan = aggregator("/a").apply {
            onEntry(dir("/a/b"))
            onEntry(file("/a/b/file", 10L))
            onEntry(dir("/a/b/locked"))
            onEntry(dir("/a/sibling"))
            onEntry(file("/a/sibling/file", 20L))
            onError(dir("/a/b/locked"))
        }.result(scannedAt)

        scan.sizes.getValue("/a/b/locked").isComplete shouldBe false
        scan.sizes.getValue("/a/b").isComplete shouldBe false
        scan.sizes.getValue("/a").isComplete shouldBe false
        scan.sizes.getValue("/a/sibling").isComplete shouldBe true
    }

    @Test
    fun `an error on a directory marks what was already recorded below it`() {
        val scan = aggregator("/a").apply {
            onEntry(dir("/a/b"))
            onEntry(dir("/a/b/c"))
            onEntry(file("/a/b/c/file", 10L))
            onEntry(dir("/a/sibling"))
            onEntry(file("/a/sibling/file", 20L))
            onError(dir("/a/b"))
        }.result(scannedAt)

        scan.sizes.getValue("/a/b/c").isComplete shouldBe false
        scan.sizes.getValue("/a/b").isComplete shouldBe false
        scan.sizes.getValue("/a/sibling").isComplete shouldBe true
    }

    @Test
    fun `a directory entry that carries an error is incomplete`() {
        val scan = aggregator("/a").apply { onEntry(dir("/a/b", error = "denied")) }.result(scannedAt)

        scan.sizes.getValue("/a/b").isComplete shouldBe false
        scan.sizes.getValue("/a").isComplete shouldBe false
    }

    @Test
    fun `a file without a size makes its parent chain incomplete`() {
        val scan = aggregator("/a").apply {
            onEntry(dir("/a/b"))
            onEntry(file("/a/b/file", null))
        }.result(scannedAt)

        scan.errorCount shouldBe 1
        scan.sizes.getValue("/a/b").isComplete shouldBe false
        scan.sizes.getValue("/a").isComplete shouldBe false
    }

    @Test
    fun `an unknown entry makes its parent chain incomplete`() {
        val scan = aggregator("/a").apply {
            onEntry(dir("/a/b"))
            onEntry(unknown("/a/b/mystery"))
        }.result(scannedAt)

        scan.errorCount shouldBe 1
        scan.sizes.getValue("/a/b").isComplete shouldBe false
        scan.sizes.getValue("/a").isComplete shouldBe false
    }

    @Test
    fun `the filesystem root is credited and terminates`() {
        val scan = aggregator("/").apply {
            onEntry(dir("/a"))
            onEntry(file("/a/b", 42L))
        }.result(scannedAt)

        scan.sizes.getValue("/") shouldBe DirectorySize(42L, true)
        scan.sizes.getValue("/a") shouldBe DirectorySize(42L, true)
    }

    @Test
    fun `entries and errors are counted`() {
        val scan = aggregator("/a").apply {
            onEntry(dir("/a/b"))
            onEntry(file("/a/b/one", 1L))
            onEntry(file("/a/b/two", null))
            onError(dir("/a/c"))
        }.result(scannedAt)

        scan.itemCount shouldBe 3
        scan.errorCount shouldBe 2
    }

    @Test
    fun `an entry that is the root itself changes nothing`() {
        val scan = aggregator("/a").apply { onEntry(file("/a", 100L)) }.result(scannedAt)

        scan.itemCount shouldBe 1
        scan.sizes.getValue("/a") shouldBe DirectorySize(0L, true)
    }

    @Test
    fun `an entry that only shares a name prefix with the root changes nothing`() {
        val scan = aggregator("/a").apply { onEntry(file("/ab/x", 100L)) }.result(scannedAt)

        scan.itemCount shouldBe 1
        scan.sizes.getValue("/a") shouldBe DirectorySize(0L, true)
        scan.sizes.keys shouldBe setOf("/a")
    }
}
