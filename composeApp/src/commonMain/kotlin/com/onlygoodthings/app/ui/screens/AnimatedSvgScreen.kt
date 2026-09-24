package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.theme.OgtLogo
import com.onlygoodthings.app.ui.components.OgtTopBar

@Composable
fun AnimatedSvgScreen(onBack: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        OgtTopBar(title = "Logo", onBack = onBack, hideOnScroll = false)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            OgtLogo(size = 320.dp)
        }
    }
}
