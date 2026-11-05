package com.evan.voicesrs

/** h89: correlation id "s<8hex>-c<card>-l<listen>" for diag lines. Pure; not thread-safe (DrillService drill thread only). */
class CardCid(private val random: () -> Int = { kotlin.random.Random.nextInt() }) {
    var sessionId: String = ""
        private set
    var cardIndex: Int = 0
        private set
    var listenN: Int = 0
        private set

    init { newSession() }

    /** New drill session: fresh 8-hex id, card 0, listen 0. */
    fun newSession() {
        sessionId = "%08x".format(random())
        cardIndex = 0
        listenN = 0
    }

    /** Next card: card index +1, listen count resets to 0. */
    fun nextCard() {
        cardIndex++
        listenN = 0
    }

    /** One more listen attempt on the current card; returns the new cid. */
    fun nextListen(): String {
        listenN++
        return current()
    }

    fun current(): String = format(sessionId, cardIndex, listenN)

    companion object {
        fun format(sessionId: String, cardIndex: Int, listenN: Int): String = "s$sessionId-c$cardIndex-l$listenN"
    }
}
