package app.discipline

import app.discipline.health.HcType
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HealthPermissionsTest {
    private val manifest: String = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        .map(::File).first { it.exists() }.readText()

    @Test
    fun everyRequestedPermissionIsDeclared() {
        val missing = HcType.allPermissions.filter { "\"$it\"" !in manifest }
        assertTrue("Missing from AndroidManifest.xml: $missing", missing.isEmpty())
    }

    @Test
    fun everyTypeHasReadAndWrite() {
        HcType.entries.forEach {
            assertTrue(it.readPermission.startsWith("android.permission.health.READ_"))
            assertTrue(it.writePermission.startsWith("android.permission.health.WRITE_"))
        }
    }
}
