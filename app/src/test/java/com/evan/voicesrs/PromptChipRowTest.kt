package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptChipRowTest {

    @Test
    fun cardBlobDecodesTagsAndEntities() {
        assertEquals("Front: What is Delta?\nBack: A table format",
            PromptChipRow.cardBlob("<b>What is Delta?</b>", "A table <i>format</i>"))
    }

    @Test
    fun cardBlobSkipsBlankBack() {
        assertEquals("Front: Q only", PromptChipRow.cardBlob("Q only", ""))
    }

    @Test
    fun cardBlobBothBlankIsEmpty() {
        assertEquals("", PromptChipRow.cardBlob("", "   "))
    }

    @Test
    fun cardBlobHandlesBrBothCases() {
        assertEquals("Front: line1\nline2\nline3",
            PromptChipRow.cardBlob("line1<br>line2<BR/>line3", ""))
    }

    @Test
    fun cardBlobDecodesEntities() {
        assertEquals("Front: a <b> & c d",
            PromptChipRow.cardBlob("a &lt;b&gt; &amp; c&nbsp;d", ""))
    }

    @Test
    fun chatsBlobTrims() {
        assertEquals("hello", PromptChipRow.chatsBlob("  hello  "))
    }

    @Test
    fun chatsBlobKeepsNewest() {
        assertEquals("ghij", PromptChipRow.chatsBlob("abcdefghij", max = 4))
    }

    @Test
    fun chatsBlobAtLimitUnchanged() {
        assertEquals("abcd", PromptChipRow.chatsBlob("abcd", max = 4))
    }

    @Test
    fun chatsBlobEmptyIsEmpty() {
        assertEquals("", PromptChipRow.chatsBlob(""))
    }
}
