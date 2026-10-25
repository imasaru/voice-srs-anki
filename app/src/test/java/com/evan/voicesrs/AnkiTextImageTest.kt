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
