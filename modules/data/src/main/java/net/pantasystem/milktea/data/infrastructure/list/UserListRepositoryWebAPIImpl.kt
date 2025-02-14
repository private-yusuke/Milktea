package net.pantasystem.milktea.data.infrastructure.list

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.pantasystem.milktea.api.misskey.I
import net.pantasystem.milktea.api.misskey.list.CreateList
import net.pantasystem.milktea.api.misskey.list.ListId
import net.pantasystem.milktea.api.misskey.list.ListUserOperation
import net.pantasystem.milktea.api.misskey.list.UpdateList
import net.pantasystem.milktea.common.Encryption
import net.pantasystem.milktea.common.throwIfHasError
import net.pantasystem.milktea.data.api.misskey.MisskeyAPIProvider
import net.pantasystem.milktea.data.infrastructure.toEntity
import net.pantasystem.milktea.model.account.AccountRepository
import net.pantasystem.milktea.model.list.UserList
import net.pantasystem.milktea.model.list.UserListMember
import net.pantasystem.milktea.model.list.UserListRepository
import net.pantasystem.milktea.model.list.UserListWithMembers
import net.pantasystem.milktea.model.user.User
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserListRepositoryWebAPIImpl @Inject constructor(
    val encryption: Encryption,
    val misskeyAPIProvider: MisskeyAPIProvider,
    val accountRepository: AccountRepository,
    private val userListDao: UserListDao
) : UserListRepository {
    override suspend fun findByAccountId(accountId: Long): List<UserList> {
        val account = accountRepository.get(accountId).getOrThrow()
        val api = misskeyAPIProvider.get(account)
        val body = api.userList(I(account.getI(encryption)))
            .throwIfHasError()
            .body()
        return body!!.map {
            it.toEntity(account)
        }
    }

    override suspend fun create(accountId: Long, name: String): UserList {
        val account = accountRepository.get(accountId).getOrThrow()
        val res = misskeyAPIProvider.get(account).createList(
            CreateList(
                account.getI(encryption),
                name = name
            )
        ).throwIfHasError()
        return res.body()!!.toEntity(account).also {
            upsert(it)
        }
    }

    override suspend fun update(listId: UserList.Id, name: String) {
        val account = accountRepository.get(listId.accountId).getOrThrow()
        misskeyAPIProvider.get(account).updateList(
            UpdateList(
                account.getI(encryption),
                name = name,
                listId = listId.userListId
            )
        ).throwIfHasError()
    }

    override suspend fun appendUser(
        listId: UserList.Id,
        userId: User.Id
    ) {
        val account = accountRepository.get(listId.accountId).getOrThrow()
        val misskeyAPI = misskeyAPIProvider.get(account)
        misskeyAPI.pushUserToList(
            ListUserOperation(
                userId = userId.id,
                listId = listId.userListId,
                i = account.getI(encryption)
            )
        ).throwIfHasError()
    }

    override suspend fun removeUser(
        listId: UserList.Id,
        userId: User.Id
    ) {
        val account = accountRepository.get(listId.accountId).getOrThrow()
        val misskeyAPI = misskeyAPIProvider.get(account)
        misskeyAPI.pullUserFromList(
            ListUserOperation(
                userId = userId.id,
                listId = listId.userListId,
                i = account.getI(encryption)
            )
        ).throwIfHasError()
    }

    override suspend fun delete(listId: UserList.Id) {
        val account = accountRepository.get(listId.accountId).getOrThrow()
        val misskeyAPI = misskeyAPIProvider.get(account)
        misskeyAPI.deleteList(ListId(account.getI(encryption), listId.userListId))
            .throwIfHasError()
    }

    override suspend fun findOne(userListId: UserList.Id): UserList {
        val account = accountRepository.get(userListId.accountId).getOrThrow()
        val misskeyAPI = misskeyAPIProvider.get(account)
        val res = misskeyAPI.showList(ListId(account.getI(encryption), userListId.userListId))
            .throwIfHasError()
        return res.body()!!.toEntity(account)
    }

    override fun observeByAccountId(accountId: Long): Flow<List<UserListWithMembers>> {
        return userListDao.observeUserListRelatedWhereByAccountId(accountId)
            .map { list ->
                list.map { relatedRecord ->
                    UserListWithMembers(
                        userList = relatedRecord.toModel(),
                        members = relatedRecord.members.map { member ->
                            UserListMember(
                                avatarUrl = member.avatarUrl,
                                userId = User.Id(relatedRecord.userList.accountId, member.serverId)
                            )
                        }
                    )
                }
            }
    }

    override suspend fun syncByAccountId(accountId: Long): Result<Unit> = runCatching {
        val source = findByAccountId(accountId)
        val beforeInsertRecords = source.map { ul ->
            UserListRecord(
                serverId = ul.id.userListId,
                accountId = ul.id.accountId,
                createdAt = ul.createdAt,
                name = ul.name,
            )
        }

        val resultIds = userListDao.insertAll(beforeInsertRecords)
        val localLists = userListDao.findUserListWhereIn(accountId, source.map { it.id.userListId })

        val ids = resultIds.mapIndexed { index, resultId ->
            if (resultId == -1L) {
                localLists[index].id
            } else {
                resultId
            }
        }
        resultIds.forEachIndexed { index, l ->
            if (l == -1L) {
                val id = ids[index]
                val updateTarget = beforeInsertRecords[index]
                userListDao.update(updateTarget.copy(id = id))
            }
        }



        ids.forEachIndexed { index, l ->
            userListDao.detachUserIds(l)
            userListDao.attachMemberIds(
                source[index].userIds.map {
                    UserListMemberIdRecord(
                        userId = it.id,
                        userListId = l
                    )
                }
            )
        }

    }

    override suspend fun syncOne(userListId: UserList.Id): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            val source = findOne(userListId)

            upsert(source)
        }

    }

    override fun observeOne(userListId: UserList.Id): Flow<UserListWithMembers?> {
        return userListDao.observeByServerId(userListId.accountId, userListId.userListId)
            .distinctUntilChanged()
            .map { relatedRecord ->
                relatedRecord?.let {
                    UserListWithMembers(
                        userList = relatedRecord.toModel(),
                        members = relatedRecord.members.map { member ->
                            UserListMember(
                                avatarUrl = member.avatarUrl,
                                userId = User.Id(relatedRecord.userList.accountId, member.serverId)
                            )
                        }
                    )
                }
            }.flowOn(Dispatchers.IO)
    }

    private suspend fun upsert(source: UserList) {
        val beforeInsertRecord = UserListRecord(
            serverId = source.id.userListId,
            accountId = source.id.accountId,
            createdAt = source.createdAt,
            name = source.name,
        )
        val resultId = userListDao.insert(
            beforeInsertRecord
        )

        val id = if (resultId == -1L) {
            val exists = userListDao.findByServerId(source.id.accountId, source.id.userListId)!!
            userListDao.update(
                beforeInsertRecord.copy(
                    id = exists.userList.id
                )
            )
            exists.userList.id
        } else {
            resultId
        }
        userListDao.detachUserIds(id)
        userListDao.attachMemberIds(
            source.userIds.map {
                UserListMemberIdRecord(
                    id,
                    userId = it.id
                )
            }
        )
    }
}