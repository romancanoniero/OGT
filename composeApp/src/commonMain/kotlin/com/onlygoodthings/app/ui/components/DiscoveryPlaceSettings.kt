package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalAuth
import com.onlygoodthings.shared.domain.AdoptionProximity
import com.onlygoodthings.shared.domain.GeoPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable fun DiscoveryPlaceSettings() {
    val auth=LocalAuth.current;val scope=rememberCoroutineScope()
    var place by remember { mutableStateOf("") };var point by remember { mutableStateOf<GeoPoint?>(null) }
    var busy by remember { mutableStateOf(false) };var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { auth.feed.discoveryScope() }.getOrNull()?.let { saved ->
        place=saved.place.orEmpty();if(saved.place!=null)point=AdoptionProximity.point(saved.latitude,saved.longitude)
    } }
    Column(Modifier.fillMaxWidth().padding(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("Tu zona para adopciones")
        Text("Si no usás GPS, elegí tu barrio o localidad. Usamos una ubicación aproximada.",style=MaterialTheme.typography.bodySmall)
        PublicationPlaceField(place,{place=it;message=null},label="Barrio o localidad",enabled=!busy,onPointChange={point=it})
        Button(enabled=!busy&&point!=null,onClick={val chosen=point ?: return@Button;busy=true;scope.launch {
            try {
                val saved=auth.feed.saveDiscoveryPlace(place,chosen)
                auth.feedTimeline.confirmViewerLocation(AdoptionProximity.point(saved.latitude,saved.longitude),manual=true)
                auth.feedTimeline.applyDiscoverySettings(saved.settings)
                auth.feedTimeline.refresh(auth.feedTimeline.presentation.query,force=true)
                message="Zona guardada."
            } catch(cancel:CancellationException) { throw cancel }
            catch(_:Exception) { message="No pudimos guardar la zona. Volvé a intentarlo." }
            finally { busy=false }
        } }) { Text(if(busy)"Guardando…" else "Guardar zona") }
        message?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
    }
}
