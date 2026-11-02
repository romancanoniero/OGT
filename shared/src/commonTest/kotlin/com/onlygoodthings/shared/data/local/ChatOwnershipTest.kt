package com.onlygoodthings.shared.data.local

import kotlin.test.*

class ChatOwnershipTest {
    @Test fun onlyParticipantsCanWriteALocalThreadAndAnInvalidThreadNeverCreatesAMessage() {
        val db=OgtLocalDatabase.seeded()
        val match=db.matches.first { it.chatEnabled }
        val owner=db.user(match.requesterId)
        val stranger=owner.copy(id="stranger",firebaseUid="different-firebase-id")
        val size=db.messages.size
        assertNull(db.addChatMessage(stranger,match.id,"Esto no debe entrar"))
        assertNull(db.addChatMessage(owner,"missing-thread","Tampoco debe entrar"))
        assertEquals(size,db.messages.size)
        assertNotNull(db.addChatMessage(owner,match.id,"Hola, coordinamos por acá."))
        assertEquals(size+1,db.messages.size)
    }
}
