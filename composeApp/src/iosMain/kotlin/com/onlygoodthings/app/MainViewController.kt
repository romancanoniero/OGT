package com.onlygoodthings.app

import androidx.compose.ui.window.ComposeUIViewController

/** Host iOS: el mismo [App] de commonMain que abre MainActivity en Android. */
fun MainViewController() = ComposeUIViewController { App() }
