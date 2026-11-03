package com.evan.voicesrs

import org.junit.Assert.assertEquals
import org.junit.Test

class H123Test {
    @Test fun `each choice letter starts its own sentence`() {
        assertEquals(listOf("Which fixes the skew?", "A. Use repartition(8)", "B. Use coalesce(1)"), SentenceSplit.sentences("Which fixes the skew? A. Use repartition(8) B. Use coalesce(1)"))
    }

    @Test fun `text without choices is unchanged`() {
        assertEquals(listOf("I chose plan B.", "Then it worked."), SentenceSplit.sentences("I chose plan B. Then it worked."))
    }

    @Test fun `newline separated choices each start a sentence`() {
        assertEquals(listOf("Pick one.", "A. Delta", "B. Parquet", "C. CSV"), SentenceSplit.sentences("Pick one.\nA. Delta\nB. Parquet\nC. CSV"))
    }
}
