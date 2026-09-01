package de.spardirekt.agents.pro.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProjectThumbnailTest {

    @Test
    fun blankIsNull() {
        assertNull(ProjectThumbnail.model(""))
        assertNull(ProjectThumbnail.model("   "))
    }

    @Test
    fun absoluteFilePathBecomesFile() {
        val model = ProjectThumbnail.model("/data/user/0/app/files/projects/p/img.jpg")
        assertTrue(model is File)
        assertEquals("/data/user/0/app/files/projects/p/img.jpg", (model as File).path)
    }
}
