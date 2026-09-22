package com.onlygoodthings.shared.domain

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SocialPostCodecTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun feedDeLaVpsSinFotoDeAutor() {
        val payload = """
            {
              "success": true,
              "data": [{
                "id": "a2000000-0000-4000-8000-000000000001",
                "authorKind": "USER",
                "authorId": "11111111-1111-1111-1111-111111111111",
                "authorName": "Ana Pérez",
                "body": "En febrero volvieron 158 tortugas.",
                "mediaUrls": ["http://217.216.82.209:19080/media/u/c2000000-0000-4000-8000-000000000001.jpg"],
                "impactCount": 0,
                "commentCount": 0,
                "isStory": false,
                "createdAtEpochMs": 1,
                "viewerHasImpacted": false,
                "topic": "Llegó a casa",
                "discovery": true,
                "protagonistUserId": "11111111-1111-1111-1111-111111111111",
                "media": [],
                "sourceUrl": "https://galapagos.gob.ec/un-regreso-a-casa-para-floreana/",
                "placement": "ORGANIC",
                "achievedCount": 0,
                "empresaQueSuma": false
              }]
            }
        """.trimIndent()
        val parsed = json.decodeFromString<ApiResponse<List<SocialPost>>>(payload)
        assertTrue(parsed.success)
        val post = parsed.data!!.single()
        assertEquals("a2000000-0000-4000-8000-000000000001", post.id)
        assertNull(post.authorPhotoUrl)
        assertEquals("Llegó a casa", post.topic)
    }
}
