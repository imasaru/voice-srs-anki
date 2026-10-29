package com.evan.voicesrs

import java.io.File

class OwnWordsWiringTest {

    private fun drillServiceText(): String {
        val f = listOf(
            File("src/main/java/com/evan/voicesrs/DrillService.kt"),
            File("app/src/main/java/com/evan/voicesrs/DrillService.kt")
        ).firstOrNull { it.isFile }
        requireNotNull(f) { "DrillService.kt not found" }
        return f.readText()
    }

    private fun containsAll(text: String, substrings: List<String>) {
        for (s in substrings) {
            if (!text.contains(s)) {
                throw AssertionError("Missing string in DrillService.kt: $s")
            }
        }
    }

    /** Extract the body text of a top-level/inner `fun` from its declaration line. */
    private fun funBody(text: String, declStartsWith: String): String {
        val lines = text.lines()
        var start = -1
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith(declStartsWith)) {
                start = i
                break
            }
        }
        require(start >= 0) { "Declaration not found: $declStartsWith" }
        val sb = StringBuilder()
        var depth = 0
        var sawOpen = false
        for (i in start until lines.size) {
            val line = lines[i]
            sb.append(line).append('\n')
            for (c in line) {
                when (c) {
                    '{' -> { depth++; sawOpen = true }
                    '}' -> depth--
                }
            }
            if (sawOpen && depth == 0) break
        }
        return sb.toString()
    }

    private val text by lazy { drillServiceText() }

    private fun requireInFun(declStartsWith: String, mustContain: List<String>, mustNotContain: List<String>) {
        val body = funBody(text, declStartsWith)
        for (s in mustContain) {
            if (!body.contains(s)) throw AssertionError("In $declStartsWith: missing $s")
        }
        for (s in mustNotContain) {
            if (body.contains(s)) throw AssertionError("In $declStartsWith: unexpected $s")
        }
    }

    private fun requireAnywhere(substrings: List<String>) = containsAll(text, substrings)

    private fun requireLetterChoice() {
        val body = funBody(text, "private fun letterChoice(raw: String?):")
        if (!body.contains("OwnWordsMode.prefixedLetter(raw, all, partial = false)")) {
            throw AssertionError("letterChoice missing OwnWordsMode.prefixedLetter(raw, all, partial = false)")
        }
    }

    private fun requireRestartOwnWordsListen() {
        val body = funBody(text, "private fun restartOwnWordsListen(session: Int)")
        if (!body.contains("finishListen(ownListen?.transcript())")) {
            throw AssertionError("restartOwnWordsListen missing finishListen(ownListen?.transcript())")
        }
        if (!body.contains("restartSilenceListen")) {
            throw AssertionError("restartOwnWordsListen should delegate to restartSilenceListen")
        }
    }

    @Test fun ownsModeHelpersAndLetterChoice() {
        requireAnywhere(listOf("private fun spokenLetters(): Set<String>?"))
        requireLetterChoice()
    }

    @Test fun cardLoopUsesOwnWordsMode() {
        requireAnywhere(listOf(
            "OwnWordsMode.answerAction(heard, lettersHeard())"
        ))
    }

    @Test fun thinkForAnswerUsesOwnWordsModeAndSpokenLetters() {
        requireInFun(
            "private fun thinkForAnswer(",
            mustContain = listOf(
                "OwnWordsMode.thinkHear(heard, lettersHeard())"
            ),
            mustNotContain = listOf("answerLetters()")
        )
    }

    @Test fun judgeOwnWordsUsesTappedAndLettersHeard() {
        requireAnywhere(listOf(
            "if (!OwnWordsJudge.shouldJudge(transcript, letters, lettersHeard(), tapped = chosenLetter)) return"
        ))
    }

    @Test fun ownListenStitchingWired() {
        requireAnywhere(listOf(
            "ownListen?.onFinal(",
            "ownListen?.onError(",
            "ownListen?.onDeadline()",
            "restartOwnWordsListen("
        ))
    }

    @Test fun answerWindowsDropAnswerLetters() {
        requireInFun(
            "private fun listenForAnswer(",
            mustContain = emptyList(),
            mustNotContain = listOf("answerLetters()")
        )
        requireInFun(
            "private fun onPartialResults(",
            mustContain = emptyList(),
            mustNotContain = listOf("answerLetters()")
        )
        requireInFun(
            "private fun absorbAnswer(",
            mustContain = emptyList(),
            mustNotContain = listOf("answerLetters()")
        )
    }
}
