/*
 * Tabler Icons, "snowflake-off".
 *
 * Copyright (c) 2020 Tabler, licensed under the MIT License.
 * https://github.com/tabler/tabler-icons/blob/master/LICENSE
 *
 * Vendored because the icon is not part of the material-icons-extended artifact.
 */
package eu.darken.butler.common.compose.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.PreviewWrapper as ComposePreviewWrapper
import androidx.compose.ui.unit.dp
import eu.darken.butler.common.compose.ButlerPreviewWrapper
import eu.darken.butler.common.compose.Preview2

/** Unfreezing an app: the icon for enabling a package. */
val Icons.TwoTone.SnowflakeOff: ImageVector
    get() {
        _snowflakeOff?.let { return it }
        return ImageVector.Builder(
            name = "TwoTone.SnowflakeOff",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(10f, 4f)
                lineToRelative(2f, 1f)
                lineToRelative(2f, -1f)
                moveTo(12f, 2f)
                verticalLineToRelative(6f)
                moveToRelative(1.196f, 1.186f)
                lineToRelative(1.804f, 1.034f)
                moveTo(17.928f, 6.268f)
                lineToRelative(0.134f, 2.232f)
                lineToRelative(1.866f, 1.232f)
                moveTo(20.66f, 7f)
                lineToRelative(-5.629f, 3.25f)
                lineToRelative(-0.031f, 0.75f)
                moveTo(19.928f, 14.268f)
                lineToRelative(-1.015f, 0.67f)
                moveTo(14.212f, 14.226f)
                lineToRelative(-2.171f, 1.262f)
                moveTo(14f, 20f)
                lineToRelative(-2f, -1f)
                lineToRelative(-2f, 1f)
                moveTo(12f, 22f)
                verticalLineToRelative(-6.5f)
                lineToRelative(-3f, -1.72f)
                moveTo(6.072f, 17.732f)
                lineToRelative(-0.134f, -2.232f)
                lineToRelative(-1.866f, -1.232f)
                moveTo(3.34f, 17f)
                lineToRelative(5.629f, -3.25f)
                lineToRelative(-0.01f, -3.458f)
                moveTo(4.072f, 9.732f)
                lineToRelative(1.866f, -1.232f)
                lineToRelative(0.134f, -2.232f)
                moveTo(3.34f, 7f)
                lineToRelative(5.629f, 3.25f)
                lineToRelative(0.802f, -0.466f)
                moveTo(3f, 3f)
                lineToRelative(18f, 18f)
            }
        }.build().also { _snowflakeOff = it }
    }

private var _snowflakeOff: ImageVector? = null

@Preview2
@ComposePreviewWrapper(ButlerPreviewWrapper::class)
@Composable
private fun SnowflakeOffPreview() {
    Icon(imageVector = Icons.TwoTone.SnowflakeOff, contentDescription = null)
}
