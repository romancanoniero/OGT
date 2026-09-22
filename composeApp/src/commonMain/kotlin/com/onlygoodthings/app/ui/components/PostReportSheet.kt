package com.onlygoodthings.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.OgtMotion
import com.onlygoodthings.app.theme.ogtOutlinedFieldColors
import com.onlygoodthings.shared.domain.FeedCardKind
import com.onlygoodthings.shared.domain.PostReportCatalog
import com.onlygoodthings.shared.domain.ReportMotive

/**
 * Reporte en dos pasos, anclado abajo como aplausos y honores.
 * El diálogo centrado del ⋯ no alcanza para motivo + submotivo.
 */
@Composable
fun PostReportSheet(
    kind: FeedCardKind? = null,
    onDismiss: () -> Unit,
    onSubmit: (reason: String, details: String?) -> Unit,
    onOpenRules: (() -> Unit)? = null,
) {
    var parent by remember { mutableStateOf<ReportMotive?>(null) }
    var details by remember { mutableStateOf("") }
    val motives = remember(kind) { PostReportCatalog.motives(kind) }
    OgtBottomSheet(onDismiss = onDismiss) { sheet ->
        AnimatedContent(
            targetState = parent,
            transitionSpec = { OgtMotion.enterUp togetherWith OgtMotion.exitUp },
            label = "report-step",
        ) { chosen ->
            if (chosen == null) {
                ReportStep(
                    title = "Reportar publicación",
                    caption = "Elegí el motivo. La sacamos de tu feed y la mira moderación.",
                    onBack = null,
                ) {
                    motives.forEach { motive ->
                        ReportChoice(motive.label, motive.hint) { parent = motive }
                    }
                    if (onOpenRules != null) {
                        Text(
                            "Leer las reglas de la comunidad",
                            color = OgtColors.secondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.fillMaxWidth().clickable {
                                sheet.closeNow()
                                onOpenRules()
                            }.padding(vertical = 10.dp),
                        )
                    }
                }
            } else if (chosen.children.isEmpty()) {
                ReportStep(
                    title = chosen.label,
                    caption = chosen.hint,
                    onBack = { parent = null },
                ) {
                    OutlinedTextField(
                        value = details,
                        onValueChange = { if (it.length <= 180) details = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        placeholder = { Text("Opcional: una línea para moderación", color = OgtColors.muted) },
                        shape = RoundedCornerShape(OgtDimens.buttonRadius),
                        colors = ogtOutlinedFieldColors(),
                    )
                    OgtPrimaryButton("Enviar reporte") {
                        onSubmit(chosen.code, details.trim().ifBlank { null })
                        sheet.close()
                    }
                    Text(
                        "Cancelar",
                        color = OgtColors.muted,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth().clickable(onClick = sheet.close).padding(vertical = 10.dp),
                    )
                }
            } else {
                ReportStep(
                    title = chosen.label,
                    caption = "¿Qué es lo que no corresponde?",
                    onBack = { parent = null },
                ) {
                    chosen.children.forEach { child ->
                        ReportChoice(child.label) {
                            onSubmit(child.code, null)
                            sheet.close()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportStep(
    title: String,
    caption: String?,
    onBack: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (onBack != null) {
            Text(
                "Volver",
                color = OgtColors.muted,
                fontSize = 13.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(bottom = 4.dp),
            )
        }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = OgtColors.ink)
        if (!caption.isNullOrBlank()) {
            OgtCaption(caption)
        }
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            content()
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ReportChoice(label: String, hint: String? = null, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Text(label, color = OgtColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (!hint.isNullOrBlank()) {
            Text(hint, color = OgtColors.muted, fontSize = 12.sp)
        }
    }
}
