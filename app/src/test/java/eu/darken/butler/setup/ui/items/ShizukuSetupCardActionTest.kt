package eu.darken.butler.setup.ui.items

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.test.core.app.ApplicationProvider
import eu.darken.butler.R
import eu.darken.butler.common.adb.shizuku.ShizukuServiceState
import eu.darken.butler.common.compose.PreviewWrapper
import eu.darken.butler.common.pkgs.toPkgId
import eu.darken.butler.setup.core.SetupItem
import eu.darken.butler.setup.core.SetupModule
import eu.darken.butler.setup.core.shizuku.ShizukuSetupModule
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import testhelpers.ComposeTest

/**
 * ADB access enabled but not connected is a dead end without an action: the card names a state and
 * offers no way to reach the manager app that would change it.
 */
class ShizukuSetupCardActionTest : ComposeTest() {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun installManager() {
        val info = PackageInfo().apply {
            packageName = MANAGER_PKG
            applicationInfo = ApplicationInfo().apply {
                packageName = MANAGER_PKG
                nonLocalizedLabel = MANAGER_LABEL
            }
        }
        shadowOf(context.packageManager).installPackage(info)
    }

    private fun render(
        pkg: String,
        isInstalled: Boolean,
        serviceState: ShizukuServiceState = ShizukuServiceState.NotChecked,
        otherManagerInstalled: Boolean = false,
    ) {
        composeTestRule.setContent {
            PreviewWrapper {
                RootShizukuActions(
                    item = SetupItem(
                        type = SetupModule.Type.SHIZUKU,
                        state = ShizukuSetupModule.Result(
                            pkg = pkg.toPkgId(),
                            useShizuku = true,
                            isCompatible = true,
                            isInstalled = isInstalled,
                            otherManagerInstalled = otherManagerInstalled,
                            serviceState = serviceState,
                        ),
                        isRequired = false,
                        priority = 6,
                    ),
                    onExecuteAction = {},
                    switchLabel = context.getString(R.string.setup_use_shizuku_label),
                )
            }
        }
    }

    @Test
    fun `an installed manager is offered by name`() {
        installManager()

        render(pkg = MANAGER_PKG, isInstalled = true)

        composeTestRule
            .onNode(hasClickAction() and hasText(MANAGER_LABEL, substring = true))
            .assertIsDisplayed()
    }

    @Test
    fun `without a manager the card offers to install one`() {
        render(pkg = DEFAULT_MANAGER_PKG, isInstalled = false)

        composeTestRule
            .onNode(hasClickAction() and hasText(INSTALL_WORDING, substring = true, ignoreCase = true))
            .assertIsDisplayed()
    }

    @Test
    fun `a connected card offers no manager action`() {
        installManager()

        render(pkg = MANAGER_PKG, isInstalled = true, serviceState = ShizukuServiceState.Available)

        composeTestRule
            .onAllNodes(hasClickAction() and hasText(MANAGER_LABEL, substring = true))
            .assertCountEquals(0)
        composeTestRule
            .onAllNodes(hasClickAction() and hasText(INSTALL_WORDING, substring = true, ignoreCase = true))
            .assertCountEquals(0)
    }

    @Test
    fun `a manager for the other backend offers no install action`() {
        installManager()

        render(pkg = DEFAULT_MANAGER_PKG, isInstalled = false, otherManagerInstalled = true)

        composeTestRule
            .onAllNodes(hasClickAction() and hasText(INSTALL_WORDING, substring = true, ignoreCase = true))
            .assertCountEquals(0)
        composeTestRule
            .onNode(hasText(context.getString(R.string.setup_adb_restart_required)))
            .assertIsDisplayed()
    }

    companion object {
        private const val MANAGER_PKG = "moe.shizuku.privileged.api"
        private const val MANAGER_LABEL = "Shizuku"
        private const val DEFAULT_MANAGER_PKG = "eu.darken.porter"

        private const val INSTALL_WORDING = "install"
    }
}
