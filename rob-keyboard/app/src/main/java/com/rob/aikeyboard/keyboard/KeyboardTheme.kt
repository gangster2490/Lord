package com.rob.aikeyboard.keyboard

import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One keyboard design: colours for the keys (Canvas) and for the toolbar and panels (Compose). */
data class ThemePalette(
    val id: String,
    val name: String,
    val subtitle: String,
    val backgroundTop: Int,
    val backgroundBottom: Int,
    val key: Int,
    val keyPressed: Int,
    val functionKey: Int,
    val functionKeyPressed: Int,
    val outline: Int,
    val outlineDim: Int,
    val label: Int,
    val labelOnFunction: Int,
    val hint: Int,
    /** Toolbar icons, active chips, highlights. */
    val accent: Int,
    /** Text/icons drawn on top of [accent]. */
    val onAccent: Int,
    /** Enter and active Shift are filled with [accent] (most new designs); off = classic grey keys. */
    val accentKeys: Boolean,
    val bar: Int,
    val chip: Int,
    val text: Int,
    val textDim: Int,
    val popup: Int,
    val light: Boolean = false,
)

private fun c(hex: String) = Color.parseColor(hex)

object KeyboardTheme {
    val all: List<ThemePalette> = listOf(
        ThemePalette(
            "rob_cyan", "ROB Cyan", "Исходный дизайн: тёмно-серый, голубые символы",
            c("#353537"), c("#353537"), c("#141416"), c("#0E3A52"), c("#4A4A4D"), c("#5E5E62"),
            c("#1AA6E8"), c("#0F5F86"), c("#2FB8F6"), c("#D9D9DC"), c("#7FA9BF"),
            c("#2FB8F6"), c("#141416"), false, c("#3A3A3D"), c("#47474B"), c("#E6E6E8"), c("#9A9AA0"), c("#202024"),
        ),
        ThemePalette(
            "modern_dark", "Modern Dark", "Современный, чистый, удобный",
            c("#141922"), c("#0C0F15"), c("#1E2430"), c("#2A3446"), c("#1E2430"), c("#2A3446"),
            c("#33405A"), c("#252E3E"), c("#EEF2F8"), c("#EEF2F8"), c("#7D8AA3"),
            c("#2E8BFF"), c("#FFFFFF"), true, c("#121620"), c("#232A37"), c("#EEF2F8"), c("#8C97AB"), c("#1A1F2A"),
        ),
        ThemePalette(
            "neon_purple", "Neon Purple", "Стильный фиолетовый акцент",
            c("#1A0B2E"), c("#0D0619"), c("#211238"), c("#3A1D63"), c("#211238"), c("#3A1D63"),
            c("#A855F7"), c("#5B2E8C"), c("#F1E6FF"), c("#F1E6FF"), c("#B794E6"),
            c("#B05CFF"), c("#FFFFFF"), true, c("#150926"), c("#2A1747"), c("#F1E6FF"), c("#A993C9"), c("#1D0F33"),
        ),
        ThemePalette(
            "nature_green", "Nature Green", "Свежий зелёный стиль",
            c("#0A1E14"), c("#05110B"), c("#0F2419"), c("#174D30"), c("#0F2419"), c("#174D30"),
            c("#22C55E"), c("#14663A"), c("#E3FFEE"), c("#E3FFEE"), c("#86D9A6"),
            c("#4ADE80"), c("#05110B"), true, c("#08180F"), c("#143022"), c("#E3FFEE"), c("#8FBFA2"), c("#0D2116"),
        ),
        ThemePalette(
            "sunset_orange", "Sunset Orange", "Тёплый оранжевый дизайн",
            c("#1E120A"), c("#0F0905"), c("#22160C"), c("#4A2C12"), c("#22160C"), c("#4A2C12"),
            c("#F59E0B"), c("#7A4A12"), c("#FFE9CF"), c("#FFE9CF"), c("#E0B07A"),
            c("#FB923C"), c("#1A0F06"), true, c("#170E07"), c("#2E1D10"), c("#FFE9CF"), c("#C9A27E"), c("#20140B"),
        ),
        ThemePalette(
            "cute_pink", "Cute Pink", "Нежный розовый вариант",
            c("#FDE7EF"), c("#F9D3E0"), c("#FFFFFF"), c("#FBD0DF"), c("#FBD7E4"), c("#F7BCD1"),
            c("#F6B8CD"), c("#F9D3E0"), c("#3A2730"), c("#7A3E57"), c("#B9879A"),
            c("#F06292"), c("#FFFFFF"), true, c("#FCDCE7"), c("#FFFFFF"), c("#3A2730"), c("#9A7383"), c("#FFF5F8"), light = true,
        ),
        ThemePalette(
            "light_clean", "Light Clean", "Светлый и чистый дизайн",
            c("#EEF3FA"), c("#E2EAF5"), c("#FFFFFF"), c("#D6E4F7"), c("#DCE5F2"), c("#C9D7EB"),
            c("#D5DEEA"), c("#E2EAF5"), c("#1F2937"), c("#1F2937"), c("#8A97A8"),
            c("#2F80ED"), c("#FFFFFF"), true, c("#E6EDF7"), c("#FFFFFF"), c("#1F2937"), c("#6B7685"), c("#FFFFFF"), light = true,
        ),
        ThemePalette(
            "red_black", "Red & Black", "Контрастный красный стиль",
            c("#120708"), c("#070405"), c("#161112"), c("#4A1015"), c("#161112"), c("#4A1015"),
            c("#E11D2E"), c("#6E1018"), c("#FFFFFF"), c("#FFFFFF"), c("#C98088"),
            c("#E11D2E"), c("#FFFFFF"), true, c("#0E0607"), c("#24161A"), c("#FFFFFF"), c("#B39A9D"), c("#170B0D"),
        ),
        ThemePalette(
            "ice_blue", "Ice Blue", "Холодный голубой стиль",
            c("#0A1C2B"), c("#050F18"), c("#0D2233"), c("#13405F"), c("#0D2233"), c("#13405F"),
            c("#38BDF8"), c("#14587A"), c("#E2F6FF"), c("#E2F6FF"), c("#86C9E6"),
            c("#38D5FF"), c("#04121C"), true, c("#081826"), c("#123047"), c("#E2F6FF"), c("#8DB4C8"), c("#0B1F2E"),
        ),
        ThemePalette(
            "minimal", "Minimal", "Минималистичный и плоский",
            c("#1C1C1E"), c("#1C1C1E"), c("#2C2C2E"), c("#48484A"), c("#3A3A3C"), c("#545458"),
            c("#2C2C2E"), c("#2C2C2E"), c("#F2F2F7"), c("#F2F2F7"), c("#8E8E93"),
            c("#D1D1D6"), c("#1C1C1E"), false, c("#1C1C1E"), c("#2C2C2E"), c("#F2F2F7"), c("#8E8E93"), c("#2C2C2E"),
        ),
        ThemePalette(
            "premium_gold", "Premium Gold", "Премиальный золотой стиль",
            c("#1A140A"), c("#0B0905"), c("#17130B"), c("#4A3A14"), c("#17130B"), c("#4A3A14"),
            c("#D4A73A"), c("#6E5620"), c("#F5D88A"), c("#F5D88A"), c("#B8995A"),
            c("#E8B84A"), c("#1A1307"), true, c("#120E07"), c("#2A2212"), c("#F5D88A"), c("#B3A07A"), c("#1C160C"),
        ),
    )

    /** Observable so the Compose toolbar and panels recolour together with the keys. */
    var current: ThemePalette by mutableStateOf(all.first())

    fun byId(id: String?): ThemePalette = all.firstOrNull { it.id == id } ?: all.first()

    val background get() = current.backgroundTop
    val key get() = current.key
    val keyPressed get() = current.keyPressed
    val functionKey get() = current.functionKey
    val functionKeyPressed get() = current.functionKeyPressed
    val outline get() = current.outline
    val outlineDim get() = current.outlineDim
    val label get() = current.label
    val labelOnFunction get() = current.labelOnFunction
    val hint get() = current.hint
    val accent get() = current.accent
    val popup get() = current.popup
    val popupSelected get() = current.accent
    val shiftActive get() = current.accent

    const val KEY_RADIUS_DP = 7f
    const val KEY_GAP_H_DP = 4f
    const val KEY_GAP_V_DP = 9f
    const val BASE_KEY_HEIGHT_DP = 50f
}
