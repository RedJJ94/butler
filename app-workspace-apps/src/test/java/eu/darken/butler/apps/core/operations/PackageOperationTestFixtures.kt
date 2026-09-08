package eu.darken.butler.apps.core.operations

import eu.darken.butler.common.ca.toCaString
import eu.darken.butler.common.pkgs.Pkg
import eu.darken.butler.common.pkgs.features.InstallId
import eu.darken.butler.common.user.UserHandle2
import eu.darken.butler.workspace.core.Workspace
import eu.darken.butler.workspace.core.operations.Operation
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlin.time.Clock
import kotlin.time.Instant

/*
 * A submitted operation whose states a test drives by hand, so a workspace can be observed while
 * one is unfinished. Mirrors the manager's own FakeOperation; kept here rather than reached for
 * across modules.
 */

internal fun stubPackageOperation(
    workspaceId: Workspace.Id,
    states: Flow<Operation.State>,
    operationKind: Operation.Metadata.Kind = Operation.Metadata.Kind.UNINSTALL,
): PackageActionOperation = mockk(relaxed = true) {
    every { metadata } returns mockk(relaxed = true) {
        every { origin } returns Operation.Metadata.Origin.Apps(workspaceId)
        every { kind } returns operationKind
        every { isCancellable } returns false
        every { closePolicy } returns Operation.Metadata.ClosePolicy.REQUIRE_ORIGIN
    }
    every { perform(any()) } returns states
}

internal fun completedState(at: Instant = Clock.System.now()): Operation.State.Completed =
    object : Operation.State.Completed {
        override val startedAt: Instant = at
        override val completedAt: Instant = at
        override val summary = "done".toCaString()
        override val report: Operation.Report? = null
        override val error: Throwable? = null
    }

internal fun testTarget(name: String = "com.example.app") = PackageCommand.Target(
    installId = InstallId(Pkg.Id(name), UserHandle2(0)),
    label = name.toCaString(),
)
