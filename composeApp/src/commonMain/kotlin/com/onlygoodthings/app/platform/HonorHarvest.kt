package com.onlygoodthings.app.platform

/** Portapapeles: el landing copia `ogt://h/{token}` al ir a la tienda. */
expect fun readHonorClipboard(): String?

/** Play Install Referrer (`honor=token`). En iOS no existe. */
expect fun startHonorInstallReferrer(onToken: (String) -> Unit)
