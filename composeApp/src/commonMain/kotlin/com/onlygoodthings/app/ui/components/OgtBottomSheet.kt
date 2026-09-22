package com.onlygoodthings.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.onlygoodthings.app.platform.SoftenDialogBackdrop
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.OgtMotion

/**
 * Cierra la hoja. [close] anima la salida; [closeNow] la saca al instante
 * (cuando se abre otra hoja o se cambia de ruta).
 */
class OgtSheetCloser(
    val close: () -> Unit,
    val closeNow: () -> Unit,
)

/**
 * Host de bottom sheet: scrim y panel suben y bajan con [OgtMotion].
 * El Dialog no se desmonta hasta que termina la salida.
 */
@Composable
fun OgtBottomSheet(
    onDismiss: () -> Unit,
    maxHeight: Dp = 520.dp,
    content: @Composable ColumnScope.(OgtSheetCloser) -> Unit,
) {
    val visibility = remember { MutableTransitionState(false).apply { targetState = true } }
    var finished by remember { mutableStateOf(false) }
    val closer = remember {
        OgtSheetCloser(
            close = { if (visibility.targetState) visibility.targetState = false },
            closeNow = {
                if (!finished) {
                    finished = true
                    onDismiss()
                }
            },
        )
    }
    LaunchedEffect(visibility.currentState, visibility.targetState) {
        if (!visibility.currentState && !visibility.targetState && !finished) {
            finished = true
            onDismiss()
        }
    }
    val sheet = RoundedCornerShape(topStart = OgtDimens.cardRadius, topEnd = OgtDimens.cardRadius)
    Dialog(
        onDismissRequest = closer.close,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        SoftenDialogBackdrop()
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visibleState = visibility,
                enter = OgtMotion.scrimEnter,
                exit = OgtMotion.scrimExit,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    OgtColors.canvas.copy(alpha = 0.22f),
                                    OgtColors.sand.copy(alpha = 0.48f),
                                ),
                            ),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = closer.close,
                        ),
                )
            }
            AnimatedVisibility(
                visibleState = visibility,
                enter = OgtMotion.sheetEnter,
                exit = OgtMotion.sheetExit,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(sheet)
                        .background(OgtColors.surface)
                        .border(1.dp, OgtColors.hairline, sheet)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .heightIn(max = maxHeight),
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(width = 36.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(OgtColors.stone),
                    )
                    Spacer(Modifier.height(14.dp))
                    content(closer)
                }
            }
        }
    }
}
