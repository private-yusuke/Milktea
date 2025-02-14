package net.pantasystem.milktea.note.timeline.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.datetime.Instant
import net.pantasystem.milktea.app_store.account.AccountStore
import net.pantasystem.milktea.app_store.notes.NoteTranslationStore
import net.pantasystem.milktea.app_store.notes.TimelineStore
import net.pantasystem.milktea.common.APIError
import net.pantasystem.milktea.common.Logger
import net.pantasystem.milktea.common.PageableState
import net.pantasystem.milktea.common.StateContent
import net.pantasystem.milktea.common_android.resource.StringSource
import net.pantasystem.milktea.data.gettters.NoteRelationGetter
import net.pantasystem.milktea.data.infrastructure.url.UrlPreviewStoreProvider
import net.pantasystem.milktea.model.account.Account
import net.pantasystem.milktea.model.account.AccountRepository
import net.pantasystem.milktea.model.account.CurrentAccountWatcher
import net.pantasystem.milktea.model.account.UnauthorizedException
import net.pantasystem.milktea.model.account.page.Pageable
import net.pantasystem.milktea.model.notes.NoteCaptureAPIAdapter
import net.pantasystem.milktea.model.notes.NoteStreaming
import net.pantasystem.milktea.model.notes.muteword.WordFilterConfigRepository
import net.pantasystem.milktea.note.R
import net.pantasystem.milktea.note.viewmodel.PlaneNoteViewData
import net.pantasystem.milktea.note.viewmodel.PlaneNoteViewDataCache
import java.io.IOException
import java.net.SocketTimeoutException

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel @AssistedInject constructor(
    timelineStoreFactory: TimelineStore.Factory,
    noteStreaming: NoteStreaming,
    accountRepository: AccountRepository,
    noteRelationGetter: NoteRelationGetter,
    loggerFactory: Logger.Factory,
    noteCaptureAPIAdapter: NoteCaptureAPIAdapter,
    noteTranslationStore: NoteTranslationStore,
    private val urlPreviewStoreProvider: UrlPreviewStoreProvider,
    private val accountStore: AccountStore,
    private val wordFilterConfigRepository: WordFilterConfigRepository,
    @Assisted val account: Account?,
    @Assisted val accountId: Long? = account?.accountId,
    @Assisted val pageable: Pageable,
) : ViewModel() {

    @AssistedFactory
    interface ViewModelAssistedFactory {
        fun create(
            account: Account?,
            accountId: Long?,
            pageable: Pageable,
        ): TimelineViewModel
    }

    companion object

    val tag = "TimelineViewModel"


    var position: Int = 0
    private val currentAccountWatcher = CurrentAccountWatcher(
        if (accountId != null && accountId <= 0) null else accountId,
        accountRepository
    )

    val timelineStore: TimelineStore =
        timelineStoreFactory.create(pageable, viewModelScope, currentAccountWatcher::getAccount)

    private val timelineState = timelineStore.timelineState.map { pageableState ->
        pageableState.suspendConvert { list ->
            cache.getByIds(list)
        }
    }.flatMapLatest { state ->
        wordFilterConfigRepository.observe().distinctUntilChanged().map { config ->
            state.suspendConvert { notes ->
                notes.filterNot {
                    config.checkMatchText(it.text)
                            || config.checkMatchText(it.cw)
                            || config.checkMatchText(it.subNote?.note?.text)
                            || config.checkMatchText(it.subNote?.note?.cw)
                }
            }
        }
    }

    val timelineListState: StateFlow<List<TimelineListItem>> = timelineState.map { state ->
        state.toList()
    }.stateIn(
        viewModelScope + Dispatchers.IO,
        SharingStarted.Lazily,
        listOf(TimelineListItem.Loading)
    )


    val errorEvent = timelineStore.timelineState.map {
        (it as? PageableState.Error)?.throwable
    }.filterNotNull().shareIn(viewModelScope, SharingStarted.WhileSubscribed())

    val isLoading = timelineStore.timelineState.map {
        it is PageableState.Loading
    }.asLiveData()


    private val logger = loggerFactory.create("TimelineViewModel")
    private val cache = PlaneNoteViewDataCache(
        currentAccountWatcher::getAccount,
        noteCaptureAPIAdapter,
        noteTranslationStore,
        { account -> urlPreviewStoreProvider.getUrlPreviewStore(account) },
        viewModelScope,
        noteRelationGetter,
    )

    init {

        viewModelScope.launch(Dispatchers.IO) {
            accountStore.observeCurrentAccount.filterNotNull().distinctUntilChanged().map {
                currentAccountWatcher.getAccount()
            }.distinctUntilChanged().catch {
                logger.error("observe account error", it)
            }.collect {
                loadInit()
            }
        }

        accountStore.observeCurrentAccount.filterNotNull().distinctUntilChanged().flatMapLatest {
            noteStreaming.connect(currentAccountWatcher::getAccount, pageable)
        }.map {
            timelineStore.onReceiveNote(it.id)
        }.catch {
            logger.error("receive not error", it)
        }.launchIn(viewModelScope + Dispatchers.IO)

    }


    fun loadNew() {
        viewModelScope.launch(Dispatchers.IO) {
            timelineStore.loadFuture()
        }
    }

    fun loadOld() {
        viewModelScope.launch(Dispatchers.IO) {
            timelineStore.loadPrevious()
        }
    }

    fun loadInit(initialUntilDate: Instant? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            cache.clear()
            timelineStore.clear(initialUntilDate)
            timelineStore.loadPrevious()
            timelineStore.loadFuture()
        }
    }


}

@Suppress("UNCHECKED_CAST")
fun TimelineViewModel.Companion.provideViewModel(
    assistedFactory: TimelineViewModel.ViewModelAssistedFactory,
    account: Account?,
    accountId: Long? = account?.accountId,
    pageable: Pageable,

    ) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return assistedFactory.create(account, accountId, pageable) as T
    }
}

sealed interface TimelineListItem {
    object Loading : TimelineListItem
    data class Note(val note: PlaneNoteViewData) : TimelineListItem
    data class Error(val throwable: Throwable) : TimelineListItem {
        fun getErrorMessage(): StringSource {
            return when(throwable) {
                is SocketTimeoutException -> {
                    StringSource(R.string.timeout_error)
                }
                is IOException -> {
                    StringSource(R.string.timeout_error)
                }
                is APIError.AuthenticationException -> {
                    StringSource(R.string.auth_error)
                }
                is APIError.IAmAIException -> {
                    StringSource(R.string.bot_error)
                }
                is APIError.InternalServerException -> {
                    StringSource(R.string.server_error)
                }
                is APIError.ClientException -> {
                    StringSource(R.string.parameter_error)
                }
                is UnauthorizedException -> {
                    StringSource(R.string.timeline_unauthorized_error)
                }
                else -> {
                    StringSource("error:$throwable")
                }
            }
        }

        fun isUnauthorizedError(): Boolean {
            return throwable is APIError.AuthenticationException
                    || throwable is UnauthorizedException
        }
    }
    object Empty : TimelineListItem
}

fun PageableState<List<PlaneNoteViewData>>.toList(): List<TimelineListItem> {
    return when (val content = this.content) {
        is StateContent.Exist -> {
            content.rawContent.map {
                TimelineListItem.Note(it)
            } + if (this is PageableState.Loading.Previous) {
                listOf(TimelineListItem.Loading)
            } else {
                emptyList()
            }
        }
        is StateContent.NotExist -> {
            listOf(
                when (this) {
                    is PageableState.Error -> {
                        TimelineListItem.Error(this.throwable)
                    }
                    is PageableState.Fixed -> {
                        TimelineListItem.Empty
                    }
                    is PageableState.Loading -> TimelineListItem.Loading
                }
            )
        }
    }
}