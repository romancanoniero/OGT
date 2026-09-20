package com.onlygoodthings.app.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.ogt_logo
import org.jetbrains.compose.resources.painterResource

/**
 * Isotipo B01 Sostener (Stitch). El PNG vive en composeResources; no se redibuja en Canvas.
 * [animated] se conserva por compatibilidad; el mark es estático.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun OgtLogo(
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    animated: Boolean = true,
) {
    Image(
        painter = painterResource(Res.drawable.ogt_logo),
        contentDescription = "OnlyGoodThings",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}
