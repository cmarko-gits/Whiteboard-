package dev.whiteboard.app

import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalPrivacyTest {
    @Test fun applicationCannotUseNetworkPermissions() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        for (permission in listOf("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE")) {
            assertEquals(PackageManager.PERMISSION_DENIED, context.packageManager.checkPermission(permission, context.packageName))
        }
    }
}
