package com.onlygoodthings.shared.domain

import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import kotlin.test.*

class PetAlertsTest {
    private fun alert(kind:String="FOUND")=AnimalListingDto("alert-listing","alert-post","author","author-firebase",kind,"DOG","","MEDIUM",
        "Mascota encontrada","Busca a su familia","Castelar (aproximado)",2000,false,marks="Canela y collar azul",lastSeenPlace="Gorki Grana",
        latitude=-34.66,longitude=-58.66,createdAtEpochMs=1000)
    @Test fun filtersKeepLostFoundAndAdoptionDistinctAndExcludeResolved() {
        for(kind in listOf("LOST","FOUND","ADOPTION")) {
            assertTrue(PetListingFilter.ALL.matches(kind))
            assertFalse(PetListingFilter.ALL.matches(kind,true))
            for(filter in PetListingFilter.values().filter { it!=PetListingFilter.ALL }) assertEquals(filter.name==kind,filter.matches(kind))
        }
        assertFalse(PetListingFilter.ALL.matches("UNKNOWN"))
        assertEquals(FeedCardKind.LOST_PET,feedCardKind("Mascota encontrada","FOUND"))
        assertEquals(FeedCardKind.ADOPTION,feedCardKind("Adopción","ADOPTION"))
        assertEquals(FeedCardKind.PET_STORY,feedCardKind("Adopción",sourceUrl="https://example.test/news"))
    }
    @Test fun openingThePetSectionPreservesFeedReactionsDiffusionAndExpiryWithoutDuplicatingThePost() {
        val db=OgtLocalDatabase();db.upsertRemoteAnimal(alert())
        val original=db.posts.single().copy(impactCount=4,commentCount=2,viewerHasImpacted=true,viewerHasDiffused=true,feedExpiresAtEpochMs=9000)
        db.posts[0]=original
        repeat(2) { db.upsertRemoteAnimal(alert()) }
        val post=db.posts.single();val animal=db.animals.single()
        assertEquals("FOUND",animal.kind);assertFalse(animal.resolved)
        assertEquals("Mascota encontrada",post.tag);assertEquals("",animal.size)
        assertEquals(-34.66,animal.latitude);assertEquals(-58.66,animal.longitude)
        assertEquals(original.impactCount,post.impactCount);assertEquals(original.commentCount,post.commentCount)
        assertTrue(post.viewerHasImpacted);assertTrue(post.viewerHasDiffused);assertEquals(9000,post.feedExpiresAtEpochMs)
    }
}
