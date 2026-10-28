package com.onlygoodthings.backend.business

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class PaymentSignatureTest {
    @Test fun validatesSignedPaymentIdsAndRejectsTamperingReplayAndDuplicateHeaders() {
        val secret="synthetic-webhook-secret";val stamp=1800000000L;val rid="qa-request-1";val id="123456789"
        val hash=Mac.getInstance("HmacSHA256").apply{init(SecretKeySpec(secret.toByteArray(),"HmacSHA256"))}.doFinal("id:$id;request-id:$rid;ts:$stamp;".toByteArray()).joinToString(""){"%02x".format(it)}
        val signature="ts=$stamp,v1=$hash"
        assertTrue(validPaymentSignature(id,rid,signature,secret,stamp))
        assertFalse(validPaymentSignature("123456788",rid,signature,secret,stamp))
        assertFalse(validPaymentSignature(id,"other",signature,secret,stamp))
        assertFalse(validPaymentSignature(id,rid,signature,secret,stamp+301))
        assertFalse(validPaymentSignature(id,rid,"$signature,ts=$stamp",secret,stamp))
        assertFalse(validPaymentSignature(id,rid,signature,"wrong",stamp))
        assertFalse(validPaymentSignature(id,null,signature,secret,stamp))
    }
    @Test fun monetaryAmountsNeverRoundOrOverflowSilently() {
        assertEquals(12345,cents("123.45"));assertEquals(10,cents("0.1"))
        assertThrows(PaymentProviderFailure::class.java){cents("1.001")}
        assertThrows(PaymentProviderFailure::class.java){cents("99999999999999999999999")}
    }
}
