package com.onlygoodthings.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.OgtMotion
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource

/**
 * Reel de producto en login/registro. Avanza solo, sin prisa.
 * Usa los tiles de onboarding; no es un video ni un loader.
 */
@Composable
internal fun AuthFeatureReel(modifier: Modifier = Modifier) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(OgtMotion.reelHoldMs)
            index = (index + 1) % OnboardPages.size
        }
    }
    val page = OnboardPages[index]
    Column(
        modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(168.dp)
                .clip(RoundedCornerShape(OgtDimens.cardRadius))
                .background(OgtColors.sand),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { OgtMotion.enterUp togetherWith OgtMotion.exitDown },
                label = "auth-reel",
            ) { scene ->
                Image(
                    painter = painterResource(scene.image),
                    contentDescription = scene.title,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = { OgtMotion.enterUp togetherWith OgtMotion.exitDown },
            label = "auth-reel-copy",
        ) { scene ->
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    scene.kicker,
                    color = OgtColors.secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    scene.title,
                    color = OgtColors.ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            OnboardPages.indices.forEach { i ->
                val on = i == index
                val width by animateFloatAsState(
                    targetValue = if (on) 22f else 8f,
                    animationSpec = OgtMotion.layout,
                    label = "reel-dot",
                )
                Box(
                    Modifier
                        .height(6.dp)
                        .width(width.dp)
                        .clip(CircleShape)
                        .background(if (on) OgtColors.secondary else OgtColors.stone),
                )
            }
        }
    }
}
