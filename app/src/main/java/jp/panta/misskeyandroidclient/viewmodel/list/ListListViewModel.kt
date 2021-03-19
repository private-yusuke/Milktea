package jp.panta.misskeyandroidclient.viewmodel.list

import android.util.Log
import androidx.lifecycle.*
import jp.panta.misskeyandroidclient.model.I
import jp.panta.misskeyandroidclient.model.account.page.Page
import jp.panta.misskeyandroidclient.model.account.Account
import jp.panta.misskeyandroidclient.model.account.page.Pageable
import jp.panta.misskeyandroidclient.api.list.CreateList
import jp.panta.misskeyandroidclient.api.list.ListId
import jp.panta.misskeyandroidclient.api.list.UserListDTO
import jp.panta.misskeyandroidclient.util.eventbus.EventBus
import jp.panta.misskeyandroidclient.viewmodel.MiCore
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import kotlin.collections.LinkedHashMap

class ListListViewModel(
    val miCore: MiCore
) : ViewModel(){

    @Suppress("UNCHECKED_CAST")
    class Factory(val miCore: MiCore) : ViewModelProvider.Factory{
        override fun <T : ViewModel?> create(modelClass: Class<T>): T {
            return ListListViewModel( miCore) as T
        }
    }

    companion object{
        private const val TAG = "ListListViewModel"
    }

    val encryption = miCore.getEncryption()

    var account: Account? = null
    val userListList = MediatorLiveData<List<UserListDTO>>().apply{
        miCore.getCurrentAccount().onEach {
            account = it
            loadListList(it)
        }.launchIn(viewModelScope)
    }

    val pagedUserList = MediatorLiveData<Set<UserListDTO>>().apply{
        addSource(userListList){ userLists ->
            this.value = userLists.filter{ ul ->
                account?.pages?.any {
                    (it.pageable() as? Pageable.UserListTimeline)?.listId == ul.id
                }?:false
            }.toSet()
        }
    }

    private val mUserListIdMap = LinkedHashMap<String, UserListDTO>()


    val showUserDetailEvent = EventBus<UserListDTO>()

   

    fun loadListList(account: Account? = this.account){
        val i = account?.getI(encryption)
            ?: return
        miCore.getMisskeyAPI(account).userList(I(i)).enqueue(object : Callback<List<UserListDTO>>{
            override fun onResponse(
                call: Call<List<UserListDTO>>,
                response: Response<List<UserListDTO>>
            ) {
                val userListMap = response.body()?.map{
                    it.id to it
                }?.toMap()?: emptyMap()
                mUserListIdMap.clear()
                mUserListIdMap.putAll(userListMap)

                userListList.postValue(mUserListIdMap.values.toList())
            }

            override fun onFailure(call: Call<List<UserListDTO>>, t: Throwable) {
                Log.d(TAG, "loadListList error", t)
            }
        })
    }



    /**
     * 他Activityで変更を加える場合onActivityResultで呼び出し変更を適応する
     */
    fun onUserListUpdated(userList: UserListDTO?){
        userList?: return
        mUserListIdMap[userList.id] = userList
        userListList.postValue(mUserListIdMap.values.toList())
    }

    /**
     * 他Activity等でUserListを正常に作成できた場合onActivityResultで呼び出し変更を適応する
     */
    fun onUserListCreated(userList: UserListDTO){

        mUserListIdMap[userList.id] = userList
        userListList.postValue(mUserListIdMap.values.toList())
    }



    fun showUserListDetail(userList: UserListDTO?){
        userList?.let{ ul ->
            showUserDetailEvent.event = ul
        }
    }

    fun toggleTab(userList: UserListDTO?){
        userList?.let{ ul ->
            val exPage = account?.pages?.firstOrNull {
                val pageable = it.pageable()
                if(pageable is Pageable.UserListTimeline){
                    pageable.listId == ul.id
                }else{
                    false
                }
            }
            if(exPage == null && account != null){
                val page = Page(account!!.accountId, ul.name, pageable =  Pageable.UserListTimeline(ul.id), weight = 0)
                miCore.addPageInCurrentAccount(page)
            }else if(exPage != null){
                miCore.removePageInCurrentAccount(exPage)
            }
        }
    }

    fun delete(userList: UserListDTO?){
        val account = this.account
        val misskeyAPI = account?.let{
            miCore.getMisskeyAPI(it)
        }
        if(misskeyAPI == null || userList == null){
            return
        }
        misskeyAPI.deleteList(
            ListId(
            i = account.getI(miCore.getEncryption())!!,
            listId = userList.id
        )
        ).enqueue(object : Callback<Unit>{
            override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                userListList.postValue(userListList.value?.let{ ulList ->
                    ulList.filterNot{
                        it.id == userList.id
                    }
                })
            }

            override fun onFailure(call: Call<Unit>, t: Throwable) {

            }
        })
    }

    fun createUserList(name: String){
        val api = account?.let{
            miCore.getMisskeyAPI(it)
        }
        api?.createList(
            CreateList(
            account?.getI(miCore.getEncryption())!!,
            name = name
        )
        )?.enqueue(object : Callback<UserListDTO>{
            override fun onResponse(call: Call<UserListDTO>, response: Response<UserListDTO>) {
                val ul = response.body()
                if(ul != null){

                    onUserListCreated(ul)
                }
            }
            override fun onFailure(call: Call<UserListDTO>, t: Throwable) {

            }
        })
    }


}