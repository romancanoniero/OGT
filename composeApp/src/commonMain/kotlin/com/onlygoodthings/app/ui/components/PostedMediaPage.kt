package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.media.OgtMediaCache
import com.onlygoodthings.app.platform.OgtPostedVideo
import com.onlygoodthings.app.resources.*
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.screens.OgtPostImage
import com.onlygoodthings.shared.data.local.LocalPostMedia
import com.onlygoodthings.shared.domain.MediaKind
import org.jetbrains.compose.resources.DrawableResource
import kotlinx.coroutines.launch

private object PostedPlayback {
    var activeId by mutableStateOf<String?>(null)
    var muted by mutableStateOf(false)
    private var sequence=0L
    fun newPlayerId()="posted-${++sequence}"
}

fun pausePostedMediaPlayback() { PostedPlayback.activeId=null }

/** One player at a time; changing page, leaving the viewport or disposing pauses it. */
@Composable
fun PostedMediaPage(
    item:LocalPostMedia?, fallback:DrawableResource, description:String?,
    active:Boolean, multiple:Boolean, modifier:Modifier=Modifier, autoPlay:Boolean=false, inlineVideo:Boolean=true,
    onPrevious:()->Unit, onNext:()->Unit, onOpen:(()->Unit)?=null,
) {
    val video=item?.kind==MediaKind.VIDEO
    val preview=LocalInspectionMode.current
    var loaded by remember(item?.id,item?.url) { mutableStateOf(false) }
    var visible by remember { mutableStateOf(true) }
    val key=remember(item?.id,item?.url) { PostedPlayback.newPlayerId() }
    val playing=loaded && active && visible && PostedPlayback.activeId==key
    // Recheck on play: the background prefetch can finish after this page was composed.
    val url=remember(item?.url,loaded) { item?.url?.let { OgtMediaCache.cachedFileUrl(it) ?: it } }
    fun toggle() {
        if(video) {
            if(!inlineVideo && onOpen!=null) { onOpen();return }
            loaded=true;PostedPlayback.activeId=if(playing)null else key
        }
    }
    var attemptedAuto by remember(key) { mutableStateOf(false) }
    LaunchedEffect(active,autoPlay) {
        if(active && autoPlay && video && !attemptedAuto) { attemptedAuto=true;loaded=true;PostedPlayback.activeId=key }
    }
    DisposableEffect(key) {
        onDispose { if(PostedPlayback.activeId==key)PostedPlayback.activeId=null }
    }
    LaunchedEffect(active,visible) { if((!active || !visible) && PostedPlayback.activeId==key)PostedPlayback.activeId=null }
    Box(modifier.background(OgtColors.ink).onGloballyPositioned { visible=!it.boundsInWindow().isEmpty }) {
        if(item!=null) OgtPostImage(item,fallback,description,Modifier.fillMaxSize())
        else androidx.compose.foundation.Image(org.jetbrains.compose.resources.painterResource(fallback),description,Modifier.fillMaxSize(),contentScale=androidx.compose.ui.layout.ContentScale.Fit)
        if(video && loaded && url!=null && !preview) {
            OgtPostedVideo(url,Modifier.fillMaxSize(),playing,PostedPlayback.muted) {
                loaded=false;if(PostedPlayback.activeId==key)PostedPlayback.activeId=null
            }
        }
        // Keep the detector alive when playing/page visibility changes during a swipe.
        val tap by rememberUpdatedState<(Float,Float)->Unit>({ x,width ->
            when {
                video && (!multiple || x in width*.30f..width*.70f) -> toggle()
                multiple && x<width/2f -> onPrevious()
                multiple -> onNext()
                else -> onOpen?.invoke()
            }
        })
        val previous by rememberUpdatedState(onPrevious)
        val next by rememberUpdatedState(onNext)
        val hasPages by rememberUpdatedState(multiple)
        Box(Modifier.fillMaxSize().pointerInput(Unit) {
            var downX=0f
            detectTapGestures(onPress={downX=it.x},onTap={at ->
                // A fast swipe may arrive as down/up without intermediate move samples.
                // Normal pager drags consume the gesture before this fallback runs.
                val dx=at.x-downX
                if(hasPages && kotlin.math.abs(dx)>viewConfiguration.touchSlop*2) {
                    if(dx<0)next() else previous()
                } else tap(at.x,size.width.toFloat())
            })
        })
        if(video) {
            if(!playing) Box(Modifier.align(Alignment.Center).size(52.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha=.58f)).clickable { toggle() },contentAlignment=Alignment.Center) {
                OgtStitchIcon(Res.drawable.qs_play,"Reproducir video",tint=Color.White)
            }
            // A small pause target remains accessible without a transport bar.
            if(playing) Box(Modifier.align(Alignment.TopStart).padding(start=10.dp,top=46.dp).size(36.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha=.42f)).clickable { toggle() },contentAlignment=Alignment.Center) {
                OgtStitchIcon(Res.drawable.qs_pause,"Pausar video",size=16.dp,tint=Color.White)
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(40.dp).clip(CircleShape)
                .background(Color.Black.copy(alpha=.58f)).clickable { PostedPlayback.muted=!PostedPlayback.muted },contentAlignment=Alignment.Center) {
                OgtStitchIcon(if(PostedPlayback.muted)Res.drawable.qs_sound_off else Res.drawable.qs_sound_on,
                    if(PostedPlayback.muted)"Activar sonido" else "Silenciar video",size=18.dp,tint=Color.White)
            }
        }
        if(onOpen!=null) Box(Modifier.align(Alignment.TopEnd).padding(top=8.dp,end=8.dp).size(48.dp).clip(CircleShape)
            .background(Color.Black.copy(alpha=.42f)).clickable { onOpen() },contentAlignment=Alignment.Center) {
            OgtStitchIcon(Res.drawable.qs_expand,"Ampliar archivo actual",size=24.dp,tint=Color.White)
        }
    }
}

@Composable
fun PostMediaTypes(media:List<LocalPostMedia>,modifier:Modifier=Modifier) {
    Row(modifier.padding(vertical=4.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        val photo=media.any { it.kind==MediaKind.IMAGE };val video=media.any { it.kind==MediaKind.VIDEO }
        OgtStitchIcon(Res.drawable.qs_media_photo,if(photo)"Contiene fotos" else "Sin fotos",size=14.dp,
            tint=if(photo)OgtColors.secondary else OgtColors.muted.copy(alpha=.35f))
        OgtStitchIcon(Res.drawable.qs_media_video,if(video)"Contiene videos" else "Sin videos",size=14.dp,
            tint=if(video)OgtColors.secondary else OgtColors.muted.copy(alpha=.35f))
    }
}

@Composable
fun PostedMediaCarousel(
    media:List<LocalPostMedia>,fallback:DrawableResource,description:String?,modifier:Modifier=Modifier,
    initialIndex:Int=0,onOpen:((Int)->Unit)?=null,autoPlay:Boolean=false,inlineVideo:Boolean=true,
    onPageChanged:(Int)->Unit={},
    overlay:@Composable BoxScope.()->Unit={},
) {
    val pager=androidx.compose.foundation.pager.rememberPagerState(initialPage=initialIndex.coerceIn(0,(media.size-1).coerceAtLeast(0))) { media.size.coerceAtLeast(1) }
    val scope=rememberCoroutineScope()
    val pageChanged by rememberUpdatedState(onPageChanged)
    LaunchedEffect(pager) { snapshotFlow { pager.currentPage }.collect { pageChanged(it) } }
    fun go(page:Int) { scope.launch { pager.animateScrollToPage(page.coerceIn(0,(media.size-1).coerceAtLeast(0))) } }
    Box(modifier) {
        androidx.compose.foundation.pager.HorizontalPager(state=pager,modifier=Modifier.fillMaxSize(),key={media.getOrNull(it)?.id ?: it}) { page ->
            PostedMediaPage(media.getOrNull(page),fallback,description,active=pager.currentPage==page && !pager.isScrollInProgress,
                multiple=media.size>1,modifier=Modifier.fillMaxSize(),autoPlay=autoPlay,inlineVideo=inlineVideo,
                onPrevious={go(page-1)},onNext={go(page+1)},onOpen=onOpen?.let { open -> { pageChanged(page);open(page) } })
        }
        overlay()
        MediaPagerIndicator(pager.currentPage,media.size,onPageSelected={go(it)})
    }
}
