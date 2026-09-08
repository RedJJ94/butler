package eu.darken.butler.apps.ui.operations

import eu.darken.butler.common.debug.logging.Logging.Priority.*
import eu.darken.butler.common.debug.logging.log
import eu.darken.butler.common.issue.Issue
import eu.darken.butler.workspace.core.Workspace
import eu.darken.butler.workspace.core.operations.Operation
import eu.darken.butler.workspace.core.operations.OperationFocusRequest
import eu.darken.butler.workspace.ui.operations.OperationsDisplayState
import eu.darken.butler.workspace.ui.operations.bar.OperationsBarAction
import eu.darken.butler.workspace.ui.operations.details.OperationDialogState
import eu.darken.butler.workspace.ui.page.WorkspacePageChrome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The operations bar's state and dialogs for the apps pages, whose content and overlay slots are
 * siblings: everything they share has to live outside composition.
 *
 * The ViewModel launches [focusRequestHandler] and [issueRetireHandler] and forwards [onCleared].
 */
class PackageOperationUiController(
    private val workspaceId: Workspace.Id,
    private val chrome: WorkspacePageChrome,
    private val operationFocusRequest: OperationFocusRequest,
    private val scope: CoroutineScope,
    private val tag: String,
) {

    val operations: StateFlow<OperationsDisplayState> = chrome.operations
        .stateIn(scope, SharingStarted.Eagerly, OperationsDisplayState())

    private val dialogStateFlow = MutableStateFlow<OperationDialogState>(OperationDialogState.None)
    val dialogState: StateFlow<OperationDialogState> = dialogStateFlow

    private val issueFlow = MutableStateFlow<Issue?>(null)
    val issue: StateFlow<Issue?> = issueFlow

    /** Which operation the shown [issue] was raised for, so a stale one can be retired. */
    private val issueOperation = MutableStateFlow<Operation.Id?>(null)

    fun onBarAction(action: OperationsBarAction) {
        log(tag) { "onBarAction($action)" }
        when (action) {
            is OperationsBarAction.ShowDetails -> dialogStateFlow.value =
                OperationDialogState.OperationDetails(action.id)

            is OperationsBarAction.ShowConflict -> showIssue(action.id)
            is OperationsBarAction.Dismiss -> chrome.dismissOperation(action.id)
            OperationsBarAction.ClearCompleted -> chrome.clearCompletedOperations()
            // Nothing a package action does can be interrupted, so the bar offers no cancel and a
            // stale notification action lands here.
            is OperationsBarAction.RequestCancel -> log(tag, WARN) { "Not cancellable: ${action.id}" }
        }
    }

    private fun showIssue(operationId: Operation.Id) {
        scope.launch {
            val pending = chrome.pendingConflicts.first()[operationId]
            if (pending == null) {
                log(tag, WARN) { "showIssue($operationId): nothing is waiting" }
                return@launch
            }
            issueOperation.value = operationId
            issueFlow.value = pending
        }
    }

    /** A "tap to resolve" notification routes here; the request is consumed once it is on screen. */
    val focusRequestHandler = operationFocusRequest.requests
        .filterNotNull()
        .filter { it.workspaceId == workspaceId }
        .flatMapLatest { request -> chrome.pendingConflicts.map { request to it[request.operationId] } }
        .distinctUntilChanged()
        .onEach { (request, pending) ->
            if (pending == null) return@onEach
            issueOperation.value = request.operationId
            issueFlow.value = pending
            operationFocusRequest.consume(request)
        }

    /**
     * An operation drops out of the pending conflicts the moment it stops waiting, so this is what
     * keeps a confirmation sheet from outliving the request it was raised for.
     */
    val issueRetireHandler = chrome.pendingConflicts
        .onEach { conflicts ->
            val shownFor = issueOperation.value ?: return@onEach
            val live = conflicts[shownFor]
            if (live != null && live.id == issueFlow.value?.id) return@onEach
            log(tag) { "Retiring the issue of $shownFor, it is no longer pending" }
            issueFlow.value = null
            issueOperation.value = null
        }

    fun dismissDialog() {
        dialogStateFlow.value = OperationDialogState.None
    }

    fun dismissIssue() {
        issueFlow.value = null
        issueOperation.value = null
    }

    fun showInHistory(operationId: Operation.Id) = chrome.showOperationInHistory(operationId)

    fun shareError(operationId: Operation.Id) = chrome.shareOperationError(operationId)

    fun onCleared() {
        operationFocusRequest.clearForWorkspace(workspaceId)
    }
}
