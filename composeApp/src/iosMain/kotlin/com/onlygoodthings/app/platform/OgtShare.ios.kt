package com.onlygoodthings.app.platform

import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController
import kotlinx.cinterop.*
import platform.Foundation.*

@OptIn(ExperimentalForeignApi::class)
actual fun sharePlainText(text: String) {
    // Let the custom sharing dialog close before presenting the system's installed apps.
    platform.darwin.dispatch_after(platform.darwin.dispatch_time(platform.darwin.DISPATCH_TIME_NOW,200_000_000L),platform.darwin.dispatch_get_main_queue()) {
        val sheet=UIActivityViewController(activityItems=listOf(text),applicationActivities=null)
        var top=UIApplication.sharedApplication.keyWindow?.rootViewController
        while(top?.presentedViewController!=null)top=top?.presentedViewController
        val presenter=top ?: return@dispatch_after
        sheet.popoverPresentationController?.sourceView=presenter.view
        sheet.popoverPresentationController?.sourceRect=platform.CoreGraphics.CGRectMake(0.0,0.0,1.0,1.0)
        presenter.presentViewController(sheet,true,null)
    }
}

@OptIn(ExperimentalForeignApi::class)
actual suspend fun shareAccountExport(json: String, isCurrentOwner: () -> Boolean) {
    val path = NSTemporaryDirectory()+"OnlyGoodThings-mis-datos-${NSUUID().UUIDString}.json"
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        val bytes=json.encodeToByteArray()
        val data=bytes.usePinned { NSData.create(bytes=it.addressOf(0),length=bytes.size.toULong()) }
        check(data.writeToFile(path,atomically=true)) { "No se pudo guardar la copia" }
    }
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
        check(isCurrentOwner()) { "La sesión cambió" }
        val sheet=UIActivityViewController(listOf(NSURL.fileURLWithPath(path)),null)
        var top=UIApplication.sharedApplication.keyWindow?.rootViewController
        while(top?.presentedViewController!=null)top=top?.presentedViewController
        val presenter=checkNotNull(top) { "No se pudo abrir la hoja de guardado" }
        sheet.popoverPresentationController?.sourceView=presenter.view
        sheet.popoverPresentationController?.sourceRect=platform.CoreGraphics.CGRectMake(0.0,0.0,1.0,1.0)
        presenter.presentViewController(sheet,true,null)
    }
}
