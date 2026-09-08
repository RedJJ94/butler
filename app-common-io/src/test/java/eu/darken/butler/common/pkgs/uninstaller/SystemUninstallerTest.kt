package eu.darken.butler.common.pkgs.uninstaller

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import androidx.test.core.app.ApplicationProvider
import eu.darken.butler.common.pkgs.PackageEventListener
import eu.darken.butler.common.pkgs.Pkg
import eu.darken.butler.common.pkgs.features.InstallId
import eu.darken.butler.common.pkgs.installer.AppInstallStatusReceiver
import eu.darken.butler.common.pkgs.installer.AppInstallStatusRelay
import eu.darken.butler.common.user.UserHandle2
import eu.darken.butler.common.user.UserManager2
import eu.darken.butler.common.user.UserProfile2
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.fakes.RoboIntentSender
import testhelpers.BaseTest

/**
 * The wait loop around Android's own removal dialog: what ends it, what is ignored, and what
 * happens when nothing ever reports back.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemUninstallerTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val installId = InstallId(Pkg.Id(PKG), UserHandle2(0))
    private val relay = AppInstallStatusRelay()
    private val packageEvents = MutableSharedFlow<PackageEventListener.Event>()
    private val packageEventListener = mockk<PackageEventListener> {
        every { events } returns packageEvents
    }

    private val requestedSenders = mutableListOf<IntentSender>()

    private fun create(currentUser: Int = 0) = SystemUninstaller(
        context = context,
        statusRelay = relay,
        packageEventListener = packageEventListener,
        userManager = mockk<UserManager2> {
            coEvery { currentUser() } returns UserProfile2(handle = UserHandle2(currentUser))
        },
        requester = SystemUninstaller.Requester { _, statusReceiver -> requestedSenders += statusReceiver },
    )

    /** The request id the uninstaller generated, read back off the PendingIntent it handed out. */
    private fun requestedIds(): List<String> = requestedSenders.map { sender ->
        val pendingIntent = (sender as RoboIntentSender).pendingIntent
        shadowOf(pendingIntent).savedIntent.getStringExtra(AppInstallStatusReceiver.EXTRA_REQUEST_ID)!!
    }

    private fun status(
        requestId: String,
        status: Int,
        message: String? = null,
        userAction: Intent? = null,
    ) = AppInstallStatusRelay.Status(
        requestId = requestId,
        status = status,
        message = message,
        userAction = userAction,
    )

    private fun TestScope.startUninstall(
        uninstaller: SystemUninstaller = create(),
        onConfirmationRequired: suspend (AppUninstallConfirmationIssue) -> Unit = {},
    ): Pair<Job, CompletableDeferred<Result<Unit>>> {
        val outcome = CompletableDeferred<Result<Unit>>()
        val job = launch {
            outcome.complete(
                runCatching { uninstaller.uninstall(installId, LABEL, onConfirmationRequired) }
            )
        }
        // Never advanceUntilIdle(): the wait is a virtual-time deadline, so idling the scheduler
        // would run it out before the test ever reports anything.
        runCurrent()
        return job to outcome
    }

    @Test
    fun `a success ends the wait`() = runTest {
        val (_, outcome) = startUninstall()

        relay.publish(status(requestedIds().single(), PackageInstaller.STATUS_SUCCESS))
        runCurrent()

        outcome.getCompleted().isSuccess shouldBe true
    }

    @Test
    fun `an aborted removal is a decline`() = runTest {
        val (_, outcome) = startUninstall()

        relay.publish(status(requestedIds().single(), PackageInstaller.STATUS_FAILURE_ABORTED))
        runCurrent()

        outcome.getCompleted().exceptionOrNull().shouldBeInstanceOf<UninstallDeclinedException>()
    }

    @Test
    fun `a pending user action is offered and started`() = runTest {
        val seen = mutableListOf<AppUninstallConfirmationIssue>()
        val (_, outcome) = startUninstall(onConfirmationRequired = { seen += it })
        val confirm = Intent("eu.darken.butler.test.CONFIRM")

        relay.publish(
            status(requestedIds().single(), PackageInstaller.STATUS_PENDING_USER_ACTION, userAction = confirm)
        )
        runCurrent()

        seen.single().label shouldBe LABEL
        seen.single().confirmIntent shouldBe confirm
        shadowOf(context).nextStartedActivity?.action shouldBe "eu.darken.butler.test.CONFIRM"

        relay.publish(status(requestedIds().single(), PackageInstaller.STATUS_SUCCESS))
        runCurrent()
        outcome.getCompleted().isSuccess shouldBe true
    }

    @Test
    fun `a status for another request is ignored`() = runTest {
        val (job, outcome) = startUninstall()

        relay.publish(status("some-other-request", PackageInstaller.STATUS_SUCCESS))
        runCurrent()

        job.isActive shouldBe true

        relay.publish(status(requestedIds().single(), PackageInstaller.STATUS_SUCCESS))
        runCurrent()
        outcome.getCompleted().isSuccess shouldBe true
    }

    @Test
    fun `an unknown status fails the removal`() = runTest {
        val (_, outcome) = startUninstall()

        relay.publish(status(requestedIds().single(), PackageInstaller.STATUS_FAILURE_BLOCKED, message = "Blocked"))
        runCurrent()

        outcome.getCompleted().exceptionOrNull().shouldBeInstanceOf<SystemUninstallException>()
            .message shouldBe "Blocked"
    }

    /** The package can vanish without the status ever arriving; that is the removal succeeding. */
    @Test
    fun `a package removal event ends the wait`() = runTest {
        val (_, outcome) = startUninstall()

        packageEvents.emit(PackageEventListener.Event.PackageRemoved(Pkg.Id(PKG)))
        runCurrent()

        outcome.getCompleted().isSuccess shouldBe true
    }

    @Test
    fun `a target belonging to another user is refused before anything is requested`() = runTest {
        val (_, outcome) = startUninstall(uninstaller = create(currentUser = 11))

        outcome.getCompleted().exceptionOrNull().shouldBeInstanceOf<SystemUninstallException>()
        requestedSenders.shouldBeEmpty()
    }

    /** Extras don't distinguish PendingIntents; without distinct data the second would rewrite the first. */
    @Test
    fun `two requests carry distinct pending intents`() = runTest {
        startUninstall()
        startUninstall()

        val intents = requestedSenders.map { sender ->
            shadowOf((sender as RoboIntentSender).pendingIntent).savedIntent
        }
        intents.size shouldBe 2
        intents[0].data shouldNotBe intents[1].data
        intents[0].filterEquals(intents[1]) shouldBe false
    }

    @Test
    fun `a result that never arrives ends the wait`() = runTest {
        val (_, outcome) = startUninstall()

        advanceTimeBy(SystemUninstaller.MAX_WAIT_MS + 1)
        runCurrent()

        outcome.getCompleted().exceptionOrNull().shouldBeInstanceOf<SystemUninstallException>()

        // The late answer has nothing left to complete, and nothing to crash into either.
        relay.publish(status(requestedIds().single(), PackageInstaller.STATUS_SUCCESS))
        runCurrent()
        outcome.getCompleted().isFailure shouldBe true
    }

    companion object {
        private const val PKG = "eu.darken.myperm.testapp"
        private const val LABEL = "PP Test App"
    }
}
