package com.haiom.app.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("hai_om_secure", Context.MODE_PRIVATE)
    private val alias = "hai_om_local_aes"

    fun saveGitHubToken(value: String) = putEncrypted("github_token", value.trim())
    fun githubToken(): String = getEncrypted("github_token")
    fun clearGitHubToken() = prefs.edit().remove("github_token").apply()

    fun saveGitHubApp(
        appId: Long,
        slug: String,
        privateKeyPem: String,
        ownerLogin: String,
        installationId: Long
    ) {
        putEncrypted("github_app_id", appId.toString())
        putEncrypted("github_app_slug", slug)
        putEncrypted("github_app_private_key", privateKeyPem)
        putEncrypted("github_app_owner", ownerLogin)
        putEncrypted("github_installation_id", installationId.toString())
    }

    fun githubAppId(): Long? = getEncrypted("github_app_id").toLongOrNull()
    fun githubAppSlug(): String = getEncrypted("github_app_slug")
    fun githubAppPrivateKey(): String = getEncrypted("github_app_private_key")
    fun githubAppOwner(): String = getEncrypted("github_app_owner")
    fun githubInstallationId(): Long? = getEncrypted("github_installation_id").toLongOrNull()

    fun hasGitHubApp(): Boolean =
        githubAppId() != null &&
            githubInstallationId() != null &&
            githubAppPrivateKey().isNotBlank()

    fun clearGitHubApp() {
        prefs.edit()
            .remove("github_app_id")
            .remove("github_app_slug")
            .remove("github_app_private_key")
            .remove("github_app_owner")
            .remove("github_installation_id")
            .apply()
    }

    fun saveOmniRouteUrl(value: String) = putEncrypted("omniroute_url", value.trim())
    fun omniRouteUrl(): String = getEncrypted("omniroute_url")

    fun saveOmniRouteKey(value: String) = putEncrypted("omniroute_key", value.trim())
    fun omniRouteKey(): String = getEncrypted("omniroute_key")

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun putEncrypted(name: String, plain: String) {
        if (plain.isBlank()) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val packed = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(body, Base64.NO_WRAP)
        prefs.edit().putString(name, packed).apply()
    }

    private fun getEncrypted(name: String): String {
        val packed = prefs.getString(name, null) ?: return ""
        return runCatching {
            val parts = packed.split(":", limit = 2)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val body = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.doFinal(body).toString(Charsets.UTF_8)
        }.getOrDefault("")
    }
}
