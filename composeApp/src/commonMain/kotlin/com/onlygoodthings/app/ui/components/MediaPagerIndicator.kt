package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.sp

/** Contador n/m arriba a la izquierda y dots al pie, al centro. Solo si hay más de un medio. */
@Composable
fun MediaPagerIndicator(
    pageIndex: Int,
    pageCount: Int,
    modifier: Modifier = Modifier,
    onPageSelected: ((Int) -> Unit)? = null,
) {
    if (pageCount <= 1) return
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .clip(CircleShape)
                .background(Color(0x99000000))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                "${pageIndex + 1}/$pageCount",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp).clip(CircleShape).background(Color.Black.copy(alpha=.38f)).padding(horizontal=4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            repeat(pageCount) { index ->
                val active = index == pageIndex
                Box(
                    Modifier.size(20.dp).semantics { contentDescription="Archivo ${index+1} de $pageCount";selected=active }.then(if(onPageSelected==null)Modifier else Modifier.clickable { onPageSelected(index) }),
                    contentAlignment=Alignment.Center,
                ) {
                    Box(Modifier.size(if(active)7.dp else 6.dp).clip(CircleShape)
                        .background(if(active)Color.White else Color.White.copy(alpha=.45f)))
                }
            }
        }
    }
}
