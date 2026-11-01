package dev.whiteboard.server

import dev.whiteboard.core.cloud.DeltaEngine
import dev.whiteboard.core.model.*
import dev.whiteboard.format.DocumentDeltaCodec
import dev.whiteboard.format.ElementDigest
import dev.whiteboard.server.boards.BoardOperations
import dev.whiteboard.server.boards.BoardRole
import dev.whiteboard.server.boards.CloudDocuments
import dev.whiteboard.server.database.AccountQuota
import dev.whiteboard.server.database.query
import dev.whiteboard.server.database.update
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class BoardOperationsTest {
    private val engine = DeltaEngine(ElementDigest())
    private val codec = DocumentDeltaCodec()
    private val first = Note(id = "first", bounds = Bounds(0f, 0f, 100f, 100f), text = "One")
    private val second = first.copy(id = "second", text = "Two")
    private fun initial() = Board(name = "Initial", pages = listOf(Page(elements = listOf(first, second))))
    private fun Board.objects(vararg values: Element) = withPage(activePage.copy(elements = values.toList()))

    @Test fun independentOfflineEditsMergeAndReceiptsAndCursorSurviveNewServiceInstance() {
        ServerFixture().use { f ->
            val alice = f.account("alice@example.com")
            val bob = f.account("bob@example.com")
            val source = f.boards.create(alice.accessToken, initial()).document
            val invite = f.boards.invite(alice.accessToken, source.id, "bob@example.com", BoardRole.EDITOR)
            f.boards.accept(bob.accessToken, invite)
            val a = engine.between(source, source.objects(first.copy(text = "Alice"), second))
            val b = engine.between(source, source.objects(first, second.copy(text = "Bob")))
            val operation = UUID.randomUUID().toString()
            assertEquals(1L, f.operations.submit(alice.accessToken, source.id, operation, 0, a))
            assertEquals(2L, f.operations.submit(bob.accessToken, source.id, UUID.randomUUID().toString(), 0, b))
            val reopened = BoardOperations(f.auth, f.clock)
            assertEquals(1L, reopened.submit(alice.accessToken, source.id, operation, 0, a))
            val changes = reopened.changes(alice.accessToken, source.id, 0)
            assertEquals(listOf(1L, 2L), changes.operations.map { it.revision })
            var replay = source
            changes.operations.forEach { replay = engine.apply(replay, codec.decode(it.delta)).board }
            assertEquals(f.boards.get(alice.accessToken, source.id).document, replay)
            assertTrue(reopened.changes(bob.accessToken, source.id, 2).operations.isEmpty())
        }
    }

    @Test fun conflictOnLaterObjectRollsBackWholeBatchAndDoesNotChargeHistory() {
        ServerFixture().use { f ->
            val user = f.account("alice@example.com")
            val source = f.boards.create(user.accessToken, initial()).document
            val remote = engine.between(source, source.objects(first, second.copy(text = "Remote")))
            f.operations.submit(user.accessToken, source.id, UUID.randomUUID().toString(), 0, remote)
            val beforeBytes = f.database.transaction { db ->
                db.query("SELECT history_bytes FROM boards WHERE id = ?", source.id) {
                    it.next()
                    it.getLong(1)
                }
            }
            val batch = engine.between(source, source.objects(first.copy(text = "Mine one"), second.copy(text = "Mine two")))
            failure(409, "conflict_object_modified") {
                f.operations.submit(user.accessToken, source.id, UUID.randomUUID().toString(), 0, batch)
            }
            assertEquals(
                listOf(first, second.copy(text = "Remote")),
                f.boards.get(user.accessToken, source.id).document.activePage.elements
            )
            f.database.transaction { db ->
                db.query("SELECT revision, history_bytes FROM boards WHERE id = ?", source.id) {
                    it.next()
                    assertEquals(1L, it.getLong(1))
                    assertEquals(beforeBytes, it.getLong(2))
                }
            }
        }
    }

    @Test fun persistentDeletionBlocksLateEditAndSnapshotResurrectionButAllowsRecoveryCopy() {
        ServerFixture().use { f ->
            val user = f.account("alice@example.com")
            val source = f.boards.create(user.accessToken, initial()).document
            val deleted = source.objects(second)
            f.operations.submit(user.accessToken, source.id, UUID.randomUUID().toString(), 0, engine.between(source, deleted))
            val reopened = BoardOperations(f.auth, f.clock)
            failure(409, "conflict_object_deleted") {
                reopened.submit(
                    user.accessToken,
                    source.id,
                    UUID.randomUUID().toString(),
                    0,
                    engine.between(source, source.objects(first.copy(text = "Late"), second))
                )
            }
            failure(409, "conflict_id_reused") { f.boards.commit(user.accessToken, source.id, UUID.randomUUID().toString(), 1, source) }
            val recovered = deleted.objects(first.duplicate(), second)
            assertEquals(
                2L,
                reopened.submit(user.accessToken, source.id, UUID.randomUUID().toString(), 1, engine.between(deleted, recovered))
            )
            assertEquals(2, f.boards.get(user.accessToken, source.id).document.activePage.elements.size)
        }
    }

    @Test fun newViewerCannotReadHistoryPredatingInvitationAndRevocationStopsCursor() {
        ServerFixture().use { f ->
            val alice = f.account("alice@example.com")
            val bob = f.account("bob@example.com")
            val source = f.boards.create(alice.accessToken, initial()).document
            val privateVersion = source.objects(first.copy(text = "private before sharing"), second)
            f.operations.submit(alice.accessToken, source.id, UUID.randomUUID().toString(), 0, engine.between(source, privateVersion))
            val removed = privateVersion.objects(second)
            f.operations.submit(alice.accessToken, source.id, UUID.randomUUID().toString(), 1, engine.between(privateVersion, removed))
            val invite = f.boards.invite(alice.accessToken, source.id, "bob@example.com", BoardRole.VIEWER)
            f.boards.accept(bob.accessToken, invite)
            failure(409, "snapshot_required") { f.operations.changes(bob.accessToken, source.id, 0) }
            assertFalse(f.boards.get(bob.accessToken, source.id).document.activePage.elements.contains(first))
            val renamed = removed.copy(name = "Shared title")
            val next = engine.between(removed, renamed)
            f.operations.submit(alice.accessToken, source.id, UUID.randomUUID().toString(), 2, next)
            assertEquals(listOf(3L), f.operations.changes(bob.accessToken, source.id, 2).operations.map { it.revision })
            failure(403) {
                f.operations.submit(
                    bob.accessToken,
                    source.id,
                    UUID.randomUUID().toString(),
                    3,
                    engine.between(renamed, renamed.copy(name = "Forbidden"))
                )
            }
            f.boards.changeMember(alice.accessToken, source.id, f.auth.authenticate(bob.accessToken).id, null)
            failure(404) { f.operations.changes(bob.accessToken, source.id, 2) }
        }
    }

    @Test fun createIsIdempotentAcrossEditsAndCannotResurrectADeletedBoard() {
        ServerFixture().use { f ->
            val user = f.account("alice@example.com")
            val source = initial()
            val operation = UUID.randomUUID().toString()
            val first = f.boards.create(user.accessToken, source, operation)
            f.boards.commit(user.accessToken, first.document.id, UUID.randomUUID().toString(), 0, first.document.copy(name = "Updated"))
            val repeated = f.boards.create(user.accessToken, source, operation)
            assertEquals(first.document.id, repeated.document.id)
            assertEquals(1L, repeated.revision)
            assertEquals("Updated", repeated.document.name)
            assertEquals(1, f.boards.list(user.accessToken).size)
            failure(409, "operation_id_reused") { f.boards.create(user.accessToken, source.copy(name = "Different request"), operation) }
            f.boards.delete(user.accessToken, first.document.id)
            failure(409, "creation_was_deleted") { f.boards.create(user.accessToken, source, operation) }
            assertTrue(f.boards.list(user.accessToken).isEmpty())
        }
    }

    @Test fun deletingContributorAnonymizesHistoryAndKeepsOwnersDocumentAndReceipts() {
        ServerFixture().use { f ->
            val alice = f.account("alice@example.com")
            val bob = f.account("bob@example.com")
            val source = f.boards.create(alice.accessToken, initial()).document
            f.boards.accept(bob.accessToken, f.boards.invite(alice.accessToken, source.id, "bob@example.com", BoardRole.EDITOR))
            val delta = engine.between(source, source.copy(name = "Contributed"))
            val id = UUID.randomUUID().toString()
            f.operations.submit(bob.accessToken, source.id, id, 0, delta)
            f.auth.deleteAccount(bob.accessToken, ServerFixture.PASSWORD)
            val page = f.operations.changes(alice.accessToken, source.id, 0)
            assertEquals(1, page.operations.size)
            assertNull(page.operations.single().actorId)
            assertEquals("Contributed", f.boards.get(alice.accessToken, source.id).document.name)
            failure(409, "operation_id_reused") { f.operations.submit(alice.accessToken, source.id, id, 0, delta) }
        }
    }

    @Test fun largeDeletionBatchPersistsEveryTombstone() {
        ServerFixture().use { f ->
            val user = f.account("alice@example.com")
            val source = initial().let { it.withPage(it.activePage.copy(elements = List(700) { index -> first.copy(id = "note-$index") })) }
            val created = f.boards.create(user.accessToken, source).document
            val removed = created.objects()
            f.operations.submit(user.accessToken, created.id, UUID.randomUUID().toString(), 0, engine.between(created, removed))
            f.database.transaction { db ->
                db.query("SELECT COUNT(*) FROM board_tombstones WHERE board_id = ?", created.id) {
                    it.next()
                    assertEquals(700, it.getInt(1))
                }
            }
            failure(409, "conflict_id_reused") { f.boards.commit(user.accessToken, created.id, UUID.randomUUID().toString(), 1, created) }
        }
    }

    @Test fun ownerBudgetSerializesDifferentEditorsWritingDifferentBoards() {
        ServerFixture().use { f ->
            val alice = f.account("alice@example.com")
            val bob = f.account("bob@example.com")
            val cara = f.account("cara@example.com")
            val sources =
                listOf(f.boards.create(alice.accessToken, initial()).document, f.boards.create(alice.accessToken, initial()).document)
            val actors = listOf(bob, cara)
            listOf("bob@example.com", "cara@example.com").forEachIndexed { index, email ->
                f.boards.accept(actors[index].accessToken, f.boards.invite(alice.accessToken, sources[index].id, email, BoardRole.EDITOR))
            }
            val changed = sources.mapIndexed { index, board -> board.copy(name = "Changed $index") }
            val deltas = sources.mapIndexed { index, board -> engine.between(board, changed[index]) }
            val costs = sources.mapIndexed { index, board ->
                CloudDocuments.bytes(codec.encode(deltas[index])) + CloudDocuments.bytes(CloudDocuments.payload(changed[index])) -
                    CloudDocuments.bytes(CloudDocuments.payload(board))
            }
            val owner = f.auth.authenticate(alice.accessToken).id
            f.database.transaction { db ->
                val documents = db.query("SELECT SUM(payload_bytes) FROM boards WHERE owner_id = ?", owner) {
                    it.next()
                    it.getLong(1)
                }
                db.update(
                    "UPDATE boards SET history_bytes = ? WHERE id = ?",
                    AccountQuota.PAYLOAD_BYTES - documents - costs.max(),
                    sources[0].id
                )
            }
            val executor = Executors.newFixedThreadPool(2)
            val gate = CountDownLatch(1)
            try {
                val futures = actors.mapIndexed { index, actor ->
                    executor.submit(
                        Callable {
                            gate.await()
                            runCatching {
                                f.operations.submit(actor.accessToken, sources[index].id, UUID.randomUUID().toString(), 0, deltas[index])
                            }
                        }
                    )
                }
                gate.countDown()
                val results = futures.map { it.get(15, TimeUnit.SECONDS) }
                assertEquals(1, results.count { it.isSuccess })
                assertEquals("storage_quota_reached", (results.first { it.isFailure }.exceptionOrNull() as ServiceFailure).code)
                f.database.transaction { db ->
                    db.query("SELECT SUM(payload_bytes + history_bytes) FROM boards WHERE owner_id = ?", owner) {
                        it.next()
                        assertTrue(it.getLong(1) <= AccountQuota.PAYLOAD_BYTES)
                    }
                }
            } finally {
                executor.shutdownNow()
            }
        }
    }
}
