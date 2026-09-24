package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.ScreenColumn
import com.onlygoodthings.app.ui.components.ogtDismissImeOnScroll
import com.onlygoodthings.shared.domain.CommunityRules

/**
 * Lectura de reglas: mismo aire que el resto (título, cuerpo, sin cards).
 */
@Composable
fun CommunityRulesScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(OgtColors.canvas)
            .ogtDismissImeOnScroll()
            .verticalScroll(rememberScrollState()),
    ) {
        OgtTopBar(title = CommunityRules.title, onBack = onBack)
        ScreenColumn {
            OgtCaption(CommunityRules.kicker)
            Text(
                CommunityRules.preamble,
                color = OgtColors.ink,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            )
            CommunityRules.sections.forEach { section ->
                OgtSectionTitle(section.title)
                OgtCaption(section.lead)
                Column(
                    Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    section.points.forEach { point ->
                        Text(
                            point,
                            color = OgtColors.ink,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(88.dp))
        }
    }
}
