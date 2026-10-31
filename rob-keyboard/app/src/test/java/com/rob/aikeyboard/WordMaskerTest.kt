package com.rob.aikeyboard

import com.rob.aikeyboard.text.WordMasker
import org.junit.Assert.assertEquals
import org.junit.Test

class WordMaskerTest {
    private val stems = WordMasker.parse(WordMasker.DEFAULT_STEMS)

    @Test fun listedWordsGetAHiddenCharacter() {
        assertEquals("ц​ена", WordMasker.mask("цена", stems))
        assertEquals("К​упить", WordMasker.mask("Купить", stems))
        assertEquals("P​reis", WordMasker.mask("Preis", stems))
    }

    @Test fun otherWordsAndShortWordsStay() {
        assertEquals("привет", WordMasker.mask("привет", stems))
        assertEquals("до", WordMasker.mask("до", stems))
    }

    @Test fun alreadyMaskedIsNotMaskedTwice() {
        assertEquals("ц​ена", WordMasker.mask("ц​ена", stems))
    }

    @Test fun visibleTextIsUnchanged() {
        assertEquals("доставка", WordMasker.mask("доставка", stems).replace("​", ""))
    }

    @Test fun parsingDropsShortAndEmptyEntries() {
        assertEquals(listOf("цен", "куп"), WordMasker.parse(" Цен, ,ку; куп\n"))
    }
}
