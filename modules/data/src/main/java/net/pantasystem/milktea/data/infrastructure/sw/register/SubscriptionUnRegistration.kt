package net.pantasystem.milktea.data.infrastructure.sw.register

import com.google.android.gms.tasks.Task
import com.google.firebase.messaging.FirebaseMessaging
import net.pantasystem.milktea.api.misskey.register.UnSubscription
import net.pantasystem.milktea.common.Encryption
import net.pantasystem.milktea.common.throwIfHasError
import net.pantasystem.milktea.data.api.misskey.MisskeyAPIProvider
import net.pantasystem.milktea.model.account.AccountRepository
import net.pantasystem.milktea.model.sw.register.SubscriptionUnRegistration
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class SubscriptionUnRegistrationImpl @Inject constructor(

    val accountRepository: AccountRepository,
    val encryption: Encryption,
    val lang: String,
    val misskeyAPIProvider: MisskeyAPIProvider,
    private val publicKey: String,
    private val auth: String,
    private val endpointBase: String,
) : SubscriptionUnRegistration {


    override suspend fun unregister(accountId: Long) {
        val token = FirebaseMessaging.getInstance().token.asSuspend()
        val account = accountRepository.get(accountId).getOrThrow()
        val apiProvider = misskeyAPIProvider.get(account)
        val endpoint = EndpointBuilder(
            accountId = account.accountId,
            deviceToken = token,
            lang = lang,
            publicKey = publicKey,
            endpointBase = endpointBase,
            auth = auth,
        ).build()
        apiProvider.swUnRegister(
            UnSubscription(
            i = account.getI(encryption),
            endpoint = endpoint
        )
        ).throwIfHasError()
    }
}

suspend fun<T> Task<T>.asSuspend() = suspendCoroutine<T> { continuation ->
    addOnSuccessListener {
        continuation.resume(it)
    }
    addOnFailureListener {
        throw it
    }
}