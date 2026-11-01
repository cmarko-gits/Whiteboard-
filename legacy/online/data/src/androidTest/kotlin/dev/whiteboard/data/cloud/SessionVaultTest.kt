package dev.whiteboard.data.cloud

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.whiteboard.core.cloud.CloudAccount
import dev.whiteboard.core.cloud.CloudError
import dev.whiteboard.core.cloud.CloudFailure
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionVaultTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val preferences = context.getSharedPreferences("cloud-session", Context.MODE_PRIVATE)
    private val session = CloudSession(CloudAccount("test-account", "test@example.invalid"), "private-access", "private-refresh", 900_000)

    @Before fun prepare() {
        preferences.edit().clear().commit()
    }

    @After fun clean() {
        preferences.edit().clear().commit()
    }

    @Test fun sessionSurvivesNewVaultInstanceWithoutPlaintextCredentials() {
        SessionVault(context, "https://api.example.invalid").save(session)
        val sealed = checkNotNull(preferences.getString("sealed", null))
        assertFalse(sealed.contains(session.access))
        assertFalse(sealed.contains(session.refresh))
        assertFalse(sealed.contains(session.account.email))
        assertEquals(session, SessionVault(context, "https://api.example.invalid").load())
    }

    @Test fun modifiedCiphertextIsRejectedAndCleared() {
        val vault = SessionVault(context, "https://api.example.invalid")
        vault.save(session)
        val bytes = Base64.decode(preferences.getString("sealed", null), Base64.NO_WRAP)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        preferences.edit().putString("sealed", Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()
        rejected { vault.load() }
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun anotherServerCannotReuseStoredCredentials() {
        SessionVault(context, "https://first.example.invalid").save(session)
        rejected { SessionVault(context, "https://second.example.invalid").load() }
        assertTrue(preferences.all.isEmpty())
    }

    private fun rejected(action: () -> Unit) {
        try {
            action()
            fail("Expected authenticated decryption failure")
        } catch (error: CloudFailure) {
            assertEquals(CloudError.SECURE_STORAGE, error.error)
        }
    }
}
