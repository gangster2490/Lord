package com.rob.aikeyboard

import com.rob.aikeyboard.text.Language
import com.rob.aikeyboard.text.LanguageDetector
import com.rob.aikeyboard.text.QuickFix
import com.rob.aikeyboard.text.SuggestionEngine
import com.rob.aikeyboard.text.UserModel
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickFixTest {
    private val fix = QuickFix(SuggestionEngine({ TestDicts.get(it) }, UserModel()), LanguageDetector { TestDicts.get(it) })
    private fun ru(s: String) = fix.fix(s, Language.RU)

    @Test fun screenshotExample() = assertEquals("Музыка хорошая у вас.", ru("музыка хорошая у вас"))

    @Test fun commasBeforeConjunctions() {
        assertEquals("Я думаю, что ты прав.", ru("я думаю что ты прав"))
        assertEquals("Хотел прийти, но не смог.", ru("хотел прийти но не смог"))
        assertEquals("Не пришёл, потому что болел.", ru("не пришёл потому что болел"))
        assertEquals("Что-то случилось.", ru("что-то случилось"))
    }

    @Test fun questionsSpacingAndTypos() {
        assertEquals("Как дела?", ru("как дела"))
        assertEquals("Привет, как дела? Я дома.", ru("привет ,как дела?я дома"))
        assertEquals("Привет, как дела?", ru("пртвет, как дела"))
    }

    @Test fun germanAndEnglish() {
        assertEquals("Ich glaube, dass er kommt.", fix.fix("ich glaube dass er kommt", Language.DE))
        assertEquals("Wie geht es dir?", fix.fix("wie geht es dir", Language.DE))
        assertEquals("I wanted to come, but I was sick.", fix.fix("i wanted to come but i was sick", Language.EN))
    }

    @Test fun alreadyCorrectTextIsUnchanged() {
        assertEquals("Всё хорошо, спасибо!", ru("Всё хорошо, спасибо!"))
    }
}
