package ge.usltd.btu_classroom

import java.io.File
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/** AndroidX uses '/' for descendant checks; Robolectric files use the host OS separator. */
@Implements(className = "androidx.core.content.FileProvider\$SimplePathStrategy", isInAndroidSdk = false)
class HostFileProviderPathStrategy {
  @Implementation
  protected fun belongsToRoot(filePath: String, rootPath: String): Boolean {
    val file = File(filePath).toPath().normalize()
    val root = File(rootPath).toPath().normalize()
    return file != root && file.startsWith(root)
  }
}
