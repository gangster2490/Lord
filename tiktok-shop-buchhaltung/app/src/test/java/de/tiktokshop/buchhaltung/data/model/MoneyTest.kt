package de.tiktokshop.buchhaltung.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {

    @Test
    fun `formats cents with German decimal separator`() {
        assertThat(70261L.formatGerman()).isEqualTo("702,61")
    }

    @Test
    fun `formats thousands with grouping separator`() {
        assertThat(123456L.formatGerman()).isEqualTo("1.234,56")
    }

    @Test
    fun `formats negative amounts with leading minus`() {
        assertThat((-500L).formatGerman()).isEqualTo("-5,00")
    }
}
