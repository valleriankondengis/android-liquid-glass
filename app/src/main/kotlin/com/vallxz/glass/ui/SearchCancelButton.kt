// SearchCancelButton: the action button beside the search field.
// Its width follows a reveal progress while its content - a text label,
// an icon, or anything else - is caller-provided.

package com.vallxz.glass.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The button revealed beside the search field while it is active. Its
 * reserved width (10 dp gap + 56 dp button) follows [progress]: at zero
 * it takes no space, and as the width grows the end-aligned button is
 * revealed from the right edge while the field narrows. The clip also
 * clips hit testing, so the hidden button is untappable.
 *
 * Nothing about the content is fixed: [content] is a caller-provided
 * slot, so the button can hold a "Cancel" label, an icon, or any other
 * content. The slot runs inside a centered box, so content needs no
 * alignment of its own.
 *
 * [alpha] fades the content independently of the width. By default it
 * follows [progress] through a curve that keeps the content hidden
 * until the button is wide enough to show it whole (56 of the 66 dp)
 * and fades it out first on collapse, so the content never looks
 * squeezed while the width changes in either direction. Pass an
 * explicit value to fade differently.
 */
@Composable
fun SearchCancelButton(
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    alpha: Float = ((progress - 0.85f) / 0.15f).coerceIn(0f, 1f),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .width(66.dp * progress)
            .clipToBounds()
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.CenterEnd,
    ) {
        Row {
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(width = 56.dp, height = 48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onClick() },
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
