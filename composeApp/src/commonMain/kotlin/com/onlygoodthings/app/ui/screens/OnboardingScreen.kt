package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtBackChevron
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

@Composable
fun OnboardingScreen(onDone: () -> Unit, onLogin: () -> Unit) {
    val pager = rememberPagerState(pageCount = { OnboardPages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == OnboardPages.lastIndex

    Column(Modifier.fillMaxSize().background(OgtColors.canvas).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("OnlyGoodThings", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = OgtColors.ink)
            Text(
                "Omitir",
                modifier = Modifier.clip(CircleShape).clickable(onClick = onDone).padding(horizontal = 12.dp, vertical = 8.dp),
                color = OgtColors.muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
        }
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { index ->
            val page = OnboardPages[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.05f)
                        .clip(RoundedCornerShape(28.dp))
                        .background(OgtColors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(page.image),
                        contentDescription = page.title,
                        modifier = Modifier.fillMaxSize().padding(20.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
                Spacer(Modifier.height(20.dp))
                OgtPill(page.kicker)
                Spacer(Modifier.height(10.dp))
                Text(
                    page.title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = OgtColors.ink,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    page.body,
                    color = OgtColors.muted,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircleNav(forward = false, enabled = pager.currentPage > 0) {
                scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OnboardPages.indices.forEach { i ->
                    val active = i == pager.currentPage
                    Box(
                        Modifier
                            .height(8.dp)
                            .width(if (active) 28.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (active) OgtColors.primary else OgtColors.stone)
                            .clickable { scope.launch { pager.animateScrollToPage(i) } },
                    )
                }
            }
            CircleNav(forward = true, enabled = !last) {
                scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }
        }
        OgtPrimaryButton(if (last) "Comenzar mi impacto" else "Siguiente") {
            if (last) onDone() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
        }
        GhostLink("¿Ya tienes cuenta? Iniciar sesión") { onLogin() }
    }
}

@Composable
private fun CircleNav(forward: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) OgtColors.sand else OgtColors.disabledFill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        OgtBackChevron(
            tint = if (enabled) OgtColors.ink else OgtColors.disabledInk,
            forward = forward,
        )
    }
}
