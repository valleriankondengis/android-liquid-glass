// GlassCard: a glass card and a plain glass box for grouped content.
// Cards can export their glass background for nested glass inside.

package com.vallxz.glass.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.vallxz.glass.GlassStyle
import com.vallxz.glass.GlassSurface
import com.vallxz.glass.engine.GlassBackdrop

/**
 * A glass card: a [GlassSurface] with a [ColumnScope] content block, for
 * stacked card content (title, body, actions). No content padding is
 * applied by default; rows inside supply their own padding.
 *
 * The backdrop resolves like [GlassSurface] (the explicit argument,
 * else the nearest `LocalGlassBackdrop`). Pass
 * [exportBackdrop] to let glass surfaces inside the card sample the
 * card's own finished pixels.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    style: GlassStyle = GlassStyle.Default,
    shape: Shape = RoundedCornerShape(22.dp),
    exportBackdrop: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        style = style,
        shape = shape,
        exportBackdrop = exportBackdrop,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/**
 * A glass container with a [BoxScope] content block, for freely positioned
 * content (overlays, badges, centered art). Same backdrop resolution as
 * [GlassSurface].
 */
@Composable
fun GlassBox(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = null,
    style: GlassStyle = GlassStyle.Default,
    shape: Shape = RoundedCornerShape(22.dp),
    exportBackdrop: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        backdrop = backdrop,
        modifier = modifier,
        style = style,
        shape = shape,
        exportBackdrop = exportBackdrop,
        content = content,
    )
}
