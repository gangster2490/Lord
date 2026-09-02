package de.spardirekt.recipeveo.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Locks the current Veo Prompt Pro look. If this test fails, the change
 * restyled the product — revert unless a new visual baseline was requested.
 */
class VisualBaselineTokensTest {

    @Test
    fun pageIsLightLavenderNotBlack() {
        assertHex(0xFFF7F5FB, VppColors.backgroundLight)
        assertHex(0xFFEAE4F8, VppColors.backgroundGlow)
        assertHex(0xFF1A1F36, VppColors.textDark)
    }

    @Test
    fun cardsAreNavyWithSoftOutline() {
        assertHex(0xFF141B3A, VppColors.cardNavy)
        assertHex(0xFF1B2448, VppColors.cardNavySecondary)
        assertHex(0xFF0E1430, VppColors.cardInset)
        assertHex(0xFFD7D2E8, VppColors.outlineSoft)
        assertHex(0xFFF4F6FF, VppColors.textLight)
        assertHex(0xFF9AA3C7, VppColors.textMuted)
    }

    @Test
    fun accentsAreVioletToBlueGradient() {
        assertHex(0xFF7C5CFF, VppColors.accentPurple)
        assertHex(0xFF3D8BFF, VppColors.accentBlue)
        assertHex(0xFFE8E0FF, VppColors.chipSelected)
    }

    @Test
    fun bottomNavIsDarkNavyWithLilacPill() {
        assertHex(0xFF0F1738, VppColors.bottomBar)
        assertHex(0xFFE6DEFF, VppColors.navPill)
    }

    @Test
    fun radiiAndSpacingStayPremiumRounded() {
        assertEquals(26.dp, VppShapes.cardRadius)
        assertEquals(20.dp, VppShapes.buttonRadius)
        assertEquals(16.dp, VppShapes.thumbRadius)
        assertEquals(18.dp, VppShapes.insetRadius)
        assertEquals(18.dp, VppShapes.navPillRadius)
        assertEquals(22.dp, VppDimens.screenPadding)
        assertEquals(22.dp, VppDimens.cardPadding)
        assertEquals(16.dp, VppDimens.sectionGap)
    }

    @Test
    fun typeScaleStaysBaseline() {
        assertEquals(28, VppType.screenTitle.fontSize.value.toInt())
        assertEquals(20, VppType.sectionTitle.fontSize.value.toInt())
        assertEquals(16, VppType.cardTitle.fontSize.value.toInt())
        assertEquals(14, VppType.body.fontSize.value.toInt())
        assertEquals(16, VppType.buttonLabel.fontSize.value.toInt())
    }

    private fun assertHex(expectedArgb: Long, actual: Color) {
        assertEquals(
            "0x%08X".format(expectedArgb),
            "0x%08X".format(actual.toArgb().toLong() and 0xFFFFFFFFL),
        )
    }
}
