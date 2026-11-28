package com.onlygoodthings.shared.data.local

import com.onlygoodthings.shared.domain.*
import kotlin.test.*

class PostDiffusionTest {
    @Test fun firstDiffusionSurvivesRetriesLegacyWithdrawalAndCacheRestart() {
        val db=OgtLocalDatabase()
        val viewer=LocalUser("viewer","viewer",null,null,"@qa",null,UserRole.USER,0,"invite","Zona","Nivel","",0.0,0.0)
        db.upsertRemoteSocial(SocialPost("post",AuthorKind.USER,"author","Autor",body="Contenido protegido",mediaUrls=emptyList(),impactCount=7,commentCount=3,isStory=false,createdAtEpochMs=5))
        val original=db.posts.single()
        assertSame(original,db.diffusePost(viewer,"post",false))
        val first=db.diffusePost(viewer,"post",true)
        assertTrue(first.viewerHasDiffused)
        assertEquals(1,first.diffusedBy.size)
        assertSame(first,db.diffusePost(viewer,"post",true))
        assertSame(first,db.diffusePost(viewer,"post",false))
        val restarted=OgtLocalDatabase()
        restarted.importSocialFeed(db.exportSocialFeed(viewer.id));restarted.bindSocialFeedViewer(viewer.id)
        val restored=restarted.posts.single()
        assertTrue(restored.viewerHasDiffused)
        assertEquals(first.diffusedBy,restored.diffusedBy)
        assertEquals(original.body,restored.body)
        assertEquals(original.createdAtEpochMs,restored.createdAtEpochMs)
        assertEquals(original.impactCount,restored.impactCount)
        assertEquals(original.commentCount,restored.commentCount)
    }
}
