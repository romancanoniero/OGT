package com.onlygoodthings.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.qs_clap
import com.onlygoodthings.app.resources.qs_comment
import com.onlygoodthings.app.resources.qs_heart
import com.onlygoodthings.app.resources.qs_share
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtMotion
import org.jetbrains.compose.resources.DrawableResource

/** Acción extra de la ficha (mapa, adoptar, asistiré, invitar). */
data class PostExtraAction(
    val art: DrawableResource,
    val label: String,
    val selected: Boolean = false,
    val onClick: () -> Unit,
)

/** Fila tipo Instagram: expresiones debajo de la foto, a la izquierda. */
@Composable
fun PostActionRow(
    clapped: Boolean,
    thanks: Int,
    comments: Int,
    onClap: (() -> Unit)?,
    onComments: () -> Unit,
    modifier: Modifier = Modifier,
    hearted: Boolean = false,
    hearts: Int = 0,
    onHeart: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    extras: List<PostExtraAction> = emptyList(),
    extrasVisible: Boolean = true,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (onClap != null) {
            ExpressionGlyph(
                art = Res.drawable.qs_clap,
                label = "Aplaudir",
                selected = clapped,
                count = thanks,
                burstOnSelect = true,
                onClick = onClap,
            )
        }
        if (onHeart != null) {
            ExpressionGlyph(
                art = Res.drawable.qs_heart,
                label = "Sentir",
                selected = hearted,
                count = hearts,
                burstOnSelect = true,
                onClick = onHeart,
            )
        }
        ExpressionGlyph(
            art = Res.drawable.qs_comment,
            label = "Comentar",
            selected = false,
            count = comments,
            onClick = onComments,
        )
        if (onShare != null) {
            ExpressionGlyph(
                art = Res.drawable.qs_share,
                label = "Compartir",
                selected = false,
                count = null,
                onClick = onShare,
            )
        }
        extras.forEach { extra ->
            AnimatedVisibility(
                visible = extrasVisible,
                enter = fadeIn(OgtMotion.fade) + expandHorizontally(),
                exit = fadeOut(OgtMotion.fade) + shrinkHorizontally(),
            ) {
                ExpressionGlyph(
                    art = extra.art,
                    label = extra.label,
                    selected = extra.selected,
                    count = null,
                    burstOnSelect = true,
                    onClick = extra.onClick,
                )
            }
        }
    }
}

@Composable
fun ExpressionGlyph(
    art: DrawableResource,
    label: String,
    selected: Boolean,
    count: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    burstOnSelect: Boolean = false,
    onCountClick: (() -> Unit)? = null,
    active: Color = OgtColors.expressionOn,
    idle: Color = OgtColors.expressionIdle,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) OgtMotion.pressScale else 1f,
        animationSpec = OgtMotion.press,
        label = "expr-press",
    )
    val burst = remember { Animatable(1f) }
    var sawSelect by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (burstOnSelect && selected && !sawSelect) {
            burst.snapTo(0.9f)
            burst.animateTo(OgtMotion.clapPeak, OgtMotion.press)
            burst.animateTo(1f, OgtMotion.settle)
        }
        sawSelect = selected
    }
    val tint by animateColorAsState(
        targetValue = if (selected) active else idle,
        animationSpec = OgtMotion.color,
        label = "expr-tint",
    )
    val well by animateColorAsState(
        targetValue = if (selected) active.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = OgtMotion.color,
        label = "expr-well",
    )
    Row(
        modifier.padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val s = burst.value * pressScale
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick)
                .padding(4.dp)
                .size(32.dp)
                .background(well, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            OgtStitchIcon(art, label, tint = tint)
        }
        if (count != null) {
            val openList = onCountClick
            AnimatedContent(
                targetState = count,
                transitionSpec = { fadeIn(OgtMotion.fade) togetherWith fadeOut(OgtMotion.fade) },
                label = "expr-count",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        if (openList != null && count > 0) openList() else onClick()
                    }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            ) { value ->
                Text(
                    value.toString(),
                    color = tint,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
