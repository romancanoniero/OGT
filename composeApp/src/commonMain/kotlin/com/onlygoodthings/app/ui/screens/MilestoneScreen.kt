package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSecondaryButton
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PrefCategory
import com.onlygoodthings.app.ui.components.PrefDivider
import com.onlygoodthings.app.ui.components.PrefGroup
import com.onlygoodthings.app.ui.components.PrefLine

@Composable
fun MilestoneScreen(onBack: () -> Unit, onGoFeed: () -> Unit = onBack) {
    val me = LocalOgtSession.current.me()
    Column(Modifier.fillMaxSize().background(OgtColors.canvas)) {
        OgtTopBar(title = "Hito", onBack = onBack, hideOnScroll = false)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OgtCaption("Tu participación en la comunidad")
        Text(com.onlygoodthings.shared.domain.communityLevelLabel(me.communityPoints),
            color = OgtColors.secondary, fontWeight = FontWeight.Bold)
        Text("${me.communityPoints} pts", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = OgtColors.primary)
        OgtSecondaryButton("Continuar al feed") { onGoFeed() }
        Spacer(Modifier.height(16.dp))
    }
    }
}
