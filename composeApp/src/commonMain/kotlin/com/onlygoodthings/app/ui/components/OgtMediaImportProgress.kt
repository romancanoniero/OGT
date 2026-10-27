package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors

/** Inline feedback leaves native picker/editor presentation and cancellation intact. */
@Composable
fun OgtMediaImportProgress(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OgtLoader(size = 20.dp)
        Text("Importando archivo y preparando el editor…", Modifier.weight(1f), color = OgtColors.muted, fontSize = 13.sp)
    }
}
