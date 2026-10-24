package com.rob.aikeyboard.keyboard

import android.view.KeyEvent

/**
 * Physical keyboards (e.g. a Bluetooth keyboard case) have Latin legends only. Key codes are
 * position-based, so Russian is typed by mapping each key to the standard ЙЦУКЕН position.
 */
object HardwareKeys {
    private val RU = mapOf(
        KeyEvent.KEYCODE_Q to 'й', KeyEvent.KEYCODE_W to 'ц', KeyEvent.KEYCODE_E to 'у', KeyEvent.KEYCODE_R to 'к',
        KeyEvent.KEYCODE_T to 'е', KeyEvent.KEYCODE_Y to 'н', KeyEvent.KEYCODE_U to 'г', KeyEvent.KEYCODE_I to 'ш',
        KeyEvent.KEYCODE_O to 'щ', KeyEvent.KEYCODE_P to 'з', KeyEvent.KEYCODE_LEFT_BRACKET to 'х',
        KeyEvent.KEYCODE_RIGHT_BRACKET to 'ъ',
        KeyEvent.KEYCODE_A to 'ф', KeyEvent.KEYCODE_S to 'ы', KeyEvent.KEYCODE_D to 'в', KeyEvent.KEYCODE_F to 'а',
        KeyEvent.KEYCODE_G to 'п', KeyEvent.KEYCODE_H to 'р', KeyEvent.KEYCODE_J to 'о', KeyEvent.KEYCODE_K to 'л',
        KeyEvent.KEYCODE_L to 'д', KeyEvent.KEYCODE_SEMICOLON to 'ж', KeyEvent.KEYCODE_APOSTROPHE to 'э',
        KeyEvent.KEYCODE_Z to 'я', KeyEvent.KEYCODE_X to 'ч', KeyEvent.KEYCODE_C to 'с', KeyEvent.KEYCODE_V to 'м',
        KeyEvent.KEYCODE_B to 'и', KeyEvent.KEYCODE_N to 'т', KeyEvent.KEYCODE_M to 'ь', KeyEvent.KEYCODE_COMMA to 'б',
        KeyEvent.KEYCODE_PERIOD to 'ю', KeyEvent.KEYCODE_GRAVE to 'ё',
    )

    /** On the Russian layout "/" types "." and Shift+"/" types ",". */
    private val RU_SLASH = '.' to ','

    /** Russian character for a physical key, or null to use the system layout. */
    fun russian(keyCode: Int, shift: Boolean): Char? {
        if (keyCode == KeyEvent.KEYCODE_SLASH) return if (shift) RU_SLASH.second else RU_SLASH.first
        val c = RU[keyCode] ?: return null
        return if (shift) c.uppercaseChar() else c
    }
}
