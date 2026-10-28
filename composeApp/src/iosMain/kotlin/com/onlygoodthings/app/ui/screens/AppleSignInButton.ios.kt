package com.onlygoodthings.app.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.UIKitView
import com.onlygoodthings.app.auth.IosAuthRuntime
import kotlinx.cinterop.ExperimentalForeignApi

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun AppleSignInButton(enabled:Boolean,onClick:()->Unit) {
    val current=rememberUpdatedState(onClick)
    UIKitView(factory={ requireNotNull(IosAuthRuntime.host).appleSignInButton { current.value() } },
        modifier=Modifier.fillMaxWidth().height(52.dp),update={ it.userInteractionEnabled=enabled;it.alpha=if(enabled)1.0 else .5 })
}
