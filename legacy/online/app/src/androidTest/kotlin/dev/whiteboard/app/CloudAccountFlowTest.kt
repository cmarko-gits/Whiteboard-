package dev.whiteboard.app

import android.content.Context
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.*
import dev.whiteboard.data.AppSettings
import dev.whiteboard.data.DatabaseMigrations
import dev.whiteboard.data.WhiteboardDatabase
import dev.whiteboard.data.cloud.CloudHttp
import dev.whiteboard.data.cloud.RemoteAccounts
import dev.whiteboard.data.cloud.RemoteBoards
import dev.whiteboard.data.cloud.RoomPublicationStore
import dev.whiteboard.data.cloud.RoomSyncStore
import dev.whiteboard.format.ElementDigest
import dev.whiteboard.sync.AndroidSyncScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Run explicitly through tools/Test-CloudAccounts.ps1, which owns the loopback server and disposable account. */
@RunWith(AndroidJUnit4::class)
class CloudAccountFlowTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun text(value: String): UiObject2 = checkNotNull(device.wait(Until.findObject(By.text(value)), 15_000)) { value }
    private fun findLocalBoard(name: String) {
        control("New board")
        repeat(10) {
            if (device.hasObject(By.text(name))) return
            val grid = checkNotNull(device.wait(Until.findObject(By.desc("All boards").scrollable(true)), 5_000))
            grid.scroll(Direction.UP, 0.8f)
            device.waitForIdle()
        }
        text(name)
    }
    private fun control(value: String): UiObject2 {
        val found = device.wait(Until.findObject(By.desc(value)), 15_000)
        if (found == null) {
            device.dumpWindowHierarchy(java.io.File(context.getExternalFilesDir(null), "cloud-account-failure.xml"))
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "cloud-account-failure.png"))
        }
        return checkNotNull(found) { value }
    }

    @Test fun loginSecureRestoreLogoutAndDeleteAccount(): Unit = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val email = arguments.getString("whiteboard.cloudEmail")
        assumeTrue("Requires disposable local backend account", email != null)
        val password = "disposable integration passphrase"
        val http = CloudHttp(BuildConfig.CLOUD_BASE_URL, true)
        // Clear only credentials belonging to this integration account; refuse to overwrite another signed-in account.
        val previous = RemoteAccounts(context, http)
        previous.restore()
        assumeTrue("Do not replace an existing user's cloud session", previous.account.value == null)
        try {
            AppSettings(context).finishOnboarding()
            context.startActivity(
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            device.waitForIdle()
            device.findObject(By.text("OK"))?.click()
            control("New board")
            control("Cloud boards").click()
            text("Sign in").click()
            val inputs = checkNotNull(device.wait(Until.findObjects(By.clazz("android.widget.EditText")), 10_000))
            assertEquals(2, inputs.size)
            inputs[0].text = email
            inputs[1].text = password
            control("Continue").click()
            text(checkNotNull(email))
            text("Sign out")
            val preferences = context.getSharedPreferences("cloud-session", Context.MODE_PRIVATE)
            val sealed = checkNotNull(preferences.getString("sealed", null))
            assertFalse(sealed.contains(email))
            assertFalse(sealed.contains(password))
            val restored = RemoteAccounts(context, http)
            restored.restore()
            assertEquals(email, restored.account.value?.email)
            verifyDocumentProtocol(restored)
            verifyPublicationProtocol(restored)
            verifyBackgroundPublication(restored)
            verifyPublicationUi(restored)
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "account-signed-in.png"))
            text("Sign out").click()
            text("Signed out.")
            assertTrue(preferences.all.isEmpty())
            // A separate restored instance confirms online deletion removes the identity and clears secure credentials.
            val deletion = RemoteAccounts(context, http)
            deletion.login(email, password)
            deletion.delete(password)
            assertNull(deletion.account.value)
            assertTrue(preferences.all.isEmpty())
            text("Done").click()
        } finally {
            // Clean only this disposable identity, including when a UI assertion interrupts the flow.
            val cleanup = RemoteAccounts(context, http)
            runCatching { cleanup.restore() }
            cleanup.account.value?.takeIf { it.email == email }?.let { identity ->
                AndroidSyncScheduler(context, BuildConfig.CLOUD_BASE_URL).cancel(identity.id)
                runCatching { cleanup.logout() }
            }
        }
    }

    private suspend fun verifyDocumentProtocol(accounts: RemoteAccounts) {
        val gateway = RemoteBoards(accounts)
        val accountId = checkNotNull(accounts.account.value).id
        val stroke = Stroke(
            points = listOf(Point(0.1f, 0.2f), Point(20.3f, 30.7f), Point(40.9f, 50.1f)),
            style = InkStyle(width = 2.4f, opacity = 0.7f)
        )
        val note = Note(bounds = Bounds(1.1f, 2.2f, 100.3f, 120.7f), text = "Život Ћирилица 🖊", fontSize = 21.4f)
        val group = ElementGroup(children = listOf(stroke, note))
        val text = TextElement(bounds = Bounds(3.1f, 4.7f, 200.9f, 140.1f), text = "Typed text", fontSize = 22.3f)
        val source = Board(name = "Disposable protocol fixture", pages = listOf(Page(elements = listOf(group, text))))
        val creationId = newId()
        val created = gateway.create(accountId, creationId, source)
        val shared = created.document
        assertEquals(shared.id, gateway.create(accountId, creationId, source).document.id)
        assertTrue(gateway.list(accountId).items.any { it.id == shared.id })
        val changed = shared.withPage(
            shared.activePage.copy(
                elements = shared.activePage.elements.map { element ->
                    element.mapLeaves { leaf ->
                        when (leaf) {
                            is Stroke -> leaf.copy(style = leaf.style.copy(width = 4.1f, opacity = 0.63f))
                            is Note -> leaf.copy(text = "Changed 🖊", fontSize = 19.7f)
                            is TextElement -> leaf.copy(fontSize = 23.7f)
                            else -> leaf
                        }
                    }
                }
            )
        )
        val engine = DeltaEngine(ElementDigest())
        val databaseName = "cloud-protocol-${newId()}.db"
        fun open() = Room.databaseBuilder(context, WhiteboardDatabase::class.java, databaseName).build()
        var database = open()
        try {
            val store = RoomSyncStore(database)
            store.install(accountId, created)
            val operationId = newId()
            store.edit(accountId, changed, 0, operationId)
            database.close()
            database = open()
            val reopened = RoomSyncStore(database)
            val operation = checkNotNull(reopened.load(accountId, shared.id)).pending.single()
            assertEquals(operationId, operation.id)
            var loseAcknowledgment = true
            val sentIds = mutableListOf<String>()
            val unreliable = object : BoardGateway by gateway {
                override suspend fun submit(
                    accountId: String,
                    boardId: String,
                    operationId: String,
                    baseRevision: Long,
                    delta: DocumentDelta
                ): Long {
                    sentIds += operationId
                    val revision = gateway.submit(accountId, boardId, operationId, baseRevision, delta)
                    if (loseAcknowledgment) {
                        loseAcknowledgment = false
                        throw CloudFailure(CloudError.OFFLINE)
                    }
                    return revision
                }
            }
            val coordinator = SyncCoordinator(accounts, unreliable, reopened)
            try {
                coordinator.reconcile(accountId, shared.id)
                fail("Expected lost acknowledgment")
            } catch (error: CloudFailure) {
                assertEquals(CloudError.OFFLINE, error.error)
            }
            assertEquals(operation.id, checkNotNull(reopened.load(accountId, shared.id)).pending.single().id)
            val synchronized = coordinator.reconcile(accountId, shared.id)
            assertEquals(listOf(operation.id, operation.id), sentIds)
            val events = gateway.changes(accountId, shared.id, 0)
            assertEquals(1, events.operations.size)
            assertEquals(1L, events.nextRevision)
            val replay = engine.apply(shared, events.operations.single().delta).board
            assertEquals(changed.activePage.elements, replay.activePage.elements)
            val loaded = gateway.load(accountId, shared.id)
            assertEquals(changed.activePage.elements, loaded.document.activePage.elements)
            assertTrue(synchronized.pending.isEmpty())
            assertEquals(changed.activePage.elements, synchronized.working.activePage.elements)
        } finally {
            database.close()
            context.deleteDatabase(databaseName)
        }
        gateway.delete(accountId, shared.id)
        assertFalse(gateway.list(accountId).items.any { it.id == shared.id })
    }

    private suspend fun verifyPublicationProtocol(accounts: RemoteAccounts) {
        val accountId = checkNotNull(accounts.account.value).id
        val gateway = RemoteBoards(accounts)
        val source = Board(
            name = "Disposable publication protocol",
            pages = listOf(
                Page(
                    elements = listOf(
                        Note(bounds = Bounds(0f, 0f, 100f, 100f), text = "Captured Ћ 🖊")
                    )
                )
            )
        )
        val name = "publication-protocol-${newId()}.db"
        fun open() = Room.databaseBuilder(context, WhiteboardDatabase::class.java, name).build()
        var database = open()
        val ids = mutableListOf<String>()
        val unreliable = object : BoardGateway by gateway {
            override suspend fun create(accountId: String, operationId: String, document: Board): CloudBoardSnapshot {
                ids += operationId
                val created = gateway.create(accountId, operationId, document)
                if (ids.size == 1) throw CloudFailure(CloudError.OFFLINE)
                return created
            }
        }
        try {
            val initial = PublicationCoordinator(accounts, unreliable, RoomPublicationStore(database))
            try {
                initial.publish(accountId, source)
                fail("Expected lost creation acknowledgment")
            } catch (error: CloudFailure) {
                assertEquals(CloudError.OFFLINE, error.error)
            }
            database.close()
            database = open()
            val resumed = PublicationCoordinator(accounts, unreliable, RoomPublicationStore(database))
            val result = resumed.publish(accountId, source.copy(name = "Later private edit"))
            assertEquals(listOf(ids.first(), ids.first()), ids)
            assertEquals(source.name, result.working.name)
            assertEquals(1, gateway.list(accountId).items.count { it.name == source.name })
            assertTrue(RoomPublicationStore(database).find(accountId, source.id)?.document == null)
            gateway.delete(accountId, result.working.id)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    private suspend fun verifyBackgroundPublication(accounts: RemoteAccounts) {
        val accountId = checkNotNull(accounts.account.value).id
        val source = Board(name = "Background delivery ${newId()}")
        fun open() = Room.databaseBuilder(context, WhiteboardDatabase::class.java, "whiteboard.db")
            .addMigrations(DatabaseMigrations.FROM_1_TO_2, DatabaseMigrations.FROM_2_TO_3, DatabaseMigrations.FROM_3_TO_4).build()
        // Persist and reopen the explicit capture before the real application Worker/Factory sees it.
        open().let { database ->
            try {
                RoomPublicationStore(database).stage(accountId, source)
            } finally {
                database.close()
            }
        }
        val database = open()
        try {
            val publications = RoomPublicationStore(database)
            val capture = checkNotNull(publications.find(accountId, source.id))
            AndroidSyncScheduler(context, BuildConfig.CLOUD_BASE_URL).enqueue(accountId)
            val completed = withTimeout(60_000) {
                var receipt = publications.find(accountId, source.id)
                while (receipt?.remoteId == null) {
                    delay(250)
                    receipt = publications.find(accountId, source.id)
                }
                receipt
            }
            assertEquals(capture.operationId, completed.operationId)
            assertNull(completed.document)
            val remoteId = checkNotNull(completed.remoteId)
            val gateway = RemoteBoards(accounts)
            assertEquals(source.name, gateway.load(accountId, remoteId).document.name)
            assertEquals(1, gateway.list(accountId).items.count { it.name == source.name })
            gateway.delete(accountId, remoteId)
        } finally {
            database.close()
        }
    }

    private suspend fun verifyPublicationUi(accounts: RemoteAccounts) {
        text("Done").click()
        device.pressBack()
        val name = "Cloud UI ${System.currentTimeMillis()}"
        control("New board").click()
        text("Board name")
        checkNotNull(device.findObjects(By.clazz("android.widget.EditText")).lastOrNull()).text = name
        text("Confirm").click()
        control("Pen").click()
        val canvas = checkNotNull(device.wait(Until.findObject(By.descContains("Drawing canvas")), 10_000)).visibleBounds
        device.swipe(
            canvas.left + canvas.width() / 4,
            canvas.top + canvas.height() / 3,
            canvas.left + canvas.width() * 3 / 4,
            canvas.top + canvas.height() / 2,
            20
        )
        text("Unsaved changes")
        control("Save").click()
        text("Saved on this device")
        control("Back to boards").click()
        findLocalBoard(name)
        control("Actions for $name").click()
        text("Publish to cloud").click()
        text("Publish copy").click()
        control("Cloud board preview")
        text("Make editable local copy")
        device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "cloud-preview.png"))
        verifyCloudPreviewLayouts()
        val gateway = RemoteBoards(accounts)
        val accountId = checkNotNull(accounts.account.value).id
        assertEquals(1, gateway.list(accountId).items.count { it.name == name })
        text("Make editable local copy").click()
        text("Confirm").click()
        control("Save")
        text("$name (copy)")
        control("Back to boards").click()
        findLocalBoard("$name (copy)")
        text(name)
        text("$name (copy)")
        // Publishing the same private original again must open its receipt, not create another cloud board.
        control("Actions for $name").click()
        text("Publish to cloud").click()
        text("Publish copy").click()
        control("Cloud board preview")
        assertEquals(1, gateway.list(accountId).items.count { it.name == name })
        device.pressBack()
        control("Refresh cloud boards")
        text("Cloud boards")
        device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "cloud-library.png"))
        device.pressBack()
        control("Whiteboard account").click()
        text("Sign out")
    }

    private fun verifyCloudPreviewLayouts() {
        val size = Regex("Override size: (\\d+x\\d+)").find(device.executeShellCommand("wm size"))?.groupValues?.get(1) ?: "reset"
        val density = Regex("Override density: (\\d+)").find(device.executeShellCommand("wm density"))?.groupValues?.get(1) ?: "reset"
        try {
            device.executeShellCommand("wm size 2400x1080")
            control("Cloud board preview")
            control("Fit to content").click()
            device.waitForIdle()
            assertTrue(device.displayWidth >= 2000)
            assertTrue(control("Cloud board preview").visibleBounds.height() >= 200)
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "cloud-phone-landscape.png"))
            device.executeShellCommand("wm size 2560x1600")
            device.executeShellCommand("wm density 180")
            control("Cloud board preview")
            control("Fit to content").click()
            device.waitForIdle()
            device.takeScreenshot(java.io.File(context.getExternalFilesDir(null), "cloud-tablet-preview.png"))
        } finally {
            device.executeShellCommand("wm density $density")
            device.executeShellCommand("wm size $size")
            device.waitForIdle()
        }
        control("Cloud board preview")
    }
}
