package jp.panta.misskeyandroidclient.view.notification

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import jp.panta.misskeyandroidclient.MiApplication
import jp.panta.misskeyandroidclient.R
import jp.panta.misskeyandroidclient.viewmodel.notes.NotesViewModel
import jp.panta.misskeyandroidclient.viewmodel.notification.NotificationViewData
import jp.panta.misskeyandroidclient.viewmodel.notification.NotificationViewModel
import jp.panta.misskeyandroidclient.viewmodel.notification.NotificationViewModelFactory
import kotlinx.android.synthetic.main.fragment_notification.*

class NotificationFragment : Fragment(R.layout.fragment_notification){

    /*override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notification, container, false)
    }*/
    lateinit var mLinearLayoutManager: LinearLayoutManager
    lateinit var mViewModel: NotificationViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mLinearLayoutManager = LinearLayoutManager(this.context!!)

        val miApplication = context?.applicationContext as MiApplication
        val nowConnectionInstance = miApplication.currentConnectionInstanceLiveData.value

        if(nowConnectionInstance != null){
            val factory = NotificationViewModelFactory(nowConnectionInstance, miApplication)
            mViewModel = ViewModelProvider(this, factory).get(NotificationViewModel::class.java)

            val notesViewModel = ViewModelProvider(activity!!).get(NotesViewModel::class.java)

            val adapter = NotificationListAdapter(diffUtilItemCallBack, notesViewModel, viewLifecycleOwner)
            notification_list_view.adapter = adapter
            notification_list_view.layoutManager = mLinearLayoutManager

            mViewModel.loadInit()

            mViewModel.notificationsLiveData.observe(viewLifecycleOwner, Observer {
                adapter.submitList(it)
            })

            mViewModel.isLoading.observe(viewLifecycleOwner, Observer {
                notification_swipe_refresh.isRefreshing = it
            })

            notification_swipe_refresh.setOnRefreshListener {
                mViewModel.loadInit()
            }
        }


    }

    private val mScrollListener = object : RecyclerView.OnScrollListener(){
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)

            val firstVisibleItemPosition = mLinearLayoutManager?.findFirstVisibleItemPosition()?: -1
            val endVisibleItemPosition = mLinearLayoutManager?.findLastVisibleItemPosition()?: -1
            val itemCount = mLinearLayoutManager?.itemCount?: -1

            //mFirstVisibleItemPosition = firstVisibleItemPosition
            //val childCount = recyclerView.childCount
            //Log.d("", "firstVisibleItem: $firstVisibleItemPosition, itemCount: $itemCount, childCount: $childCount")
            //Log.d("", "first:$firstVisibleItemPosition, end:$endVisibleItemPosition, itemCount:$itemCount")
            if(firstVisibleItemPosition == 0){
                Log.d("", "先頭")
            }

            if(endVisibleItemPosition == (itemCount - 1)){
                Log.d("", "後ろ")
                //mTimelineViewModel?.getOldTimeline()
                mViewModel?.loadOld()

            }

        }
    }

    private val diffUtilItemCallBack = object : DiffUtil.ItemCallback<NotificationViewData>(){
        override fun areContentsTheSame(
            oldItem: NotificationViewData,
            newItem: NotificationViewData
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areItemsTheSame(
            oldItem: NotificationViewData,
            newItem: NotificationViewData
        ): Boolean {
            return oldItem.id == newItem.id
        }
    }
}