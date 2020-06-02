package jp.panta.misskeyandroidclient.viewmodel.notes.editor

import android.net.Uri
import android.util.Log
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import jp.panta.misskeyandroidclient.model.Encryption
import jp.panta.misskeyandroidclient.model.api.MisskeyAPI
import jp.panta.misskeyandroidclient.model.core.AccountRelation
import jp.panta.misskeyandroidclient.model.core.EncryptedConnectionInformation
import jp.panta.misskeyandroidclient.model.drive.FileProperty
import jp.panta.misskeyandroidclient.model.drive.UploadFile
import jp.panta.misskeyandroidclient.model.emoji.Emoji
import jp.panta.misskeyandroidclient.model.meta.Meta
import jp.panta.misskeyandroidclient.model.notes.Note
import jp.panta.misskeyandroidclient.model.notes.draft.DraftNote
import jp.panta.misskeyandroidclient.model.notes.draft.DraftNoteDao
import jp.panta.misskeyandroidclient.model.notes.draft.DraftPoll
import jp.panta.misskeyandroidclient.model.reaction.ReactionSelection
import jp.panta.misskeyandroidclient.model.users.User
import jp.panta.misskeyandroidclient.util.eventbus.EventBus
import jp.panta.misskeyandroidclient.view.notes.editor.FileNoteEditorData
import jp.panta.misskeyandroidclient.viewmodel.MiCore
import jp.panta.misskeyandroidclient.viewmodel.notes.editor.poll.PollEditor
import jp.panta.misskeyandroidclient.viewmodel.users.UserViewData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.lang.NullPointerException
import java.util.*
import kotlin.collections.ArrayList

class NoteEditorViewModel(
    //private val accountRelation: AccountRelation,
    //private val misskeyAPI: MisskeyAPI,
    private val miCore: MiCore,
    private val draftNoteDao: DraftNoteDao,
    //meta: Meta,
    private val replyToNoteId: String? = null,
    private val quoteToNoteId: String? = null,
    private val encryption: Encryption = miCore.getEncryption(),
    val note: Note? = null,
    val draftNote: DraftNote? = null
) : ViewModel(){


    val currentAccount = miCore.currentAccount

    val currentUser = Transformations.map(currentAccount){
        UserViewData(it.account.id).apply{
            val ci = it.getCurrentConnectionInformation()
            val i = ci?.getI(miCore.getEncryption())
            i?.let{
                setApi(i, miCore.getMisskeyAPI(ci))
            }
        }
    }

    val hasCw = MutableLiveData<Boolean>(note?.cw != null || draftNote?.cw != null)
    val cw = MutableLiveData<String>(note?.cw?: draftNote?.cw)
    val text = MutableLiveData<String>(note?.text?: draftNote?.text)
    var maxTextLength = Transformations.map(currentAccount){
        miCore.getCurrentInstanceMeta()?.maxNoteTextLength?: 1500
    }
    /*val textRemaining = Transformations.map(text){ t: String? ->
        (maxTextLength.value?: 1500) - (t?.codePointCount(0, t.length)?: 0)
    }*/
    val textRemaining = MediatorLiveData<Int>().apply{
        addSourceChain(maxTextLength){ maxSize ->
            val t = text.value
            value = (maxSize?: 1500) - (t?.codePointCount(0, t.length)?: 0)
        }
        addSource(text){ t ->
            val max = maxTextLength.value?: 1500
            value = max - (t?.codePointCount(0, t.length)?: 0)
        }
    }

    val editorFiles = MediatorLiveData<List<FileNoteEditorData>>().apply{
        this.postValue(note?.files?.map{
            FileNoteEditorData(it)
        }?: draftNote?.draftFiles?.map{
            FileNoteEditorData(it)
        }?: emptyList())
    }

    val totalImageCount = MediatorLiveData<Int>().apply{

        this.addSource(editorFiles){
            Log.d("NoteEditorViewModel", "list$it, sizeは: ${it.size}")
            this.value = it.size
        }
    }


    val isPostAvailable = MediatorLiveData<Boolean>().apply{
        this.addSource(textRemaining){
            val totalImageTmp = totalImageCount.value
            this.value =  it in 0 until (maxTextLength.value?: 1500)
                    || (totalImageTmp != null && totalImageTmp > 0 && totalImageTmp <= 4)
                    || quoteToNoteId != null
        }
        this.addSource(totalImageCount){
            val tmpTextSize = textRemaining.value
            this.value = tmpTextSize in 0 until (maxTextLength.value?: 1500)
                    || (it != null && it > 0 && it <= 4)
                    || quoteToNoteId != null
        }
    }

    private val mVisibilityEnums = PostNoteTask.Visibility.values()
    private val mExVisibility = mVisibilityEnums.firstOrNull {
        it.visibility == note?.visibility?.toLowerCase(Locale.US) && it.isLocalOnly == note.localOnly?: false
                || it.visibility == draftNote?.visibility?.toLowerCase(Locale.US) && it.isLocalOnly == draftNote.localOnly?: false
    }
    val visibility = MutableLiveData<PostNoteTask.Visibility>(mExVisibility?: PostNoteTask.Visibility.PUBLIC)
    val showVisibilitySelectionEvent = EventBus<Unit>()
    val visibilitySelectedEvent = EventBus<Unit>()

    val address = MutableLiveData<List<UserViewData>>(
        note?.visibleUserIds?.map(::setUpUserViewData)
            ?: draftNote?.visibleUserIds?.map(::setUpUserViewData)
    )

    private fun setUpUserViewData(userId: String) : UserViewData{
        return UserViewData(userId).apply{
            val ci = getCurrentInformation()
            val i = ci?.getI(miCore.getEncryption())
            i?.let{
                setApi(i, miCore.getMisskeyAPI(ci))
            }
        }
    }

    val isSpecified = Transformations.map(visibility){
        it == PostNoteTask.Visibility.SPECIFIED
    }

    val poll = MutableLiveData<PollEditor?>(note?.poll?.let{
        PollEditor(it)
    })

    val noteTask = MutableLiveData<PostNoteTask>()

    val showPollDatePicker = EventBus<Unit>()
    val showPollTimePicker = EventBus<Unit>()

    val showPreviewFileEvent = EventBus<FileNoteEditorData>()

    val isSaveNoteAsDraft = EventBus<Long?>()
    init{
        currentAccount.observeForever {
            miCore.getCurrentInstanceMeta()
        }
    }

    fun post(){
        val noteTask = PostNoteTask(getCurrentInformation()!!, encryption)
        noteTask.cw = cw.value
        noteTask.files = editorFiles.value
        noteTask.text =text.value
        noteTask.poll = poll.value?.buildCreatePoll()
        noteTask.renoteId = quoteToNoteId
        noteTask.replyId = replyToNoteId
        noteTask.setVisibility(visibility.value, address.value?.map{
            it.userId
        })
        this.noteTask.postValue(noteTask)
    }

    fun add(file: Uri){
        val files = editorFiles.value.toArrayList()
        files.add(FileNoteEditorData(UploadFile(file, true)))
        editorFiles.value = files
    }

    fun add(fp: FileProperty){
        val files = editorFiles.value.toArrayList()
        files.add(FileNoteEditorData(fp))
        editorFiles.value = files
    }

    fun addAllFile(file: List<Uri>){
        val files = editorFiles.value.toArrayList()
        files.addAll(file.map{
            FileNoteEditorData(UploadFile(it, true))
        })
        editorFiles.value = files
    }

    fun addAllFileProperty(fpList: List<FileProperty>){
        val files = editorFiles.value.toArrayList()
        files.addAll(fpList.map{
            FileNoteEditorData(it)
        })
        editorFiles.value = files
    }

    fun removeFileNoteEditorData(data: FileNoteEditorData){
        val files = editorFiles.value.toArrayList()
        files.remove(data)
        editorFiles.value = files
    }

    fun localFileTotal(): Int{
        return editorFiles.value?.filter{
            it.isLocal
        }?.size?: 0
    }

    fun driveFileTotal(): Int{
        return editorFiles.value?.filter{
            !it.isLocal
        }?.size?: 0
    }

    fun fileTotal(): Int{
        return editorFiles.value?.size?: 0
    }

    fun driveFiles(): List<FileProperty>{
        return editorFiles.value?.filter {
            !it.isLocal && it.fileProperty != null
        }?.mapNotNull {
            it.fileProperty
        } ?: emptyList()
    }



    fun changeCwEnabled(){
        hasCw.value = !(hasCw.value?: false)
    }

    fun enablePoll(){
        val p = poll.value
        if(p == null){
            poll.value = PollEditor()
        }
    }

    fun disablePoll(){
        val p = poll.value
        if(p != null){
            poll.value = null
        }
    }

    fun showVisibilitySelection(){
        showVisibilitySelectionEvent.event = Unit
    }

    fun setVisibility(visibility: PostNoteTask.Visibility){
        this.visibility.value = visibility
        this.visibilitySelectedEvent.event = Unit
    }


    private fun List<FileNoteEditorData>?.toArrayList(): ArrayList<FileNoteEditorData>{
        return if(this == null){
            ArrayList<FileNoteEditorData>()
        }else{
            ArrayList<FileNoteEditorData>(this)
        }
    }

    fun setAddress(added: Array<String>, removed: Array<String>){
        val list = address.value?.let{
            ArrayList(it)
        }?: ArrayList()

        list.addAll(
            added.map{
                UserViewData(it).apply{
                    val ci = getCurrentInformation()
                    val i = ci?.getI(miCore.getEncryption())
                    i?.let{
                        setApi(i, miCore.getMisskeyAPI(ci))
                    }
                }
            }
        )

        list.removeAll { uv ->
            removed.any{
                uv.userId == it
            }
        }
        address.postValue(list)
    }

    fun showPreviewFile(previewImage: FileNoteEditorData){
        showPreviewFileEvent.event = previewImage
    }

    fun addMentionUsers(users: List<User>, pos: Int): Int{
        val mentionBuilder = StringBuilder()
        users.forEachIndexed { index, it ->
            val userName = it.getDisplayUserName()
            if(index < users.size - 1){
                mentionBuilder.appendln(userName)
            }else{
                mentionBuilder.append(userName)
            }
        }
        val builder = StringBuilder(text.value?: "")
        builder.insert(pos, mentionBuilder.toString())
        text.value = builder.toString()
        return pos + mentionBuilder.length
    }

    fun addEmoji(emoji: Emoji, pos: Int): Int{
        return addEmoji(":${emoji.name}:", pos)
    }

    fun addEmoji(emoji: String, pos: Int): Int{
        val builder = StringBuilder(text.value?: "")
        builder.insert(pos, emoji)
        text.value = builder.toString()
        Log.d("NoteEditorViewModel", "position:${pos + emoji.length - 1}")
        return pos + emoji.length
    }

    private fun getCurrentInformation(): EncryptedConnectionInformation?{
        return currentAccount.value?.getCurrentConnectionInformation()
    }

    fun saveDraft(){
        if(!canSaveDraft()){
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try{
                val dfNote = DraftNote(
                    accountId = currentAccount.value?.account?.id!!,
                    text = text.value,
                    cw = cw.value,
                    visibleUserIds = address.value?.map{
                        it.userId
                    },
                    draftPoll = poll.value?.toDraftPoll(),
                    visibility = visibility.value?.visibility?: "public",
                    localOnly = visibility.value?.isLocalOnly,
                    renoteId = quoteToNoteId,
                    replyId = replyToNoteId
                ).apply{
                    this.draftNoteId = draftNote?.draftNoteId
                }

                try{
                    isSaveNoteAsDraft.event = draftNoteDao.fullInsert(dfNote)
                }catch(e: Exception){
                    Log.e("NoteEditorVM", "下書き書き込み中にエラー発生：失敗してしまった", e)
                }
            }catch(e: IOException){

            }catch(e: NullPointerException){
                Log.e("NoteEditorVM", "下書き保存に失敗した", e)

            }catch (e: Throwable){
                Log.e("NoteEditorVM", "下書き保存に失敗した", e)

            }

        }
    }

    fun canSaveDraft(): Boolean{
        return !cw.value.isNullOrBlank()
                || !text.value.isNullOrBlank()
                || !editorFiles.value.isNullOrEmpty()
                || !poll.value?.choices?.value.isNullOrEmpty()
                || !address.value.isNullOrEmpty()
    }

    private fun <T, S>MediatorLiveData<T>.addSourceChain(liveData: LiveData<S>, observer: (out: S)-> Unit): MediatorLiveData<T>{
        this.addSource(liveData, observer)
        return this
    }

}