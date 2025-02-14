package jp.panta.misskeyandroidclient.ui.main

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import net.pantasystem.milktea.app_store.account.AccountStore
import net.pantasystem.milktea.common_viewmodel.CurrentPageableTimelineViewModel
import net.pantasystem.milktea.common_viewmodel.SuitableType
import net.pantasystem.milktea.common_viewmodel.suitableType
import net.pantasystem.milktea.gallery.GalleryPostsActivity
import net.pantasystem.milktea.model.channel.Channel
import net.pantasystem.milktea.note.NoteEditorActivity

internal class FabClickHandler(
    private val currentPageableTimelineViewModel: CurrentPageableTimelineViewModel,
    private val activity: AppCompatActivity,
    private val accountStore: AccountStore,
) {

    fun onClicked() {
        activity.apply {
            when (val type = currentPageableTimelineViewModel.currentType.value.suitableType()) {
                is SuitableType.Other -> {
                    startActivity(Intent(this, NoteEditorActivity::class.java))
                }
                is SuitableType.Gallery -> {
                    val intent = Intent(this, GalleryPostsActivity::class.java)
                    intent.action = Intent.ACTION_EDIT
                    startActivity(intent)
                }
                is SuitableType.Channel -> {
                    val accountId = accountStore.currentAccountId!!
                    startActivity(
                        NoteEditorActivity.newBundle(
                            this,
                            channelId = Channel.Id(accountId, type.channelId)
                        )
                    )
                }
            }
        }

    }
}