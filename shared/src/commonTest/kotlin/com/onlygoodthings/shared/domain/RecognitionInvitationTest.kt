package com.onlygoodthings.shared.domain

import kotlin.test.*
import com.onlygoodthings.shared.data.local.seeded
import kotlinx.serialization.json.Json

class RecognitionInvitationTest {
    @Test fun contactAndNicknameValidationRetainFullNamesSeparately() {
        assertEquals("irma.sartirana",normalizeNickname(" @Irma.Sartirana "))
        assertEquals("irma@example.test",normalizeInvitationContact("EMAIL"," Irma@Example.test "))
        assertEquals("+541155551234",normalizeInvitationContact("SMS","+54 (11) 5555-1234"))
        assertFailsWith<IllegalArgumentException>{normalizeInvitationContact("SMS","1155551234")}
        assertFailsWith<IllegalArgumentException>{normalizeNickname("Irma Sartirana")}
        validateInvitationDraft(PostInvitationDraft("Irma Sartirana",channel="SHARE"))
        assertFailsWith<IllegalArgumentException>{validateInvitationDraft(PostInvitationDraft("Irma Sartirana",channel="IN_APP"))}
        val draft=RecognitionPostDraft("Ayudó al barrio","Limpieza","limpieza","request-123456",emptyList(),"Irma Sartirana")
        assertEquals("Irma Sartirana",Json.decodeFromString<RecognitionPostDraft>(Json.encodeToString(RecognitionPostDraft.serializer(),draft)).honoreeFullName)
    }
    @Test fun invitationLinksAreStrictAndIndependentFromLegacyHonorLinks() {
        val token="a".repeat(43)
        assertEquals(token,parseInvitationToken(invitationLink(token)))
        assertEquals(token,parseInvitationToken("ogt://i/$token"))
        assertEquals(token,parseInvitationToken("https://www.onlygoodthings.lat/i/$token?utm_source=share"))
        assertNull(parseInvitationToken("https://onlygoodthings.lat.evil.test/i/$token"))
        assertNull(parseInvitationToken("https://onlygoodthings.lat/i/$token/other"))
        assertNull(parseInvitationToken("ogt://h/$token"))
        assertEquals(token,parseInvitationReferrer("invitation=$token&utm_source=share"))
        assertNull(parseInvitationReferrer("honor=$token"))
        val mail=invitationEmailUri("irma+ogt@example.test","Irma & José\nhttps://onlygoodthings.lat/i/$token")
        assertTrue(mail.startsWith("mailto:irma%2Bogt%40example.test?subject="))
        assertTrue(mail.contains("Irma%20%26%20Jos%C3%A9%0A"))
    }
    @Test fun fullNameCorrectionsAndCategoriesSurviveFeedRefreshAndRestore() {
        val db=com.onlygoodthings.shared.data.local.OgtLocalDatabase.seeded()
        val old=db.posts.first { !it.isStory && db.honorOfPost(it.id)==null }
        val remote=db.toDomain(old).copy(honoreeName="Irma María Sartirana",categoryId="movilidad",publicationKind="ACTION")
        db.upsertRemoteSocial(remote)
        assertEquals("Irma María Sartirana",db.post(old.id)?.honoreeName)
        db.applySocialLive(SocialLiveCounters(old.id,0,0,honoreeName="Irma Sartirana",categoryId="movilidad",publicationKind="ACTION"))
        assertEquals("Irma Sartirana",db.post(old.id)?.honoreeName)
        val user=db.users.first()
        db.applyUserLive(user.id,user.displayName,user.photoUrl,"irma.sartirana")
        db.upsertRemoteSocial(remote)
        assertEquals("irma.sartirana",db.userOrNull(user.id)?.nickname)
        val restored=com.onlygoodthings.shared.data.local.OgtLocalDatabase.seeded()
        restored.importSocialFeed(db.exportSocialFeed(user.id))
        assertEquals("Irma María Sartirana",restored.post(old.id)?.honoreeName)
        assertEquals("movilidad",restored.post(old.id)?.categoryId)
        assertEquals("ACTION",restored.post(old.id)?.publicationKind)
    }
    @Test fun categoryNamesCannotTurnNewRecognitionsIntoOtherPostFlows() {
        assertEquals(FeedCardKind.COMMUNITY,feedCardKind("Ternura",publicationKind="ACTION"))
        assertEquals(FeedCardKind.TERNURA,feedCardKind("Ternura"))
        assertEquals(FeedCardKind.LOST_PET,feedCardKind("Ternura","LOST",publicationKind="ACTION"))
    }
}
