package com.onlygoodthings.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.apple_continue_es
import org.jetbrains.compose.resources.painterResource

/** Apple's official rendered button; Firebase handles the browser OAuth flow on Android. */
@Composable
actual fun AppleSignInButton(enabled:Boolean,onClick:()->Unit) {
    Box(Modifier.fillMaxWidth().height(52.dp).clip(AuthCorner).background(Color.Black).clickable(enabled=enabled,role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        Image(painterResource(Res.drawable.apple_continue_es),contentDescription="Continuar con Apple",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
    }
}
