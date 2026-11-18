// AboutScreen: about the Liquid Glass engine template and its
// maintainer.

package com.vallxz.glass.ui.screens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vallxz.glass.engine.GlassBackdrop
import com.vallxz.glass.ui.GlassSettings
import com.vallxz.glass.ui.GroupCard
import com.vallxz.glass.ui.SectionHeader
import com.vallxz.glass.ui.TextRow
import com.vallxz.glass.ui.appLabelColor
import com.vallxz.glass.ui.appSecondaryColor

/**
 * The About page: what Liquid Glass is, and who maintains it.
 * [topPadding] clears the transparent top bar.
 */
@Composable
fun AboutScreen(
    backdrop: GlassBackdrop,
    settings: GlassSettings,
    topPadding: Dp,
) {
    val darkTheme = isSystemInDarkTheme()
    val labelColor = appLabelColor(darkTheme)
    val secondaryColor = appSecondaryColor(darkTheme)
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
        item {
            Column {
                SectionHeader("About", secondaryColor)
                GroupCard(
                    settings = settings,
                    darkTheme = darkTheme,
                    backdrop = backdrop,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        BasicText(
                            text = "Liquid Glass is a user interface engine " +
                                "template in Kotlin with " +
                                "Jetpack Compose.",
                            style = TextStyle(color = labelColor, fontSize = 15.sp),
                        )
                        BasicText(
                            text = "It renders real glass surfaces: refraction, " +
                                "chromatic dispersion, specular highlights, and " +
                                "soft shadows, over live content, and ships a " +
                                "set of ready-made glass components: bars, " +
                                "sliders, selectors, menus, dialogs, and sheets.",
                            style = TextStyle(color = labelColor, fontSize = 15.sp),
                        )
                        BasicText(
                            text = "Pure native Android, with no extra " +
                                "dependencies beyond the standard Google Maven " +
                                "libraries.",
                            style = TextStyle(color = labelColor, fontSize = 15.sp),
                        )
                    }
                }
            }
        }
        item {
            Column {
                SectionHeader("Maintainer", secondaryColor)
                GroupCard(
                    settings = settings,
                    darkTheme = darkTheme,
                    backdrop = backdrop,
                ) {
                    TextRow(
                        label = "Vallerian Kondengis",
                        labelColor = labelColor,
                        secondaryColor = secondaryColor,
                        detail = "Vallxz",
                    )
                }
            }
        }
    }
}
