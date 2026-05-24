package com.deepshield.ai.watermark

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyPair
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cryptographic signing engine for media authenticity.
 * Uses ECDSA (as Ed25519 fallback) for digital signatures
 * and SHA-256 for content hashing.
 *
 * In production, the private key would be stored in:
 * - Android Keystore (hardware-backed TEE)
 * - Qualcomm Secure Enclave via QTI Keymaster
 */
@Singleton
class CryptoSigner @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "deepshield_crypto_signer"
        private const val PREF_PUBLIC_KEY = "public_key"
        private const val PREF_PRIVATE_KEY = "private_key"
    }

    private val keyPair: KeyPair by lazy { generateKeyPair() }

    /**
     * Sign content data with the device's private key.
     * Returns hex-encoded signature string.
     */
    fun sign(data: ByteArray): String {
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(data)
        val signatureBytes = signer.sign()
        return signatureBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify a signature against content data.
     */
    fun verify(data: ByteArray, signatureHex: String): Boolean {
        return try {
            val signatureBytes = signatureHex.chunked(2)
                .map { it.toInt(16).toByte() }
                .toByteArray()

            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(keyPair.public)
            verifier.update(data)
            verifier.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Compute SHA-256 hash of data.
     */
    fun sha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    /**
     * Compute SHA-256 hash of a string.
     */
    fun sha256(data: String): String = sha256(data.toByteArray(Charsets.UTF_8))

    /**
     * Get the public key fingerprint (for display/verification).
     */
    fun getPublicKeyFingerprint(): String {
        val pubKeyBytes = keyPair.public.encoded
        return sha256(pubKeyBytes).take(16) // First 16 chars
    }

    private fun generateKeyPair(): KeyPair {
        loadPersistedKeyPair()?.let { return it }

        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        return keyPairGenerator.generateKeyPair().also(::persistKeyPair)
    }

    private fun loadPersistedKeyPair(): KeyPair? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val publicKeyBase64 = prefs.getString(PREF_PUBLIC_KEY, null) ?: return null
        val privateKeyBase64 = prefs.getString(PREF_PRIVATE_KEY, null) ?: return null

        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val publicKey = decodePublicKey(keyFactory, publicKeyBase64)
            val privateKey = decodePrivateKey(keyFactory, privateKeyBase64)
            KeyPair(publicKey, privateKey)
        } catch (_: Exception) {
            null
        }
    }

    private fun persistKeyPair(keyPair: KeyPair) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_PUBLIC_KEY, Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP))
            .putString(PREF_PRIVATE_KEY, Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP))
            .apply()
    }

    private fun decodePublicKey(keyFactory: KeyFactory, base64: String): PublicKey {
        val keyBytes = Base64.decode(base64, Base64.DEFAULT)
        return keyFactory.generatePublic(X509EncodedKeySpec(keyBytes))
    }

    private fun decodePrivateKey(keyFactory: KeyFactory, base64: String): PrivateKey {
        val keyBytes = Base64.decode(base64, Base64.DEFAULT)
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(keyBytes))
    }
}
