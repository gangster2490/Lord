package com.rob.aikeyboard

import com.rob.aikeyboard.text.Dictionary
import com.rob.aikeyboard.text.Language
import java.io.File

/** Loads the real bundled dictionaries from the assets folder. */
object TestDicts {
    private val cache = HashMap<Language, Dictionary>()

    private fun assets(): File = listOf("src/main/assets", "app/src/main/assets").map(::File).first { it.isDirectory }

    fun get(language: Language): Dictionary = cache.getOrPut(language) {
        File(assets(), "dict_${language.code}.txt").inputStream().use { Dictionary.load(language, it) }
    }

    fun emojiFile(): File = File(assets(), "emoji.tsv")
}
