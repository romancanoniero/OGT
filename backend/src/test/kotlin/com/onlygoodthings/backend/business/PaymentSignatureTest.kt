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
    @Test fun sandboxBuyerConfigurationFailsClosedAndCannotEnterLiveMode() {
        assertTrue(sandboxBuyerAllowlist("",true).isEmpty())
        assertEquals(setOf("123","456"),sandboxBuyerAllowlist("123, 456,123",false))
        for(raw in listOf("123,","abc","-1","1.2","123\n456"))assertThrows(IllegalArgumentException::class.java){sandboxBuyerAllowlist(raw,false)}
        assertThrows(IllegalArgumentException::class.java){sandboxBuyerAllowlist("123",true)}
    }
    @Test fun providerEnvironmentMismatchNeedsAnExplicitSandboxBuyer() {
        fun provider(liveMode:Boolean)=object:PaymentProvider{
            override val live=liveMode;override val collector="123";override val sandboxBuyerIds=setOf("456")
            override fun preference(order:String,amount:Long):PaymentPreference=error("No network")
            override fun payment(id:String):ProviderPayment=error("No network")
        }
        val payment=ProviderPayment("1","order","123",true,"ARS",10000,0,"approved",payer="456")
        assertTrue(provider(false).matchesEnvironment(payment))
        assertFalse(provider(false).matchesEnvironment(payment.copy(payer="789")))
        assertFalse(provider(false).matchesEnvironment(payment.copy(payer="")))
        assertFalse(provider(true).matchesEnvironment(payment.copy(live=false)))
        assertTrue(provider(false).matchesEnvironment(payment.copy(live=false,payer="")))
    }
}
