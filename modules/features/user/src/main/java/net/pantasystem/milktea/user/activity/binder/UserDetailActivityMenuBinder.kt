package net.pantasystem.milktea.user.activity.binder

import android.app.Activity
import android.util.Log
import android.view.Menu
import net.pantasystem.milktea.app_store.account.AccountStore
import net.pantasystem.milktea.common.ui.ApplyMenuTint
import net.pantasystem.milktea.model.account.page.Pageable
import net.pantasystem.milktea.user.R
import net.pantasystem.milktea.user.viewmodel.UserDetailViewModel

class UserDetailActivityMenuBinder(
    val activity: Activity,
    val userDetailViewModel: UserDetailViewModel,
    val applyMenuTint: ApplyMenuTint,
    val accountStore: AccountStore,
) {

    fun bind(menu: Menu) {

        val state = userDetailViewModel.userState.value
        Log.d("UserDetailActivity", "onCreateOptionsMenu: state:$state")

        val block = menu.findItem(R.id.block)
        val mute = menu.findItem(R.id.mute)
        val unblock = menu.findItem(R.id.unblock)
        val unmute = menu.findItem(R.id.unmute)
        val report = menu.findItem(R.id.report_user)
        mute?.isVisible = !(state?.isMuting ?: false)
        block?.isVisible = !(state?.isBlocking ?: false)
        unblock?.isVisible = (state?.isBlocking ?: false)
        unmute?.isVisible = (state?.isMuting ?: false)
        if (userDetailViewModel.isMine.value) {
            block?.isVisible = false
            mute?.isVisible = false
            unblock?.isVisible = false
            unmute?.isVisible = false
            report?.isVisible = false
        }

        val tab = menu.findItem(R.id.nav_add_to_tab)
        val page = accountStore.currentAccount?.pages?.firstOrNull {
            val pageable = it.pageable()
            if (pageable is Pageable.UserTimeline) {
                pageable.userId == state?.id?.id
            } else {
                false
            }
        }
        if (page == null) {
            tab?.setIcon(R.drawable.ic_add_to_tab_24px)
        } else {
            tab?.setIcon(R.drawable.ic_remove_to_tab_24px)
        }

        applyMenuTint(activity, menu)
    }

}