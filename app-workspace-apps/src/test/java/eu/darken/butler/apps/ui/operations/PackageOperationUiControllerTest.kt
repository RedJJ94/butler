package eu.darken.butler.apps.ui.operations

import eu.darken.butler.common.issue.Issue
import eu.darken.butler.workspace.core.Workspace
import eu.darken.butler.workspace.core.operations.Operation
import eu.darken.butler.workspace.core.operations.OperationFocusRequest
import eu.darken.butler.workspace.ui.operations.OperationsDisplayState
import eu.darken.butler.workspace.ui.operations.bar.OperationsBarAction
import eu.darken.butler.workspace.ui.page.WorkspacePageChrome
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import testhelpers.BaseTest

/**
 * The apps pages compose their content and their overlays as siblings, so this state lives outside
 * composition - including the rule that a confirmation sheet dies with the request it was for.
 */
class PackageOperationUiControllerTest : BaseTest() {

    private val workspaceId = Workspace.Id()
    private val otherWorkspaceId = Workspace.Id()
    private val operationId = Operation.Id()

    private val pendingConflicts = MutableStateFlow<Map<Operation.Id, Issue>>(emptyMap())
    private val focusRequest = OperationFocusRequest()

    private val issue = mockk<Issue> {
        every { id } returns Issue.Id()
    }

    private val chrome = mockk<WorkspacePageChrome>(relaxed = true) {
        every { operations } returns flowOf(OperationsDisplayState())
        every { this@mockk.pendingConflicts } returns this@PackageOperationUiControllerTest.pendingConflicts
    }

    private fun TestScope.create(scope: CoroutineScope = backgroundScope) = PackageOperationUiController(
        workspaceId = workspaceId,
        chrome = chrome,
        operationFocusRequest = focusRequest,
        scope = scope,
        tag = "test",
    )

    @Test
    fun `showing a conflict surfaces the pending issue`() = runTest {
        val controller = create()
        pendingConflicts.value = mapOf(operationId to issue)

        controller.onBarAction(OperationsBarAction.ShowConflict(operationId))
        runCurrent()

        controller.issue.value shouldBe issue
    }

    @Test
    fun `showing a conflict for an operation that is not waiting shows nothing`() = runTest {
        val controller = create()

        controller.onBarAction(OperationsBarAction.ShowConflict(operationId))
        runCurrent()

        controller.issue.value shouldBe null
    }

    @Test
    fun `a focus request for this workspace opens the issue and is consumed`() = runTest {
        val controller = create()
        backgroundScope.launch { controller.focusRequestHandler.collect { } }
        pendingConflicts.value = mapOf(operationId to issue)

        focusRequest.request(workspaceId, operationId)
        runCurrent()

        controller.issue.value shouldBe issue
        focusRequest.requests.value shouldBe null
    }

    @Test
    fun `a focus request for another workspace is ignored`() = runTest {
        val controller = create()
        backgroundScope.launch { controller.focusRequestHandler.collect { } }
        pendingConflicts.value = mapOf(operationId to issue)

        focusRequest.request(otherWorkspaceId, operationId)
        runCurrent()

        controller.issue.value shouldBe null
        focusRequest.requests.value shouldBe OperationFocusRequest.Request(otherWorkspaceId, operationId)
    }

    @Test
    fun `clearing the controller drops a request that was never fulfilled`() = runTest {
        val controller = create()
        focusRequest.request(workspaceId, operationId)

        controller.onCleared()

        focusRequest.requests.value shouldBe null
    }

    @Test
    fun `a shown issue is retired once its operation stops waiting`() = runTest {
        val controller = create()
        backgroundScope.launch { controller.issueRetireHandler.collect { } }
        pendingConflicts.value = mapOf(operationId to issue)
        controller.onBarAction(OperationsBarAction.ShowConflict(operationId))
        runCurrent()
        controller.issue.value shouldBe issue

        pendingConflicts.value = emptyMap()
        runCurrent()

        controller.issue.value shouldBe null
    }
}
