package dev.whiteboard.server

import dev.whiteboard.core.model.Board
import dev.whiteboard.core.model.Bounds
import dev.whiteboard.core.model.Note
import dev.whiteboard.core.model.Point
import dev.whiteboard.core.model.Viewport
import dev.whiteboard.server.boards.BoardRole
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class BoardServiceTest {
    @Test fun unicodeDocumentsRoundTripThroughDatabase() {
        ServerFixture().use { f ->
            val user = f.account("alice@example.com")
            val source = Board(name = "Život Ћирилица 🖊")
            val note = Note(bounds = Bounds(0f, 0f, 200f, 100f), text = "Đak piše: ћирилица и 🖊")
            val created = f.boards.create(user.accessToken, source.withPage(source.activePage.copy(elements = listOf(note))))
            val loaded = f.boards.get(user.accessToken, created.document.id)
            assertEquals(source.name, loaded.document.name)
            assertEquals(note, loaded.document.activePage.elements.single())
        }
    }

    @Test fun invitationsAreBoundToVerifiedRecipientAndOwnerControlsRoles() {
        ServerFixture().use { f ->
            val alice = f.account("alice@example.com")
            val bob = f.account("bob@example.com")
            val mallory = f.account("mallory@example.com")
            val board = f.boards.create(alice.accessToken, Board(name = "Private ideas"))
            val id = board.document.id
            assertEquals(BoardRole.OWNER, board.role)
            failure(404) { f.boards.get(bob.accessToken, id) }
            assertTrue(f.boards.list(bob.accessToken).isEmpty())
            val invite = f.boards.invite(alice.accessToken, id, "bob@example.com", BoardRole.VIEWER)
            failure(404) { f.boards.accept(mallory.accessToken, invite) }
            assertEquals(id, f.boards.accept(bob.accessToken, invite))
            failure(404) { f.boards.accept(bob.accessToken, invite) }
            assertEquals(BoardRole.VIEWER, f.boards.get(bob.accessToken, id).role)
            failure(403) { f.boards.commit(bob.accessToken, id, UUID.randomUUID().toString(), 0, board.document) }
            failure(403) { f.boards.invite(bob.accessToken, id, "mallory@example.com", BoardRole.EDITOR) }
            failure(403) { f.boards.delete(bob.accessToken, id) }
            val bobId = f.auth.authenticate(bob.accessToken).id
            f.boards.changeMember(alice.accessToken, id, bobId, BoardRole.EDITOR)
            assertEquals(1L, f.boards.commit(bob.accessToken, id, UUID.randomUUID().toString(), 0, board.document.copy(name = "Together")))
            assertEquals("Together", f.boards.get(alice.accessToken, id).document.name)
            f.boards.changeMember(alice.accessToken, id, bobId, null)
            failure(404) { f.boards.get(bob.accessToken, id) }
        }
    }

    @Test fun staleAndReusedOperationCannotOverwriteNewRevision() {
        ServerFixture().use { f ->
            val user = f.account("alice@example.com")
            val board = f.boards.create(user.accessToken, Board(name = "Initial"))
            val updated = board.document.copy(name = "Saved once")
            val operation = UUID.randomUUID().toString()
            assertEquals(1L, f.boards.commit(user.accessToken, updated.id, operation, 0, updated))
            assertEquals(1L, f.boards.commit(user.accessToken, updated.id, operation, 0, updated))
            failure(409, "operation_id_reused") {
                f.boards.commit(user.accessToken, updated.id, operation, 0, updated.copy(name = "Changed replay"))
            }
            failure(409, "revision_conflict") {
                f.boards.commit(user.accessToken, updated.id, UUID.randomUUID().toString(), 0, updated.copy(name = "Stale overwrite"))
            }
            assertEquals("Saved once", f.boards.get(user.accessToken, updated.id).document.name)
            assertEquals(1L, f.boards.get(user.accessToken, updated.id).revision)
        }
    }

    @Test fun concurrentCommitsHaveExactlyOneWinner() {
        ServerFixture().use { f ->
            val user = f.account("alice@example.com")
            val other = f.account("bob@example.com")
            val board = f.boards.create(user.accessToken, Board(name = "Initial"))
            val invitation = f.boards.invite(user.accessToken, board.document.id, "bob@example.com", BoardRole.EDITOR)
            f.boards.accept(other.accessToken, invitation)
            val participants = listOf(user, other)
            val pool = Executors.newFixedThreadPool(2)
            val gate = CountDownLatch(1)
            try {
                val futures = participants.mapIndexed { number, participant ->
                    pool.submit(
                        Callable {
                            gate.await()
                            runCatching {
                                f.boards.commit(
                                    participant.accessToken,
                                    board.document.id,
                                    UUID.randomUUID().toString(),
                                    0,
                                    board.document.copy(name = "Edit $number")
                                )
                            }
                        }
                    )
                }
                gate.countDown()
                val results = futures.map { it.get(10, TimeUnit.SECONDS) }
                assertEquals(1, results.count { it.isSuccess })
                assertEquals("revision_conflict", (results.first { it.isFailure }.exceptionOrNull() as ServiceFailure).code)
                assertEquals(1L, f.boards.get(user.accessToken, board.document.id).revision)
            } finally {
                pool.shutdownNow()
            }
        }
    }

    @Test fun localOrganizationAndCameraAreNotSharedAndDeleteCascades() {
        ServerFixture().use { f ->
            val user = f.account("alice@example.com")
            val source = Board(name = "Personal", favorite = true, folderId = "private-folder", trashedAt = 12)
            val local = source.withPage(source.activePage.copy(viewport = Viewport(Point(300f, 400f), 2f)))
            val result = f.boards.create(user.accessToken, local)
            assertNotEquals(local.id, result.document.id)
            assertFalse(result.document.favorite)
            assertNull(result.document.folderId)
            assertNull(result.document.trashedAt)
            assertEquals(Viewport(), result.document.activePage.viewport)
            f.boards.delete(user.accessToken, result.document.id)
            assertTrue(f.boards.list(user.accessToken).isEmpty())
            failure(404) { f.boards.get(user.accessToken, result.document.id) }
        }
    }

    @Test fun invitationExpiryAndRevocationAreEnforced() {
        ServerFixture().use { f ->
            val alice = f.account("alice@example.com")
            val bob = f.account("bob@example.com")
            val board = f.boards.create(alice.accessToken, Board(name = "Private"))
            val code = f.boards.invite(alice.accessToken, board.document.id, "bob@example.com", BoardRole.EDITOR)
            f.boards.revokeInvites(alice.accessToken, board.document.id)
            failure(404) { f.boards.accept(bob.accessToken, code) }
            val expires = f.boards.invite(alice.accessToken, board.document.id, "bob@example.com", BoardRole.VIEWER)
            f.clock.advance(8 * 86_400L)
            val bobFresh = f.auth.refresh(bob.refreshToken)
            failure(404) { f.boards.accept(bobFresh.accessToken, expires) }
        }
    }
}
