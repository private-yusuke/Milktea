package net.pantasystem.milktea.note.editor

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import com.google.android.material.composethemeadapter.MdcTheme
import com.wada811.databinding.dataBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pantasystem.milktea.app_store.account.AccountStore
import net.pantasystem.milktea.app_store.notes.toCreateNote
import net.pantasystem.milktea.common_android.ui.listview.applyFlexBoxLayout
import net.pantasystem.milktea.common_android.ui.text.CustomEmojiTokenizer
import net.pantasystem.milktea.common_android_ui.account.AccountSwitchingDialog
import net.pantasystem.milktea.common_compose.FilePreviewTarget
import net.pantasystem.milktea.common_navigation.*
import net.pantasystem.milktea.common_viewmodel.viewmodel.AccountViewModel
import net.pantasystem.milktea.model.drive.DriveFileRepository
import net.pantasystem.milktea.model.drive.FileProperty
import net.pantasystem.milktea.model.drive.FilePropertyDataSource
import net.pantasystem.milktea.model.emoji.Emoji
import net.pantasystem.milktea.model.file.toAppFile
import net.pantasystem.milktea.model.file.toFile
import net.pantasystem.milktea.model.instance.MetaRepository
import net.pantasystem.milktea.model.notes.draft.DraftNoteService
import net.pantasystem.milktea.model.user.User
import net.pantasystem.milktea.note.NoteEditorActivity
import net.pantasystem.milktea.note.R
import net.pantasystem.milktea.note.databinding.FragmentSimpleEditorBinding
import net.pantasystem.milktea.note.emojis.CustomEmojiPickerDialog
import net.pantasystem.milktea.note.emojis.viewmodel.EmojiSelectionViewModel
import net.pantasystem.milktea.note.editor.viewmodel.NoteEditorViewModel
import javax.inject.Inject


interface SimpleEditor {

    val isShowEditorMenu: MutableLiveData<Boolean>
    fun goToNormalEditor()

    fun closeMenu()
    fun openMenu()
}

@AndroidEntryPoint
class SimpleEditorFragment : Fragment(R.layout.fragment_simple_editor), SimpleEditor {


    val accountViewModel: AccountViewModel by activityViewModels()

    val mViewModel: NoteEditorViewModel by activityViewModels()
    private val mBinding: FragmentSimpleEditorBinding by dataBinding()

    override val isShowEditorMenu: MutableLiveData<Boolean> = MutableLiveData(false)

    @Inject
    lateinit var accountStore: AccountStore

    @Inject
    lateinit var metaRepository: MetaRepository


    @Inject
    lateinit var filePropertyDataSource: FilePropertyDataSource

    @Inject
    lateinit var fileRepository: DriveFileRepository

    @Inject
    lateinit var draftNoteService: DraftNoteService

    @Inject
    lateinit var driveNavigation: DriveNavigation

    @Inject
    lateinit var mediaNavigation: MediaNavigation

    @Inject
    lateinit var userDetailNavigation: UserDetailNavigation

    @Inject
    lateinit var searchAndSelectUserNavigation: SearchAndSelectUserNavigation

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        mBinding.simpleEditor = this

        mBinding.lifecycleOwner = this
        mBinding.noteEditorViewModel = mViewModel

        val userChipAdapter =
            net.pantasystem.milktea.common_android_ui.user.UserChipListAdapter(viewLifecycleOwner)
        mBinding.addressUsersView.adapter = userChipAdapter
        mBinding.addressUsersView.applyFlexBoxLayout(requireContext())


        mBinding.accountViewModel = accountViewModel
        accountViewModel.switchAccount.observe(this) {
            AccountSwitchingDialog().show(childFragmentManager, "tag")
        }
        accountViewModel.showProfile.observe(this) {
            val intent = userDetailNavigation.newIntent(
                UserDetailNavigationArgs.UserId(
                    User.Id(it.accountId, it.remoteId)
                )
            )

            startActivity(intent)
        }

        accountStore.observeCurrentAccount.filterNotNull().flatMapLatest {
            metaRepository.observe(it.instanceDomain)
        }.mapNotNull {
            it?.emojis
        }.distinctUntilChanged().onEach { emojis ->
            mBinding.inputMainText.setAdapter(
                CustomEmojiCompleteAdapter(
                    emojis,
                    requireContext()
                )
            )
            mBinding.inputMainText.setTokenizer(CustomEmojiTokenizer())

            mBinding.inputCw.setAdapter(
                CustomEmojiCompleteAdapter(
                    emojis,
                    requireContext()
                )
            )
            mBinding.inputCw.setTokenizer(CustomEmojiTokenizer())
        }.launchIn(lifecycleScope)


        val viewModel = mViewModel
        mBinding.noteEditorViewModel = viewModel
        mBinding.filePreview.apply {
            setContent {
                MdcTheme {
                    NoteFilePreview(
                        noteEditorViewModel = viewModel,
                        fileRepository = fileRepository,
                        dataSource = filePropertyDataSource,
                        onShow = {
                            val file = when (it) {
                                is FilePreviewTarget.Remote -> {
                                    it.fileProperty.toFile()
                                }
                                is FilePreviewTarget.Local -> {
                                    it.file.toFile()
                                }
                            }

                            val intent = mediaNavigation.newIntent(MediaNavigationArgs.Files(
                                listOf(file),
                                0
                            ))
                            requireActivity().startActivity(intent)
                        }
                    )
                }
            }
        }



        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.poll.collect { poll ->
                    if (poll == null) {
                        removePollFragment()
                    } else {
                        setPollFragment()
                    }
                }
            }
        }


        viewModel.isPost.observe(viewLifecycleOwner) {
            viewModel.clear()
        }

        viewModel.showVisibilitySelectionEvent.observe(viewLifecycleOwner) {
            Log.d("NoteEditorActivity", "公開範囲を設定しようとしています")
            val dialog = VisibilitySelectionDialog()
            dialog.show(childFragmentManager, "NoteEditor")
        }

        lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.address.collect {
                    userChipAdapter.submitList(it)
                }
            }
        }

        viewModel.showPollTimePicker.observe(this) {
            PollTimePickerDialog().show(childFragmentManager, "TimePicker")
        }

        viewModel.showPollDatePicker.observe(this) {
            PollDatePickerDialog().show(childFragmentManager, "DatePicker")
        }

        mBinding.inputCw.addTextChangedListener { e ->
            viewModel.setCw((e?.toString() ?: ""))
        }

        mBinding.inputMainText.addTextChangedListener { e ->
            viewModel.setText((e?.toString() ?: ""))
        }




        mBinding.selectFileFromDrive.setOnClickListener {
            showDriveFileSelector()
        }

        mBinding.selectFileFromLocal.setOnClickListener {
            showFileManager()
        }

        mBinding.addAddress.setOnClickListener {
            startSearchAndSelectUser()
        }

        mBinding.mentionButton.setOnClickListener {
            startMentionToSearchAndSelectUser()
        }

        mBinding.showEmojisButton.setOnClickListener {
            CustomEmojiPickerDialog().show(childFragmentManager, "Editor")
        }


        mBinding.postButton.setOnClickListener {
            viewModel.post()
        }

        val emojiSelectionViewModel =
            ViewModelProvider(requireActivity())[EmojiSelectionViewModel::class.java]
        emojiSelectionViewModel.selectedEmojiName.observe(viewLifecycleOwner, (::onSelect))
        emojiSelectionViewModel.selectedEmoji.observe(viewLifecycleOwner, (::onSelect))

    }


    private fun onSelect(emoji: Emoji) {
        val pos = mBinding.inputMainText.selectionEnd
        mViewModel.addEmoji(emoji, pos).let { newPos ->
            mBinding.inputMainText.setText(mViewModel.text.value ?: "")
            mBinding.inputMainText.setSelection(newPos)
            Log.d("NoteEditorActivity", "入力されたデータ:${mBinding.inputMainText.text}")
        }
    }

    private fun onSelect(emoji: String) {
        val pos = mBinding.inputMainText.selectionEnd
        mViewModel.addEmoji(emoji, pos).let { newPos ->
            mBinding.inputMainText.setText(mViewModel.text.value ?: "")
            mBinding.inputMainText.setSelection(newPos)
        }
    }

    private fun setPollFragment() {
        val ft = childFragmentManager.beginTransaction()
        ft.replace(R.id.edit_poll, PollEditorFragment(), "pollFragment")
        ft.commit()
    }

    private fun removePollFragment() {
        val fragment = childFragmentManager.findFragmentByTag("pollFragment")
        if (fragment != null) {
            val ft = childFragmentManager.beginTransaction()
            ft.remove(fragment)
            ft.commit()
        }
    }

    private fun showFileManager() {
        if (checkPermission()) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.type = "*/*"
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            openLocalStorageResult.launch(intent)
        } else {
            requestPermission()
        }

    }

    private fun showDriveFileSelector() {
        val selectedSize = mViewModel.totalImageCount.value ?: 0

        //Directoryは既に選択済みのファイルの数も含めてしまうので選択済みの数も合わせる
        val selectableMaxSize = 4 - selectedSize
        Log.d("", "選択済みのサイズ:$selectedSize")
        val intent = driveNavigation.newIntent(DriveNavigationArgs(
            selectableFileMaxSize = selectableMaxSize
        ))
        intent.action = Intent.ACTION_OPEN_DOCUMENT
        registerForOpenDriveActivityResult.launch(intent)
    }

    private fun checkPermission(): Boolean {
        val permissionCheck = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        return permissionCheck == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        //val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        if (!checkPermission()) {
            requestReadStoragePermissionResult.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun startSearchAndSelectUser() {
        val selectedUserIds = mViewModel.address.value.mapNotNull {
            it.userId ?: it.user.value?.id
        }

        val intent = searchAndSelectUserNavigation.newIntent(
            SearchAndSelectUserNavigationArgs(selectedUserIds = selectedUserIds)
        )
        selectUserResult.launch(intent)
    }

    private fun startMentionToSearchAndSelectUser() {
        val intent = searchAndSelectUserNavigation.newIntent(SearchAndSelectUserNavigationArgs())
        selectMentionToUserResult.launch(intent)
    }

//    override fun onSelect(file: File?) {
//        file?.let{
//            val intent = Intent(requireContext(), MediaActivity::class.java)
//            intent.putExtra(MediaActivity.EXTRA_FILE, file)
//            startActivity(intent)
//        }
//
//    }
//
//    override fun onDetach(file: File?) {
//        file?.let{
//            mViewModel.removeFileNoteEditorData(file)
//        }
//    }

    override fun closeMenu() {
        isShowEditorMenu.value = false
    }

    override fun openMenu() {
        isShowEditorMenu.value = true
    }


    override fun goToNormalEditor() {

        lifecycleScope.launch(Dispatchers.IO) {

            accountStore.currentAccount?.let {
                draftNoteService.save(mViewModel.state.value.toCreateNote(it)).onSuccess {
                    withContext(Dispatchers.Main) {
                        val intent = NoteEditorActivity.newBundle(requireContext(), draftNoteId = it.draftNoteId)
                        startActivity(intent)
                        mViewModel.clear()
                    }
                }
            }
        }

    }

    private val registerForOpenDriveActivityResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val selectedFilePropertyIds =
                    (result.data?.getSerializableExtra(EXTRA_SELECTED_FILE_PROPERTY_IDS) as List<*>).map {
                        it as FileProperty.Id
                    }
                mViewModel.addFilePropertyFromIds(selectedFilePropertyIds)
            }

        }

    private val openLocalStorageResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

            val uri = result?.data?.data
            if (uri != null) {
                mViewModel.add(uri.toAppFile(requireContext()))
                Log.d("NoteEditorActivity", "成功しました")

            }
        }

    private val requestReadStoragePermissionResult =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) {
                showFileManager()
            } else {
                Toast.makeText(
                    requireContext(),
                    "ストレージへのアクセスを許可しないとファイルを読み込めないぽよ",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    private val selectUserResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val changed =
                    result.data?.getSerializableExtra(SearchAndSelectUserNavigation.EXTRA_SELECTED_USER_CHANGED_DIFF) as? ChangedDiffResult
                if (changed != null) {
                    mViewModel.setAddress(changed.added, changed.removed)
                }
            }
        }

    private val selectMentionToUserResult =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                val changed =
                    result.data?.getSerializableExtra(SearchAndSelectUserNavigation.EXTRA_SELECTED_USER_CHANGED_DIFF) as? ChangedDiffResult

                if (changed != null) {
                    val pos = mBinding.inputMainText.selectionEnd
                    mViewModel.addMentionUserNames(changed.selectedUserNames, pos).let { newPos ->
                        mBinding.inputMainText.setText(mViewModel.text.value ?: "")
                        mBinding.inputMainText.setSelection(newPos)
                    }
                }

            }
        }


}