package com.onlygoodthings.app.media

import com.onlygoodthings.shared.domain.MediaKind

/** A still image must never download or decode the video stream as a bitmap. */
internal fun postPreviewUrl(kind: MediaKind, url: String, posterUrl: String?): String =
    if (kind == MediaKind.VIDEO) posterUrl.orEmpty() else url
