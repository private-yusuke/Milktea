package jp.panta.misskeyandroidclient.model.messaging

import jp.panta.misskeyandroidclient.model.auth.ConnectionInstance
import java.io.Serializable

data class MessageAction(
    val i: String?,
    val userId: String?,
    val groupId: String?,
    val text: String?,
    val fileId: String?,
    val messageId: String?
): Serializable{
    class Factory(val connectionInstance: ConnectionInstance, val message: Message){
        fun actionCreateMessage(text: String?, fileId: String?): MessageAction{
            return MessageAction(
                connectionInstance.getI(),
                if(message.isGroup()) null else message.opponentUser(connectionInstance)?.id,
                message.group?.id,
                text,
                fileId,
            null
            )
        }

        fun actionDeleteMessage(message: Message): MessageAction{
            return MessageAction(
                connectionInstance.getI(),
                null,
                null,
                null,
                null,
                message.id
            )
        }

        fun actionRead(message: Message): MessageAction{
            return MessageAction(
                connectionInstance.getI(),
                null,
                null,
                null,
                null,
                message.id
            )
        }
    }
}