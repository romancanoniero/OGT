package com.onlygoodthings.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.OgtLogo
import com.onlygoodthings.app.theme.OgtMotion
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import kotlin.math.abs
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.painterResource

/**
 * Historia de producto, separada de la autenticación.
 * Las páginas avanzan solas hasta la última. Un deslizamiento corta el temporizador
 * y deja el recorrido en manos de quien usa la app. «Continuar» solo aparece al final.
 */
@Composable
fun OnboardingScreen(onContinue: () -> Unit) {
    val pager = rememberPagerState(pageCount = { OnboardPages.size })
    val last = OnboardPages.lastIndex
    var autoplay by remember { mutableStateOf(true) }
    val reading = remember { Animatable(0f) }

    LaunchedEffect(autoplay) {
        if (!autoplay) return@LaunchedEffect
        while (isActive && autoplay) {
            val page = pager.settledPage
            if (page >= last) {
                reading.snapTo(1f)
                break
            }
            reading.snapTo(0f)
            reading.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = OgtMotion.onboardHoldMs.toInt(),
                    easing = LinearEasing,
                ),
            )
            if (!autoplay || pager.settledPage != page) break
            pager.animateScrollToPage(page + 1, animationSpec = OgtMotion.pagerScroll)
        }
    }

    val showContinue = pager.settledPage == last
    Column(
        Modifier
            .fillMaxSize()
            .background(OgtColors.canvas)
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
            .padding(horizontal = OgtDimens.margin)
            .padding(top = 8.dp, bottom = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OgtLogo(size = 28.dp, animated = false)
                Text(
                    "OnlyGoodThings",
                    color = OgtColors.ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
            Text(
                (pager.currentPage + 1).toString().padStart(2, '0'),
                color = OgtColors.muted,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                letterSpacing = 0.6.sp,
            )
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val slop = viewConfiguration.touchSlop
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            val delta = change.position - down.position
                            if (abs(delta.x) > slop && abs(delta.x) > abs(delta.y)) {
                                autoplay = false
                                break
                            }
                        }
                    }
                },
        ) { index ->
            OnboardSlide(OnboardPages[index])
        }
        OnboardProgress(
            page = if (autoplay) pager.settledPage else pager.currentPage,
            autoplay = autoplay,
            reading = if (autoplay) reading.value else 1f,
        )
        Spacer(Modifier.height(16.dp))
        OnboardContinue(visible = showContinue, onContinue = onContinue)
    }
}

/** Fuera del ColumnScope: si no, Compose elige el AnimatedVisibility de columna. */
@Composable
private fun OnboardContinue(visible: Boolean, onContinue: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(OgtDimens.touch), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = visible,
            enter = OgtMotion.enterUp,
            exit = OgtMotion.exitDown,
        ) {
            OgtPrimaryButton("Continuar", onClick = onContinue)
        }
    }
}

@Composable
private fun OnboardSlide(page: OnboardPage) {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(page.image),
                contentDescription = page.title,
                modifier = Modifier
                    .fillMaxHeight(0.92f)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            page.kicker,
            color = OgtColors.secondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            page.title,
            color = OgtColors.ink,
            fontWeight = FontWeight.Bold,
            fontSize = 26.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.5).sp,
            textAlign = TextAlign.Center,
        )
        if (page.body.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                page.body,
                modifier = Modifier.padding(bottom = 12.dp),
                color = OgtColors.muted,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun OnboardProgress(page: Int, autoplay: Boolean, reading: Float) {
    val last = OnboardPages.lastIndex
    Row(
        Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "Paso ${page + 1} de ${OnboardPages.size}"
            },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnboardPages.indices.forEach { i ->
            val fraction = when {
                i < page -> 1f
                i > page -> 0f
                !autoplay || page == last -> 1f
                else -> reading.coerceIn(0f, 1f)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(OgtColors.stone),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(CircleShape)
                        .background(OgtColors.secondary),
                )
            }
        }
    }
}
