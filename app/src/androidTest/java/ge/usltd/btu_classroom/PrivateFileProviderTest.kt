package ge.usltd.btu_classroom

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivateFileProviderTest {
  private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
  private val authority: String get() = "${context.packageName}.files"
  private val fixtureBytes = "%PDF-1.4\n% Sanitized local provider fixture\n%%EOF\n".toByteArray(Charsets.UTF_8)

  @Test
  fun configuredProviderRemainsPrivateAndAllowsExplicitUriGrants() {
    val provider = context.packageManager.resolveContentProvider(authority, 0)
    assertNotNull(provider)
    assertEquals(authority, requireNotNull(provider).authority)
    assertFalse(provider.exported)
    assertTrue(provider.grantUriPermissions)
  }

  @Test
  fun approvedCachedPdfOpensThroughContentResolverWithOriginalBytes() {
    val directory = File(context.cacheDir, "downloads")
    assertTrue(directory.isDirectory || directory.mkdirs())
    val fixture = File(directory, "provider_${UUID.randomUUID()}.pdf")
    try {
      fixture.writeBytes(fixtureBytes)
      val uri = FileProvider.getUriForFile(context, authority, fixture)
      assertEquals("content", uri.scheme)
      assertEquals(authority, uri.authority)
      assertEquals("downloads", uri.pathSegments.first())
      assertEquals("application/pdf", context.contentResolver.getType(uri))
      val actual = requireNotNull(context.contentResolver.openInputStream(uri)).use { it.readBytes() }
      assertArrayEquals(fixtureBytes, actual)
    } finally {
      fixture.delete()
    }
  }

  @Test
  fun outsideCacheFileAndSimilarlyNamedSiblingCannotObtainShareUris() {
    val outside = File(context.cacheDir, "outside_${UUID.randomUUID()}.pdf")
    val siblingDirectory = File(context.cacheDir, "downloads-sibling-${UUID.randomUUID()}")
    val sibling = File(siblingDirectory, "fixture.pdf")
    try {
      outside.writeBytes(fixtureBytes)
      assertTrue(siblingDirectory.mkdir())
      sibling.writeBytes(fixtureBytes)
      assertThrows(IllegalArgumentException::class.java) {
        FileProvider.getUriForFile(context, authority, outside)
      }
      assertThrows(IllegalArgumentException::class.java) {
        FileProvider.getUriForFile(context, authority, sibling)
      }
    } finally {
      outside.delete()
      sibling.delete()
      siblingDirectory.delete()
    }
  }

  @Test
  fun canonicalTraversalAndForgedContentUriCannotEscapeDownloads() {
    val directory = File(context.cacheDir, "downloads")
    assertTrue(directory.isDirectory || directory.mkdirs())
    val outside = File(context.cacheDir, "traversal_${UUID.randomUUID()}.pdf")
    try {
      outside.writeBytes(fixtureBytes)
      val traversalFile = File(directory, "../${outside.name}")
      assertEquals(outside.canonicalFile, traversalFile.canonicalFile)
      assertThrows(IllegalArgumentException::class.java) {
        FileProvider.getUriForFile(context, authority, traversalFile)
      }
      val forgedUri = Uri.Builder()
        .scheme("content")
        .authority(authority)
        .appendPath("downloads")
        .appendPath("../${outside.name}")
        .build()
      assertThrows(SecurityException::class.java) {
        context.contentResolver.openInputStream(forgedUri)?.close()
      }
    } finally {
      outside.delete()
    }
  }
}
