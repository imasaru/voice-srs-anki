package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class WavBytesTest {

    @Test
    fun `header describes 16 kHz mono pcm16 and the data size`() {
        val pcm = ByteArray(100) { it.toByte() }
        val wav = WavBytes.wrap(pcm)
        assertEquals("RIFF", ascii(wav, 0, 4))
        assertEquals("WAVE", ascii(wav, 8, 4))
        assertEquals("fmt ", ascii(wav, 12, 4))
        assertEquals("data", ascii(wav, 36, 4))
        assertEquals(16000, le32(wav, 24))
        assertEquals(32000, le32(wav, 28))
        assertEquals(2, le16(wav, 32))
        assertEquals(16, le16(wav, 34))
        assertEquals(pcm.size, le32(wav, 40))
        assertEquals(pcm.size + 44, wav.size)
    }

    @Test
    fun `empty pcm is a 44 byte header`() {
        val wav = WavBytes.wrap(ByteArray(0))
        assertEquals(44, wav.size)
        assertEquals(0, le32(wav, 40))
        assertEquals("RIFF", ascii(wav, 0, 4))
        assertEquals("data", ascii(wav, 36, 4))
    }

    private fun ascii(bytes: ByteArray, at: Int, n: Int): String =
        bytes.copyOfRange(at, at + n).toString(Charsets.US_ASCII)

    private fun le16(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xff) or ((bytes[at + 1].toInt() and 0xff) shl 8)

    private fun le32(bytes: ByteArray, at: Int): Int =
        (bytes[at].toInt() and 0xff) or
            ((bytes[at + 1].toInt() and 0xff) shl 8) or
            ((bytes[at + 2].toInt() and 0xff) shl 16) or
            ((bytes[at + 3].toInt() and 0xff) shl 24)
}
