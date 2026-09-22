package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors

/**
 * Menú del ⋯: el autor edita o saca el post; el resto oculta o reporta.
 */
@Composable
fun PostOverflowSheet(
    isAuthor: Boolean,
    canEdit: Boolean,
    onDismiss: () -> Unit,
    onHide: () -> Unit,
    onReport: () -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var confirmDelete by remember { mutableStateOf(false) }
    OgtBottomSheet(onDismiss = onDismiss, maxHeight = 420.dp) { sheet ->
        if (confirmDelete) {
            Text("¿Eliminar esta publicación?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OgtColors.ink)
            Text(
                "Se saca del feed. Quien ya la abrió deja de verla en el próximo refresco.",
                color = OgtColors.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OverflowChoice("Eliminar") {
                onDelete()
                sheet.close()
            }
            OverflowChoice("Cancelar", muted = true, onClick = sheet.close)
        } else {
            Text("Publicación", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = OgtColors.ink)
            if (isAuthor && canEdit) {
                OverflowChoice("Editar") {
                    onEdit()
                    sheet.closeNow()
                }
            }
            if (isAuthor) {
                OverflowChoice("Eliminar") { confirmDelete = true }
            }
            OverflowChoice("Ocultar") {
                onHide()
                sheet.close()
            }
            if (!isAuthor) {
                OverflowChoice("Reportar") {
                    onReport()
                    sheet.closeNow()
                }
            }
        }
    }
}

@Composable
private fun OverflowChoice(label: String, muted: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        color = if (muted) OgtColors.muted else OgtColors.ink,
        fontSize = 15.sp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}
