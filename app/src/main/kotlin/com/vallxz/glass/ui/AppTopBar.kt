// AppTopBar: the transparent top bar. No background, no glass, and
// no header text of its own - content scrolls underneath it. It only
// holds buttons: a back button on the left while in Settings, and the
// settings button on the right everywhere else.

package com.vallxz.glass.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.vallxz.glass.R
import com.vallxz.glass.components.GlassBackButton
import com.vallxz.glass.components.GlassIconButton
import com.vallxz.glass.engine.GlassBackdrop

/**
 * A transparent top bar holding buttons only. While [showBackButton]
 * is true, a circular glass back button sits on the left; otherwise
 * the circular glass settings button sits on the right. The bar
 * itself draws nothing, so page content passes underneath it; only
 * the buttons are interactive. The buttons sample [backdrop], the
 * merged background-plus-screen layer, like the bottom bar.
 */
@Composable
fun AppTopBar(
    showBackButton: Boolean,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    backdrop: GlassBackdrop,
    modifier: Modifier = Modifier,
) {
    val darkTheme = isSystemInDarkTheme()
    val labelColor = appLabelColor(darkTheme)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBackButton) {
            GlassBackButton(
                onClick = onBackClick,
                backdrop = backdrop,
                size = 44.dp,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = "Back",
                    colorFilter = ColorFilter.tint(labelColor),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(22.dp),
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (!showBackButton) {
            GlassIconButton(
                onClick = onSettingsClick,
                icon = painterResource(R.drawable.ic_settings),
                contentDescription = "Settings",
                backdrop = backdrop,
                contentColor = labelColor,
                size = 44.dp,
                iconSize = 22.dp,
            )
        }
    }
}
