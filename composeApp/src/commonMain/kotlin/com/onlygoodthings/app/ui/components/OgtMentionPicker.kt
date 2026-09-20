package com.onlygoodthings.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.theme.OgtDimens
import com.onlygoodthings.app.theme.ogtOutlinedFieldColors
import com.onlygoodthings.shared.domain.titleCasePersonName
import com.onlygoodthings.shared.domain.MentionCandidate
import com.onlygoodthings.shared.domain.mentionHandle
import com.onlygoodthings.shared.domain.searchMentions
import org.jetbrains.compose.resources.painterResource

/**
 * Typeahead de @ como Instagram: @nick, nombre y foto.
 * Si no hay match, se puede nombrar sin invitar o mandar una invitación.
 */
@Composable
fun OgtMentionPicker(
    selected: List<MentionCandidate>,
    onSelect: (MentionCandidate) -> Unit,
    onRemove: (MentionCandidate) -> Unit,
    pool: List<MentionCandidate>,
    modifier: Modifier = Modifier,
    single: Boolean = false,
    excludeIds: Set<String> = emptySet(),
    placeholder: String = "Buscá @nick o nombre",
    atPrefix: Boolean = true,
    missing: Boolean = false,
    allowNameOnly: Boolean = false,
    onInvite: (givenName: String) -> Unit = {},
    onNameOnly: (givenName: String) -> Unit = {},
) {
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val taken = selected.mapNotNull { it.userId }.toSet() + excludeIds
    val available = pool.filter { it.userId !in taken }
    val hits = searchMentions(query, available)
    val typing = query.trim().removePrefix("@")
    val showList = (focused || typing.isNotBlank()) && !(single && selected.isNotEmpty())
    val canInvite = typing.length >= 2 && hits.none { it.handle == mentionHandle(typing) }
    val canNameOnly = allowNameOnly && typing.length >= 2
    fun confirmNameOnly() {
        val given = titleCasePersonName(typing.trim())
        if (given.length < 2) return
        onNameOnly(given)
        query = ""
        focused = false
        focus.clearFocus()
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selected.isNotEmpty()) {
            selected.forEach { hit ->
                MentionChip(hit, onRemove = { onRemove(hit) })
            }
        }
        if (!single || selected.isEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { raw ->
                    val cleaned = raw.removePrefix("@")
                    query = if (atPrefix) cleaned else titleCasePersonName(cleaned)
                    focused = true
                },
                modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
                singleLine = true,
                prefix = if (atPrefix) {
                    { Text("@", color = OgtColors.secondary, fontWeight = FontWeight.SemiBold) }
                } else {
                    null
                },
                placeholder = {
                    Text(
                        placeholder,
                        color = if (missing) OgtColors.onErrorContainer else OgtColors.muted,
                    )
                },
                shape = RoundedCornerShape(OgtDimens.buttonRadius),
                colors = ogtOutlinedFieldColors(missing = missing),
                keyboardOptions = KeyboardOptions(imeAction = if (allowNameOnly) ImeAction.Done else ImeAction.Default),
                keyboardActions = KeyboardActions(onDone = { confirmNameOnly() }),
            )
        } else if (single && selected.isNotEmpty()) {
            Text(
                "Cambiar",
                color = OgtColors.secondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable {
                    selected.forEach(onRemove)
                    focused = true
                },
            )
        }
        if (showList) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(OgtColors.canvas)
                    .border(1.dp, OgtColors.hairline, RoundedCornerShape(16.dp)),
            ) {
                hits.forEach { hit ->
                    MentionRow(hit) {
                        onSelect(hit)
                        query = ""
                        focused = false
                    }
                }
                if (canNameOnly) {
                    val given = typing.trim()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { confirmNameOnly() }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text(
                            "Nombrar a $given",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = OgtColors.ink,
                        )
                        Text(
                            "Alcanza el nombre. No hace falta invitar.",
                            color = OgtColors.muted,
                            fontSize = 12.sp,
                        )
                    }
                }
                if (canInvite) {
                    val given = typing.trim()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onInvite(titleCasePersonName(given))
                                query = ""
                                focused = false
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text(
                            "Invitar a $given",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = OgtColors.ink,
                        )
                        Text(
                            if (allowNameOnly) {
                                "Opcional. WhatsApp, SMS o mail si alguien puede reivindicar el crédito."
                            } else {
                                "Aún no está en Only Good Things · WhatsApp, SMS o mail"
                            },
                            color = OgtColors.muted,
                            fontSize = 12.sp,
                        )
                    }
                } else if (!canNameOnly && hits.isEmpty() && typing.isNotBlank()) {
                    Text(
                        "Nadie con ese @ en tu red. Probá el nombre o invitala.",
                        modifier = Modifier.padding(12.dp),
                        color = OgtColors.muted,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MentionChip(hit: MentionCandidate, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(OgtColors.sand)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painterResource(neighborAvatarArt(hit.userId.orEmpty())),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(hit.displayName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OgtColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    hit.pending -> "${hit.atHandle} · Invitada"
                    hit.namedOnly -> "Solo el nombre"
                    else -> hit.atHandle
                },
                color = OgtColors.muted,
                fontSize = 12.sp,
                maxLines = 1,
            )
        }
        Text("✕", color = OgtColors.muted, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onRemove).padding(8.dp))
    }
}

@Composable
private fun MentionRow(hit: MentionCandidate, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painterResource(neighborAvatarArt(hit.userId.orEmpty())),
            contentDescription = null,
            modifier = Modifier.size(44.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Column(Modifier.weight(1f)) {
            Text(hit.displayName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = OgtColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(hit.atHandle, color = OgtColors.muted, fontSize = 13.sp, maxLines = 1)
        }
    }
}
