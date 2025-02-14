package net.pantasystem.milktea.data.infrastructure.channel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.pantasystem.milktea.model.account.AccountRepository
import net.pantasystem.milktea.model.channel.*

class ChannelRepositoryImpl(
    private val channelAPIAdapter: ChannelAPIAdapter,
    private val channelStateModel: ChannelStateModel,
    private val accountRepository: AccountRepository
) : ChannelRepository {
    override suspend fun findOne(id: Channel.Id): Result<Channel> {
        return runCatching {
            var channel = channelStateModel.get(id)
            if (channel == null) {
                val account = accountRepository.get(id.accountId).getOrThrow()
                channel = channelAPIAdapter.findOne(id).getOrThrow()
                    .toModel(account)
                channelStateModel.add(channel)
            }
            return@runCatching channel
        }
    }

    override suspend fun create(model: CreateChannel): Result<Channel> {
        return runCatching {
            val account = accountRepository.get(model.accountId).getOrThrow()
            val channel = channelAPIAdapter.create(model).getOrThrow()
                .toModel(account)
            channelStateModel.add(channel)
        }
    }

    override suspend fun follow(id: Channel.Id): Result<Channel> {
        return runCatching {
            var channel = findOne(id).getOrThrow()
            channelAPIAdapter.follow(id).getOrThrow()
            channel = channel.copy(isFollowing = true)
            channelStateModel.add(channel)
        }
    }

    override suspend fun unFollow(id: Channel.Id): Result<Channel> {
        return runCatching {
            var channel = findOne(id).getOrThrow()
            channelAPIAdapter.unFollow(id).getOrThrow()
            channel = channel.copy(isFollowing = false)
            channelStateModel.add(channel)
        }
    }

    override suspend fun update(model: UpdateChannel): Result<Channel> {
        return runCatching {
            val channel = channelAPIAdapter.update(model).getOrThrow()
            val account = accountRepository.get(model.id.accountId).getOrThrow()
            channelStateModel.add(channel.toModel(account))
        }
    }

    override suspend fun findFollowedChannels(
        accountId: Long,
        sinceId: Channel.Id?,
        untilId: Channel.Id?,
        limit: Int
    ): Result<List<Channel>> = runCatching {
        withContext(Dispatchers.IO) {
            channelAPIAdapter.findFollowedChannels(accountId, sinceId, untilId, 99).mapCatching { list ->
                val account = accountRepository.get(accountId).getOrThrow()
                list.map {
                    it.toModel(account)
                }
            }.mapCatching {
                channelStateModel.addAll(it)
                it
            }.getOrThrow()

        }
    }
}