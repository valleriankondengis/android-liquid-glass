// HomeScreen: the component catalog, every glass component live
// and interactive on one scrolling page.

package com.vallxz.glass.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vallxz.glass.engine.GlassBackdrop
import com.vallxz.glass.ui.AppOverlayState
import com.vallxz.glass.ui.CatalogContext
import com.vallxz.glass.ui.GlassSettings
import com.vallxz.glass.ui.appAccentColor
import com.vallxz.glass.ui.appDestructiveColor
import com.vallxz.glass.ui.appLabelColor
import com.vallxz.glass.ui.appSecondaryColor
import com.vallxz.glass.ui.appSeparatorColor
import com.vallxz.glass.ui.catalogItems
import com.vallxz.glass.ui.componentCatalog

/**
 * The Home page: the full component catalog. Each entry renders the
 * live component with its own state, so the page is both the showcase
 * and the test bed. [topPadding] clears the transparent top bar; the
 * bottom padding clears the bottom bar.
 */
@Composable
fun HomeScreen(
    backdrop: GlassBackdrop,
    settings: GlassSettings,
    overlays: AppOverlayState,
    onResetSettings: () -> Unit,
    topPadding: Dp,
) {
    val darkTheme = isSystemInDarkTheme()
    val context = CatalogContext(
        backdrop = backdrop,
        settings = settings,
        darkTheme = darkTheme,
        labelColor = appLabelColor(darkTheme),
        secondaryColor = appSecondaryColor(darkTheme),
        accentColor = appAccentColor(darkTheme),
        destructiveColor = appDestructiveColor(darkTheme),
        separatorColor = appSeparatorColor(darkTheme),
        overlays = overlays,
        onResetSettings = onResetSettings,
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding,
            bottom = 110.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        catalogItems(componentCatalog, context)
    }
}
