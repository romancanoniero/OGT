package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.resources.Res
import com.onlygoodthings.app.resources.qs_close
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.ogtRadioColors
import com.onlygoodthings.app.theme.ogtSwitchColors

@Composable
fun PrefCategory(title: String, hint: String? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title.uppercase(),
            color = OgtColors.secondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
        if (!hint.isNullOrBlank()) OgtCaption(hint)
    }
}

@Composable
fun PrefGroup(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(OgtColors.sand)
            .border(1.dp, OgtColors.hairline, shape)
            .padding(horizontal = 4.dp),
        content = content,
    )
}

@Composable
fun PrefDivider() {
    Spacer(Modifier.fillMaxWidth().height(1.dp).padding(horizontal = 12.dp).background(OgtColors.hairline))
}

@Composable
fun PrefSwitch(
    title: String,
    body: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = OgtColors.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (!body.isNullOrBlank()) OgtCaption(body)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, colors = ogtSwitchColors())
    }
}

@Composable
fun PrefRadio(
    title: String,
    selected: Boolean,
    body: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selected, onClick = onSelect, colors = ogtRadioColors())
        if (leading != null) leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = OgtColors.ink,
                fontSize = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            )
            if (!body.isNullOrBlank()) OgtCaption(body)
        }
    }
}

/** Fila de lectura en un grupo de ajustes: título, detalle y valor a la derecha. */
@Composable
fun PrefLine(
    title: String,
    body: String? = null,
    trailing: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(OgtColors.sand)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (leading != null) leading()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = OgtColors.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (!body.isNullOrBlank()) OgtCaption(body)
        }
        if (!trailing.isNullOrBlank()) {
            Text(trailing, color = OgtColors.muted, fontSize = 15.sp)
        }
    }
}

/**
 * Piel de Ajustes sobre [SwipeReveal]: detrás, borrar en rojo.
 * El toque de la fila sigue siendo editar.
 */
@Composable
fun PrefRevealDelete(
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val copy = LocalOgtCopy.current
    SwipeReveal(
        actions = {
            Row(
                Modifier
                    .fillMaxSize()
                    .background(OgtColors.error)
                    .clickable(onClick = onDelete),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .width(92.dp)
                        .fillMaxHeight()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OgtStitchIcon(Res.drawable.qs_close, copy.deleteCar, size = 18.dp, tint = OgtColors.onError)
                    Text(copy.deleteCar, color = OgtColors.onError, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        content = content,
    )
}

@Composable
fun PrefNav(title: String, value: String? = null, body: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = OgtColors.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (!body.isNullOrBlank()) OgtCaption(body)
        }
        if (!value.isNullOrBlank()) {
            Text(value, color = OgtColors.muted, fontSize = 15.sp)
        }
    }
}

@Composable
fun PrefValue(title: String, value: String, detail: String? = null, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, color = OgtColors.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Text(value, color = OgtColors.ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        if (!detail.isNullOrBlank()) OgtCaption(detail)
    }
}
