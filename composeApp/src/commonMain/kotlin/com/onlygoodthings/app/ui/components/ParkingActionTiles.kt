package com.onlygoodthings.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.parking_action_find
import com.onlygoodthings.app.resources.parking_action_parked
import com.onlygoodthings.app.resources.parking_action_search
import com.onlygoodthings.app.resources.parking_action_search_transparent
import com.onlygoodthings.app.resources.parking_action_yield
import com.onlygoodthings.app.theme.OgtColors
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

private val TileRadius = 16.dp
private val TileLabelStyle = TextStyle(fontSize=12.sp,lineHeight=15.sp,fontWeight=FontWeight.SemiBold)

enum class ParkingActionKind { PARKED, FIND, YIELD, SEARCH }

/** Cómo marcar el lugar del auto: GPS, dirección o mapa. */
enum class PlacePickKind { HERE, ADDRESS, MAP }

@Composable
fun ParkingActionRow(
    parkedLabel: String,
    yieldLabel: String,
    searchLabel: String,
    parkedEnabled: Boolean = true,
    yieldEnabled: Boolean = true,
    searchEnabled: Boolean = true,
    findParked: Boolean = false,
    yieldSelected: Boolean = false,
    showSearch: Boolean = true,
    showYield: Boolean = true,
    progress: Float = 0f,
    searchLayoutLabels: List<String> = listOf(searchLabel),
    onParked: () -> Unit,
    onYield: () -> Unit,
    onSearch: () -> Unit,
) {
    val tiles=buildList {
        add(ActionTile(parkedLabel,
            if(findParked) Res.drawable.parking_action_find else Res.drawable.parking_action_parked,
            if(findParked) "Auto con lupa: dónde lo dejé" else "Auto estacionado junto al pin",
            parkedEnabled,findParked && !yieldSelected,onParked))
        if(showYield) add(ActionTile(yieldLabel,Res.drawable.parking_action_yield,"Ceder el lugar: llaves y dos autos",yieldEnabled,yieldSelected,onYield))
        if(showSearch) add(ActionTile(searchLabel,Res.drawable.parking_action_search_transparent,"Radar de la comunidad buscando un lugar",searchEnabled,false,onSearch))
    }
    AdaptiveActionTiles(tiles,progress,if(showSearch) searchLayoutLabels else emptyList())
}

@Composable
fun PlacePickRow(
    hereLabel: String,
    addressLabel: String,
    mapLabel: String,
    selected: PlacePickKind?,
    enabled: Boolean = true,
    onHere: () -> Unit,
    onAddress: () -> Unit,
    onMap: () -> Unit,
) {
    AdaptiveActionTiles(listOf(
        ActionTile(hereLabel,Res.drawable.parking_action_search,"Marcar con la ubicación actual del GPS",enabled,selected==PlacePickKind.HERE,onHere),
        ActionTile(addressLabel,Res.drawable.parking_action_find,"Buscar y normalizar una dirección",enabled,selected==PlacePickKind.ADDRESS,onAddress),
        ActionTile(mapLabel,Res.drawable.parking_action_parked,"Ajustar el pin en el mapa",enabled,selected==PlacePickKind.MAP,onMap),
    ),0f)
}

private data class ActionTile(
    val label:String,val art:DrawableResource,val caption:String,
    val enabled:Boolean,val selected:Boolean,val onClick:()->Unit,
)

/** Measure the actual translated text at the user's font scale, not a guessed device width. */
@Composable
private fun AdaptiveActionTiles(tiles:List<ActionTile>,progress:Float,layoutLabels:List<String> = emptyList()) {
    val measurer=rememberTextMeasurer()
    val density=LocalDensity.current
    val labelStyle=LocalTextStyle.current.merge(TileLabelStyle)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widestWord=(tiles.map { it.label }+layoutLabels).maxOf { measurer.measure(it,style=labelStyle).multiParagraph.minIntrinsicWidth }
        val cellWidth=(maxWidth-8.dp*(tiles.size-1))/tiles.size.toFloat()
        val horizontal=with(density) { cellWidth < widestWord.toDp()+20.dp }
        // Text is already transparent when the stacked layout folds into the
        // original compact icon row. Expanding never clips incoming content.
        AnimatedContent(
            targetState=horizontal && progress.coerceIn(0f,1f)<.45f,
            transitionSpec={ (fadeIn(tween(160)) togetherWith fadeOut(tween(80))).using(SizeTransform(clip=false)) },
            label="parkingActionLayout",
        ) { stacked ->
            if(stacked) {
                Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    tiles.forEach { OgtActionTile(it,progress,Modifier.fillMaxWidth(),horizontal=true) }
                }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Max),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    tiles.forEach { OgtActionTile(it,progress,Modifier.weight(1f).fillMaxHeight(),horizontal=false) }
                }
            }
        }
    }
}

@Composable
private fun OgtActionTile(tile:ActionTile,progress:Float,modifier:Modifier,horizontal:Boolean) {
    val fill=when {
        !tile.enabled -> OgtColors.disabledFill
        tile.selected -> OgtColors.stone
        else -> OgtColors.sand
    }
    val ink=if(tile.enabled) OgtColors.ink else OgtColors.disabledInk
    val t=progress.coerceIn(0f,1f)
    val labelAlpha=(1f-t/.45f).coerceIn(0f,1f)
    val labelCollapse=((t-.45f)/.55f).coerceIn(0f,1f)
    val grayArt=ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    val shell=modifier
        .heightIn(min=if(horizontal) lerp(80.dp,56.dp,t) else lerp(132.dp,72.dp,t))
        .clip(RoundedCornerShape(TileRadius))
        .background(fill)
        .border(1.dp,if(tile.enabled) OgtColors.hairline else OgtColors.disabledLine,RoundedCornerShape(TileRadius))
        .semantics { contentDescription=tile.label }
        .clickable(enabled=tile.enabled,role=Role.Button,onClick=tile.onClick)
        .padding(lerp(10.dp,6.dp,t))
    val art:@Composable ()->Unit = {
        Image(painter=painterResource(tile.art),contentDescription=tile.caption,
            modifier=Modifier.size(lerp(if(horizontal) 56.dp else 72.dp,40.dp,t))
                .graphicsLayer { alpha=if(tile.enabled) 1f else .55f },
            contentScale=androidx.compose.ui.layout.ContentScale.Fit,
            colorFilter=if(tile.enabled) null else grayArt)
    }
    val label:@Composable (Modifier)->Unit = { labelModifier ->
        Text(tile.label,style=TileLabelStyle,color=ink,
            textAlign=if(horizontal) TextAlign.Start else TextAlign.Center,
            // At rest, the full measured text determines the card height. Only the
            // existing scroll transition collapses it after fading it out.
            modifier=labelModifier.clipToBounds().layout { measurable,constraints ->
                val placeable=measurable.measure(constraints)
                val height=(placeable.height*(1f-labelCollapse)).roundToInt()
                layout(placeable.width,height) { placeable.placeRelative(0,0) }
            }.graphicsLayer { alpha=labelAlpha })
    }
    if(horizontal) {
        Row(shell,verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            art();label(Modifier.weight(1f))
        }
    } else {
        Column(shell,horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(lerp(6.dp,0.dp,t),Alignment.CenterVertically)) {
            art();label(Modifier.fillMaxWidth())
        }
    }
}
