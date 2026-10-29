package com.onlygoodthings.shared.domain

import com.onlygoodthings.shared.data.local.OgtLocalDatabase
import com.onlygoodthings.shared.protocol.WsCodec
import com.onlygoodthings.shared.realtime.socialLiveCountersFrom
import kotlinx.serialization.json.Json
import kotlin.test.*

class ContentAttributionTest {
    @Test fun externalCreditsAcceptOnlyWebLinksWithoutCredentials() {
        assertNull(ContentAttribution.normalize(null))
        assertNull(ContentAttribution.normalize("  "))
        assertEquals("https://example.org/original?a=1#autor", ContentAttribution.normalize(" https://example.org/original?a=1#autor "))
        for (bad in listOf("javascript:alert(1)", "data:text/html,a", "file:///tmp/a", "//example.org", "https://", "https://a:b@example.org", "https://example.org/a b", "https://example.org/\\a", "https://example.org/"+"a".repeat(2048))) {
            assertFailsWith<IllegalArgumentException>(bad) { ContentAttribution.normalize(bad) }
        }
        assertFailsWith<IllegalArgumentException> { ContentAttribution.normalize("https://exam\nple.org") }
    }

    @Test fun creditSurvivesDomainAndLocalCacheWithoutReclassifyingThePost() {
        val post=SocialPost("credit-post",AuthorKind.USER,"author","Ana",body="Ayudamos a la comunidad",mediaUrls=emptyList(),
            impactCount=0,commentCount=0,isStory=false,createdAtEpochMs=1,topic="Ternura",attributionUrl="https://example.org/autor")
        val json=Json { encodeDefaults=true }
        val restored=json.decodeFromString<SocialPost>(json.encodeToString(SocialPost.serializer(),post))
        val db=OgtLocalDatabase()
        db.upsertRemoteSocial(restored)
        val local=db.post(post.id)!!
        assertEquals(post.attributionUrl,local.attributionUrl)
        assertEquals(post.attributionUrl,db.toDomain(local).attributionUrl)
        assertNull(local.sourceUrl)
        assertEquals(feedCardKind(post.topic,null,null),feedCardKind(local.tag,null,local.sourceUrl))
        assertNull(db.toDomain(db.editOwnPost("author",post.id,"Texto editado",attributionUrl="")!!).attributionUrl)
    }

    @Test fun oldPayloadsAndCounterOnlyEventsDoNotEraseCredits() {
        val live=socialLiveCountersFrom(mapOf("id" to "p","impactCount" to 1),null)!!
        assertFalse(live.attributionChanged)
        val clear=WsCodec.json.decodeFromString<SocialLiveCounters>("""{"id":"p","commentCount":0,"impactCount":1,"attributionChanged":true,"attributionUrl":null}""")
        assertTrue(clear.attributionChanged);assertNull(clear.attributionUrl)
        val draft=StoryDraft("s","a","local",MediaKind.IMAGE,attributionUrl="https://example.org/original")
        assertEquals(draft,Json.decodeFromString<StoryDraft>(Json.encodeToString(StoryDraft.serializer(),draft)))
        assertNull(Json.decodeFromString<StoryDraft>("""{"id":"s","ownerId":"a","path":"local","kind":"IMAGE"}""").attributionUrl)
    }
}
