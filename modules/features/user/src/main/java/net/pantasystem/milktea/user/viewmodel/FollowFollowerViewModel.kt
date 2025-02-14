package net.pantasystem.milktea.user.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import net.pantasystem.milktea.app_store.user.FollowFollowerPagingStore
import net.pantasystem.milktea.app_store.user.RequestType
import net.pantasystem.milktea.common.Logger
import net.pantasystem.milktea.common.PageableState
import net.pantasystem.milktea.model.user.User
import net.pantasystem.milktea.model.user.UserDataSource
import net.pantasystem.milktea.model.user.UserRepository

class FollowFollowerViewModel @AssistedInject constructor(
    followFollowerPagingStoreFactory: FollowFollowerPagingStore.Factory,
    private val userRepository: UserRepository,
    private val userDataSource: UserDataSource,
    private val loggerFactory: Logger.Factory,
    @Assisted val userId: User.Id
) : ViewModel() {

    companion object

    @AssistedFactory
    interface ViewModelAssistedFactory {
        fun create(userId: User.Id): FollowFollowerViewModel
    }

    val logger = loggerFactory.create("FollowFollowerVM")

    private val followingPagingStore =
        followFollowerPagingStoreFactory.create(RequestType.Following(userId))
    private val followerPagingStore =
        followFollowerPagingStoreFactory.create(RequestType.Follower(userId))

    private val user = userDataSource.observe(userId).flowOn(Dispatchers.IO).catch {
        logger.error("observeに失敗", it)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val followUsers = followingPagingStore.users.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    private val followerUsers = followerPagingStore.users.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )
    private val followUsersState = followingPagingStore.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PageableState.Loading.Init()
    )
    private val followerUsersState = followerPagingStore.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        PageableState.Loading.Init()
    )
    val uiState = combine(
        user,
        followUsers,
        followerUsers,
        followerUsersState,
        followUsersState
    ) { u, follows, followers, followerState, followState ->
        FollowFollowerUiState(
            user = u,
            followerUsers = followers,
            followUsers = follows,
            followerUsersState = followerState,
            followUsersState = followState,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FollowFollowerUiState()
    )

    fun loadInit() = viewModelScope.launch(Dispatchers.IO) {
        launch {
            followerPagingStore.clear()
            followerPagingStore.loadPrevious()
        }
        launch {
            followingPagingStore.clear()
            followingPagingStore.loadPrevious()
        }
        launch {
            userRepository.sync(userId)
        }

    }


    fun loadOld(loadType: LoadType) = viewModelScope.launch(Dispatchers.IO) {
        when(loadType) {
            LoadType.Follow -> {
                followingPagingStore.loadPrevious()
            }
            LoadType.Follower -> {
                followerPagingStore.loadPrevious()
            }
        }
    }


}

enum class LoadType {
    Follow, Follower
}
data class FollowFollowerUiState(
    val user: User? = null,
    val followUsers: List<User.Detail> = emptyList(),
    val followerUsers: List<User.Detail> = emptyList(),
    val followerUsersState: PageableState<List<User.Id>> = PageableState.Loading.Init(),
    val followUsersState: PageableState<List<User.Id>> = PageableState.Loading.Init()
)

fun FollowFollowerViewModel.Companion.provideFactory(
    factory: FollowFollowerViewModel.ViewModelAssistedFactory,
    userId: User.Id,
) = object : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return factory.create(userId) as T
    }
}