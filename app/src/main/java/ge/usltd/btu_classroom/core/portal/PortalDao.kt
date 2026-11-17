package ge.usltd.btu_classroom.core.portal

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PortalDao {
  @Query("SELECT * FROM portal_snapshots WHERE moduleId = :moduleId LIMIT 1")
  fun observe(moduleId: String): Flow<PortalSnapshotEntity?>

  @Query("SELECT * FROM portal_snapshots WHERE moduleId = :moduleId LIMIT 1")
  suspend fun get(moduleId: String): PortalSnapshotEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(snapshot: PortalSnapshotEntity)

  @Query("DELETE FROM portal_snapshots")
  suspend fun clearAll()
}
