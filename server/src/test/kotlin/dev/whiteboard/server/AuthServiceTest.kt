package dev.whiteboard.server

import dev.whiteboard.core.model.Board
import dev.whiteboard.server.boards.BoardRole
import dev.whiteboard.server.database.query
import dev.whiteboard.server.security.ArgonPasswords
import dev.whiteboard.server.security.Tokens
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class AuthServiceTest {
    @Test fun emailValidationRejectsHeaderInjectionAndInvalidDomainLabels() {
        assertEquals("alice+notes@example.com", dev.whiteboard.server.auth.normalizeEmail(" ALICE+notes@Example.com "))
        listOf(
            "alice@example..com",
            "alice@-example.com",
            ".alice@example.com",
            "alice..notes@example.com",
            "alice@example.com\r\nBcc: other@example.com"
        )
            .forEach { email -> failure(400) { dev.whiteboard.server.auth.normalizeEmail(email) } }
    }

    @Test fun argonUsesRandomSaltAndRejectsWrongPasswordAndUnboundedParameters() {
        val passwords = ArgonPasswords()
        val first = passwords.hash(ServerFixture.PASSWORD)
        val second = passwords.hash(ServerFixture.PASSWORD)
        assertNotEquals(first, second)
        assertTrue(passwords.verify(ServerFixture.PASSWORD, first))
        assertFalse(passwords.verify("this is a wrong password", first))
        assertFalse(passwords.verify(ServerFixture.PASSWORD, first.replace("65536", "999999999")))
        failure(400) { passwords.hash("short") }
    }

    @Test fun registrationRequiresVerificationAndCodesAreSingleUse() {
        ServerFixture().use { f ->
            f.auth.register(" Alice@Example.com ", ServerFixture.PASSWORD)
            failure(401) { f.auth.login("alice@example.com", ServerFixture.PASSWORD) }
            val code = f.mail.code("alice@example.com")
            f.auth.verify(code)
            failure(400) { f.auth.verify(code) }
            val tokens = f.auth.login("ALICE@example.com", ServerFixture.PASSWORD)
            assertEquals("alice@example.com", f.auth.authenticate(tokens.accessToken).email)
            f.auth.register("alice@example.com", "a different valid password")
            assertEquals(1, f.mail.items.size)
            failure(401) { f.auth.login("alice@example.com", "a different valid password") }
            failure(401) { f.auth.login("nobody@example.com", ServerFixture.PASSWORD) }
            failure(401) { f.auth.authenticate(tokens.refreshToken) }
            f.database.transaction { db ->
                db.query("SELECT password_hash FROM accounts") { rows ->
                    assertTrue(rows.next())
                    assertFalse(rows.getString(1).contains(ServerFixture.PASSWORD))
                }
                db.query("SELECT access_hash, refresh_hash FROM sessions") { rows ->
                    assertTrue(rows.next())
                    assertEquals(Tokens.hash(tokens.accessToken), rows.getString(1))
                    assertNotEquals(tokens.refreshToken, rows.getString(2))
                }
            }
        }
    }

    @Test fun refreshRotatesAndReplayRevokesWholeFamily() {
        ServerFixture().use { f ->
            val first = f.account("alice@example.com")
            val second = f.auth.refresh(first.refreshToken)
            failure(401) { f.auth.authenticate(first.accessToken) }
            f.auth.authenticate(second.accessToken)
            failure(401) { f.auth.refresh(first.refreshToken) }
            failure(401) { f.auth.authenticate(second.accessToken) }
            failure(401) { f.auth.refresh(second.refreshToken) }
        }
    }

    @Test fun simultaneousRefreshCannotCreateTwoValidDescendants() {
        ServerFixture().use { f ->
            val first = f.account("alice@example.com")
            val pool = Executors.newFixedThreadPool(2)
            val gate = CountDownLatch(1)
            try {
                val tasks = (1..2).map {
                    pool.submit(
                        Callable {
                            gate.await()
                            runCatching { f.auth.refresh(first.refreshToken) }
                        }
                    )
                }
                gate.countDown()
                val results = tasks.map { it.get(10, TimeUnit.SECONDS) }
                assertEquals(1, results.count { it.isSuccess })
                val rotated = results.first { it.isSuccess }.getOrThrow()
                failure(401) { f.auth.authenticate(rotated.accessToken) }
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test fun expiryAndLogoutTakeEffectImmediately() {
        ServerFixture().use { f ->
            val first = f.account("alice@example.com")
            f.clock.advance(901)
            failure(401) { f.auth.authenticate(first.accessToken) }
            val second = f.auth.refresh(first.refreshToken)
            f.auth.logout(second.accessToken)
            failure(401) { f.auth.authenticate(second.accessToken) }
            failure(401) { f.auth.refresh(second.refreshToken) }
            val third = f.auth.login("alice@example.com", ServerFixture.PASSWORD)
            f.clock.advance(31 * 86_400L)
            failure(401) { f.auth.refresh(third.refreshToken) }
        }
    }

    @Test fun passwordResetRevokesEveryDeviceAndOnlyWorksOnce() {
        ServerFixture().use { f ->
            val first = f.account("alice@example.com")
            val second = f.auth.login("alice@example.com", ServerFixture.PASSWORD)
            f.auth.requestAction("missing@example.com", "reset")
            assertEquals(1, f.mail.items.size)
            f.auth.requestAction("alice@example.com", "reset")
            val code = f.mail.code("alice@example.com", "reset")
            val replacement = "my new secure passphrase"
            f.auth.reset(code, replacement)
            failure(400) { f.auth.reset(code, replacement) }
            failure(401) { f.auth.authenticate(first.accessToken) }
            failure(401) { f.auth.refresh(second.refreshToken) }
            failure(401) { f.auth.login("alice@example.com", ServerFixture.PASSWORD) }
            f.auth.login("alice@example.com", replacement)
        }
    }

    @Test fun resendReplacesCodeAndExpiredCodesCannotVerify() {
        ServerFixture().use { f ->
            f.auth.register("alice@example.com", ServerFixture.PASSWORD)
            val first = f.mail.code("alice@example.com")
            f.auth.requestAction("alice@example.com", "verify")
            failure(400) { f.auth.verify(first) }
            f.clock.advance(86_401)
            failure(400) { f.auth.verify(f.mail.code("alice@example.com")) }
        }
    }

    @Test fun deletionRequiresCurrentPasswordAndRemovesAccountAndSessions() {
        ServerFixture().use { f ->
            val session = f.account("alice@example.com")
            val other = f.account("bob@example.com")
            val owned = f.boards.create(session.accessToken, Board(name = "Alice owns this"))
            val retained = f.boards.create(other.accessToken, Board(name = "Bob keeps this"))
            val invitation = f.boards.invite(session.accessToken, owned.document.id, "bob@example.com", BoardRole.EDITOR)
            f.boards.accept(other.accessToken, invitation)
            failure(401) { f.auth.deleteAccount(session.accessToken, "a wrong secure password") }
            f.boards.get(other.accessToken, owned.document.id)
            f.auth.deleteAccount(session.accessToken, ServerFixture.PASSWORD)
            failure(401) { f.auth.authenticate(session.accessToken) }
            failure(401) { f.auth.refresh(session.refreshToken) }
            failure(401) { f.auth.login("alice@example.com", ServerFixture.PASSWORD) }
            failure(404) { f.boards.get(other.accessToken, owned.document.id) }
            assertEquals("Bob keeps this", f.boards.get(other.accessToken, retained.document.id).document.name)
            assertEquals(listOf(retained.document.id), f.boards.list(other.accessToken).map { it.id })
        }
    }
}
