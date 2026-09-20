package com.onlygoodthings.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.nav.ComposePostKind
import com.onlygoodthings.app.nav.OgtBottomTabs
import com.onlygoodthings.app.nav.OgtRoute
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.feed_avatar_me
import com.onlygoodthings.app.resources.qs_adopt
import com.onlygoodthings.app.resources.qs_attend
import com.onlygoodthings.app.resources.qs_back
import com.onlygoodthings.app.resources.qs_claim
import com.onlygoodthings.app.resources.qs_feed
import com.onlygoodthings.app.resources.qs_invite
import com.onlygoodthings.app.resources.qs_notifications
import com.onlygoodthings.app.resources.qs_paw
import com.onlygoodthings.app.resources.qs_pets
import com.onlygoodthings.app.resources.qs_plus
import com.onlygoodthings.app.resources.qs_skills
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.OgtMotion
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val Hairline = BorderStroke(1.dp, OgtColors.hairline)
private val Pill = RoundedCornerShape(OgtDimens.pill)

/** Botón circular 44px: hairline + glifo. Activo = pozo arena + glifo harbor. */
@Composable
fun CircleIconButton(
    art: DrawableResource,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    muted: Boolean = false,
    enabled: Boolean = true,
) {
    val bg = when {
        !enabled -> OgtColors.disabledFill
        filled -> OgtColors.sand
        else -> Color.White
    }
    val glyph = when {
        !enabled -> OgtColors.disabledInk
        filled -> OgtColors.secondary
        muted -> OgtColors.muted
        else -> OgtColors.ink
    }
    val stroke = if (enabled) Hairline else BorderStroke(1.dp, OgtColors.disabledLine)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) OgtMotion.pressScale else 1f,
        animationSpec = OgtMotion.press,
        label = "chrome-press",
    )
    Box(
        modifier
            .requiredSize(OgtDimens.iconButton)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(bg)
            .border(stroke, CircleShape)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OgtStitchIcon(art, label, size = OgtDimens.glyph, tint = glyph)
    }
}

@Composable
fun OgtBackButton(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = OgtColors.ink,
    forward: Boolean = false,
) {
    Box(
        modifier
            .zIndex(2f)
            .size(OgtDimens.iconButton)
            .clip(CircleShape)
            .background(Color.White)
            .border(Hairline, CircleShape)
            .clickable(onClick = onBack)
            .graphicsLayer { if (forward) scaleX = -1f },
        contentAlignment = Alignment.Center,
    ) {
        OgtStitchIcon(Res.drawable.qs_back, if (forward) "Siguiente" else "Volver", tint = tint)
    }
}

/** Compat: onboarding usa el chevron hacia adelante. */
@Composable
fun OgtBackChevron(
    modifier: Modifier = Modifier,
    tint: Color = OgtColors.ink,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    forward: Boolean = false,
) {
    Box(
        modifier
            .size(iconSize)
            .graphicsLayer { if (forward) scaleX = -1f },
        contentAlignment = Alignment.Center,
    ) {
        OgtStitchIcon(Res.drawable.qs_back, if (forward) "Siguiente" else "Volver", size = iconSize, tint = tint)
    }
}

@Composable
fun OgtTopBar(
    title: String = "OnlyGoodThings",
    onBack: (() -> Unit)? = null,
    onNotifications: () -> Unit = {},
    onProfile: () -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .zIndex(2f)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = OgtDimens.margin, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            OgtBackButton(onBack)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = OgtColors.ink,
            letterSpacing = (-0.3).sp,
        )
        CircleIconButton(Res.drawable.qs_notifications, "Alertas", onNotifications)
        Spacer(Modifier.width(8.dp))
        Image(
            painterResource(Res.drawable.feed_avatar_me),
            contentDescription = "Perfil",
            modifier = Modifier
                .size(OgtDimens.iconButton)
                .clip(CircleShape)
                .border(Hairline, CircleShape)
                .clickable(onClick = onProfile),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun CircleStitchAction(art: DrawableResource, label: String, onClick: () -> Unit) {
    CircleIconButton(art, label, onClick)
}

/** Fila compacta de radar: dirección exacta, vehículo y pedir lugar a la derecha. */
@Composable
fun RadarSpotRow(
    address: String,
    vehicle: String,
    selected: Boolean,
    claimEnabled: Boolean,
    claimLabel: String,
    onSelect: () -> Unit,
    onClaim: () -> Unit,
    distance: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) OgtColors.sand else Color.White)
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    address,
                    modifier = Modifier.weight(1f),
                    color = OgtColors.ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!distance.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        distance,
                        color = OgtColors.muted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
            Text(
                vehicle,
                color = OgtColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        CircleIconButton(
            art = Res.drawable.qs_claim,
            label = claimLabel,
            onClick = onClaim,
            filled = selected && claimEnabled,
            muted = !claimEnabled,
            enabled = claimEnabled,
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(OgtColors.hairline))
}

@Composable
fun OgtPrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(OgtDimens.buttonRadius)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) OgtMotion.pressScale else 1f,
        animationSpec = OgtMotion.press,
        label = "cta-press",
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .fillMaxWidth()
            .height(OgtDimens.touch)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = shape,
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = OgtColors.ink,
            disabledContainerColor = OgtColors.disabledFill,
            disabledContentColor = OgtColors.disabledInk,
        ),
        border = BorderStroke(1.dp, if (enabled) OgtColors.hairline else OgtColors.disabledLine),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun OgtSecondaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(OgtDimens.buttonRadius)
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(OgtDimens.touch),
        shape = shape,
        contentPadding = PaddingValues(horizontal = 16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = OgtColors.sand,
            contentColor = OgtColors.ink,
            disabledContainerColor = OgtColors.disabledFill,
            disabledContentColor = OgtColors.disabledInk,
        ),
        border = BorderStroke(1.dp, if (enabled) Color.Transparent else OgtColors.disabledLine),
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

@Composable
fun OgtPill(text: String, tint: Color = OgtColors.sand, ink: Color = OgtColors.ink) {
    Text(
        text,
        modifier = Modifier
            .clip(Pill)
            .background(tint)
            .border(Hairline, Pill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = ink,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun OgtCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .shadow(8.dp, RoundedCornerShape(OgtDimens.cardRadius), ambientColor = Color(0x0F0A0A0B))
            .clip(RoundedCornerShape(OgtDimens.cardRadius))
            .background(OgtColors.surface)
            .border(1.dp, OgtColors.hairline, RoundedCornerShape(OgtDimens.cardRadius))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(OgtDimens.buttonGap),
    ) { content() }
}

@Composable
fun OgtSectionTitle(text: String) {
    Text(text, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = OgtColors.ink, letterSpacing = (-0.4).sp)
}

@Composable
fun OgtCaption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, color = OgtColors.muted, fontSize = 14.sp, lineHeight = 20.sp)
}

private val DockPad = 6.dp
private val DockChrome = OgtDimens.iconButton
private val DockComposeRow = 56.dp
private val DockGap = 6.dp
private val DockChromeButtons = OgtBottomTabs.size + 1
private val DockCollapsedWidth =
    DockPad * 2 + OgtDimens.iconButton * DockChromeButtons + DockGap * (DockChromeButtons - 1)
private val DockComposeBlock =
    DockComposeRow * ComposePostKind.entries.size + DockGap * (ComposePostKind.entries.size - 1)

@Composable
fun OgtBottomBar(
    current: OgtRoute,
    onSelect: (OgtRoute) -> Unit,
    onCompose: (ComposePostKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val copy = LocalOgtCopy.current
    var open by remember { mutableStateOf(false) }
    val unfold by animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = OgtMotion.layout,
        label = "dock-unfold",
    )
    val scrim by animateFloatAsState(
        targetValue = if (open) 0.16f else 0f,
        animationSpec = OgtMotion.fade,
        label = "dock-scrim",
    )
    LaunchedEffect(current) { open = false }
    val composeH = lerp(0.dp, DockComposeBlock, unfold)
    val chromeH = lerp(DockChrome, 0.dp, unfold)
    val height = DockPad + composeH + chromeH + DockPad
    // Cerrado: cápsula (56/2). Abierto: card redondeada, no óvalo.
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.fillMaxSize()) {
        if (scrim > 0.01f || unfold > 0.01f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(OgtColors.ink.copy(alpha = scrim))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { open = false },
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
                .width(DockCollapsedWidth)
                .height(height)
                .shadow(12.dp, shape, ambientColor = Color(0x140A0A0B))
                .clip(shape)
                .background(Color.White)
                .border(Hairline, shape)
                .padding(DockPad),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(composeH)
                    .graphicsLayer {
                        alpha = unfold
                        translationY = (1f - unfold) * 12f
                    },
            ) {
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(DockGap),
                ) {
                    ComposePostKind.entries.forEach { kind ->
                        val spec = composeDockSpec(kind)
                        ComposeDockRow(
                            art = spec.art,
                            title = spec.title,
                            enabled = unfold > 0.85f,
                            onClick = {
                                open = false
                                onCompose(kind)
                            },
                        )
                    }
                }
            }
            if (chromeH > 0.5.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(chromeH)
                        .graphicsLayer { alpha = 1f - unfold },
                    horizontalArrangement = Arrangement.spacedBy(DockGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OgtBottomTabs.forEach { route ->
                        val selected = current == route
                        CircleIconButton(
                            art = tabStitchArt(route),
                            label = tabLabel(route, copy),
                            onClick = { if (unfold < 0.15f) onSelect(route) },
                            filled = selected,
                            muted = !selected,
                            enabled = unfold < 0.2f,
                        )
                    }
                    CircleIconButton(
                        art = Res.drawable.qs_plus,
                        label = "Publicar",
                        onClick = { open = true },
                        filled = false,
                    )
                }
            }
        }
    }
}

private data class ComposeDockSpec(val art: DrawableResource, val title: String)

private fun composeDockSpec(kind: ComposePostKind) = when (kind) {
    ComposePostKind.ACTION -> ComposeDockSpec(Res.drawable.qs_feed, "Buena acción")
    ComposePostKind.GATHERING -> ComposeDockSpec(Res.drawable.qs_attend, "Convocatoria")
    ComposePostKind.LOST -> ComposeDockSpec(Res.drawable.qs_paw, "Mascota perdida")
    ComposePostKind.ADOPT -> ComposeDockSpec(Res.drawable.qs_adopt, "Adopción")
    ComposePostKind.SKILL -> ComposeDockSpec(Res.drawable.qs_skills, "Intercambio de Ayuda")
    ComposePostKind.TERNURA -> ComposeDockSpec(Res.drawable.qs_pets, "Ternura")
    ComposePostKind.HOMENAJE -> ComposeDockSpec(Res.drawable.qs_invite, "Homenajes")
}

@Composable
private fun ComposeDockRow(
    art: DrawableResource,
    title: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) OgtMotion.pressScale else 1f,
        OgtMotion.press,
        label = "dock-row",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(22.dp))
            .clickable(enabled = enabled, interactionSource = press, indication = null, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(OgtDimens.iconButton)
                .clip(CircleShape)
                .background(OgtColors.sand),
            contentAlignment = Alignment.Center,
        ) {
            OgtStitchIcon(art, title, size = OgtDimens.glyph, tint = OgtColors.secondary)
        }
        Text(
            title,
            color = OgtColors.ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = OgtDimens.margin),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

private fun tabLabel(route: OgtRoute, copy: com.onlygoodthings.app.i18n.OgtCopy) = when (route) {
    OgtRoute.Feed -> copy.tabHome
    OgtRoute.Parking -> copy.tabParking
    OgtRoute.Animals -> copy.tabPets
    OgtRoute.Skills -> copy.tabSkills
    OgtRoute.Settings -> copy.tabProfile
    else -> route.title
}

@Composable
fun GhostLink(text: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = Pill,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Text(text, color = OgtColors.ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
