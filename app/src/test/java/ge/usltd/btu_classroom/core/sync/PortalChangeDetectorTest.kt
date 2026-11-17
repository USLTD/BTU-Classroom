package ge.usltd.btu_classroom.core.sync

import ge.usltd.btu_classroom.core.portal.PortalCachedPage
import ge.usltd.btu_classroom.core.portal.PortalContent
import ge.usltd.btu_classroom.core.portal.PortalField
import ge.usltd.btu_classroom.core.schedule.ScheduleEntry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortalChangeDetectorTest {
  @Test
  fun changedClassTimeNotifies() {
    val previous = schedulePage()
    val content = requireNotNull(previous.content)
    val changedEntry = content.schedule.single().copy(startTime = "11:00", endTime = "12:00")
    val current = page(content.copy(schedule = listOf(changedEntry)))

    assertTrue(PortalChangeDetector.hasChangedContent(previous, current))
  }

  @Test
  fun changedClassRoomNotifies() {
    val previous = schedulePage()
    val content = requireNotNull(previous.content)
    val changedEntry = content.schedule.single().copy(room = "B202")
    val current = page(content.copy(schedule = listOf(changedEntry)))

    assertTrue(PortalChangeDetector.hasChangedContent(previous, current))
  }

  @Test
  fun changedAcademicCardFieldNotifies() {
    val previous = page(PortalContent("Academic card", fields = listOf(PortalField("Earned credits", "60"))))
    val current = page(PortalContent("Academic card", fields = listOf(PortalField("Earned credits", "65"))))

    assertTrue(PortalChangeDetector.hasChangedContent(previous, current))
  }

  @Test
  fun metadataChangesDoNotNotify() {
    val previous = schedulePage()
    val current = previous.copy(
      lastSuccessAtMillis = 5_000,
      lastAttemptAtMillis = 6_000,
      status = "OFFLINE",
      message = "Saved information is available while offline.",
    )

    assertFalse(PortalChangeDetector.hasChangedContent(previous, current))
  }

  @Test
  fun firstSynchronizationAndMissingSnapshotsDoNotNotify() {
    val current = schedulePage()

    assertFalse(PortalChangeDetector.hasChangedContent(null, current))
    assertFalse(PortalChangeDetector.hasChangedContent(current, null))
    assertFalse(PortalChangeDetector.hasChangedContent(current.copy(lastSuccessAtMillis = null), current))
    assertFalse(PortalChangeDetector.hasChangedContent(current, current.copy(lastSuccessAtMillis = null)))
  }

  @Test
  fun unreadableCacheDoesNotNotify() {
    val current = schedulePage()
    val unreadable = current.copy(content = null, status = "CACHE_UNREADABLE")

    assertFalse(PortalChangeDetector.hasChangedContent(unreadable, current))
    assertFalse(PortalChangeDetector.hasChangedContent(current, unreadable))
  }

  @Test
  fun universityRemovingPublishedClassesNotifies() {
    val previous = schedulePage()
    val current = page(PortalContent("Weekly schedule")).copy(status = "EMPTY")

    assertTrue(PortalChangeDetector.hasChangedContent(previous, current))
  }

  private fun schedulePage(): PortalCachedPage = page(
    PortalContent(
      title = "Weekly schedule",
      schedule = listOf(
        ScheduleEntry(
          id = "sanitized-class",
          dayOfWeek = 1,
          startTime = "10:00",
          endTime = "11:00",
          courseTitle = "Sanitized course",
          room = "A101",
        ),
      ),
    ),
  )

  private fun page(content: PortalContent): PortalCachedPage = PortalCachedPage(
    content = content,
    lastSuccessAtMillis = 1_000,
    lastAttemptAtMillis = 1_000,
    status = "SUCCESS",
    message = null,
  )
}
