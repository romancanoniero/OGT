package com.onlygoodthings.app.platform

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.onlygoodthings.app.OgtApplication
import kotlin.test.*

class PostInvitationShareTest {
    @Test fun invitationsUseTheSystemChooserAndNeverSendMessagesDirectly() {
        check(OgtApplication.instance.packageName=="com.onlygoodthings.app.qa")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        for(channel in listOf("SHARE","EMAIL","SMS")) {
            var captured:Intent?=null
            val monitor=object:Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent:Intent):Instrumentation.ActivityResult? {
                    captured=intent
                    return Instrumentation.ActivityResult(Activity.RESULT_CANCELED,null)
                }
            }
            instrumentation.addMonitor(monitor)
            try {
                val text="Irma & José te invitan: https://onlygoodthings.lat/i/"+"a".repeat(43)
                val contact=if(channel=="EMAIL")"irma+ogt@example.test" else "+541155551234"
                instrumentation.runOnMainSync { sharePostInvitation(text,channel,contact) }
                val chooser=assertNotNull(captured)
                assertEquals(Intent.ACTION_CHOOSER,chooser.action)
                @Suppress("DEPRECATION") val target=assertNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
                if(channel=="SHARE") { assertEquals(Intent.ACTION_SEND,target.action);assertEquals(text,target.getStringExtra(Intent.EXTRA_TEXT)) }
                else {
                    assertEquals(Intent.ACTION_SENDTO,target.action)
                    if(channel=="SMS") { assertEquals("smsto",target.data?.scheme);assertEquals(text,target.getStringExtra("sms_body")) }
                    else { assertEquals("mailto",target.data?.scheme);assertTrue(target.data.toString().contains("irma%2Bogt%40example.test"));assertTrue(target.data.toString().contains("Irma%20%26%20Jos%C3%A9")) }
                }
            } finally { instrumentation.removeMonitor(monitor) }
        }
    }
}
