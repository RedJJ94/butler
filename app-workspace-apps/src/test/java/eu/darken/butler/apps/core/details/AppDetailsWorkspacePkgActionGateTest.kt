package eu.darken.butler.apps.core.details

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import eu.darken.butler.apps.core.AppSizeCache
import eu.darken.butler.apps.core.details.components.ComponentEntry
import eu.darken.butler.apps.core.details.components.ComponentKind
import eu.darken.butler.apps.core.operations.PackageActionOperation
import eu.darken.butler.apps.core.operations.PackageCommand
import eu.darken.butler.apps.core.operations.completedState
import eu.darken.butler.apps.core.operations.stubPackageOperation
import eu.darken.butler.apps.core.operations.testTarget
import eu.darken.butler.apps.ui.apps.preview.AppsMockDataProvider
import eu.darken.butler.common.files.APath
import eu.darken.butler.common.files.Existence
import eu.darken.butler.common.pkgs.Pkg
import eu.darken.butler.common.pkgs.PkgRepo
import eu.darken.butler.common.pkgs.features.InstallId
import eu.darken.butler.common.user.UserHandle2
import eu.darken.butler.permissions.core.PathRequirements
import eu.darken.butler.workspace.contracts.apps.AppDetailsArguments
import eu.darken.butler.workspace.core.Workspace
import eu.darken.butler.workspace.core.operations.Operation
import eu.darken.butler.workspace.core.operations.OperationsManager
import eu.darken.butler.workspace.core.operations.current
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import testhelpers.coroutine.TestDispatcherProvider

/**
 * Disabling the rows in Compose is feedback, not a barrier: two taps can both be delivered before
 * a recomposition, and an uninstall dispatched twice is a second removal of a package the first one
 * is still working on. The check and the submit share a lock, so neither tap can pass the check
 * before the other has submitted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDetailsWorkspacePkgActionGateTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private val workspaceId = Workspace.Id()
    private val installId = InstallId(Pkg.Id(PKG), UserHandle2(0))
    private val installed = AppsMockDataProvider.createMockInstalled(packageName = PKG, label = "Butler")

    private val operationsManager = OperationsManager(TestDispatcherProvider())

    // Silent until a test finishes it, so a submitted operation stays unfinished as long as needed.
    private val operationStates = MutableSharedFlow<Operation.State>(replay = 1)

    private fun TestScope.createWorkspace(): AppDetailsWorkspace = AppDetailsWorkspace(
        id = workspaceId,
        creationArguments = AppDetailsArguments(installId = installId),
        context = context,
        dispatcherProvider = TestDispatcherProvider(StandardTestDispatcher(testScheduler)),
        pkgRepo = mockk<PkgRepo> {
            every { data } returns MutableStateFlow(PkgRepo.PkgData.from(listOf(installed)))
            coEvery { refresh() } returns listOf(installed)
        },
        pkgOps = mockk(relaxed = true),
        apkArchiveParser = mockk(relaxed = true),
        appSizeCache = mockk(relaxed = true) {
            every { snapshot } returns MutableStateFlow(AppSizeCache.Snapshot())
            every { isAvailable } returns MutableStateFlow(false)
        },
        gatewaySwitch = mockk { coEvery { existsStrict(any()) } returns Existence.PRESENT },
        pathPermissionCheck = mockk { every { monitor(any<APath<*>>()) } returns flowOf(PathRequirements()) },
        rootManager = mockk(relaxed = true),
        adbManager = mockk(relaxed = true),
        workspaceRemote = mockk(relaxed = true),
        operationsManager = operationsManager,
        operationFactory = mockk<PackageActionOperation.Factory> {
            every { create(any(), any()) } answers {
                stubPackageOperation(
                    workspaceId = workspaceId,
                    states = operationStates,
                    operationKind = when (secondArg<PackageCommand>()) {
                        is PackageCommand.SetComponents -> Operation.Metadata.Kind.COMPONENTS
                        else -> Operation.Metadata.Kind.UNINSTALL
                    },
                )
            }
        },
    )

    private fun uninstall() = PackageCommand.Uninstall(listOf(testTarget(PKG)), viaSystemDialog = false)

    @Test
    fun `a second uninstall while the first runs is rejected`() = runTest {
        val workspace = createWorkspace()
        val submitted = mutableListOf<Any?>()

        // Both are launched before the scheduler runs either, which is what two taps delivered
        // within one frame look like.
        launch { submitted += workspace.submit(uninstall()) }
        launch { submitted += workspace.submit(uninstall()) }
        advanceUntilIdle()

        submitted.count { it != null } shouldBe 1
        submitted.count { it == null } shouldBe 1
        operationsManager.current().size shouldBe 1
    }

    @Test
    fun `a later uninstall goes through once the first finished`() = runTest {
        val workspace = createWorkspace()

        workspace.submit(uninstall()).shouldNotBeNull()
        advanceUntilIdle()

        operationStates.emit(completedState())
        advanceUntilIdle()

        workspace.submit(uninstall()).shouldNotBeNull()
    }

    /** One lock across both would let a running app action block the component screen. */
    @Test
    fun `a component toggle is not blocked by a running app action`() = runTest {
        val workspace = createWorkspace()

        workspace.submit(uninstall())
        advanceUntilIdle()

        val components = workspace.submit(
            PackageCommand.SetComponents(
                target = testTarget(PKG),
                entries = listOf(
                    ComponentEntry(
                        kind = ComponentKind.ACTIVITY,
                        packageName = PKG,
                        className = "$PKG.MainActivity",
                        isExported = true,
                    )
                ),
                enabled = false,
            )
        )
        advanceUntilIdle()

        components.shouldNotBeNull()
        operationsManager.current().size shouldBe 2
    }

    companion object {
        private const val PKG = "eu.darken.butler"
    }
}
