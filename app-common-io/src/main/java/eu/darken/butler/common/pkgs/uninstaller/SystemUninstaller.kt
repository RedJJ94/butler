package eu.darken.butler.common.pkgs.uninstaller

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import androidx.core.net.toUri
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import eu.darken.butler.common.debug.logging.Logging.Priority.*
import eu.darken.butler.common.debug.logging.asLog
import eu.darken.butler.common.debug.logging.log
import eu.darken.butler.common.debug.logging.logTag
import eu.darken.butler.common.hasApiLevel
import eu.darken.butler.common.io.R
import eu.darken.butler.common.pkgs.PackageEventListener
import eu.darken.butler.common.pkgs.features.InstallId
import eu.darken.butler.common.pkgs.installer.AppInstallStatusReceiver
import eu.darken.butler.common.pkgs.installer.AppInstallStatusRelay
import eu.darken.butler.common.user.UserManager2
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.Uuid

/**
 * Removes a package through `PackageInstaller`, i.e. through Android's own confirmation dialog.
 *
 * The pending-UI/result contract is the installer's: the request carries a mutable `PendingIntent`
 * to [AppInstallStatusReceiver], statuses come back through [AppInstallStatusRelay] keyed by a
 * per-request id, and a `STATUS_PENDING_USER_ACTION` is offered again as an
 * [AppUninstallConfirmationIssue] because Android drops a confirmation activity started from the
 * background.
 *
 * The wait is bounded: a dialog that dies without ever reporting would otherwise pin the calling
 * operation, and with it the tab that has to answer for it. A confirmation given after the deadline
 * still reaches the app through the package events - only this call's receipt is stale.
 */
@Singleton
class SystemUninstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statusRelay: AppInstallStatusRelay,
    private val packageEventListener: PackageEventListener,
    private val userManager: UserManager2,
    private val requester: Requester,
) {

    /**
     * The `PackageInstaller` call itself, so the wait loop can be exercised without one.
     */
    fun interface Requester {
        fun uninstall(packageName: String, statusReceiver: IntentSender)
    }

    private sealed interface Signal {
        data class Reported(val status: AppInstallStatusRelay.Status) : Signal

        /** The package is gone, whether or not a status ever arrived for it. */
        data object Removed : Signal
    }

    suspend fun uninstall(
        installId: InstallId,
        label: String?,
        onConfirmationRequired: suspend (AppUninstallConfirmationIssue) -> Unit,
    ) {
        // PackageInstaller only ever removes the calling user's copy, while the app list spans
        // profiles: acting on this would silently remove the wrong install.
        val currentUser = userManager.currentUser().handle
        if (installId.userHandle != currentUser) {
            log(TAG, WARN) { "uninstall($installId): not the calling user ($currentUser)" }
            throw SystemUninstallException(context.getString(R.string.app_uninstall_other_user_unsupported))
        }

        val requestId = Uuid.random().toString()
        log(TAG, INFO) { "uninstall($installId) as $requestId" }

        coroutineScope {
            val signals = Channel<Signal>(Channel.BUFFERED)
            // UNDISPATCHED so both collectors are registered before the request can produce anything.
            val statusJob = launch(start = CoroutineStart.UNDISPATCHED) {
                statusRelay.statuses
                    .filter { it.requestId == requestId }
                    .collect { signals.send(Signal.Reported(it)) }
            }
            val removalJob = launch(start = CoroutineStart.UNDISPATCHED) {
                packageEventListener.events
                    .filterIsInstance<PackageEventListener.Event.PackageRemoved>()
                    // An update is announced as a removal with EXTRA_REPLACING, the package is still there afterwards.
                    .filter { it.packageId == installId.pkgId && !it.isReplacing }
                    .collect { signals.send(Signal.Removed) }
            }

            try {
                requester.uninstall(installId.pkgId.name, buildStatusIntent(requestId).intentSender)

                while (true) {
                    val signal = withTimeoutOrNull(MAX_WAIT_MS) { signals.receive() }
                        ?: throw SystemUninstallException(context.getString(R.string.app_uninstall_no_result))

                    when (signal) {
                        is Signal.Removed -> {
                            log(TAG, INFO) { "uninstall($installId): the package is gone" }
                            return@coroutineScope
                        }

                        is Signal.Reported -> when (val status = signal.status.status) {
                            PackageInstaller.STATUS_SUCCESS -> {
                                log(TAG, INFO) { "uninstall($installId): removed" }
                                return@coroutineScope
                            }

                            // The platform's own status for a removal the user turned down.
                            PackageInstaller.STATUS_FAILURE_ABORTED -> throw UninstallDeclinedException()

                            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                                val confirm = signal.status.userAction
                                    ?: throw SystemUninstallException("No removal confirmation was offered")
                                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                onConfirmationRequired(
                                    AppUninstallConfirmationIssue(label = label, confirmIntent = confirm)
                                )
                                try {
                                    context.startActivity(confirm)
                                } catch (e: Exception) {
                                    log(TAG, WARN) { "Confirmation could not be shown right now: ${e.asLog()}" }
                                }
                            }

                            else -> throw SystemUninstallException(
                                signal.status.message ?: "The system reported status $status"
                            )
                        }
                    }
                }
            } finally {
                statusJob.cancel()
                removalJob.cancel()
                signals.close()
            }
        }
    }

    private fun buildStatusIntent(requestId: String): PendingIntent {
        val intent = Intent(context, AppInstallStatusReceiver::class.java).apply {
            action = AppInstallStatusReceiver.ACTION_INSTALL_STATUS
            setPackage(context.packageName)
            putExtra(AppInstallStatusReceiver.EXTRA_REQUEST_ID, requestId)
            // Extras don't distinguish PendingIntents, the data does: without this every request
            // would filterEquals the previous one and FLAG_UPDATE_CURRENT would rewrite it.
            data = "butler-uninstall://$requestId".toUri()
        }
        // Mutable is mandatory: PackageInstaller fills in the status extras, and an immutable status
        // receiver is rejected outright at Butler's target SDK.
        val mutable = if (hasApiLevel(31)) {
            @Suppress("NewApi")
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        return PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutable,
        )
    }

    @Module
    @InstallIn(SingletonComponent::class)
    object RequesterModule {
        @Provides
        @Singleton
        fun requester(@ApplicationContext context: Context): Requester = Requester { packageName, statusReceiver ->
            context.packageManager.packageInstaller.uninstall(packageName, statusReceiver)
        }
    }

    companion object {
        private val TAG = logTag("Pkg", "Uninstaller", "System")
        internal const val MAX_WAIT_MS = 10 * 60 * 1000L
    }
}

/** The user answered the system's confirmation dialog with "Cancel". Never an error. */
class UninstallDeclinedException : Exception("The user declined the removal")

/** The removal could not be carried out; [message] is what will be shown for the target. */
class SystemUninstallException(message: String) : Exception(message)
