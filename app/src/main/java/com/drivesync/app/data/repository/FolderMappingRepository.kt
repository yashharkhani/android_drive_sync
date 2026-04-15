package com.drivesync.app.data.repository

import com.drivesync.app.data.local.FolderMapping
import com.drivesync.app.data.local.FolderMappingDao
import kotlinx.coroutines.flow.Flow

class FolderMappingRepository(private val dao: FolderMappingDao) {

    val allMappings: Flow<List<FolderMapping>> = dao.getAllMappings()

    suspend fun getEnabledMappings(): List<FolderMapping> = dao.getEnabledMappings()

    suspend fun getMappingById(id: Long): FolderMapping? = dao.getMappingById(id)

    suspend fun addMapping(mapping: FolderMapping): Long = dao.insertMapping(mapping)

    suspend fun updateMapping(mapping: FolderMapping) = dao.updateMapping(mapping)

    suspend fun deleteMapping(mapping: FolderMapping) = dao.deleteMapping(mapping)

    suspend fun deleteMappingById(id: Long) = dao.deleteMappingById(id)

    suspend fun updateSyncResult(id: Long, syncTime: Long, status: String, fileCount: Int) =
        dao.updateSyncResult(id, syncTime, status, fileCount)

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)
}
