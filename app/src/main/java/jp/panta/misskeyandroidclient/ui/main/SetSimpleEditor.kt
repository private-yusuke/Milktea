package jp.panta.misskeyandroidclient.ui.main

import androidx.fragment.app.FragmentManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import net.pantasystem.milktea.app_store.setting.SettingStore


internal class SetSimpleEditor(
    private val fragmentManager: FragmentManager,
    private val settingStore: SettingStore,
    private val fab: FloatingActionButton,
) {
    operator fun invoke() {
//        val ft = fragmentManager.beginTransaction()
//
//        val editor = fragmentManager.findFragmentByTag("simpleEditor")
//
//        if (settingStore.isSimpleEditorEnabled) {
//            fab.visibility = View.GONE
//            if (editor == null) {
//                ft.replace(R.id.simpleEditorBase, SimpleEditorFragment(), "simpleEditor")
//            }
//        } else {
//            fab.visibility = View.VISIBLE
//
//            editor?.let {
//                ft.remove(it)
//            }
//
//        }
//        ft.commit()
    }
}