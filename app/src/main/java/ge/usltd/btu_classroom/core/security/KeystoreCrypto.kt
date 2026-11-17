package ge.usltd.btu_classroom.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SessionCipher {
  fun encrypt(plaintext: String): String?
  fun decrypt(encodedBlob: String): String?
}

/**
 * Encrypts sensitive session cookies with an Android Keystore AES-256-GCM key.
 * Output format: base64(iv) + ":" + base64(ciphertext_with_auth_tag).
 */
class AndroidKeystoreSessionCipher(
  private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : SessionCipher {

  override fun encrypt(plaintext: String): String? {
    if (plaintext.isBlank()) return null
    return try {
      val secretKey = getOrCreateSecretKey()
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.ENCRYPT_MODE, secretKey)
      val iv = cipher.iv
      val ciphertext = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))
      val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
      val ctB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
      "$ivB64:$ctB64"
    } catch (_: Exception) {
      null
    }
  }

  override fun decrypt(encodedBlob: String): String? {
    if (encodedBlob.isBlank()) return null
    return try {
      val parts = encodedBlob.split(':')
      if (parts.size != 2) return null
      val iv = Base64.decode(parts[0], Base64.NO_WRAP)
      val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
      if (iv.size != 12 || ciphertext.size < 16) return null
      val secretKey = getExistingSecretKey() ?: return null
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
      val plainBytes = cipher.doFinal(ciphertext)
      String(plainBytes, StandardCharsets.UTF_8).ifBlank { null }
    } catch (_: Exception) {
      null
    }
  }

  private fun getExistingSecretKey(): SecretKey? {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val entry = keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry
    return entry?.secretKey
  }

  @Synchronized
  private fun getOrCreateSecretKey(): SecretKey {
    getExistingSecretKey()?.let { return it }
    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
      keyAlias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .setRandomizedEncryptionRequired(true)
      .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  companion object {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val DEFAULT_KEY_ALIAS = "btucm_session_aes_gcm_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
  }
}
