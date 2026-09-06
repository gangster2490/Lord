package de.spardirekt.clipforge.data.image

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageEncoderTest {

    @Test
    fun keepsSmallImages() {
        assertThat(ImageEncoder.maxEdgeAfterScale(800, 600)).isEqualTo(800 to 600)
    }

    @Test
    fun scalesLongestEdgeTo1600() {
        val (w, h) = ImageEncoder.maxEdgeAfterScale(3200, 1800)
        assertThat(w).isEqualTo(1600)
        assertThat(h).isEqualTo(900)
    }

    @Test
    fun portraitScalesHeight() {
        val (w, h) = ImageEncoder.maxEdgeAfterScale(1080, 2160, 1600)
        assertThat(h).isEqualTo(1600)
        assertThat(w).isEqualTo(800)
    }

    @Test
    fun localFileOnlyForFileScheme() {
        assertThat(ImageEncoder.localFile("file", "/tmp/a.jpg")?.path).isEqualTo("/tmp/a.jpg")
        assertThat(ImageEncoder.localFile(null, "/tmp/a.jpg")?.path).isEqualTo("/tmp/a.jpg")
        assertThat(ImageEncoder.localFile("content", "/tmp/a.jpg")).isNull()
        assertThat(ImageEncoder.localFile("file", null)).isNull()
    }
}
