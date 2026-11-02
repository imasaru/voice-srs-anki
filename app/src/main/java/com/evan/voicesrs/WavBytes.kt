package com.evan.voicesrs

/** PCM16 mono WAV wrapper. Pure, so the 44-byte header can be unit-tested. */
object WavBytes {
    const val HEADER_BYTES = 44

    fun wrap(pcm: ByteArray, rate: Int = 16000): ByteArray {
        val channels = 1
        val bits = 16
        val dataSize = pcm.size
        val out = ByteArray(dataSize + HEADER_BYTES)
        writeAscii(out, 0, "RIFF")
        writeLe32(out, 4, 36 + dataSize)
        writeAscii(out, 8, "WAVE")
        writeAscii(out, 12, "fmt ")
        writeLe32(out, 16, 16)
        writeLe16(out, 20, 1)
        writeLe16(out, 22, channels)
        writeLe32(out, 24, rate)
        val byteRate = rate * channels * (bits / 8)
        writeLe32(out, 28, byteRate)
        val blockAlign = channels * (bits / 8)
        writeLe16(out, 32, blockAlign)
        writeLe16(out, 34, bits)
        writeAscii(out, 36, "data")
        writeLe32(out, 40, dataSize)
        if (dataSize > 0) System.arraycopy(pcm, 0, out, HEADER_BYTES, dataSize)
        return out
    }

    private fun writeAscii(out: ByteArray, at: Int, text: String) {
        for (i in text.indices) out[at + i] = text[i].code.toByte()
    }

    private fun writeLe16(out: ByteArray, at: Int, value: Int) {
        out[at] = (value and 0xff).toByte()
        out[at + 1] = ((value shr 8) and 0xff).toByte()
    }

    private fun writeLe32(out: ByteArray, at: Int, value: Int) {
        out[at] = (value and 0xff).toByte()
        out[at + 1] = ((value shr 8) and 0xff).toByte()
        out[at + 2] = ((value shr 16) and 0xff).toByte()
        out[at + 3] = ((value shr 24) and 0xff).toByte()
    }
}
