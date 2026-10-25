package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiTextImageTest {

    @Test
    fun `image refs read a double quoted src`() {
        assertEquals(listOf("a.png"), AnkiText.imageRefs("""<img src="a.png">"""))
    }

    @Test
    fun `image refs read a single quoted src`() {
        assertEquals(listOf("a.png"), AnkiText.imageRefs("""<img src='a.png'>"""))
    }

    @Test
    fun `image refs read an unquoted src`() {
        assertEquals(listOf("a.png"), AnkiText.imageRefs("<img src=a.png>"))
    }

    @Test
    fun `image refs keep order and drop duplicates`() {
        val html = """<img src="b.png"><img src="a.png"><img src="b.png">"""
        assertEquals(listOf("b.png", "a.png"), AnkiText.imageRefs(html))
    }

    @Test
    fun `image refs are case insensitive for tag and attr`() {
        assertEquals(listOf("a.png"), AnkiText.imageRefs("""<IMG SRC="a.png">"""))
    }

    @Test
    fun `image refs decode ampersand and percent encoded names`() {
        assertEquals(listOf("my card.png"), AnkiText.imageRefs("""<img src="my%20card.png">"""))
        assertEquals(listOf("a&b.png"), AnkiText.imageRefs("""<img src="a&amp;b.png">"""))
    }

    @Test
    fun `image refs decode percent-encoded UTF-8 filenames`() {
        // Japanese: %E7%8A%AC = 犬 (3 UTF-8 bytes decoded as a unit).
        assertEquals(listOf("犬.jpg"), AnkiText.imageRefs("""<img src="%E7%8A%AC.jpg">"""))
        // Mixed ASCII plus encoded Japanese.
        assertEquals(listOf("a犬.png"), AnkiText.imageRefs("""<img src="a%E7%8A%AC.png">"""))
        assertEquals(listOf("犬 a.png"), AnkiText.imageRefs("""<img src="%E7%8A%AC%20a.png">"""))
        // Two consecutive multi-byte characters in one run.
        assertEquals(listOf("犬字.png"), AnkiText.imageRefs("""<img src="%E7%8A%AC%E5%AD%97.png">"""))
    }

    @Test
    fun `image refs keep a literal plus and leave malformed percent sequences unchanged`() {
        // '+' must stay literal (not become a space).
        assertEquals(listOf("a+b.jpg"), AnkiText.imageRefs("""<img src="a+b.jpg">"""))
        // Malformed '%' with no hex digits is kept as-is.
        assertEquals(listOf("a%b.jpg"), AnkiText.imageRefs("""<img src="a%b.jpg">"""))
        // Incomplete sequence at the end is kept as-is.
        assertEquals(listOf("a%E7.jpg"), AnkiText.imageRefs("""<img src="a%E7.jpg">"""))
    }

    @Test
    fun `image refs skip data and http urls and empty src`() {
        val html =
            """<img src="data:image/png;base64,AAAA"><img src="https://x.test/a.png">""" +
                """<img src="http://x.test/b.png"><img src="">"""
        assertEquals(emptyList<String>(), AnkiText.imageRefs(html))
    }

    @Test
    fun `image refs ignore an image inside script`() {
        val html = """<script><img src="x.png"></script><img src="y.png">"""
        assertEquals(listOf("y.png"), AnkiText.imageRefs(html))
    }

    @Test
    fun `has image is false for plain text`() {
        assertFalse(AnkiText.hasImage("just text"))
        assertTrue(AnkiText.hasImage("<img src=\"a.png\">"))
    }

    @Test
    fun `image occlusion matches the container id`() {
        assertTrue(AnkiText.isImageOcclusion("""<div id="image-occlusion-container"></div>"""))
        assertTrue(AnkiText.isImageOcclusion("""<div ID='image-occlusion-container'></div>"""))
    }

    @Test
    fun `image occlusion matches a class and a cloze marker but not a plain image`() {
        assertTrue(AnkiText.isImageOcclusion("""<div class="note image-occlusion">x</div>"""))
        assertTrue(AnkiText.isImageOcclusion("""{{c1::image-occlusion}}"""))
        assertFalse(AnkiText.isImageOcclusion("""<img src="a.png">"""))
    }

    @Test
    fun `needs screen is true for an image with very little spoken text`() {
        assertTrue(AnkiText.needsScreen("""<img src="a.png">Sign?"""))
    }

    @Test
    fun `needs screen is false when the spoken text is long`() {
        val html = """<img src="a.png"><p>The lakehouse stores tables in Parquet for efficient reads.</p>"""
        assertFalse(AnkiText.needsScreen(html))
    }

    @Test
    fun `needs screen is false without an image`() {
        assertFalse(AnkiText.needsScreen("Sign?"))
    }

    @Test
    fun `to display html still strips an image card`() {
        val out = AnkiText.toDisplayHtml("""<img src="a.png">Sign?""")
        assertEquals("Sign?", out)
        assertFalse(out.contains("img"))
        assertFalse(out.contains("a.png"))
    }
}
