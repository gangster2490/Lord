package com.rob.neon

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM contract test of the icon pack: appfilter <-> drawables <-> catalog. */
class PackContractTest {
    private val main = File("src/main")
    private val appfilter = File(main, "res/xml/appfilter.xml").readText()
    private val items = Regex("""component="ComponentInfo\{([^/]+)/([^}]+)\}"\s+drawable="([^"]+)"""").findAll(appfilter).toList()

    @Test fun thirtyIconsAtLeast() {
        val drawables = items.map { it.groupValues[3] }.toSet()
        assertTrue("need >= 30 icons, got ${drawables.size}", drawables.size >= 30)
    }

    private fun be32(b: ByteArray, o: Int) = ((b[o].toInt() and 255) shl 24) or ((b[o + 1].toInt() and 255) shl 16) or ((b[o + 2].toInt() and 255) shl 8) or (b[o + 3].toInt() and 255)

    @Test fun everyDrawableExistsIsRgbaAtLeast512() {
        for (d in items.map { it.groupValues[3] }.toSet()) {
            val f = File(main, "res/drawable-nodpi/$d.png")
            assertTrue("missing $d", f.exists())
            val b = f.readBytes()
            assertEquals("$d not a PNG", 0x89, b[0].toInt() and 255)
            assertTrue("$d too small", be32(b, 16) >= 512 && be32(b, 20) >= 512)
            assertEquals("$d must be RGBA (colour type 6)", 6, b[25].toInt())
        }
    }

    @Test fun noDuplicateComponents() {
        val keys = items.map { it.groupValues[1] + "/" + it.groupValues[2] }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test fun catalogAndAssetCopyAreConsistent() {
        assertEquals(appfilter, File(main, "assets/appfilter.xml").readText())
        val catalogIds = Regex(""""id":\s*"([^"]+)"""").findAll(File(main, "assets/catalog.json").readText()).map { it.groupValues[1] }.toSet()
        assertEquals(catalogIds.map { "ic_$it" }.toSet(), items.map { it.groupValues[3] }.toSet())
    }

    @Test fun wallpapersAre1440x3120() {
        for (k in listOf("home", "lock")) {
            val b = File(main, "assets/wallpapers/$k.jpg").readBytes()
            var i = 2
            var dims: Pair<Int, Int>? = null
            while (i < b.size - 9) {
                if (b[i].toInt() and 255 != 0xFF) { i++; continue }
                val m = b[i + 1].toInt() and 255
                if (m in 0xC0..0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC) {
                    dims = (((b[i + 7].toInt() and 255) shl 8) or (b[i + 8].toInt() and 255)) to
                        (((b[i + 5].toInt() and 255) shl 8) or (b[i + 6].toInt() and 255)); break
                }
                i += 2 + (((b[i + 2].toInt() and 255) shl 8) or (b[i + 3].toInt() and 255))
            }
            assertEquals(1440 to 3120, dims)
        }
    }

    @Test fun noPermissionsInManifest() {
        val m = File(main, "AndroidManifest.xml").readText()
        assertTrue("manifest must not request permissions", !m.contains("<uses-permission"))
    }
}
