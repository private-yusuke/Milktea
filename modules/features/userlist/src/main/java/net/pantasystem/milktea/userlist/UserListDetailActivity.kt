@file:Suppress("DEPRECATION")

package net.pantasystem.milktea.userlist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.composethemeadapter.MdcTheme
import dagger.hilt.android.AndroidEntryPoint
import net.pantasystem.milktea.app_store.setting.SettingStore
import net.pantasystem.milktea.common.ui.ApplyMenuTint
import net.pantasystem.milktea.common.ui.ApplyTheme
import net.pantasystem.milktea.common_android_ui.PageableFragmentFactory
import net.pantasystem.milktea.common_navigation.*
import net.pantasystem.milktea.common_navigation.SearchAndSelectUserNavigation.Companion.EXTRA_SELECTED_USER_CHANGED_DIFF
import net.pantasystem.milktea.common_viewmodel.confirm.ConfirmViewModel
import net.pantasystem.milktea.model.list.UserList
import net.pantasystem.milktea.note.viewmodel.NotesViewModel
import net.pantasystem.milktea.userlist.compose.UserListDetailScreen
import net.pantasystem.milktea.userlist.viewmodel.UserListDetailViewModel
import javax.inject.Inject

@AndroidEntryPoint
class UserListDetailActivity : AppCompatActivity(), UserListEditorDialog.OnSubmittedListener {

    companion object {
        private const val TAG = "UserListDetailActivity"
        private const val EXTRA_LIST_ID = "jp.panta.misskeyandroidclient.EXTRA_LIST_ID"


        const val ACTION_SHOW = "ACTION_SHOW"
        const val ACTION_EDIT_NAME = "ACTION_EDIT_NAME"


        fun newIntent(context: Context, listId: UserList.Id): Intent {
            return Intent(context, UserListDetailActivity::class.java).apply {
                putExtra(EXTRA_LIST_ID, listId)
            }
        }
    }


    @Inject
    lateinit var assistedFactory: UserListDetailViewModel.ViewModelAssistedFactory

    @Inject
    lateinit var settingStore: SettingStore

    @Inject
    lateinit var pageableFragmentFactory: PageableFragmentFactory

    @Inject
    lateinit var searchAndSelectUserNavigation: SearchAndSelectUserNavigation

    @Inject
    lateinit var userDetailPageNavigation: UserDetailNavigation

    @Inject
    lateinit var applyTheme: ApplyTheme

    @Inject
    lateinit var applyMenuTint: ApplyMenuTint

    private val listId by lazy {
        intent.getSerializableExtra(EXTRA_LIST_ID) as UserList.Id
    }
    private val mUserListDetailViewModel: UserListDetailViewModel by viewModels {
        UserListDetailViewModel.provideFactory(assistedFactory, listId)
    }

    val notesViewModel by viewModels<NotesViewModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyTheme()

        net.pantasystem.milktea.note.view.ActionNoteHandler(
            this,
            notesViewModel,
            ViewModelProvider(this)[ConfirmViewModel::class.java],
            settingStore
        ).initViewModelListener()

        setContent {
            MdcTheme {
                val userList by mUserListDetailViewModel.userList.collectAsState()

                val users by mUserListDetailViewModel.users.collectAsState()
                val isAddedTab by mUserListDetailViewModel.isAddedToTab.collectAsState()

                UserListDetailScreen(
                    listId = listId,
                    userList = userList,
                    users = users,
                    isAddedTab = isAddedTab,
                    onNavigateUp = {
                        finish()
                    },
                    fragmentManager = supportFragmentManager,
                    pageableFragmentFactory = pageableFragmentFactory,
                    onToggleButtonClicked = {
                        mUserListDetailViewModel.toggleAddToTab()
                    },
                    onEditButtonClicked = {
                        showEditUserListDialog()
                    },
                    onAddUserButtonClicked = {
                        val selected =
                            mUserListDetailViewModel.users.value.map {
                                it.id
                            }
                        val intent = searchAndSelectUserNavigation.newIntent(
                            SearchAndSelectUserNavigationArgs(
                                selectedUserIds = selected
                            )
                        )
                        requestSelectUserResult.launch(intent)
                    },
                    onSelectUser = {
                        startActivity(
                            userDetailPageNavigation.newIntent(
                                UserDetailNavigationArgs.UserId(it.id)
                            )
                        )
                    },
                    onDeleteUserButtonClicked = {
                        mUserListDetailViewModel.pullUser(it.id)
                    }
                )

            }
        }



        if (intent.action == ACTION_EDIT_NAME) {
            intent.action = ACTION_SHOW
            showEditUserListDialog()
        }

    }

    override fun onSubmit(name: String) {
        mUserListDetailViewModel.updateName(name)
    }


    private val requestSelectUserResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val resultCode = result.resultCode
            val data = result.data
            if (resultCode == RESULT_OK) {
                val changedDiff =
                    data?.getSerializableExtra(EXTRA_SELECTED_USER_CHANGED_DIFF) as? ChangedDiffResult
                val added = changedDiff?.added
                val removed = changedDiff?.removed
                Log.d(TAG, "新たに追加:${added?.toList()}, 削除:${removed?.toList()}")
                added?.forEach {
                    mUserListDetailViewModel.pushUser(it)
                }
                removed?.forEach {
                    mUserListDetailViewModel.pullUser(it)
                }
            }
        }

    private fun showEditUserListDialog() {
        val dialog = UserListEditorDialog.newInstance(
            listId.userListId,
            mUserListDetailViewModel.userList.value?.name ?: ""
        )
        dialog.show(supportFragmentManager, "")
    }


}

