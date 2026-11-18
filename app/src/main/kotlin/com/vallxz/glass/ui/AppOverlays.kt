// AppOverlays: the dialog, menu, and bottom-sheet overlays shared
// by every page, rendered above the screens.

package com.vallxz.glass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.vallxz.glass.components.GlassDialog
import com.vallxz.glass.components.GlassBottomSheet
import com.vallxz.glass.components.GlassMenu
import com.vallxz.glass.components.GlassMenuItem
import com.vallxz.glass.engine.GlassBackdrop

/** A pending dialog request from a page. Button colors override the defaults when set. */
data class DialogRequest(
    val title: String,
    val message: String,
    val confirmText: String,
    val onConfirm: () -> Unit,
    val cancelText: String? = null,
    val onCancel: (() -> Unit)? = null,
    val destructive: Boolean = false,
    val confirmColor: Color = Color.Unspecified,
    val cancelColor: Color = Color.Unspecified,
)

/** A pending menu request from a page. */
data class MenuRequest(
    val anchorBounds: IntRect,
    val items: List<GlassMenuItem>,
)

/**
 * Overlay state hoisted to the app root so dialogs, the sheet, and menus
 * render above everything, including the bottom bar. Pages request
 * overlays through this holder instead of rendering their own.
 */
class AppOverlayState {
    var dialog by mutableStateOf<DialogRequest?>(null)
        private set

    var sheetVisible by mutableStateOf(false)

    var menu by mutableStateOf<MenuRequest?>(null)
        private set

    fun showDialog(request: DialogRequest) {
        dialog = request
    }

    fun dismissDialog() {
        dialog = null
    }

    fun showMenu(anchorBounds: IntRect, items: List<GlassMenuItem>) {
        menu = MenuRequest(anchorBounds, items)
    }

    fun dismissMenu() {
        menu = null
    }
}

/**
 * Renders the pending overlays. The last dialog/menu request is kept after
 * dismissal so the exit animation plays with the same content instead of
 * unmounting instantly.
 */
@Composable
fun AppOverlays(
    state: AppOverlayState,
    backdrop: GlassBackdrop,
) {
    var lastDialog by remember { mutableStateOf<DialogRequest?>(null) }
    if (state.dialog != null) lastDialog = state.dialog
    lastDialog?.let { request ->
        GlassDialog(
            visible = state.dialog != null,
            onDismissRequest = { state.dismissDialog() },
            title = request.title,
            message = request.message,
            confirmText = request.confirmText,
            onConfirm = request.onConfirm,
            backdrop = backdrop,
            cancelText = request.cancelText,
            onCancel = request.onCancel,
            destructive = request.destructive,
            confirmColor = request.confirmColor,
            cancelColor = request.cancelColor,
        )
    }

    GlassBottomSheet(
        visible = state.sheetVisible,
        onDismissRequest = { state.sheetVisible = false },
        title = "Bottom Sheet",
        backdrop = backdrop,
    ) {
        SheetContent(onDismiss = { state.sheetVisible = false })
    }

    var lastMenu by remember { mutableStateOf<MenuRequest?>(null) }
    if (state.menu != null) lastMenu = state.menu
    lastMenu?.let { request ->
        GlassMenu(
            expanded = state.menu != null,
            onDismissRequest = { state.dismissMenu() },
            anchorBounds = request.anchorBounds,
            items = request.items,
            backdrop = backdrop,
        )
    }
}

@Composable
private fun SheetContent(onDismiss: () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val labelColor = appLabelColor(darkTheme)
    val secondaryColor = appSecondaryColor(darkTheme)
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            // Clip the rows to the card shape so the row press wash
            // follows the rounded corners instead of drawing square.
            .clip(RoundedCornerShape(22.dp))
            .background(
                color = (if (darkTheme) Color(0xFF2C2C2E) else Color(0xFFF2F2F7))
                    .copy(alpha = 0.72f),
                shape = RoundedCornerShape(22.dp),
            ),
    ) {
        listOf("Sheet 1", "Sheet 2", "Sheet 3", "Sheet 4").forEachIndexed { index, label ->
            if (index > 0) {
                RowDivider(color = appSeparatorColor(darkTheme), symmetricInset = true)
            }
            TextRow(
                label = label,
                labelColor = labelColor,
                secondaryColor = secondaryColor,
                chevron = true,
                onClick = onDismiss,
            )
        }
    }
}
