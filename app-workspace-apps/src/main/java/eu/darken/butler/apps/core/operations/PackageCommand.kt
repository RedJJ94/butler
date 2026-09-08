package eu.darken.butler.apps.core.operations

import eu.darken.butler.apps.core.details.components.ComponentEntry
import eu.darken.butler.common.ca.CaString
import eu.darken.butler.common.pkgs.features.InstallId

/**
 * What a package operation was asked to do. A single-app action is a batch of one.
 */
sealed interface PackageCommand {

    /** Every install this command acts on, in the order the user selected them. */
    val targets: List<Target>

    data class Target(
        val installId: InstallId,
        val label: CaString,
    )

    data class Enable(override val targets: List<Target>) : PackageCommand

    data class Disable(override val targets: List<Target>) : PackageCommand

    data class ForceStop(override val targets: List<Target>) : PackageCommand

    data class ClearData(override val targets: List<Target>) : PackageCommand

    /**
     * [viaSystemDialog] is decided once, at submit time, from the workspace's elevated-access
     * state: deciding later would re-read root/ADB and could run an elevated removal that nobody
     * confirmed.
     */
    data class Uninstall(
        override val targets: List<Target>,
        val viaSystemDialog: Boolean,
    ) : PackageCommand

    data class SetComponents(
        val target: Target,
        val entries: List<ComponentEntry>,
        val enabled: Boolean,
    ) : PackageCommand {
        override val targets: List<Target> get() = listOf(target)
    }
}
