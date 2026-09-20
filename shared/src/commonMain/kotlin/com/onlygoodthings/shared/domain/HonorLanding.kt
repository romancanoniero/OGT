package com.onlygoodthings.shared.domain

const val OgtAndroidPackage = "com.onlygoodthings.app"
const val OgtIosTeamAppId = "WER3D825VB.com.onlygoodthings.app"
const val OgtHonorPlayReferrerKey = "honor"

/** SHA-256 del keystore debug local; producción suma más via `OGT_ANDROID_SHA256`. */
val OgtAndroidDebugSha256 = "0D:B7:97:43:4D:57:AC:85:F0:22:00:8D:A0:68:95:36:82:A3:81:3D:89:E3:98:6D:3B:8E:07:15:0A:70:D2:44"

data class HonorStoreLinks(
    val iosStoreUrl: String,
    val playStoreUrl: String,
    val iosAppId: String? = null,
)

fun honorPlayReferrer(token: String): String = "$OgtHonorPlayReferrerKey=$token"

fun honorPlayStoreUrl(token: String, packageName: String = OgtAndroidPackage): String =
    "https://play.google.com/store/apps/details?id=$packageName&referrer=${percentEncode(honorPlayReferrer(token))}"

fun honorAppStoreUrl(appId: String?): String =
    if (!appId.isNullOrBlank()) "https://apps.apple.com/app/id$appId"
    else "https://apps.apple.com/search?term=Only%20Good%20Things"

fun honorAndroidIntentUrl(token: String, playUrl: String = honorPlayStoreUrl(token)): String =
    "intent://h/$token#Intent;scheme=ogt;package=$OgtAndroidPackage;end"

fun defaultHonorStoreLinks(token: String, iosAppId: String? = null, iosStoreUrl: String? = null): HonorStoreLinks =
    HonorStoreLinks(
        iosStoreUrl = iosStoreUrl?.takeIf { it.isNotBlank() } ?: honorAppStoreUrl(iosAppId),
        playStoreUrl = honorPlayStoreUrl(token),
        iosAppId = iosAppId?.takeIf { it.isNotBlank() },
    )

/** Referrer de Play (`honor=token`) o query mezclada. */
fun parseHonorReferrer(raw: String): String? {
    val decoded = percentDecode(raw.trim())
    decoded.split('&').forEach { part ->
        val eq = part.indexOf('=')
        if (eq <= 0) return@forEach
        val key = percentDecode(part.substring(0, eq))
        val value = percentDecode(part.substring(eq + 1))
        if (key.equals(OgtHonorPlayReferrerKey, ignoreCase = true)) return parseHonorToken(value)
    }
    return null
}

/** Portapapeles: solo si parece un link de honor, no un texto suelto. */
fun parseHonorClipboard(raw: String): String? {
    if (!raw.contains("/h/", ignoreCase = true)) return null
    return parseHonorToken(raw)
}

fun appleAppSiteAssociationJson(): String = """
{
  "applinks": {
    "apps": [],
    "details": [
      {
        "appID": "$OgtIosTeamAppId",
        "appIDs": ["$OgtIosTeamAppId"],
        "paths": ["/h/*", "/p/*"],
        "components": [
          { "/": "/h/*" },
          { "/": "/p/*" }
        ]
      }
    ]
  }
}
""".trimIndent()

fun assetLinksJson(fingerprints: List<String>): String {
    val sha = fingerprints.filter { it.isNotBlank() }.ifEmpty { listOf(OgtAndroidDebugSha256) }
        .joinToString(",\n        ") { "\"$it\"" }
    return """
[
  {
    "relation": ["delegate_permission/common.handle_all_urls"],
    "target": {
      "namespace": "android_app",
      "package_name": "$OgtAndroidPackage",
      "sha256_cert_fingerprints": [
        $sha
      ]
    }
  }
]
""".trimIndent()
}

fun honorLandingHtml(
    givenName: String,
    issuerName: String?,
    token: String,
    stores: HonorStoreLinks = defaultHonorStoreLinks(token),
): String {
    val who = htmlEscape(issuerName?.takeIf { it.isNotBlank() } ?: "Alguien de la comunidad")
    val asName = htmlEscape(givenName.ifBlank { "vos" })
    val safe = token.filter { it.isLetterOrDigit() || it == '.' || it == '-' }
    val app = honorAppLink(safe)
    val play = stores.playStoreUrl
    val ios = stores.iosStoreUrl
    val intent = honorAndroidIntentUrl(safe, play)
    val banner = stores.iosAppId?.let {
        """<meta name="apple-itunes-app" content="app-id=$it, app-argument=$app"/>"""
    }.orEmpty()
    return """
<!doctype html>
<html lang="es"><head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>Reivindicar mención</title>
  $banner
  <style>
    body{font-family:system-ui,sans-serif;background:#F4F5F7;color:#111213;margin:0;padding:32px 20px}
    main{max-width:420px;margin:0 auto;background:#fff;border:1px solid #E4E6EA;border-radius:20px;padding:24px}
    p{color:#5B6570;line-height:1.45}
    a,button{display:block;width:100%;margin-top:12px;padding:14px;border-radius:14px;text-align:center;text-decoration:none;font-weight:600;border:0;font:inherit;cursor:pointer;box-sizing:border-box}
    .app{background:#0D3D4D;color:#fff}
    .store{background:#111213;color:#fff}
    .again{background:transparent;color:#5B6570}
  </style>
</head><body><main>
  <h1>$who te mencionó como $asName</h1>
          <p>Tocá <strong>Abrir en la app</strong> para reivindicar el crédito. La tienda es solo si todavía no la tenés instalada.</p>
          <a class="app" id="open" href="$app">Abrir en la app</a>
          <a class="store" id="store" href="$play">No la tengo, ir a la tienda</a>
          <button class="again" id="again" type="button">Ya la instalé, continuar</button>
          <script>
            (function(){
              var app = "$app";
              var play = "$play";
              var ios = "$ios";
              var intent = "$intent";
              var android = /Android/i.test(navigator.userAgent);
              var apple = /iPhone|iPad|iPod/i.test(navigator.userAgent);
              var openEl = document.getElementById("open");
              var storeEl = document.getElementById("store");
              var againEl = document.getElementById("again");
              if (openEl) openEl.href = android ? intent : app;
              if (storeEl) storeEl.href = apple ? ios : play;
              function copyApp(){
                if (navigator.clipboard && navigator.clipboard.writeText) {
                  navigator.clipboard.writeText(app).catch(function(){});
                }
              }
              function tryOpenApp(){
                var target = android ? intent : app;
                var frame = document.createElement("iframe");
                frame.style.display = "none";
                frame.src = app;
                document.body.appendChild(frame);
                setTimeout(function(){ frame.remove(); }, 800);
                location.href = target;
              }
              if (againEl) againEl.addEventListener("click", function(){ copyApp(); tryOpenApp(); });
              tryOpenApp();
            })();
          </script>
</main></body></html>
""".trimIndent()
}

fun honorMissingHtml(): String = """
<!doctype html>
<html lang="es"><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width, initial-scale=1"/><title>Link inválido</title></head>
<body style="font-family:system-ui;background:#F4F5F7;color:#111213;padding:32px 20px">
<p>Este enlace de mención no existe o ya no sirve.</p>
</body></html>
""".trimIndent()

internal fun htmlEscape(raw: String): String = buildString(raw.length) {
    raw.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}

internal fun percentEncode(raw: String): String = buildString(raw.length) {
    raw.forEach { ch ->
        val ok = ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~'
        if (ok) append(ch)
        else append('%').append(ch.code.toString(16).uppercase().padStart(2, '0'))
    }
}

internal fun percentDecode(raw: String): String {
    val out = StringBuilder(raw.length)
    var i = 0
    while (i < raw.length) {
        val ch = raw[i]
        if (ch == '%' && i + 2 < raw.length) {
            val hex = raw.substring(i + 1, i + 3)
            val parsed = hex.toIntOrNull(16)
            if (parsed != null) {
                out.append(parsed.toChar())
                i += 3
                continue
            }
        }
        if (ch == '+') out.append(' ') else out.append(ch)
        i++
    }
    return out.toString()
}
