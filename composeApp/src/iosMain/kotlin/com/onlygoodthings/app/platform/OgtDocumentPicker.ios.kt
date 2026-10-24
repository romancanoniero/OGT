@file:OptIn(ExperimentalForeignApi::class)

package com.onlygoodthings.app.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import platform.UIKit.UIApplication
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIViewController
import platform.darwin.NSObject

@Composable
actual fun rememberOgtDocumentPicker(onPicked: (String) -> Unit): () -> Unit {
    val holder = remember { IosDocumentHolder() }
    return {
        val picker = UIImagePickerController()
        picker.sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypePhotoLibrary
        picker.mediaTypes = listOf("public.image")
        val delegate = IosDocumentDelegate(onPicked)
        holder.delegate = delegate
        picker.delegate = delegate
        topDocumentController()?.presentViewController(picker, animated = true, completion = null)
    }
}

private class IosDocumentHolder {
    var delegate: IosDocumentDelegate? = null
}

private class IosDocumentDelegate(
    private val onPicked: (String) -> Unit,
) : NSObject(), UIImagePickerControllerDelegateProtocol, UINavigationControllerDelegateProtocol {
    override fun imagePickerController(
        picker: UIImagePickerController,
        didFinishPickingMediaWithInfo: Map<Any?, *>,
    ) {
        picker.dismissViewControllerAnimated(true, completion = null)
        onPicked("foto")
    }

    override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
        picker.dismissViewControllerAnimated(true, completion = null)
    }
}

private fun topDocumentController(): UIViewController? {
    val window = UIApplication.sharedApplication.keyWindow
    var top = window?.rootViewController
    while (top?.presentedViewController != null) {
        top = top?.presentedViewController
    }
    return top
}
