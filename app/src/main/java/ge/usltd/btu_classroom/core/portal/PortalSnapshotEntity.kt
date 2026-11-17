package ge.usltd.btu_classroom.core.portal

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "portal_snapshots")
data class PortalSnapshotEntity(
  @PrimaryKey val moduleId: String,
  val payloadJson: String?,
  val lastSuccessAtMillis: Long?,
  val lastAttemptAtMillis: Long,
  val status: String,
  val message: String?,
)
