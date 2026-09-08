package eu.darken.butler.workspace.ui.operations.details

import androidx.annotation.StringRes
import eu.darken.butler.workspace.R
import eu.darken.butler.workspace.core.operations.Operation

@get:StringRes
val Operation.Report.Packages.Outcome.Status.labelRes: Int
    get() = when (this) {
        Operation.Report.Packages.Outcome.Status.DONE -> R.string.workspace_operation_package_status_done
        Operation.Report.Packages.Outcome.Status.FAILED -> R.string.workspace_operation_package_status_failed
        Operation.Report.Packages.Outcome.Status.DECLINED -> R.string.workspace_operation_package_status_declined
    }
