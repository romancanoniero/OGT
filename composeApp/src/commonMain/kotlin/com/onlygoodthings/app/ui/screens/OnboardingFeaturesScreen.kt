package com.onlygoodthings.app.ui.screens

import androidx.compose.runtime.Composable

/** El onboarding oficial es el carrusel de una función por página. */
@Composable
fun OnboardingFeaturesScreen(onContinue: () -> Unit, onLogin: () -> Unit) {
    OnboardingScreen(onDone = onContinue, onLogin = onLogin)
}
