package com.onlygoodthings.backend.auth

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.onlygoodthings.shared.domain.UserRole
import java.math.BigInteger
import java.net.URI
import java.security.KeyFactory
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

/**
 * Verifica ID tokens de Firebase con las claves públicas de Google.
 * No requiere service account: alcanza el projectId.
 */
class FirebaseJwksTokenVerifier(
    private val projectId: String,
) : FirebaseTokenVerifier {

    private val mapper = jacksonObjectMapper()
    private val cache = AtomicReference<CachedKeys?>(null)

    override fun verify(idToken: String): VerifiedFirebaseUser {
        val parts = idToken.split('.')
        require(parts.size == 3) { "JWT Firebase inválido" }
        val header = readJson(parts[0])
        val payload = readJson(parts[1])
        require(header["alg"] == "RS256") { "Algoritmo de token no soportado" }
        val kid = header["kid"]?.toString().orEmpty()
        require(kid.isNotBlank()) { "Token sin kid" }
        val key = publicKey(kid)
        val signingInput = "${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII)
        val signature = Signature.getInstance("SHA256withRSA").apply {
            initVerify(key)
            update(signingInput)
        }
        require(signature.verify(b64Url(parts[2]))) { "Firma Firebase inválida" }
        require(payload["aud"] == projectId) { "aud de token no coincide" }
        require(payload["iss"] == "https://securetoken.google.com/$projectId") { "iss de token no coincide" }
        val exp = (payload["exp"] as? Number)?.toLong() ?: 0L
        require(exp > System.currentTimeMillis() / 1000) { "Token Firebase vencido" }
        val uid = payload["sub"]?.toString().orEmpty()
        require(uid.isNotBlank()) { "Token sin uid" }
        val roleClaim = payload["role"]?.toString() ?: UserRole.USER.name
        val firebase = payload["firebase"] as? Map<*, *>
        return VerifiedFirebaseUser(
            firebaseUid = uid,
            email = payload["email"]?.toString(),
            phone = payload["phone_number"]?.toString(),
            displayName = payload["name"]?.toString() ?: firebase?.get("displayName")?.toString(),
            role = runCatching { UserRole.valueOf(roleClaim) }.getOrDefault(UserRole.USER),
        )
    }

    private fun publicKey(kid: String): RSAPublicKey {
        val now = System.currentTimeMillis()
        val hit = cache.get()
        if (hit != null && now < hit.expiresAt && hit.keys.containsKey(kid)) return hit.keys.getValue(kid)
        val fresh = fetchKeys()
        cache.set(fresh)
        return fresh.keys[kid] ?: error("kid Firebase desconocido")
    }

    @Suppress("UNCHECKED_CAST")
    private fun fetchKeys(): CachedKeys {
        val raw = URI.create(JWKS_URL).toURL().readText()
        val doc = mapper.readValue(raw, Map::class.java)
        val keys = (doc["keys"] as? List<Map<String, Any?>>).orEmpty()
        val parsed = keys.mapNotNull { jwk ->
            val kid = jwk["kid"]?.toString() ?: return@mapNotNull null
            val n = BigInteger(1, b64Url(jwk["n"]?.toString() ?: return@mapNotNull null))
            val e = BigInteger(1, b64Url(jwk["e"]?.toString() ?: return@mapNotNull null))
            val key = KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(n, e)) as RSAPublicKey
            kid to key
        }.toMap()
        require(parsed.isNotEmpty()) { "JWKS de Firebase vacío" }
        return CachedKeys(parsed, System.currentTimeMillis() + CACHE_MS)
    }

    @Suppress("UNCHECKED_CAST")
    private fun readJson(part: String): Map<String, Any?> =
        mapper.readValue(b64Url(part), Map::class.java) as Map<String, Any?>

    private fun b64Url(raw: String): ByteArray {
        val padded = raw + "=".repeat((4 - raw.length % 4) % 4)
        return Base64.getUrlDecoder().decode(padded)
    }

    private data class CachedKeys(
        val keys: Map<String, RSAPublicKey>,
        val expiresAt: Long,
    )

    private companion object {
        const val JWKS_URL =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"
        const val CACHE_MS = 60L * 60L * 1000L
    }
}
