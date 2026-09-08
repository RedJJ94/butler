package eu.darken.butler.apps.core

import eu.darken.butler.apps.core.engine.AppsEngine
import eu.darken.butler.apps.core.engine.AppsState
import eu.darken.butler.apps.core.operations.PackageActionOperation
import eu.darken.butler.apps.core.operations.PackageCommand
import eu.darken.butler.apps.core.operations.completedState
import eu.darken.butler.apps.core.operations.stubPackageOperation
import eu.darken.butler.apps.core.operations.testTarget
import eu.darken.butler.common.adb.AdbManager
import eu.darken.butler.common.root.RootManager
import eu.darken.butler.common.serialization.SerializationIOModule
import eu.darken.butler.workspace.contracts.apps.AppsArguments
import eu.darken.butler.workspace.contracts.apps.AppsViewStyle
import eu.darken.butler.workspace.contracts.apps.SortSettings
import eu.darken.butler.workspace.contracts.apps.TagFilterConfig
import eu.darken.butler.workspace.core.Workspace
import eu.darken.butler.workspace.core.operations.Operation
import eu.darken.butler.workspace.core.operations.OperationsManager
import eu.darken.butler.workspace.ui.restore.WorkspaceViewPrefs
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest
import testhelpers.coroutine.TestDispatcherProvider

/**
 * A package operation pins the tab that submitted it: releasing the workspace mid-run would take
 * away the surface that shows its progress and its receipt.
 */
class AppsWorkspacePausableTest : BaseTest() {

    private val id = Workspace.Id()
    private val operationsManager = OperationsManager(TestDispatcherProvider())

    // replay = 1: the manager subscribes when it starts the operation, the test finishes it
    // afterwards, and nothing should hinge on which of the two runs first.
    private val operationStates = MutableSharedFlow<Operation.State>(replay = 1)

    private fun createWorkspace(): AppsWorkspace {
        val engine = mockk<AppsEngine>(relaxed = true) {
            every { state } returns MutableStateFlow(AppsState())
        }
        return AppsWorkspace(
            id = id,
            creationArguments = AppsArguments.Default(
                filterConfig = TagFilterConfig(),
                sortSettings = SortSettings(),
                viewStyle = AppsViewStyle.default(),
            ),
            dispatcherProvider = TestDispatcherProvider(),
            appsEngineFactory = mockk<AppsEngine.Factory> { every { create(any(), any()) } returns engine },
            appsSettings = mockk(relaxed = true),
            tabViewStore = AppsTabViewStore(WorkspaceViewPrefs(), SerializationIOModule().json()),
            rootManager = mockk<RootManager> { every { useRoot } returns flowOf(false) },
            adbManager = mockk<AdbManager> { every { useAdb } returns flowOf(false) },
            operationsManager = operationsManager,
            operationFactory = mockk<PackageActionOperation.Factory> {
                every { create(any(), any()) } returns stubPackageOperation(id, operationStates)
            },
        )
    }

    @Test
    fun `an idle apps workspace can be paused`() = runTest(UnconfinedTestDispatcher()) {
        val workspace = createWorkspace()

        workspace.info.value.isPausable shouldBe true
    }

    @Test
    fun `an unfinished package operation blocks pausing`() = runTest(UnconfinedTestDispatcher()) {
        val workspace = createWorkspace()

        workspace.submit(PackageCommand.Uninstall(listOf(testTarget()), viaSystemDialog = false))
        workspace.info.value.isPausable shouldBe false

        operationStates.emit(completedState())

        workspace.info.value.isPausable shouldBe true
    }
}
