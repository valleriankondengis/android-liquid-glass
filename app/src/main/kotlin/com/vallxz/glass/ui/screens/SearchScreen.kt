// SearchScreen: search the component catalog by name or keyword.
// Matching components render live, exactly as on the Home page.

package com.vallxz.glass.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vallxz.glass.R
import com.vallxz.glass.components.GlassSearchBar
import com.vallxz.glass.ui.SearchCancelButton
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
 * The Search page: a glass search field over the catalog. Nothing is
 * listed until a query is typed; matches render live, so "Slider"
 * shows the slider and the double slider. While the field is focused
 * or non-empty, one spring progress (0.84/420) drives the field shrink
 * and the reserved Cancel width (10 dp gap + 56 dp button), so the
 * field narrows gradually as Cancel is revealed. The Cancel label
 * only appears once the button is wide enough to show it whole, and
 * fades out first when collapsing, so it never looks squeezed.
 * Cancel clears the text and the focus. [topPadding] clears the
 * transparent top bar.
 */
@Composable
fun SearchScreen(
    backdrop: GlassBackdrop,
    settings: GlassSettings,
    overlays: AppOverlayState,
    onResetSettings: () -> Unit,
    topPadding: Dp,
) {
    val darkTheme = isSystemInDarkTheme()
    val labelColor = appLabelColor(darkTheme)
    val secondaryColor = appSecondaryColor(darkTheme)
    val accentColor = appAccentColor(darkTheme)
    val focusManager = LocalFocusManager.current

    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val fieldActive = focused || query.isNotEmpty()
    // One spring progress drives the field shrink and the Cancel
    // width together.
    val cancelProgress by animateFloatAsState(
        targetValue = if (fieldActive) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 420f),
        label = "searchCancel",
    )
    val context = CatalogContext(
        backdrop = backdrop,
        settings = settings,
        darkTheme = darkTheme,
        labelColor = labelColor,
        secondaryColor = secondaryColor,
        accentColor = accentColor,
        destructiveColor = appDestructiveColor(darkTheme),
        separatorColor = appSeparatorColor(darkTheme),
        overlays = overlays,
        onResetSettings = onResetSettings,
    )
    val results = remember(query) {
        componentCatalog.filter { it.matches(query) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            GlassSearchBar(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focused = it.isFocused },
                backdrop = backdrop,
                searchIcon = painterResource(R.drawable.ic_search),
                clearIcon = painterResource(R.drawable.ic_close),
                placeholder = "Search components",
                // The bar follows the Settings recipe, with no surface
                // wash: the surface tint slider veils cards, not the bar.
                style = settings.toGroupStyle(darkTheme)
                    .copy(surfaceColor = Color.Unspecified),
                contentColor = labelColor,
                shrinkProgress = cancelProgress,
            )
            SearchCancelButton(
                progress = cancelProgress,
                onClick = {
                    query = ""
                    focusManager.clearFocus()
                },
            ) {
                BasicText(
                    text = "Cancel",
                    style = TextStyle(color = accentColor, fontSize = 17.sp),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
        if (query.isBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(secondaryColor),
                    modifier = Modifier.size(44.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                BasicText(
                    text = "Search the catalog",
                    style = TextStyle(
                        color = labelColor,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    ),
                )
                Spacer(modifier = Modifier.height(4.dp))
                BasicText(
                    text = "Try a component name, like “Slider”.",
                    style = TextStyle(
                        color = secondaryColor,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            }
        } else if (results.isEmpty()) {
            BasicText(
                text = "No Results for “$query”",
                style = TextStyle(color = labelColor, fontSize = 17.sp),
                modifier = Modifier.padding(start = 16.dp, top = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 18.dp,
                    bottom = 110.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                catalogItems(results, context)
            }
        }
    }
}
