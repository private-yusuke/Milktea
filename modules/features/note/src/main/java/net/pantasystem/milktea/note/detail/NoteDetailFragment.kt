package net.pantasystem.milktea.note.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.annotation.MainThread
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.wada811.databinding.dataBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.pantasystem.milktea.app_store.setting.SettingStore
import net.pantasystem.milktea.common_navigation.UserDetailNavigation
import net.pantasystem.milktea.common_viewmodel.CurrentPageableTimelineViewModel
import net.pantasystem.milktea.model.account.page.Page
import net.pantasystem.milktea.model.account.page.Pageable
import net.pantasystem.milktea.model.notes.Note
import net.pantasystem.milktea.note.R
import net.pantasystem.milktea.note.databinding.FragmentNoteDetailBinding
import net.pantasystem.milktea.note.view.NoteCardActionHandler
import net.pantasystem.milktea.note.viewmodel.NotesViewModel
import net.pantasystem.milktea.note.detail.viewmodel.NoteDetailViewModel
import net.pantasystem.milktea.note.detail.viewmodel.provideFactory
import javax.inject.Inject


@AndroidEntryPoint
class NoteDetailFragment : Fragment(R.layout.fragment_note_detail) {

    companion object {
        private const val EXTRA_NOTE_ID =
            "jp.panta.misskeyandroidclinet.view.notes.detail.EXTRA_NOTE_ID"
        private const val EXTRA_PAGE = "jp.panta.misskeyandroidclinet.view.notes.detail.EXTRA_PAGE"
        private const val EXTRA_ACCOUNT_ID =
            "jp.panta.misskeyandroidclient.view.notes.detail.EXTRA_ACCOUNT_ID"

        fun newInstance(noteId: String, accountId: Long? = null): NoteDetailFragment {
            return NoteDetailFragment().apply {
                arguments = Bundle().apply {
                    putString(EXTRA_NOTE_ID, noteId)
                    putLong(EXTRA_ACCOUNT_ID, accountId ?: -1)
                }
            }
        }

        fun newInstance(
            page: Page
        ): NoteDetailFragment {
            page.pageable() as? Pageable.Show ?: throw IllegalArgumentException("Not Pageable.Show")
            return NoteDetailFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(EXTRA_PAGE, page)
                    putLong(EXTRA_ACCOUNT_ID, page.accountId)
                }
            }
        }

        fun newInstance(noteId: Note.Id): NoteDetailFragment {
            return newInstance(noteId.noteId, noteId.accountId)
        }
    }


    val notesViewModel: NotesViewModel by activityViewModels()

    private val currentPageableTimelineViewModel: CurrentPageableTimelineViewModel by activityViewModels()

    @Inject
    lateinit var noteDetailViewModelAssistedFactory: NoteDetailViewModel.ViewModelAssistedFactory

    @Inject
    internal lateinit var settingStore: SettingStore

    @Inject
    lateinit var userDetailNavigation: UserDetailNavigation

    val page: Pageable.Show by lazy {
        (arguments?.getSerializable(EXTRA_PAGE) as? Page)?.pageable() as? Pageable.Show
            ?: Pageable.Show(arguments?.getString(EXTRA_NOTE_ID)!!)
    }
    private val noteDetailViewModel: NoteDetailViewModel by viewModels {
        val accountId = arguments?.getLong(EXTRA_ACCOUNT_ID, -1)?.let {
            if (it == -1L) null else it
        }
        NoteDetailViewModel.provideFactory(
            noteDetailViewModelAssistedFactory,
            page,
            accountId
        )
    }

    val binding: FragmentNoteDetailBinding by dataBinding()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = NoteDetailAdapter(
            noteDetailViewModel = noteDetailViewModel,
            viewLifecycleOwner = viewLifecycleOwner
        ) {
            NoteCardActionHandler(
                requireActivity() as AppCompatActivity,
                notesViewModel,
                settingStore,
                userDetailNavigation
            ).onAction(it)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                noteDetailViewModel.notes.collect {
                    adapter.submitList(it)
                }
            }
        }



        binding.notesView.adapter = adapter
        binding.notesView.layoutManager = LinearLayoutManager(context)

        binding.showInBrowser.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val url = noteDetailViewModel.getUrl()
                withContext(Dispatchers.Main) {
                    showShareLink(url)
                }
            }
        }

    }

    override fun onResume() {
        super.onResume()

        currentPageableTimelineViewModel.setCurrentPageable(page)
    }

    @MainThread
    private fun showShareLink(url: String) {
        val uri = Uri.parse(url)
        startActivity(
            Intent(Intent.ACTION_VIEW, uri)
        )
    }
}