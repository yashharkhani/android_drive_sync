package com.drivesync.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderMappingDao {

    @Query("SELECT * FROM folder_mappings ORDER BY createdAt DESC")
    fun getAllMappings(): Flow<List<FolderMapping>>

    @Query("SELECT * FROM folder_mappings WHERE isEnabled = 1 ORDER BY createdAt DESC")
    suspend fun getEnabledMappings(): List<FolderMapping>

    @Query("SELECT * FROM folder_mappings WHERE id = :id")
    suspend fun getMappingById(id: Long): FolderMapping?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMapping(mapping: FolderMapping): Long

    @Update
    suspend fun updateMapping(mapping: FolderMapping)

    @Delete
    suspend fun deleteMapping(mapping: FolderMapping)

    @Query("DELETE FROM folder_mappings WHERE id = :id")
    suspend fun deleteMappingById(id: Long)

    @Query("""
        UPDATE folder_mappings
        SET lastSyncTime = :syncTime, lastSyncStatus = :status, lastSyncFileCount = :fileCount
        WHERE id = :id
    """)
    suspend fun updateSyncResult(id: Long, syncTime: Long, status: String, fileCount: Int)

    @Query("UPDATE folder_mappings SET isEnabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)
}
