package com.onlygoodthings.app.ui.screens

import androidx.compose.runtime.Composable

/** Retains navigation compatibility without presenting fictitious sponsors. */
@Composable
fun SplashSponsorsScreen(onContinue: () -> Unit, onBack: (() -> Unit)? = null) {
    SponsorsScreen(onBack = onBack ?: onContinue, onContinue = onContinue)
}
