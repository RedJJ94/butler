package eu.darken.butler.explorer.core.operations

import eu.darken.butler.common.coroutine.DispatcherProvider
import eu.darken.butler.common.files.APathGateway
import eu.darken.butler.common.files.GatewaySwitch
import eu.darken.butler.common.files.LocalPath
import eu.darken.butler.common.files.local.LocalPathLookup
import eu.darken.butler.common.files.metadata.FileType
import eu.darken.butler.explorer.core.sizes.DirectorySizeStore
import eu.darken.butler.workspace.core.Workspace
import eu.darken.butler.workspace.core.operations.Operation
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider
import java.io.IOException
import kotlin.time.Instant

class CalculateSizesOperationTest : BaseTest() {

    private val root = LocalPath.build("/a")
    private val gateway = mockk<APathGateway<LocalPath, LocalPathLookup>>()
    private val gatewaySwitch = mockk<GatewaySwitch>().apply {
        coEvery { getGateway(any()) } returns gateway
    }

    /** The walk runs on a different dispatcher than the collector, as it does in production. */
    private val realIoDispatchers = object : DispatcherProvider {
        override val IO: CoroutineDispatcher get() = Dispatchers.IO
    }

    private fun lookup(path: String, fileType: FileType, size: Long? = null) = LocalPathLookup(
        lookedUp = LocalPath.build(path),
        fileType = fileType,
        size = size,
        modifiedAt = null,
    )

    private fun operation(store: DirectorySizeStore, dispatcherProvider: DispatcherProvider) =
        CalculateSizesOperation(
            workspaceId = Workspace.Id(),
            command = ExplorerCommand.CalculateSizes(root),
            store = store,
            gatewaySwitch = gatewaySwitch,
            dispatcherProvider = dispatcherProvider,
        )

    private fun context() = Operation.Context(id = Operation.Id(), startedAt = Instant.DISTANT_PAST)

    @Test
    fun `a failed location is reported and the totals are published`() = runTest {
        coEvery { gateway.walk(any(), any(), any()) } coAnswers {
            val options = thirdArg<APathGateway.WalkOptions<LocalPath, LocalPathLookup>>()
            flow {
                options.onError!!.invoke(lookup("/a/locked", FileType.DIRECTORY), IOException("denied"))
                emit(lookup("/a/b", FileType.DIRECTORY))
                emit(lookup("/a/b/file", FileType.FILE, size = 10L))
            }
        }
        val store = DirectorySizeStore()

        val completed = operation(store, realIoDispatchers)
            .perform(context())
            .last() as ExplorerOperation.State.Completed

        completed.error shouldBe null
        val report = completed.report as CalculateSizesOperation.Report
        report.errorCount shouldBe 1
        report.itemCount shouldBe 2

        val scan = store.snapshot.value.scanFor(root).shouldNotBeNull()
        scan.sizes.getValue("/a").bytes shouldBe 10L
        scan.sizes.getValue("/a").isComplete shouldBe false
        scan.sizes.getValue("/a/b").isComplete shouldBe true
    }

    @Test
    fun `a cancelled scan publishes nothing`() = runTest {
        coEvery { gateway.walk(any(), any(), any()) } returns flow { awaitCancellation() }
        val store = DirectorySizeStore()

        val job = launch {
            operation(store, TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)))
                .perform(context())
                .collect()
        }
        job.cancelAndJoin()

        store.snapshot.value.scans shouldBe emptyMap()
    }
}
