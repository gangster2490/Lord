package de.spardirekt.agents.pro.ui.components

/**
 * Stable handles for UI automation. Compose exposes these as resource ids
 * because the root sets `testTagsAsResourceId`, so device tests can address
 * controls without matching translated labels or pixel coordinates.
 */
object VppTags {
    const val CREATE_SCREEN = "create_screen"
    const val RESULT_SCREEN = "result_screen"
    const val HISTORY_SCREEN = "history_screen"
    const val SETTINGS_SCREEN = "settings_screen"

    const val GENERATE_BUTTON = "generate_button"
    const val GENERATE_STATUS = "generate_status"
    const val ADD_PHOTO_BUTTON = "add_photo_button"
    const val PHOTO_COUNT = "photo_count"

    const val API_KEY_DIALOG = "api_key_dialog"
    const val API_KEY_FIELD = "api_key_field"
    const val API_KEY_SAVE = "api_key_save"

    const val RESULT_PROMPT = "result_prompt"
    const val RESULT_COPY_PROMPT = "result_copy_prompt"
}
