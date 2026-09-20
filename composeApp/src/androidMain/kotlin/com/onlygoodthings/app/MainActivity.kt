package com.onlygoodthings.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.onlygoodthings.app.platform.OgtIncomingLinks

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidAuthHost.attach(this)
        offerIntent(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        offerIntent(intent)
    }

    private fun offerIntent(intent: Intent?) {
        intent?.data?.toString()?.let(OgtIncomingLinks::offer)
    }
}
