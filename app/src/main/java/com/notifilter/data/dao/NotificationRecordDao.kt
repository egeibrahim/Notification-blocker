package com.notifilter.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.notifilter.data.entity.AppNotificationStats
import com.notifilter.data.entity.NotificationRecord
import com.notifilter.data.entity.PackageChannelRow
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationRecordDao {

    @Query(
        """
        SELECT packageName, 
               MAX(appName) as appName,
               COUNT(*) as totalCount,
               SUM(CASE WHEN isBlocked = 1 THEN 1 ELSE 0 END) as blockedCount
        FROM notification_record 
        WHERE timestamp > :since 
        GROUP BY packageName 
        ORDER BY totalCount DESC
        """
    )
    fun getNotificationStatsSince(since: Long): Flow<List<AppNotificationStats>>

    @Query(
        "SELECT * FROM notification_record WHERE packageName = :packageName AND timestamp > :since ORDER BY timestamp DESC"
    )
    fun getByPackageSince(packageName: String, since: Long): Flow<List<NotificationRecord>>

    @Query(
        "SELECT DISTINCT packageName, channelId FROM notification_record WHERE timestamp > :since AND channelId IS NOT NULL"
    )
    fun getPackageChannelPairsSince(since: Long): Flow<List<PackageChannelRow>>

    @Query("SELECT * FROM notification_record WHERE timestamp > :since ORDER BY timestamp DESC")
    fun getSince(since: Long): Flow<List<NotificationRecord>>

    @Query("SELECT * FROM notification_record WHERE timestamp > :since ORDER BY timestamp DESC")
    suspend fun getSinceOnce(since: Long): List<NotificationRecord>

    @Query("DELETE FROM notification_record WHERE timestamp > :since")
    suspend fun deleteSince(since: Long): Int

    @Query("SELECT * FROM notification_record ORDER BY timestamp DESC")
    fun getAll(): Flow<List<NotificationRecord>>

    @Query("SELECT * FROM notification_record WHERE id = :id")
    suspend fun getById(id: Long): NotificationRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: NotificationRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<NotificationRecord>)

    @Delete
    suspend fun delete(record: NotificationRecord)

    @Query("DELETE FROM notification_record WHERE timestamp < :cutoffTimestamp")
    suspend fun deleteOlderThan(cutoffTimestamp: Long): Int

    /**
     * Aynı packageName, content, channelId ve 2 sn içindeki timestamp'e sahip
     * çift kayıtlardan id'si büyük olanları siler (en eskisini tutar).
     */
    @Query("""
        DELETE FROM notification_record WHERE id IN (
            SELECT n1.id FROM notification_record n1
            INNER JOIN notification_record n2
                ON n1.packageName = n2.packageName
                AND n1.content = n2.content
                AND (n1.channelId IS n2.channelId OR (n1.channelId IS NOT NULL AND n2.channelId IS NOT NULL AND n1.channelId = n2.channelId))
                AND ABS(n1.timestamp - n2.timestamp) < 2000
                AND n1.id > n2.id
        )
    """)
    suspend fun deleteDuplicateRecords(): Int
}
