package net.pantasystem.milktea.note.reaction.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.pantasystem.milktea.model.account.Account
import net.pantasystem.milktea.model.account.AccountRepository
import net.pantasystem.milktea.model.notes.reaction.LegacyReaction
import net.pantasystem.milktea.model.notes.reaction.usercustom.ReactionUserSettingDao
import javax.inject.Inject

@HiltViewModel
class ReactionPickerDialogViewModel @Inject constructor(
    reactionUserSettingDao: ReactionUserSettingDao,
    val accountRepository: AccountRepository,
): ViewModel() {
    private val _currentAccount = MutableStateFlow<Account?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val userConfigReactions = _currentAccount.filterNotNull().flatMapLatest { ac ->
        reactionUserSettingDao.observeByInstanceDomain(ac.instanceDomain).map { list ->
            list.sortedBy {
                it.weight
            }.map {
                it.reaction
            }
        }
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, LegacyReaction.defaultReaction)

    private fun setCurrentAccount(account: Account?) {
        _currentAccount.value = account
    }

    fun setCurrentAccountById(accountId: Long?) {
        if (accountId == null) {
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            accountRepository.get(accountId).onSuccess {
                setCurrentAccount(it)
            }
        }
    }
}