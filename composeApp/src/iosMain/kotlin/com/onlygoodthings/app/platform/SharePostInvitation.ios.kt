package com.onlygoodthings.app.platform

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIActivityViewController
import platform.UIKit.popoverPresentationController
import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreGraphics.CGRectMake
import com.onlygoodthings.shared.domain.invitationEmailUri

@OptIn(ExperimentalForeignApi::class)
actual fun sharePostInvitation(text:String,channel:String,contact:String) {
    if(channel=="SMS" && IosHonorInviteRuntime.host!=null) {
        IosHonorInviteRuntime.host?.sendSms(contact,text);return
    }
    if(channel=="EMAIL" && contact.isNotBlank()) {
        val url=NSURL.URLWithString(invitationEmailUri(contact,text))
        if(url!=null && UIApplication.sharedApplication.canOpenURL(url)) {
            UIApplication.sharedApplication.openURL(url);return
        }
    }
    val sheet=UIActivityViewController(listOf(text),null)
    var presenter=UIApplication.sharedApplication.keyWindow?.rootViewController
    while(presenter?.presentedViewController!=null)presenter=presenter?.presentedViewController
    presenter?.let {
        sheet.popoverPresentationController?.sourceView=it.view
        sheet.popoverPresentationController?.sourceRect=CGRectMake(0.0,0.0,1.0,1.0)
        it.presentViewController(sheet,true,null)
    }
}
