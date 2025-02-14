package net.pantasystem.milktea.note.reaction.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.pantasystem.milktea.common.Logger
import net.pantasystem.milktea.common.ResultState
import net.pantasystem.milktea.common.StateContent
import net.pantasystem.milktea.common.asLoadingStateFlow
import net.pantasystem.milktea.model.account.AccountRepository
import net.pantasystem.milktea.model.emoji.Emoji
import net.pantasystem.milktea.model.instance.MetaRepository
import net.pantasystem.milktea.model.notes.Note
import net.pantasystem.milktea.model.notes.NoteRepository
import net.pantasystem.milktea.model.notes.reaction.Reaction
import net.pantasystem.milktea.model.notes.reaction.ToggleReactionUseCase
import javax.inject.Inject

data class RemoteReaction(
    val reaction: Reaction,
    val currentAccountId: Long,
    val noteId: String
)

@HiltViewModel
class RemoteReactionEmojiSuggestionViewModel @Inject constructor(
    val metaRepository: MetaRepository,
    val accountRepository: AccountRepository,
    val noteRepository: NoteRepository,
    val loggerFactory: Logger.Factory,
    val toggleReactionUseCase: ToggleReactionUseCase,
) : ViewModel() {

    private val _reaction = MutableStateFlow<RemoteReaction?>(null)
    val reaction: StateFlow<RemoteReaction?> = _reaction
    val logger = loggerFactory.create("RemoteReactionEmojiSuggestionVM")

    @OptIn(ExperimentalCoroutinesApi::class)
    val filteredEmojis = reaction.flatMapLatest { remoteReaction ->
        val name = remoteReaction?.reaction?.getName()
        if (name == null) {
            flow {
                emit(ResultState.Fixed<List<Emoji>>(StateContent.NotExist()))
            }
        } else {
            suspend {
                val account = accountRepository.get(remoteReaction.currentAccountId).getOrThrow()
                metaRepository.get(account.instanceDomain)?.emojis?.filter {
                    it.name == name
                }
            }.asLoadingStateFlow()
        }
    }.stateIn(
        viewModelScope, SharingStarted.Lazily, ResultState.Loading(
            StateContent.NotExist()
        )
    )

    val isLoading = filteredEmojis.map {
        it is ResultState.Loading
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)


    fun setReaction(accountId: Long, reaction: String, noteId: String) {
        _reaction.value = RemoteReaction(
            Reaction(
                reaction
            ), accountId, noteId
        )
    }

    fun send() {
        val value = reaction.value ?: return
        val name = value.reaction.getName()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                toggleReactionUseCase(
                    Note.Id(
                        value.currentAccountId,
                        value.noteId
                    ),
                    ":$name:"
                )
            }.onFailure {
                logger.warning("リアクションの作成失敗", e = it)
            }
        }

    }
}