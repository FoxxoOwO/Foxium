package com.chrogecko.browser.core.database.repository

import com.chrogecko.browser.core.database.dao.TabDao
import com.chrogecko.browser.core.database.dao.TabGroupDao
import com.chrogecko.browser.core.database.entity.TabEntity
import com.chrogecko.browser.core.database.entity.TabGroupEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class TabRepository @Inject constructor(
    private val tabDao: TabDao,
    private val groupDao: TabGroupDao,
) {

    fun observeTabs(isPrivate: Boolean): Flow<List<TabEntity>> = tabDao.observeByProfile(isPrivate)

    fun observeAllTabs(): Flow<List<TabEntity>> = tabDao.observeAll()

    fun observeGroups(): Flow<List<TabGroupEntity>> = groupDao.observeAll()

    fun recentTabs(limit: Int = 50): Flow<List<TabEntity>> = tabDao.observeRecent(limit)

    fun observeGroup(groupId: String): Flow<List<TabEntity>> = tabDao.observeGroup(groupId)

    suspend fun addTab(
        url: String? = null,
        isPrivate: Boolean = false,
        groupId: String? = null,
    ): TabEntity {
        val tab = TabEntity(
            id = UUID.randomUUID().toString(),
            url = url,
            groupId = groupId,
            isPrivate = isPrivate,
            sortOrder = tabDao.maxSortOrder(isPrivate) + 1,
        )
        tabDao.upsert(tab)
        return tab
    }

    suspend fun update(tab: TabEntity) = tabDao.upsert(tab)

    suspend fun saveTab(tab: TabEntity) = tabDao.upsert(tab)

    suspend fun nextSortOrder(isPrivate: Boolean): Int = tabDao.maxSortOrder(isPrivate) + 1

    suspend fun updateContent(tabId: String, url: String?, title: String) =
        tabDao.updateContent(tabId, url, title)

    suspend fun updateSessionState(tabId: String, state: String) =
        tabDao.updateSessionState(tabId, state)

    suspend fun touch(tabId: String) = tabDao.touch(tabId, System.currentTimeMillis())

    suspend fun normalTabs(): List<TabEntity> = tabDao.normalTabs()

    suspend fun groupsOnce(): List<TabGroupEntity> = groupDao.allGroups()

    suspend fun clearPrivateTabs() = tabDao.clearPrivate()

    suspend fun saveGroup(group: TabGroupEntity) = groupDao.upsert(group)

    suspend fun updateGroup(groupId: String, title: String, color: Int) =
        groupDao.update(groupId, title, color)

    suspend fun closeTab(tabId: String) = tabDao.deleteById(tabId)

    suspend fun closeAllPrivate() = tabDao.deleteByProfile(true)

    suspend fun createGroup(title: String, color: Int, tabIds: List<String>): TabGroupEntity {
        val group = TabGroupEntity(id = UUID.randomUUID().toString(), title = title, color = color)
        groupDao.upsert(group)
        tabIds.forEach { tabDao.assignGroup(it, group.id) }
        return group
    }

    suspend fun assignToGroup(tabId: String, groupId: String?) = tabDao.assignGroup(tabId, groupId)

    suspend fun reorderTabs(ids: List<String>) = tabDao.reorderTabs(ids)

    suspend fun replaceNormalTabs(tabs: List<TabEntity>) = tabDao.replaceNormalTabs(tabs)

    suspend fun deleteGroup(groupId: String) = groupDao.deleteById(groupId)
}
