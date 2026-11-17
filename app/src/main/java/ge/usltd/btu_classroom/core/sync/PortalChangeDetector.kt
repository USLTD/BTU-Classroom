package ge.usltd.btu_classroom.core.sync

import ge.usltd.btu_classroom.core.portal.PortalCachedPage

internal object PortalChangeDetector {
  fun hasChangedContent(previous: PortalCachedPage?, current: PortalCachedPage?): Boolean =
    previous?.lastSuccessAtMillis != null && current?.lastSuccessAtMillis != null &&
      previous.content != null && current.content != null && previous.content != current.content
}
