package com.onlygoodthings.shared.domain

import kotlin.test.*
import kotlinx.datetime.*
import com.onlygoodthings.shared.data.local.*

class GatheringDraftTest {
    @Test fun explicitTypeDistinguishesAnInvitationFromRecognitionOfReforestation() {
        assertTrue(isGatheringPost("Reforestación","CONVOCATORIA"))
        assertFalse(isGatheringPost("Reforestación","ACTION"))
        assertTrue(isGatheringPost("Reforestación")) // Historical classification remains supported.
        assertFalse(canRsvpToGathering("Reforestación",200,100,"ACTION"))
        assertTrue(canRsvpToGathering("Reforestación",200,100,"CONVOCATORIA"))
        assertFalse(canRsvpToGathering("Reforestación",100,200,"CONVOCATORIA"))
    }
    @Test fun dateAndPlaceUseTheChosenLocalTimezoneAndRejectIncompleteOrPastSchedules() {
        val epoch=gatheringStart("26/11/2030","10:30",TimeZone.of("America/Argentina/Buenos_Aires"))
        assertEquals(Instant.parse("2030-11-26T13:30:00Z").toEpochMilliseconds(),epoch)
        validateGathering("Plantamos árboles","Reforestación","Parque del barrio",epoch,epoch-1)
        assertFailsWith<IllegalArgumentException>{gatheringStart("30/02/2030","10:30")}
        assertFailsWith<IllegalArgumentException>{gatheringStart("26/11/2030","25:30")}
        assertFailsWith<IllegalArgumentException>{validateGathering("Plantamos árboles","Reforestación","",epoch,epoch-1)}
        assertFailsWith<IllegalArgumentException>{validateGathering("Plantamos árboles","Reforestación","Parque del barrio",epoch,epoch)}
    }
    @Test fun serverScheduleSurvivesLocalRefreshAndCacheWithoutChangingRecognition() {
        val db=OgtLocalDatabase.seeded();val old=db.posts.first{!it.isStory}
        val remote=db.toDomain(old).copy(publicationKind="CONVOCATORIA",place="Parque del encuentro",eventStartsAtEpochMs=1234567890000)
        db.upsertRemoteSocial(remote);assertEquals(remote.place,db.post(old.id)?.place)
        assertEquals(remote.eventStartsAtEpochMs,db.toDomain(db.post(old.id)!!).eventStartsAtEpochMs)
        assertEquals(remote.eventStartsAtEpochMs,db.toSocialLive(db.post(old.id)!!).eventStartsAtEpochMs)
        assertEquals("CONVOCATORIA",db.toSocialLive(db.post(old.id)!!).publicationKind)
        val restored=OgtLocalDatabase.seeded();restored.importSocialFeed(db.exportSocialFeed(remote.authorId))
        assertEquals(remote.place,restored.post(old.id)?.place)
        assertEquals(remote.eventStartsAtEpochMs,restored.post(old.id)?.eventStartsAtEpochMs)
    }
}
