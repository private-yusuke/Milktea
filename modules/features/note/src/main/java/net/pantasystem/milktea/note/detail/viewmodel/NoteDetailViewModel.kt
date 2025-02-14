package net.pantasystem.milktea.note.detail.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.pantasystem.milktea.app_store.notes.NoteTranslationStore
import net.pantasystem.milktea.data.gettters.NoteRelationGetter
import net.pantasystem.milktea.data.infrastructure.url.UrlPreviewStoreProvider
import net.pantasystem.milktea.model.account.AccountRepository
import net.pantasystem.milktea.model.account.CurrentAccountWatcher
import net.pantasystem.milktea.model.account.page.Pageable
import net.pantasystem.milktea.model.notes.*
import net.pantasystem.milktea.note.viewmodel.PlaneNoteViewData
import net.pantasystem.milktea.note.viewmodel.PlaneNoteViewDataCache

class NoteDetailViewModel @AssistedInject constructor(
    accountRepository: AccountRepository,
    private val noteCaptureAdapter: NoteCaptureAPIAdapter,
    private val noteRelationGetter: NoteRelationGetter,
    private val noteRepository: NoteRepository,
    private val noteTranslationStore: NoteTranslationStore,
    private val urlPreviewStoreProvider: UrlPreviewStoreProvider,
    private val noteDataSource: NoteDataSource,
    @Assisted val show: Pageable.Show,
    @Assisted val accountId: Long? = null,
) : ViewModel() {

    @AssistedFactory
    interface ViewModelAssistedFactory {
        fun create(show: Pageable.Show, accountId: Long?): NoteDetailViewModel
    }

    companion object;

    private val currentAccountWatcher: CurrentAccountWatcher = CurrentAccountWatcher(accountId, accountRepository)

    private val cache = PlaneNoteViewDataCache(
        currentAccountWatcher::getAccount,
        noteCaptureAdapter,
        noteTranslationStore,
        { account -> urlPreviewStoreProvider.getUrlPreviewStore(account) },
        viewModelScope,
        noteRelationGetter
    )

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val note = suspend {
        currentAccountWatcher.getAccount()
    }.asFlow().flatMapLatest {
        noteDataSource.observeOne(Note.Id(it.accountId, show.noteId))
    }.onStart {
        emit(null)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val conversationNotes = note.filterNotNull().flatMapLatest { note ->
        noteDataSource.state.map { state ->
            state.conversation(note.id)
        }
    }.onStart {
        emit(emptyList())
    }

    private val repliesMap = noteDataSource.state.map { state ->
        state.repliesMap()
    }.onStart {
        emit(emptyMap())
    }

    val notes = combine(note, conversationNotes, repliesMap) { note, conversation, repliesMap ->
        val relatedConversation = noteRelationGetter.getIn(conversation.map { it.id }).map {
            NoteType.Conversation(it)
        }
        val relatedChildren = noteRelationGetter.getIn((repliesMap[note?.id]?: emptyList()).map {
            it.id
        }).map { childNote ->
            NoteType.Children(childNote,
                noteRelationGetter.getIn(repliesMap[childNote.note.id]?.map { it.id }
                    ?: emptyList())
            )
        }
        val relatedNote = noteRelationGetter.getIn(if (note == null) emptyList() else listOf(note.id)).map {
            NoteType.Detail(it)
        }
        relatedConversation + relatedNote + relatedChildren
    }.map { notes ->
        notes.map { note ->
            when(note) {
                is NoteType.Children -> {
                    val children = note.nextChildren.map {
                        cache.get(it)
                    }
                    NoteConversationViewData(
                        note.note, children,
                        currentAccountWatcher.getAccount(),
                        noteCaptureAdapter,
                        noteTranslationStore,
                    ).also {
                        it.capture()
                        cache.put(it)
                    }.apply {
                        this.hasConversation.postValue(false)
                        this.conversation.postValue(note.getReplies().map {
                            cache.get(it)
                        })
                    }
                }
                is NoteType.Conversation -> {
                    cache.get(note.note)
                }
                is NoteType.Detail -> {
                    NoteDetailViewData(
                        note.note,
                        currentAccountWatcher.getAccount(),
                        noteCaptureAdapter,
                        noteTranslationStore,
                    ).also {
                        cache.put(it)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun NoteDataSourceState.repliesMap(): Map<Note.Id, List<Note>> {
        return this.map.values.filterNot {
            it.replyId == null
        }.groupBy {
            it.replyId!!
        }
    }

    private fun NoteDataSourceState.conversation(noteId: Note.Id, notes: List<Note> = emptyList()): List<Note> {
        val note = getOrNull(noteId)
            ?: return notes.sortedBy {
                it.id.noteId
            }
        if (note.replyId != null) {
            val reply = getOrNull(note.replyId!!)?.let {
                listOf(it)
            }?: emptyList()

            return conversation(note.replyId!!, notes + reply)
        }

        return (notes).sortedBy {
            it.id.noteId
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val account = currentAccountWatcher.getAccount()
                val note = noteRepository.find(Note.Id(account.accountId, show.noteId))
                    .getOrThrow()

                noteRepository.syncConversation(note.id)
                recursiveSync(note.id).getOrThrow()
            } catch (e: Exception) {
                Log.w("NoteDetailViewModel", "loadDetail失敗", e)
            }
        }
    }


    private suspend fun recursiveSync(noteId: Note.Id): Result<Unit> = runCatching {
        coroutineScope {
            noteRepository.syncChildren(noteId).also {
                noteDataSource.state.value.repliesMap()[noteId]?.map {
                    async {
                        recursiveSync(it.id).getOrNull()
                    }
                }?.awaitAll()
            }
        }


    }

    suspend fun getUrl(): String {
        val account = currentAccountWatcher.getAccount()
        return "${account.instanceDomain}/notes/${show.noteId}"
    }




    private fun <T : PlaneNoteViewData> T.capture(): T {
        val self = this
        viewModelScope.launch(Dispatchers.IO) {
            self.eventFlow.collect()
        }
        return this
    }

}

@Suppress("UNCHECKED_CAST")
fun NoteDetailViewModel.Companion.provideFactory(
    factory: NoteDetailViewModel.ViewModelAssistedFactory,
    show: Pageable.Show,
    accountId: Long? = null,
) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return factory.create(show, accountId) as T
    }
}

sealed interface NoteType {
    data class Detail(val note: NoteRelation) : NoteType
    data class Conversation(val note: NoteRelation) : NoteType
    data class Children(val note: NoteRelation, val nextChildren: List<NoteRelation>) : NoteType {


        fun getReplies(): List<NoteRelation> {
            return nextChildren.filter {
                it.note.replyId == note.note.id
            }
        }

    }
}