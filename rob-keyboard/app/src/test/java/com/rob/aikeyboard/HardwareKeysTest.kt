package com.rob.aikeyboard

import android.view.KeyEvent
import com.rob.aikeyboard.keyboard.HardwareKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HardwareKeysTest {
    @Test fun physicalKeysMapToRussianPositions() {
        val word = listOf(KeyEvent.KEYCODE_G, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_B, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_T, KeyEvent.KEYCODE_N)
            .map { HardwareKeys.russian(it, false) }.joinToString("")
        assertEquals("привет", word)
        assertEquals('Й', HardwareKeys.russian(KeyEvent.KEYCODE_Q, true))
        assertEquals('ж', HardwareKeys.russian(KeyEvent.KEYCODE_SEMICOLON, false))
        assertEquals('ё', HardwareKeys.russian(KeyEvent.KEYCODE_GRAVE, false))
        assertEquals(',', HardwareKeys.russian(KeyEvent.KEYCODE_SLASH, true))
        assertNull(HardwareKeys.russian(KeyEvent.KEYCODE_1, false))
    }
}
