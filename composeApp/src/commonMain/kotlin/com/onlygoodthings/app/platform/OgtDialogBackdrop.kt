package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable

/** Quita el dim negro del Dialog y, si el SO puede, vidria el fondo. */
@Composable
expect fun SoftenDialogBackdrop()
