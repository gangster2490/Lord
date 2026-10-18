package com.rob.aikeyboard.keyboard

import android.graphics.Color

/** Colours taken from the reference screenshot: dark grey board, black keys, cyan glyphs and outlines. */
object KeyboardTheme {
    val background = Color.parseColor("#353537")
    val key = Color.parseColor("#141416")
    val keyPressed = Color.parseColor("#0E3A52")
    val functionKey = Color.parseColor("#4A4A4D")
    val functionKeyPressed = Color.parseColor("#5E5E62")
    val outline = Color.parseColor("#1AA6E8")
    val outlineDim = Color.parseColor("#0F5F86")
    val label = Color.parseColor("#2FB8F6")
    val labelOnFunction = Color.parseColor("#D9D9DC")
    val hint = Color.parseColor("#7FA9BF")
    val accent = Color.parseColor("#2FB8F6")
    val popup = Color.parseColor("#202024")
    val popupSelected = Color.parseColor("#1AA6E8")
    val shiftActive = Color.parseColor("#2FB8F6")

    const val KEY_RADIUS_DP = 7f
    const val KEY_GAP_H_DP = 4f
    const val KEY_GAP_V_DP = 9f
    const val BASE_KEY_HEIGHT_DP = 50f
}
