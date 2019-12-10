package jp.panta.misskeyandroidclient.model.messaging

import com.google.gson.annotations.SerializedName
import jp.panta.misskeyandroidclient.model.auth.ConnectionInstance
import jp.panta.misskeyandroidclient.model.drive.FileProperty
import jp.panta.misskeyandroidclient.model.group.Group
import jp.panta.misskeyandroidclient.model.users.User
import java.io.Serializable

data class Message(
    @SerializedName("id") val id: String,
    @SerializedName("createdAt") val createdAt: String,
    @SerializedName("text") val text: String?,
    @SerializedName("userId") val userId: String?,
    @SerializedName("user") val user: User?,
    @SerializedName("recipientId") val recipientId: String?,
    @SerializedName("recipient") val recipient: User?,
    @SerializedName("groupId") val groupId: String?,
    @SerializedName("group") val group: Group?,
    @SerializedName("fileId") val fileId: String?,
    @SerializedName("file") val file: FileProperty?,
    @SerializedName("isRead") val isRead: Boolean?
): Serializable{
    fun isGroup(): Boolean{
        return group != null
    }

    fun opponentUser(connectionInstance: ConnectionInstance) : User?{
        return if(recipient?.id == connectionInstance.userId){
            user
        }else{
            recipient
        }
    }
}