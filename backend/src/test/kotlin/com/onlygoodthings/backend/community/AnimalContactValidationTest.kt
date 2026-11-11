package com.onlygoodthings.backend.community

import com.onlygoodthings.shared.domain.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AnimalContactValidationTest {
    private val form=AdoptionApplicationForm("Persona QA","0000000000","Lugar QA","Casa","No","","Hogar QA","4 h","Experiencia QA","Motivo QA")
    @Test fun adoptionRequiresCompleteFormWithoutPreciseLocation() {
        val request=AnimalContactRequest("listing","ADOPTION",form)
        validateAnimalContact(request)
        for(invalid in listOf(request.copy(form=null),request.copy(form=form.copy(phone="")),request.copy(latitude=1.0),request.copy(form=form.copy(motive="x".repeat(1001)))))
            assertThrows(IllegalArgumentException::class.java) { validateAnimalContact(invalid) }
    }
    @Test fun sightingsRequireAnExplicitValidPointAndCannotCarryPrivateForms() {
        val request=AnimalContactRequest("listing","SIGHTING",note="Lugar QA",latitude=-34.6,longitude=-58.4)
        validateAnimalContact(request)
        for(invalid in listOf(request.copy(latitude=null),request.copy(longitude=Double.NaN),request.copy(latitude=91.0),request.copy(form=form),request.copy(kind="ADMIN")))
            assertThrows(IllegalArgumentException::class.java) { validateAnimalContact(invalid) }
    }
}
