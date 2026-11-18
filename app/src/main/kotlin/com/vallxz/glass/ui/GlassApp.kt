// GlassApp: the app shell - the Home, About, and Search pages plus
// the Settings page, the recorded background and screen layers, the
// transparent top bar, the bottom bar, and the overlays.

package com.vallxz.glass.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vallxz.glass.R
import com.vallxz.glass.components.GlassBottomBar
import com.vallxz.glass.components.GlassIconButton
import com.vallxz.glass.components.GlassTab
import com.vallxz.glass.components.glassBarAccentColor
import com.vallxz.glass.components.glassBarStyle
import com.vallxz.glass.engine.glassBackdrop
import com.vallxz.glass.engine.rememberGlassBackdrop
import com.vallxz.glass.engine.rememberGlassMergedBackdrop
import com.vallxz.glass.ui.screens.AboutScreen
import com.vallxz.glass.ui.screens.HomeScreen
import com.vallxz.glass.ui.screens.SearchScreen
import com.vallxz.glass.ui.screens.SettingsScreen

/**
 * App shell with state-based navigation. No navigation library: the
 * Home, About, and Search pages switch on an index, and Settings
 * opens over them from the top bar, with a back button to return. New
 * pages plug in the same way: add a tab, add a `when` branch, and
 * pass the background backdrop plus the shared settings.
 *
 * The whole app draws fullscreen behind the status and navigation bars
 * (the window is edge-to-edge, see MainActivity). The background is
 * deliberately plain - white in light mode, black in dark mode, like the
 * phone's Settings. The Settings page can import a gallery image as a
 * temporary backdrop, kept only in memory while the app runs; closing
 * the app restores the plain background.
 *
 * Two layers are recorded. The
 * background layer records the plain background only, and every glass
 * surface inside the pages samples it. The screen layer records the
 * pages themselves - rows, cards, controls - and is sampled, merged
 * over the background layer, only by the floating top bar, bottom bar,
 * and the modal overlays, which are siblings drawn after the screen
 * node. A surface must never sample a layer whose recording contains
 * it: the glass inside the pages samples the background layer only, so
 * the screen recording holds glass outputs that reference a different
 * layer, and no render-node cycle can form. The top bar is transparent
 * and draws no glass of its own, so page content passes underneath it.
 */
@Composable
fun GlassApp() {
    // 0 = Home, 1 = About, 2 = Search. The pill holds Home and About
    // only - Search is a separate circular button beside it - so the
    // pill's own selection is tracked apart and left untouched while
    // Search is active, when the pill shows no droplet.
    var tab by remember { mutableIntStateOf(0) }
    var pillTab by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    // Temporary gallery backdrop, kept only in memory: importing an image
    // from the Settings page replaces the plain background until the app
    // closes. Nothing is persisted.
    var tempBackground by remember { mutableStateOf<ImageBitmap?>(null) }
    val backgroundBackdrop = rememberGlassBackdrop()
    val screenBackdrop = rememberGlassBackdrop()
    val mergedBackdrop = rememberGlassMergedBackdrop(backgroundBackdrop, screenBackdrop)
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val settingsHolder = remember { GlassSettingsHolder(context) }
    val overlays = remember { AppOverlayState() }
    // The real status-bar height, read once from the root window
    // insets: pages draw fullscreen behind the status bar, and only
    // their content padding clears it plus the 56 dp top bar. The
    // fallback covers the first frame before the insets arrive.
    var statusBarHeight by remember { mutableStateOf(24.dp) }
    val view = LocalView.current
    val density = LocalDensity.current
    LaunchedEffect(view) {
        val rootInsets = ViewCompat.getRootWindowInsets(view)
        val topPx = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        if (topPx > 0) {
            statusBarHeight = with(density) { topPx.toDp() }
        }
    }
    val topPadding = statusBarHeight + 64.dp

    Box(modifier = Modifier.fillMaxSize()) {
        // The plain background, recorded into the background layer. The
        // color (or the temporary imported image) is a CHILD of the
        // backdrop node: a background modifier on the same node would
        // draw outside the recording and the recorded layer would come
        // out empty.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdrop(backgroundBackdrop),
        ) {
            val backgroundImage = tempBackground
            if (backgroundImage != null) {
                Image(
                    bitmap = backgroundImage,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(if (darkTheme) Color.Black else Color.White),
                )
            }
        }

        // The pages, recorded into the screen layer. The recording node
        // covers the whole screen with no padding: pages draw
        // fullscreen behind the status bar, and each page pads its own
        // content below the status bar and the top bar, so content
        // scrolls underneath both. Only the top bar itself is padded
        // clear of the status bar, inside AppTopBar.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdrop(screenBackdrop),
        ) {
            if (showSettings) {
                SettingsScreen(
                    backdrop = backgroundBackdrop,
                    holder = settingsHolder,
                    overlays = overlays,
                    tempBackground = tempBackground,
                    onTempBackgroundChange = { tempBackground = it },
                    topPadding = topPadding,
                )
            } else {
                when (tab) {
                    0 -> HomeScreen(
                        backdrop = backgroundBackdrop,
                        settings = settingsHolder.settings,
                        overlays = overlays,
                        onResetSettings = { settingsHolder.reset() },
                        topPadding = topPadding,
                    )

                    1 -> AboutScreen(
                        backdrop = backgroundBackdrop,
                        settings = settingsHolder.settings,
                        topPadding = topPadding,
                    )

                    else -> SearchScreen(
                        backdrop = backgroundBackdrop,
                        settings = settingsHolder.settings,
                        overlays = overlays,
                        onResetSettings = { settingsHolder.reset() },
                        topPadding = topPadding,
                    )
                }
            }
        }

        AppTopBar(
            showBackButton = showSettings,
            onBackClick = { showSettings = false },
            onSettingsClick = { showSettings = true },
            backdrop = mergedBackdrop,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // The bottom navigation: the Home/About pill plus a separate
        // circular search button, as in Apple Music. While Search is
        // active the pill shows no droplet, and the search icon takes
        // the accent color instead. Any navigation choice leaves
        // Settings.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 30.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassBottomBar(
                tabs = listOf(
                    GlassTab("Home", painterResource(R.drawable.ic_home)),
                    GlassTab("About", painterResource(R.drawable.ic_info)),
                ),
                selectedIndex = pillTab,
                onSelected = {
                    pillTab = it
                    tab = it
                    showSettings = false
                },
                backdrop = mergedBackdrop,
                showDroplet = tab != 2,
                // The pill draws above the search button: while held,
                // the bar and its droplet grow past the pill's edge,
                // and the growth must stay visible instead of sliding
                // under the button.
                modifier = Modifier.weight(1f).zIndex(1f),
            )
            GlassIconButton(
                onClick = {
                    tab = 2
                    showSettings = false
                },
                icon = painterResource(R.drawable.ic_search),
                contentDescription = "Search",
                backdrop = mergedBackdrop,
                // The bar's own material and content colors, so the
                // detached button reads as part of the navbar: label
                // icon like the tabs, bar accent while selected.
                style = glassBarStyle(darkTheme),
                contentColor = if (tab == 2) {
                    glassBarAccentColor(darkTheme)
                } else {
                    appLabelColor(darkTheme)
                },
                size = 64.dp,
                iconSize = 24.dp,
            )
        }

        AppOverlays(
            state = overlays,
            backdrop = mergedBackdrop,
        )
    }
}
