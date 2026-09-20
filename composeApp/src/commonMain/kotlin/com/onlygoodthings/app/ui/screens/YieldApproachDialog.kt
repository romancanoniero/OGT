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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.GhostLink
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtPill
import com.onlygoodthings.app.ui.components.OgtPrimaryButton
import com.onlygoodthings.app.ui.components.OgtSecondaryButton
import com.onlygoodthings.app.ui.components.OgtSectionTitle
import com.onlygoodthings.shared.domain.ParkingRules

/** Pregunta al volver al auto o si dijo que iba a dejar el lugar. */
@Composable
fun YieldApproachDialog(
    onYield: () -> Unit,
    onDismiss: () -> Unit,
) {
    val copy = LocalOgtCopy.current
    Column(
        Modifier
            .fillMaxSize()
            .background(OgtColors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        OgtPill(copy.yourCar)
        OgtSectionTitle(copy.yieldApproachTitle)
        OgtCaption(copy.yieldApproachBody)
        OgtPill(copy.pointsIfYield(ParkingRules.DEFAULT_REWARD_POINTS), OgtColors.sunset, OgtColors.sunsetText)
        OgtPrimaryButton(copy.yieldNow, onClick = onYield)
        OgtSecondaryButton(copy.notNow, onClick = onDismiss)
        GhostLink(copy.forgetPlace, onDismiss)
        Spacer(Modifier.height(16.dp))
    }
}
