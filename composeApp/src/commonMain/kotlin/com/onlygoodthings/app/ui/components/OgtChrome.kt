package com.onlygoodthings.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.feed_avatar_me
import com.onlygoodthings.app.resources.feed_logo
import com.onlygoodthings.app.resources.qs_feed
import com.onlygoodthings.app.resources.qs_notifications
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.OgtMotion
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

val LocalOgtChrome = staticCompositionLocalOf<OgtChromeHost?> { null }

data class OgtChromeSpec(
    val title: String,
    val onBack: (() -> Unit)? = null,
    val onNotifications: () -> Unit = {},
    val onProfile: () -> Unit = {},
    val onOverflow: (() -> Unit)? = null,
    val onMessages: (() -> Unit)? = null,
    val hideOnScroll: Boolean = true,
    val branded: Boolean = false,
)

/**
 * Chrome de proceso: una sola barra superior.
 * Se oculta mientras hay scroll vertical y vuelve al quedar idle
 * (Ivory / iOS 18 toolbarVisibility, no Material enterAlways).
 */
@Stable
class OgtChromeHost(
    private val scope: CoroutineScope,
) {
    private var layers by mutableStateOf<List<Pair<Any, OgtChromeSpec>>>(emptyList())
    var scrolling by mutableStateOf(false)
        private set
    var barHeightPx by mutableStateOf(0)
    private var hideAcc = 0f
    private var revealJob: Job? = null

    val spec: OgtChromeSpec? get() = layers.lastOrNull()?.second
    val hosted: Boolean get() = spec != null
    /** Misma fuente que el atrás del sistema: si hay a dónde volver, la flecha existe. */
    var fallbackBack by mutableStateOf<(() -> Unit)?>(null)

    fun present(token: Any, spec: OgtChromeSpec) {
        layers = layers.filterNot { it.first == token } + (token to spec)
    }

    fun release(token: Any) {
        layers = layers.filterNot { it.first == token }
        if (layers.isEmpty()) {
            scrolling = false
            hideAcc = 0f
        }
    }

    fun revealNow() {
        revealJob?.cancel()
        scrolling = false
        hideAcc = 0f
    }

    fun noteVerticalScroll(deltaPx: Float) {
        val current = spec ?: return
        if (!current.hideOnScroll) return
        hideAcc += abs(deltaPx)
        if (hideAcc < OgtMotion.chromeHidePx) return
        hideAcc = 0f
        scrolling = true
        scheduleReveal()
    }

    fun nestedScrollConnection(): NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            // Solo el dedo. Un ajuste de layout no puede esconder el chrome.
            if (source == NestedScrollSource.UserInput && abs(consumed.y) > 0.4f) {
                noteVerticalScroll(consumed.y)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            scheduleReveal()
            return Velocity.Zero
        }
    }

    private fun scheduleReveal() {
        revealJob?.cancel()
        revealJob = scope.launch {
            delay(OgtMotion.chromeIdleMs)
            scrolling = false
            hideAcc = 0f
        }
    }
}

@Composable
fun rememberOgtChromeHost(): OgtChromeHost {
    val scope = rememberCoroutineScope()
    return remember(scope) { OgtChromeHost(scope) }
}

@Composable
fun OgtHostedTopBar(host: OgtChromeHost) {
    val spec = host.spec
    val shownTarget = if (spec != null && !host.scrolling) 1f else 0f
    val shown by animateFloatAsState(
        targetValue = shownTarget,
        animationSpec = OgtMotion.chrome,
        label = "ogt-chrome",
    )
    val density = LocalDensity.current
    val status = WindowInsets.statusBars.getTop(density).toFloat()
    Column(
        Modifier
            .fillMaxWidth()
            .zIndex(4f)
            .graphicsLayer {
                translationY = -size.height * (1f - shown)
                alpha = shown
            }
            .background(OgtColors.canvas.copy(alpha = 0.94f))
            .onSizeChanged { host.barHeightPx = it.height },
    ) {
        if (spec != null) {
            OgtTopBarRow(spec, onBack = spec.onBack ?: host.fallbackBack)
            Box(Modifier.fillMaxWidth().height(1.dp).background(OgtColors.hairline.copy(alpha = shown)))
        }
    }
    if (shown < 0.35f && spec != null) {
        Box(
            Modifier
                .fillMaxWidth()
                .zIndex(3f)
                .height(with(density) { (status + 12.dp.toPx()).toDp() })
                .background(
                    Brush.verticalGradient(
                        listOf(OgtColors.canvas.copy(alpha = 0.72f), OgtColors.canvas.copy(alpha = 0f)),
                    ),
                ),
        )
    }
}

@Composable
internal fun OgtTopBarRow(spec: OgtChromeSpec, onBack: (() -> Unit)? = spec.onBack) {
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = OgtDimens.margin, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            OgtBackButton(onBack)
            Spacer(Modifier.size(10.dp))
        }
        if (spec.branded) {
            Image(
                painterResource(Res.drawable.feed_logo),
                contentDescription = spec.title,
                modifier = Modifier.size(36.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.size(8.dp))
        }
        Text(
            spec.title,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold,
            fontSize = if (spec.branded) 20.sp else 18.sp,
            color = OgtColors.ink,
            letterSpacing = (-0.3).sp,
        )
        if (spec.onOverflow != null) {
            Text(
                "⋯",
                color = OgtColors.muted,
                fontSize = 22.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = spec.onOverflow)
                    .padding(8.dp),
            )
        }
        CircleIconButton(Res.drawable.qs_notifications, "Alertas", spec.onNotifications)
        if (spec.onMessages != null) {
            Spacer(Modifier.size(6.dp))
            CircleIconButton(Res.drawable.qs_feed, "Mensajes", spec.onMessages)
        }
        Spacer(Modifier.size(8.dp))
        Image(
            painterResource(Res.drawable.feed_avatar_me),
            contentDescription = "Perfil",
            modifier = Modifier
                .size(OgtDimens.iconButton)
                .clip(CircleShape)
                .border(1.dp, OgtColors.hairline, CircleShape)
                .clickable(onClick = spec.onProfile),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
fun OgtChromePad(host: OgtChromeHost, modifier: Modifier = Modifier): Modifier {
    val spec = host.spec
    val shownTarget = if (spec != null && !host.scrolling) 1f else 0f
    val shown by animateFloatAsState(
        targetValue = shownTarget,
        animationSpec = OgtMotion.chrome,
        label = "ogt-chrome-pad",
    )
    val density = LocalDensity.current
    val status = WindowInsets.statusBars.getTop(density)
    val fallback = status + with(density) { 68.dp.roundToPx() }
    val measured = host.barHeightPx.takeIf { it > 0 } ?: fallback
    val body = (measured - status).coerceAtLeast(0)
    val topPx = status + (body * shown).toInt()
    return modifier.padding(top = with(density) { topPx.toDp() })
}

@Composable
fun rememberOgtChromeToken(): Any = remember { Any() }

@Composable
fun OgtProvideChrome(spec: OgtChromeSpec) {
    val host = LocalOgtChrome.current ?: return
    val token = rememberOgtChromeToken()
    // SideEffect actualiza título/acciones. Dispose solo al salir de la pantalla:
    // si la key es el spec (lambdas nuevas cada frame) el chrome se soltaba y la barra desaparecía.
    SideEffect { host.present(token, spec) }
    DisposableEffect(host, token) {
        onDispose { host.release(token) }
    }
}
