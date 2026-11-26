package com.onlygoodthings.app.platform

/** Opens a user-controlled composer; OGT never sends or purchases SMS/email. */
expect fun sharePostInvitation(text:String,channel:String,contact:String)
