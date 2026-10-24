package com.onlygoodthings.app.ui.screens

/**
 * En iOS, mientras la foto no llegó, se usa el indicador anterior.
 * El marco 3D queda en Android.
 */
internal expect fun legacyMediaViewHolder(): Boolean
