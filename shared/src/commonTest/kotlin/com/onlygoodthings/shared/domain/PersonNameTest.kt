package com.onlygoodthings.shared.domain

import kotlin.test.Test
import kotlin.test.assertEquals

class PersonNameTest {
    @Test
    fun capitalizaNombreYApellido() {
        assertEquals("María Soler", titleCasePersonName("maría soler"))
        assertEquals("Juan Pérez-Gómez", titleCasePersonName("juan pérez-gómez"))
        assertEquals("O'Connor", titleCasePersonName("o'connor"))
    }

    @Test
    fun respetaPreposicionesSalvoAlInicio() {
        assertEquals("María de los Ángeles", titleCasePersonName("maría de los ángeles"))
        assertEquals("Juan del Campo", titleCasePersonName("juan del campo"))
        assertEquals("Ana von Trapp", titleCasePersonName("ana von trapp"))
        assertEquals("De la Cruz", titleCasePersonName("de la cruz"))
    }

    @Test
    fun noComeElEspacioMientrasEscribe() {
        assertEquals("Juan de ", titleCasePersonName("juan de "))
        assertEquals("", titleCasePersonName(""))
    }
}
