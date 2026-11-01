package dev.whiteboard.core.testing

import dev.whiteboard.core.cloud.*
import dev.whiteboard.core.model.*
import kotlinx.coroutines.flow.*

class InMemoryAccounts(initial: CloudAccount?) : AccountGateway {
    override val available = true
    override val account = MutableStateFlow(initial)
    override suspend fun restore() = Unit
    override suspend fun register(email: String, password: String) = Unit
    override suspend fun verify(code: String) = Unit
    override suspend fun resendVerification(email: String) = Unit
    override suspend fun requestReset(email: String) = Unit
    override suspend fun reset(code: String, password: String) = Unit
    override suspend fun login(email: String, password: String) = error("Configure the fixture identity explicitly")
    override suspend fun logout(): Boolean {
        account.value = null
        return true
    }
    override suspend fun delete(password: String) {
        account.value = null
    }
}

class InMemorySyncStore : SyncStore {
    val documents = linkedMapOf<Pair<String, String>, SyncedDocument>()
    private val changes = MutableStateFlow(0)
    fun put(document: SyncedDocument) {
        documents[document.accountId to document.working.id] = document
        changes.value++
    }
    override fun observeBoards(accountId: String) = changes.map {
        documents.values.filter { it.accountId == accountId }.map {
            CachedCloudBoard(it.working.id, it.working.name, it.shadow.revision, it.shadow.role, it.pending.size, it.conflict != null)
        }
    }
    override suspend fun install(accountId: String, snapshot: CloudBoardSnapshot) =
        SyncedDocument(accountId, snapshot, snapshot.document, 0L, emptyList()).also(::put)
    override suspend fun load(accountId: String, boardId: String) = documents[accountId to boardId]
    override suspend fun edit(accountId: String, board: Board, expectedLocalVersion: Long, operationId: String): SyncedDocument =
        error("Device queue construction is tested against the real Room store")
    override suspend fun advance(
        accountId: String,
        snapshot: CloudBoardSnapshot,
        acknowledgment: OperationAcknowledgment?
    ): SyncedDocument {
        val previous = checkNotNull(load(accountId, snapshot.document.id))
        if (acknowledgment != null) check(previous.pending.first().id == acknowledgment.id)
        return previous.copy(
            shadow = snapshot,
            pending = if (acknowledgment ==
                null
            ) {
                previous.pending
            } else {
                previous.pending.drop(1)
            }
        ).also(::put)
    }
}

class InMemoryPublications(private val sync: InMemorySyncStore) : PublicationStore {
    private val requests = linkedMapOf<Pair<String, String>, Publication>()
    private val changes = MutableStateFlow(0)
    var completed = 0
    override fun observePending(accountId: String) = changes.map {
        requests.values.filter { it.accountId == accountId && it.remoteId == null }.map { PendingPublication(it.sourceId, it.name) }
    }
    override suspend fun stage(accountId: String, source: Board): Publication {
        val key = accountId to source.id
        return requests.getOrPut(key) { Publication(accountId, source.id, source.name, newId(), source) }.also { changes.value++ }
    }
    override suspend fun find(accountId: String, sourceId: String) = requests[accountId to sourceId]
    override suspend fun complete(accountId: String, sourceId: String, operationId: String, snapshot: CloudBoardSnapshot): SyncedDocument {
        val request = checkNotNull(find(accountId, sourceId))
        check(request.operationId == operationId)
        requests[accountId to sourceId] = request.copy(document = null, remoteId = snapshot.document.id)
        completed++
        changes.value++
        return sync.install(accountId, snapshot)
    }
}

class InMemoryRemoteBoards : BoardGateway {
    val snapshots = linkedMapOf<String, CloudBoardSnapshot>()
    val submitted = mutableListOf<String>()
    var created = 0
    var beforeCreate: suspend () -> Unit = {}
    var beforeSubmit: suspend () -> Unit = {}
    override suspend fun list(accountId: String, after: String?) = CloudBoardPage(emptyList(), null)
    override suspend fun create(accountId: String, operationId: String, document: Board): CloudBoardSnapshot {
        beforeCreate()
        created++
        return CloudBoardSnapshot(document.copy(id = newId()), 0L, CloudBoardRole.OWNER).also { snapshots[it.document.id] = it }
    }
    override suspend fun load(accountId: String, boardId: String) = checkNotNull(snapshots[boardId])
    override suspend fun submit(accountId: String, boardId: String, operationId: String, baseRevision: Long, delta: DocumentDelta): Long {
        beforeSubmit()
        submitted += operationId
        val previous = checkNotNull(snapshots[boardId])
        val next = previous.copy(
            document = previous.document.copy(name = delta.name?.value ?: previous.document.name),
            revision =
            previous.revision + 1
        )
        snapshots[boardId] = next
        return next.revision
    }
    override suspend fun changes(accountId: String, boardId: String, after: Long): CloudChanges = error("No cursor fixture configured")
    override suspend fun delete(accountId: String, boardId: String) {
        snapshots.remove(boardId)
    }
}

class RecordingSyncScheduler : SyncScheduler {
    val enqueued = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    override fun enqueue(accountId: String) {
        enqueued += accountId
    }
    override fun cancel(accountId: String) {
        cancelled += accountId
    }
    override fun observe(accountId: String) = flowOf(SyncWorkStatus(SyncWorkState.IDLE))
}
