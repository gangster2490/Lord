package de.spardirekt.ugcclean.gen

import com.google.common.truth.Truth.assertThat
import de.spardirekt.ugcclean.model.CopyPack
import de.spardirekt.ugcclean.model.ProjectRecord
import de.spardirekt.ugcclean.model.ProjectStatus
import de.spardirekt.ugcclean.model.SpeechLanguage
import org.junit.Test

class CopyOutputsTest {
    private val record = ProjectRecord(
        id = "copy",
        createdAt = 1,
        updatedAt = 1,
        status = ProjectStatus.READY,
        language = SpeechLanguage.DE,
        photoUris = listOf("frame", "s1", "s2", "s3", "s4", "s5"),
        plan = Fixtures.organizer,
        veoPrompt = PromptComposer.compose(Fixtures.organizer, SpeechLanguage.DE),
        copyPack = CopyPack(
            caption = "Alltag am Schreibtisch. Werbung",
            hashtags = listOf("#Büro", "#Alltag"),
            details = Compliance.details(Fixtures.organizer, SpeechLanguage.DE),
        ),
    )

    @Test
    fun videoPackage_isPromptCaptionHashtagsWithoutDetails() {
        val pack = record.videoPackage()
        assertThat(pack).contains("PRODUCT IDENTITY LOCK:")
        assertThat(pack).contains("Alltag am Schreibtisch. Werbung")
        assertThat(pack).contains("#Büro")
        assertThat(pack).doesNotContain("Produkt:")
        assertThat(pack).doesNotContain("First Frame:")
        assertThat(pack).doesNotContain(record.copyPack!!.details)
    }

    @Test
    fun copyAll_includesDetailsThenPromptCaptionHashtags() {
        val all = record.copyAll()
        assertThat(all).contains("Produkt: white desk organizer")
        assertThat(all).contains("PRODUCT IDENTITY LOCK:")
        assertThat(all).contains("Alltag am Schreibtisch. Werbung")
        assertThat(all).contains("#Büro")
        assertThat(all.indexOf("Produkt:")).isLessThan(all.indexOf("FORMAT:"))
        assertThat(all.indexOf("FORMAT:")).isLessThan(all.indexOf("Alltag am Schreibtisch"))
    }

    @Test
    fun firstFrame_isFirstPhoto_supportTakesNextFour() {
        assertThat(record.firstFrameUri()).isEqualTo("frame")
        assertThat(record.supportUris()).isEqualTo(listOf("s1", "s2", "s3", "s4"))
        assertThat(record.supportUris()).doesNotContain("s5")
    }

    @Test
    fun advancedDetails_staysOffTheVideoPackage() {
        val advanced = record.advancedDetails()
        assertThat(advanced).contains("desk organizer")
        assertThat(advanced).contains("First Frame")
        assertThat(record.videoPackage()).doesNotContain(advanced)
    }
}
