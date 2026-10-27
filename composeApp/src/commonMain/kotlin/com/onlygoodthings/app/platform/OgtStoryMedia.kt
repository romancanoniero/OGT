package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.onlygoodthings.shared.domain.StoryFrame

expect suspend fun prepareStoryMedia(item: OgtPickedMedia): OgtPickedMedia

@Composable
expect fun OgtStoryVideo(
    url: String, playing: Boolean, modifier: Modifier,
    onReady: () -> Unit, onProgress: (Long) -> Unit, onError: (String) -> Unit, frame: StoryFrame? = null,
)
