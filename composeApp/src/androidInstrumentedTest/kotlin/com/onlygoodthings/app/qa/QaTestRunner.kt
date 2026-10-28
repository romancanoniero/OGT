package com.onlygoodthings.app.qa

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import com.onlygoodthings.app.OgtApplication

/** Common persistence/image tests use real Android APIs in a separate app sandbox. */
class QaApplication:OgtApplication() {
    override fun initializePlatformServices() {
        check(packageName=="com.onlygoodthings.app.qa") { "Never run storage tests in the user's application" }
    }
}
class QaTestRunner:AndroidJUnitRunner() {
    override fun newApplication(cl:ClassLoader,className:String,context:Context):Application {
        check(context.packageName=="com.onlygoodthings.app.qa") { "Isolated QA application required" }
        return super.newApplication(cl,QaApplication::class.java.name,context)
    }
}
