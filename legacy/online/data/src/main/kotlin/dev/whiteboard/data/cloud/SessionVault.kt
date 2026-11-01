package dev.whiteboard.data.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.whiteboard.core.cloud.CloudAccount
import dev.whiteboard.core.cloud.CloudError
import dev.whiteboard.core.cloud.CloudFailure
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal data class CloudSession(val account: CloudAccount, val access: String, val refresh: String, val expires: Long)

/** Key material stays in Android Keystore; copied preferences cannot decrypt on another installation. */
internal interface SessionStorage {
    fun load(): CloudSession?
    fun save(session: CloudSession)
    fun clear()
}

internal class SessionVault(context: Context, private val origin: String) : SessionStorage {
    private val preferences = context.getSharedPreferences("cloud-session", Context.MODE_PRIVATE)
    private val associatedData = "${context.packageName}\n$origin".toByteArray(Charsets.UTF_8)

    override fun load(): CloudSession? {
        val encoded = preferences.getString("sealed", null) ?: return null
        try {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            require(bytes.size in 29..8192)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD(associatedData)
            val source = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            require(source.getString("origin") == origin)
            return CloudSession(
                CloudAccount(source.getString("id"), source.getString("email")),
                source.getString("access"),
                source.getString("refresh"),
                source.getLong("expires")
            )
        } catch (_: Exception) {
            clear()
            throw CloudFailure(CloudError.SECURE_STORAGE)
        }
    }

    override fun save(session: CloudSession) {
        try {
            val source = JSONObject().put("origin", origin).put("id", session.account.id).put("email", session.account.email)
                .put("access", session.access).put("refresh", session.refresh).put("expires", session.expires)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key())
            cipher.updateAAD(associatedData)
            val sealed = cipher.iv + cipher.doFinal(source.toString().toByteArray(Charsets.UTF_8))
            check(preferences.edit().putString("sealed", Base64.encodeToString(sealed, Base64.NO_WRAP)).commit())
        } catch (_: Exception) {
            throw CloudFailure(CloudError.SECURE_STORAGE)
        }
    }

    override fun clear() {
        if (!preferences.edit().clear().commit()) throw CloudFailure(CloudError.SECURE_STORAGE)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = store.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setKeySize(
                        256
                    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true).build()
            )
        }.generateKey()
    }

    companion object {
        private const val ALIAS = "whiteboard.cloud.session.v1"
    }
}
