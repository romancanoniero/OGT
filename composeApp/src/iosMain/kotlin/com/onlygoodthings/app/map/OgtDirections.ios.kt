package com.onlygoodthings.app.map

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openWalkingDirections(latitude: Double, longitude: Double) {
    val app = UIApplication.sharedApplication
    val native = NSURL.URLWithString("comgooglemaps://?daddr=$latitude,$longitude&directionsmode=walking")
    val web = NSURL.URLWithString(walkingDirectionsWebUrl(latitude, longitude))
    when {
        native != null && app.canOpenURL(native) -> app.openURL(native)
        web != null -> app.openURL(web)
    }
}
