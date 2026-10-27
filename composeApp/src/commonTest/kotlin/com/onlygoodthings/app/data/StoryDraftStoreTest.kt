package com.onlygoodthings.app.data

import com.onlygoodthings.shared.domain.MediaKind
import com.onlygoodthings.shared.domain.StoryDraft
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlin.test.*

class StoryDraftStoreTest {
    @Test fun framingSurvivesClosingAndReopeningWithoutChangingTheMedia()=runBlocking {
        var saved="{}"
        val frame=com.onlygoodthings.shared.domain.StoryFrame(com.onlygoodthings.shared.domain.StoryFrameMode.FILL,2.3,-.7,.6)
        val expected=StoryDraft("frame","owner","/original.jpg",MediaKind.IMAGE,caption="Una historia nueva.",frame=frame)
        val store=StoryDraftStore({saved},{saved=it});store.update("owner",expected);store.flush();store.close();store.awaitClosed()
        val reopened=StoryDraftStore({saved},{})
        assertEquals(expected,reopened.get("owner"));assertEquals("/original.jpg",reopened.get("owner")?.path)
        reopened.close();reopened.awaitClosed()
    }

    @Test fun mutedVideoAndItsMatchingAudioBackupSurviveRestart()=runBlocking {
        var saved="{}"
        val store=StoryDraftStore({saved},{saved=it})
        val video=StoryDraft("v","owner","/silent.mp4",MediaKind.VIDEO,prepared=true,
            sourcePath="/original.mov",audioMuted=true,unmutedPath="/audible-trim.mp4")
        store.update("owner",video);store.flush();store.close();store.awaitClosed()
        val reopened=StoryDraftStore({saved},{})
        assertEquals(video,reopened.get("owner"))
        reopened.close();reopened.awaitClosed()
        val legacy=StoryDraftStore({"""{"owner":{"id":"v","ownerId":"owner","path":"/legacy.mp4","kind":"VIDEO"}}"""},{})
        assertFalse(legacy.get("owner")!!.audioMuted)
        assertNull(legacy.get("owner")!!.unmutedPath)
        legacy.close();legacy.awaitClosed()
    }
    private fun draft(owner:String,text:String)=StoryDraft("draft-$owner",owner,"/cache/photo.png",MediaKind.IMAGE,caption=text)
    @Test fun typingDoesNotWaitForDiskAndTheLastCharacterIsSaved()=runBlocking {
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        var saved=""
        val store=StoryDraftStore({"{}"},{ value ->
            entered.complete(Unit)
            runBlocking { release.await() }
            saved=value
        })
        store.update("a",draft("a","H"))
        entered.await()
        val sentence="Hoy ayudamos a la comunidad. Mañana seguimos juntos."
        sentence.indices.forEach { store.update("a",draft("a",sentence.take(it+1))) }
        assertEquals(sentence,store.get("a")?.caption)
        assertEquals("",saved)
        release.complete(Unit)
        withTimeout(5000) { store.flush() }
        assertEquals(sentence,Json.decodeFromString<Map<String,StoryDraft>>(saved)["a"]?.caption)
        store.close();store.awaitClosed()
    }
    @Test fun switchingAccountsAndRemovingADraftCannotResurrectAnOlderWrite()=runBlocking {
        var saved="{}"
        val store=StoryDraftStore({saved},{saved=it})
        store.update("a",draft("a","Primera cuenta"))
        store.update("b",draft("b","Segunda cuenta"))
        store.update("a",null)
        store.flush()
        val persisted=Json.decodeFromString<Map<String,StoryDraft>>(saved)
        assertNull(persisted["a"])
        assertEquals("Segunda cuenta",persisted["b"]?.caption)
        store.close();store.awaitClosed()
    }
    @Test fun closingDrainsPendingEditsAndReopeningRestoresTheExactText()=runBlocking {
        var saved="{}"
        val first=StoryDraftStore({saved},{saved=it})
        val expected="Una historia con acentos, corazón y solidaridad."
        first.update("a",draft("a",expected))
        first.close();withTimeout(5000) { first.awaitClosed() }
        val restored=StoryDraftStore({saved},{saved=it})
        assertEquals(expected,restored.get("a")?.caption)
        assertNull(restored.get("another"))
        restored.close();restored.awaitClosed()
    }
    @Test fun preparedFileIsDurableBeforeTheUploadCanStart()=runBlocking {
        var saved="{}"
        val store=StoryDraftStore({saved},{saved=it})
        val ready=draft("a","Video de nuestra jornada.").copy(prepared=true,path="/cache/prepared.mp4",kind=MediaKind.VIDEO)
        store.update("a",ready);store.flush()
        assertEquals(ready,Json.decodeFromString<Map<String,StoryDraft>>(saved)["a"])
        store.close();store.awaitClosed()
    }
    @Test fun storageFailureIsReportedAndAFlushCanRetryWithoutLosingText()=runBlocking {
        var fail=true
        var saved="{}"
        val store=StoryDraftStore({saved},{if(fail) error("Disk unavailable") else saved=it})
        store.update("a",draft("a","Texto conservado."))
        withTimeout(5000) { store.failure.first { it!=null } }
        assertFailsWith<IllegalStateException> { store.flush() }
        assertEquals("Texto conservado.",store.get("a")?.caption)
        fail=false;store.flush()
        assertNull(store.failure.value)
        assertEquals("Texto conservado.",Json.decodeFromString<Map<String,StoryDraft>>(saved)["a"]?.caption)
        store.close();store.awaitClosed()
    }
}
