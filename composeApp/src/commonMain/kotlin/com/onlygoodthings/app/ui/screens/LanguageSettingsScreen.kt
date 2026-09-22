package com.onlygoodthings.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.onlygoodthings.app.data.LocalOgtSession
import com.onlygoodthings.app.i18n.LocalOgtCopy
import com.onlygoodthings.app.i18n.OgtLang
import com.onlygoodthings.app.theme.OgtColors
import com.onlygoodthings.app.ui.components.OgtCaption
import com.onlygoodthings.app.ui.components.OgtTopBar
import com.onlygoodthings.app.ui.components.PrefDivider
import com.onlygoodthings.app.ui.components.PrefGroup
import com.onlygoodthings.app.ui.components.PrefRadio
import com.onlygoodthings.app.ui.components.ScreenColumn

/**
 * Lista de idiomas. En ajustes solo se ve el actual; acá se elige.
 * Cuando haya más idiomas, se suman acá sin agrandar la pantalla principal.
 */
@Composable
fun LanguageSettingsScreen(onBack: () -> Unit) {
    val session = LocalOgtSession.current
    val copy = LocalOgtCopy.current
    Column(Modifier.fillMaxSize().background(OgtColors.canvas).verticalScroll(rememberScrollState())) {
        OgtTopBar(title = copy.languageTitle, onBack = onBack)
        ScreenColumn {
            OgtCaption("El que elijas se usa en toda la app.")
            PrefGroup {
                PrefRadio(
                    title = copy.languageEs,
                    selected = session.language == OgtLang.ES,
                ) { session.applyLanguage(OgtLang.ES) }
                PrefDivider()
                PrefRadio(
                    title = copy.languageEn,
                    selected = session.language == OgtLang.EN,
                ) { session.applyLanguage(OgtLang.EN) }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}
