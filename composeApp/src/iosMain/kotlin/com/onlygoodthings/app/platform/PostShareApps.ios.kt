@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.onlygoodthings.app.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.MessageUI.*
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.darwin.*

actual suspend fun installedPostShareApps():List<PostShareApp> = withContext(Dispatchers.Main) {
    buildList {
        if(UIApplication.sharedApplication.canOpenURL(NSURL.URLWithString("whatsapp://")!!))add(PostShareApp("whatsapp","WhatsApp","whatsapp"))
        if(UIApplication.sharedApplication.canOpenURL(NSURL.URLWithString("tg://")!!))add(PostShareApp("telegram","Telegram","telegram"))
        if(MFMessageComposeViewController.canSendText())add(PostShareApp("messages","Mensajes","messages"))
        if(MFMailComposeViewController.canSendMail())add(PostShareApp("mail","Mail","mail"))
    }
}
private val composerDelegate=object:NSObject(),MFMessageComposeViewControllerDelegateProtocol,MFMailComposeViewControllerDelegateProtocol {
    override fun messageComposeViewController(controller:MFMessageComposeViewController,didFinishWithResult:MessageComposeResult) { controller.dismissViewControllerAnimated(true,null) }
    override fun mailComposeController(controller:MFMailComposeViewController,didFinishWithResult:MFMailComposeResult,error:NSError?) { controller.dismissViewControllerAnimated(true,null) }
}
private fun presentComposer(make:()->UIViewController) {
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW,200_000_000L),dispatch_get_main_queue()) {
        var top=UIApplication.sharedApplication.keyWindow?.rootViewController
        while(top?.presentedViewController!=null)top=top?.presentedViewController
        val presenter=top ?: return@dispatch_after
        presenter.presentViewController(make(),true,null)
    }
}
actual fun sharePostWithApp(app:PostShareApp,text:String,url:String):Boolean {
    when(app.id) {
        "messages" -> { if(!MFMessageComposeViewController.canSendText())return false;presentComposer { MFMessageComposeViewController().apply { body=text;messageComposeDelegate=composerDelegate } };return true }
        "mail" -> { if(!MFMailComposeViewController.canSendMail())return false;presentComposer { MFMailComposeViewController().apply { setSubject("Una historia en OGT");setMessageBody(text,false);mailComposeDelegate=composerDelegate } };return true }
        else -> {
            val target=postShareAppUri(app.id,text,url)?.let { NSURL.URLWithString(it) } ?: return false
            if(!UIApplication.sharedApplication.canOpenURL(target))return false
            dispatch_after(dispatch_time(DISPATCH_TIME_NOW,200_000_000L),dispatch_get_main_queue()) {
                UIApplication.sharedApplication.openURL(target,options=emptyMap<Any?,Any>(),completionHandler={ opened ->
                    if(!opened)sharePlainText(text)
                })
            }
            return true
        }
    }
}
