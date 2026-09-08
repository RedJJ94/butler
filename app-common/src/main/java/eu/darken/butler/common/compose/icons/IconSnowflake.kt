/*
 * Material Symbols, "snowflake".
 *
 * Copyright Google LLC, licensed under the Apache License, Version 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Vendored because the icon is not part of the material-icons-extended artifact.
 */
package eu.darken.butler.common.compose.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.PreviewWrapper as ComposePreviewWrapper
import androidx.compose.ui.unit.dp
import eu.darken.butler.common.compose.ButlerPreviewWrapper
import eu.darken.butler.common.compose.Preview2

/** Freezing an app: the icon for disabling a package. */
val Icons.TwoTone.Snowflake: ImageVector
    get() {
        _snowflake?.let { return it }
        return ImageVector.Builder(
            name = "TwoTone.Snowflake",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 960f,
            viewportHeight = 960f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(440f, 549f)
                lineToRelative(-132f, 76f)
                lineToRelative(-20f, 110f)
                quadToRelative(-3f, 17f, -16f, 26.5f)
                reflectiveQuadToRelative(-30f, 6.5f)
                quadToRelative(-17f, -3f, -26f, -16.5f)
                reflectiveQuadToRelative(-6f, -30.5f)
                lineToRelative(8f, -43f)
                lineToRelative(-30f, 17f)
                quadToRelative(-14f, 8f, -30f, 3.5f)
                reflectiveQuadTo(134f, 680f)
                quadToRelative(-8f, -14f, -4f, -30.5f)
                reflectiveQuadToRelative(18f, -24.5f)
                lineToRelative(30f, -17f)
                lineToRelative(-42f, -15f)
                quadToRelative(-16f, -5f, -23f, -20f)
                reflectiveQuadToRelative(-1f, -31f)
                quadToRelative(5f, -16f, 20f, -22.5f)
                reflectiveQuadToRelative(31f, -1.5f)
                lineToRelative(105f, 38f)
                lineToRelative(132f, -76f)
                lineToRelative(-132f, -76f)
                lineToRelative(-105f, 38f)
                quadToRelative(-16f, 5f, -30.5f, -1.5f)
                reflectiveQuadTo(112f, 418f)
                quadToRelative(-6f, -16f, 1f, -31.5f)
                reflectiveQuadToRelative(23f, -20.5f)
                lineToRelative(42f, -14f)
                lineToRelative(-30f, -17f)
                quadToRelative(-14f, -8f, -18f, -24.5f)
                reflectiveQuadToRelative(4f, -30.5f)
                quadToRelative(8f, -14f, 24f, -18.5f)
                reflectiveQuadToRelative(30f, 3.5f)
                lineToRelative(30f, 17f)
                lineToRelative(-8f, -43f)
                quadToRelative(-3f, -17f, 6f, -30.5f)
                reflectiveQuadToRelative(26f, -16.5f)
                quadToRelative(17f, -3f, 30f, 6.5f)
                reflectiveQuadToRelative(16f, 26.5f)
                lineToRelative(20f, 110f)
                lineToRelative(132f, 76f)
                verticalLineToRelative(-153f)
                lineToRelative(-85f, -72f)
                quadToRelative(-13f, -11f, -14.5f, -27f)
                reflectiveQuadToRelative(9.5f, -29f)
                quadToRelative(11f, -13f, 27f, -14.5f)
                reflectiveQuadToRelative(29f, 9.5f)
                lineToRelative(34f, 29f)
                verticalLineToRelative(-34f)
                quadToRelative(0f, -17f, 11.5f, -28.5f)
                reflectiveQuadTo(480f, 80f)
                quadToRelative(17f, 0f, 28.5f, 11.5f)
                reflectiveQuadTo(520f, 120f)
                verticalLineToRelative(34f)
                lineToRelative(34f, -29f)
                quadToRelative(13f, -11f, 29f, -9.5f)
                reflectiveQuadToRelative(27f, 14.5f)
                quadToRelative(11f, 13f, 9.5f, 29f)
                reflectiveQuadTo(605f, 186f)
                lineToRelative(-85f, 72f)
                verticalLineToRelative(153f)
                lineToRelative(132f, -76f)
                lineToRelative(20f, -110f)
                quadToRelative(3f, -17f, 16f, -26.5f)
                reflectiveQuadToRelative(30f, -6.5f)
                quadToRelative(17f, 3f, 26f, 16.5f)
                reflectiveQuadToRelative(6f, 30.5f)
                lineToRelative(-8f, 43f)
                lineToRelative(30f, -17f)
                quadToRelative(14f, -8f, 30f, -3.5f)
                reflectiveQuadToRelative(24f, 18.5f)
                quadToRelative(8f, 14f, 4f, 30.5f)
                reflectiveQuadTo(812f, 335f)
                lineToRelative(-30f, 17f)
                lineToRelative(42f, 15f)
                quadToRelative(16f, 5f, 23f, 20f)
                reflectiveQuadToRelative(1f, 31f)
                quadToRelative(-5f, 16f, -20f, 22.5f)
                reflectiveQuadToRelative(-31f, 1.5f)
                lineToRelative(-105f, -38f)
                lineToRelative(-132f, 76f)
                lineToRelative(132f, 76f)
                lineToRelative(105f, -38f)
                quadToRelative(16f, -5f, 30.5f, 1.5f)
                reflectiveQuadTo(848f, 542f)
                quadToRelative(6f, 16f, -1f, 31.5f)
                reflectiveQuadTo(824f, 594f)
                lineToRelative(-42f, 14f)
                lineToRelative(30f, 17f)
                quadToRelative(14f, 8f, 18f, 24.5f)
                reflectiveQuadToRelative(-4f, 30.5f)
                quadToRelative(-8f, 14f, -24f, 18.5f)
                reflectiveQuadToRelative(-30f, -3.5f)
                lineToRelative(-30f, -17f)
                lineToRelative(8f, 43f)
                quadToRelative(3f, 17f, -6f, 30.5f)
                reflectiveQuadTo(718f, 768f)
                quadToRelative(-17f, 3f, -30f, -6.5f)
                reflectiveQuadTo(672f, 735f)
                lineToRelative(-20f, -110f)
                lineToRelative(-132f, -76f)
                verticalLineToRelative(153f)
                lineToRelative(85f, 72f)
                quadToRelative(13f, 11f, 14.5f, 27f)
                reflectiveQuadToRelative(-9.5f, 29f)
                quadToRelative(-11f, 13f, -27f, 14.5f)
                reflectiveQuadToRelative(-29f, -9.5f)
                lineToRelative(-34f, -29f)
                verticalLineToRelative(34f)
                quadToRelative(0f, 17f, -11.5f, 28.5f)
                reflectiveQuadTo(480f, 880f)
                quadToRelative(-17f, 0f, -28.5f, -11.5f)
                reflectiveQuadTo(440f, 840f)
                verticalLineToRelative(-34f)
                lineToRelative(-34f, 29f)
                quadToRelative(-13f, 11f, -29f, 9.5f)
                reflectiveQuadTo(350f, 830f)
                quadToRelative(-11f, -13f, -9.5f, -29f)
                reflectiveQuadToRelative(14.5f, -27f)
                lineToRelative(85f, -72f)
                verticalLineToRelative(-153f)
                close()
            }
        }.build().also { _snowflake = it }
    }

private var _snowflake: ImageVector? = null

@Preview2
@ComposePreviewWrapper(ButlerPreviewWrapper::class)
@Composable
private fun SnowflakePreview() {
    Icon(imageVector = Icons.TwoTone.Snowflake, contentDescription = null)
}
