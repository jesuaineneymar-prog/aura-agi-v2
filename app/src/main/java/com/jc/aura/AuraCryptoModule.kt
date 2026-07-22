package com.jc.aura

import android.content.Context
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class AuraCryptoModule(private val context: Context) {

    private val prefs = context.getSharedPreferences("aura_crypto", Context.MODE_PRIVATE)
    private val KEY_ALIAS = "aura_master_key"

    private fun getOrCreateKey(): SecretKey {
        val stored = prefs.getString(KEY_ALIAS, null)
        return if (stored != null) {
            val keyBytes = Base64.decode(stored, Base64.DEFAULT)
            SecretKeySpec(keyBytes, "AES")
        } else {
            val kg = KeyGenerator.getInstance("AES")
            kg.init(256)
            val key = kg.generateKey()
            prefs.edit().putString(KEY_ALIAS, Base64.encodeToString(key.encoded, Base64.DEFAULT)).apply()
            key
        }
    }

    fun encrypt(plaintext: String): String {
        return try {
            val key = getOrCreateKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plaintext.toByteArray())
            val combined = iv + encrypted
            Base64.encodeToString(combined, Base64.DEFAULT)
        } catch (e: Exception) {
            plaintext
        }
    }

    fun decrypt(ciphertext: String): String {
        return try {
            val key = getOrCreateKey()
            val combined = Base64.decode(ciphertext, Base64.DEFAULT)
            val iv = combined.sliceArray(0..11)
            val encrypted = combined.sliceArray(12 until combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted))
        } catch (e: Exception) {
            ciphertext
        }
    }

    fun hashSha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    suspend fun handleCryptoCommand(command: String): String {
        return when {
            command.contains("encriptar") || command.contains("criptografar") || command.contains("cifrar") -> {
                val text = command.replace(Regex("(?i)(encriptar|criptografar|cifrar)\\s*"), "").trim()
                if (text.isBlank()) "Senhor, diga o texto a encriptar. Ex: 'encriptar minha senha secreta'."
                else "Senhor, texto encriptado:\n${encrypt(text)}"
            }
            command.contains("desencriptar") || command.contains("decifrar") || command.contains("descriptografar") -> {
                val text = command.replace(Regex("(?i)(desencriptar|decifrar|descriptografar)\\s*"), "").trim()
                if (text.isBlank()) "Senhor, diga o texto a desencriptar."
                else {
                    val result = decrypt(text)
                    "Senhor, texto desencriptado:\n$result"
                }
            }
            command.contains("hash") || command.contains("sha") -> {
                val text = command.replace(Regex("(?i)(hash|sha256?)\\s*(de)?\\s*"), "").trim()
                if (text.isBlank()) "Senhor, diga o texto para gerar hash. Ex: 'hash de minha senha'."
                else "Senhor, SHA-256:\n${hashSha256(text)}"
            }
            else -> "Senhor, comandos de criptografia: 'encriptar [texto]', 'desencriptar [texto]', 'hash de [texto]'."
        }
    }
}
