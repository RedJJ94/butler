package eu.darken.butler.common.pkgs.uninstaller

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.os.Looper
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import eu.darken.butler.common.pkgs.PackageEventListener
import eu.darken.butler.common.pkgs.Pkg
import eu.darken.butler.common.pkgs.features.InstallId
import eu.darken.butler.common.pkgs.installer.AppInstallStatusReceiver
import eu.darken.butler.common.pkgs.installer.AppInstallStatusRelay
import eu.darken.butler.common.user.UserHandle2
import eu.darken.butler.common.user.UserManager2
import eu.darken.butler.common.user.UserProfile2
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
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
 * The removal wait against a real [PackageEventListener], i.e. driven by the broadcast the platform
 * actually sends rather than by a hand-built event.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SystemUninstallerReplacingTest : BaseTest() {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private val installId = InstallId(Pkg.Id(PKG), UserHandle2(0))
    private val relay = AppInstallStatusRelay()
    private val requestedSenders = mutableListOf<IntentSender>()

    /** The request id the uninstaller generated, read back off the PendingIntent it handed out. */
    private fun requestedIds(): List<String> = requestedSenders.map { sender ->
        val pendingIntent = (sender as RoboIntentSender).pendingIntent
        shadowOf(pendingIntent).savedIntent.getStringExtra(AppInstallStatusReceiver.EXTRA_REQUEST_ID)!!
    }

    /** Hands the broadcast to the registered receivers and lets both schedulers settle. */
    private fun TestScope.broadcast(intent: Intent) {
        context.sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
    }

    /**
     * `ACTION_PACKAGE_REMOVED` with `EXTRA_REPLACING` is an update, not a removal: the package is
     * still installed afterwards, so the dialog this call is waiting on has not been answered.
     */
    @Test
    fun `a replacing removal broadcast does not complete the uninstall`() = runTest {
        // backgroundScope: the shared flow's upstream outlives the collector by design.
        val uninstaller = SystemUninstaller(
            context = context,
            statusRelay = relay,
            packageEventListener = PackageEventListener(context, backgroundScope),
            userManager = mockk<UserManager2> {
                coEvery { currentUser() } returns UserProfile2(handle = UserHandle2(0))
            },
            requester = SystemUninstaller.Requester { _, statusReceiver -> requestedSenders += statusReceiver },
        )

        val outcome = CompletableDeferred<Result<Unit>>()
        val job = launch {
            outcome.complete(runCatching { uninstaller.uninstall(installId, LABEL) {} })
        }
        // Lets the shared flow start its upstream, i.e. register the BroadcastReceiver.
        runCurrent()

        broadcast(
            Intent(Intent.ACTION_PACKAGE_REMOVED, "package:$PKG".toUri())
                .putExtra(Intent.EXTRA_REPLACING, true)
        )

        job.isActive shouldBe true

        relay.publish(
            AppInstallStatusRelay.Status(
                requestId = requestedIds().single(),
                status = PackageInstaller.STATUS_SUCCESS,
                message = null,
                userAction = null,
            )
        )
        runCurrent()

        outcome.getCompleted().isSuccess shouldBe true
    }

    companion object {
        private const val PKG = "eu.darken.myperm.testapp"
        private const val LABEL = "PP Test App"
    }
}
