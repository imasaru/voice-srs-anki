package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnWordsRubricTest {
    private fun reply(content: String): String {
        val choices = JSONArray().put(JSONObject().put("message", JSONObject().put("content", content)))
        return JSONObject().put("choices", choices).toString()
    }

    private fun rated(rating: String, reason: String = "fine") =
        reply(JSONObject().put("rating", rating).put("reason", reason).toString())

    private fun system(s: OwnWordsJudge.Strictness): String =
        OwnWordsJudge.requestJson("m", "q", "B. a warm pool", "x", s)
            .getJSONArray("messages").getJSONObject(0).getString("content")

    @Test
    fun `a each rating parses to its ease`() {
        val expect = mapOf("again" to 1, "hard" to 2, "good" to 3, "easy" to 4)
        for ((word, ease) in expect) {
            val v = OwnWordsJudge.parse(rated(word, "because")) as OwnWordsJudge.Verdict.Graded
            assertEquals(word, v.rating.word)
            assertEquals(ease, v.ease)
            assertEquals("because", v.reason)
        }
        // case and spaces are tolerated
        val v = OwnWordsJudge.parse(rated(" Hard ")) as OwnWordsJudge.Verdict.Graded
        assertEquals(OwnWordsJudge.Rating.HARD, v.rating)
    }

    @Test
    fun `b malformed or old-format replies fall back`() {
        val fb = OwnWordsJudge.Verdict.Fallback
        assertEquals(fb, OwnWordsJudge.parse(reply("not json")))
        // h99: a truncated reply still names its rubric word, which the tolerant scan reads
        assertEquals(3, (OwnWordsJudge.parse(reply("{\"rating\":\"good\"")) as OwnWordsJudge.Verdict.Graded).ease)
        assertEquals(fb, OwnWordsJudge.parse(reply("{\"reason\":\"no rating\"}")))     // missing rating
        assertEquals(fb, OwnWordsJudge.parse(rated("excellent")))                      // unknown rating
        assertEquals(fb, OwnWordsJudge.parse(reply("{\"correct\":true,\"confidence\":0.99}"))) // old shape
        assertEquals("unparseable or missing judge fields", OwnWordsJudge.fallbackReason(rated("excellent"), null))
        // a missing reason is not a failure: the rating still counts
        val noReason = OwnWordsJudge.parse(reply("{\"rating\":\"good\"}")) as OwnWordsJudge.Verdict.Graded
        assertEquals(3, noReason.ease)
        assertEquals("", noReason.reason)
        // long reasons are cut, whitespace collapsed
        val long = OwnWordsJudge.parse(rated("hard", "a   b\n" + "x".repeat(400))) as OwnWordsJudge.Verdict.Graded
        assertTrue(long.reason.startsWith("a b x"))
        assertTrue(long.reason.length <= OwnWordsJudge.REASON_MAX)
    }

    @Test
    fun `c correct but partial answer is Hard, ease 2, spoken partly right`() {
        // the rubric tells the model what Hard is ...
        val sys = system(OwnWordsJudge.Strictness.NORMAL)
        assertTrue(sys.contains("hard = mostly right but incomplete, vague or partly wrong"))
        assertTrue(sys.contains("again = wrong, or missing the key idea"))
        assertTrue(sys.contains("good = correct meaning"))
        assertTrue(sys.contains("easy = correct and complete"))
        assertTrue(sys.contains("\"rating\""))
        assertTrue(sys.contains("\"reason\""))
        assertFalse(sys.contains("confidence"))
        // ... and a Hard reply for a partial answer becomes ease 2 and "Partly right."
        val v = OwnWordsJudge.parse(rated("hard", "right option, but missed that the pool keeps idle VMs pre-warmed"))
            as OwnWordsJudge.Verdict.Graded
        val final = OwnWordsJudge.applyTiming(v.rating, startedAfterMs = 500L, windowMs = 20_000L)
        assertEquals(OwnWordsJudge.Rating.HARD, final)
        assertEquals(2, final.ease)
        assertEquals(R.string.own_words_partly, OwnWordsJudge.spokenLine(final))
    }

    @Test
    fun `d Easy after the time limit is Good, Good is never upgraded`() {
        val E = OwnWordsJudge.Rating.EASY
        val G = OwnWordsJudge.Rating.GOOD
        assertEquals(6_666L, OwnWordsJudge.easyLimitMs(20_000L))
        assertEquals(E, OwnWordsJudge.applyTiming(E, 0L, 20_000L))
        assertEquals(E, OwnWordsJudge.applyTiming(E, 6_666L, 20_000L))
        assertEquals(G, OwnWordsJudge.applyTiming(E, 6_667L, 20_000L))
        assertEquals(G, OwnWordsJudge.applyTiming(E, null, 20_000L))      // start unknown
        assertEquals(G, OwnWordsJudge.applyTiming(E, 100L, 0L))           // no window known
        assertEquals(G, OwnWordsJudge.applyTiming(G, 0L, 20_000L))        // never upgraded
        assertEquals(OwnWordsJudge.Rating.HARD, OwnWordsJudge.applyTiming(OwnWordsJudge.Rating.HARD, 99_000L, 20_000L))
        assertEquals(OwnWordsJudge.Rating.AGAIN, OwnWordsJudge.applyTiming(OwnWordsJudge.Rating.AGAIN, 0L, 20_000L))
    }

    @Test
    fun `e strictness changes the rubric text sent`() {
        val lenient = system(OwnWordsJudge.Strictness.LENIENT)
        val normal = system(OwnWordsJudge.Strictness.NORMAL)
        val strict = system(OwnWordsJudge.Strictness.STRICT)
        assertNotEquals(lenient, normal)
        assertNotEquals(normal, strict)
        assertTrue(lenient.contains("choose the higher one"))
        assertTrue(strict.contains("choose the lower one"))
        assertTrue(strict.contains("key terms of the correct answer must be said"))
        assertFalse(normal.contains("choose the higher one"))
        assertFalse(normal.contains("choose the lower one"))
        // default argument and requestBody use Normal unless told otherwise
        assertEquals(normal, OwnWordsJudge.requestJson("m", "q", "B. a warm pool", "x")
            .getJSONArray("messages").getJSONObject(0).getString("content"))
        assertTrue(OwnWordsJudge.requestBody("m", "q", "c", "x", OwnWordsJudge.Strictness.STRICT)
            .contains("choose the lower one"))
        // stored keys
        assertEquals(OwnWordsJudge.Strictness.NORMAL, OwnWordsJudge.Strictness.of(null))
        assertEquals(OwnWordsJudge.Strictness.NORMAL, OwnWordsJudge.Strictness.of("garbage"))
        assertEquals(OwnWordsJudge.Strictness.STRICT, OwnWordsJudge.Strictness.of("strict"))
        assertEquals("normal", AnkiConfig(url = "", apiKey = "", deck = "").judgeStrictness)
    }

    @Test
    fun `f rating maps to ease and then to the card's buttons`() {
        val all = listOf(1, 2, 3, 4)
        assertEquals(1, GradeMapper.toAvailableEase(OwnWordsJudge.Rating.AGAIN.ease, all))
        assertEquals(2, GradeMapper.toAvailableEase(OwnWordsJudge.Rating.HARD.ease, all))
        assertEquals(3, GradeMapper.toAvailableEase(OwnWordsJudge.Rating.GOOD.ease, all))
        assertEquals(4, GradeMapper.toAvailableEase(OwnWordsJudge.Rating.EASY.ease, all))
        assertEquals(3, GradeMapper.toAvailableEase(OwnWordsJudge.Rating.EASY.ease, listOf(1, 2, 3)))
    }

    @Test
    fun `g spoken line per rating and the verdict log line`() {
        assertEquals(R.string.own_words_incorrect, OwnWordsJudge.spokenLine(OwnWordsJudge.Rating.AGAIN))
        assertEquals(R.string.own_words_partly, OwnWordsJudge.spokenLine(OwnWordsJudge.Rating.HARD))
        assertEquals(R.string.own_words_correct, OwnWordsJudge.spokenLine(OwnWordsJudge.Rating.GOOD))
        assertEquals(R.string.own_words_correct, OwnWordsJudge.spokenLine(OwnWordsJudge.Rating.EASY))
        val log = OwnWordsJudge.verdictLog(
            judged = OwnWordsJudge.Rating.EASY, final = OwnWordsJudge.Rating.GOOD, reason = "complete",
            strictness = OwnWordsJudge.Strictness.STRICT, startedAfterMs = 9_000L, windowMs = 20_000L,
            input = "a warm pool | a warm poll"
        )
        assertEquals(
            "own-words judge rating=good judged=easy reason=\"complete\" strictness=strict " +
                "startedMs=9000 easyLimitMs=6666 windowMs=20000 input=a warm pool | a warm poll",
            log
        )
        assertTrue(OwnWordsJudge.verdictLog(OwnWordsJudge.Rating.HARD, OwnWordsJudge.Rating.HARD, "", 
            OwnWordsJudge.Strictness.NORMAL, null, 0L, "x").contains("startedMs=unknown"))
    }

    // Synthetic q21 regression: invented question and recognition alternatives.
    private val q21Question = "Synthetic question 1\nWhich shape has three sides?\nA. Circle\nB. Triangle\nC. Square\nD. Pentagon"
    private val q21Correct = "B. Triangle"
    private val q21Heard = "triangle | try angle | tri angle | tree angle"

    @Test
    fun `h judge prompt carries the choices and the short-form rule (synthetic short form)`() {
        val msgs = OwnWordsJudge.requestJson("m", q21Question, q21Correct, q21Heard).getJSONArray("messages")
        val sys = msgs.getJSONObject(0).getString("content")
        val user = msgs.getJSONObject(1).getString("content")
        assertTrue(sys.contains(OwnWordsJudge.SHORT_FORM_LINE))
        assertTrue(sys.contains("short or spoken form"))
        assertTrue(sys.contains("unless it better matches a different choice"))
        for (s in OwnWordsJudge.Strictness.values()) assertTrue(system(s).contains(OwnWordsJudge.SHORT_FORM_LINE))
        assertTrue(user.contains("Choices:\n"))
        assertTrue(user.contains("A. Circle\n"))
        assertTrue(user.contains("B. Triangle\n"))
        assertTrue(user.contains("C. Square\n"))
        assertTrue(user.contains("D. Pentagon\n"))
        assertTrue(user.contains("Correct answer: $q21Correct"))
        assertTrue(user.contains("Learner's own-words answer: $q21Heard"))
        // no choices line for a non-MCQ card
        val plain = OwnWordsJudge.requestJson("m", "What is a warm pool?", "idle VMs kept ready", "x")
            .getJSONArray("messages").getJSONObject(1).getString("content")
        assertFalse(plain.contains("Choices:"))
    }

    // Synthetic q28 regression: invented question and recognition alternatives.
    private val q28Question = "Synthetic question 2\nWhat does a fictional sorter do?\nA. Stops the belt\nB. Removes every block\nC. Adds a blue block to a box\nD. Paints a red block"
    private val q28Correct = "C. Adds a blue block to a box"
    private val q28Heard = "adds a blue block to a box | ads a blue block to a box | adds a blew block to a box | adds a blue block two a box | adds a blue block to the box"

    @Test
    fun `i judge prompt carries the sound-alike rule (synthetic misheard option C)`() {
        val msgs = OwnWordsJudge.requestJson("m", q28Question, q28Correct, q28Heard).getJSONArray("messages")
        val sys = msgs.getJSONObject(0).getString("content")
        val user = msgs.getJSONObject(1).getString("content")
        assertTrue(sys.contains(OwnWordsJudge.SOUND_ALIKE_LINE))
        assertTrue(sys.contains("sound-alike errors"))
        for (s in OwnWordsJudge.Strictness.values()) assertTrue(system(s).contains(OwnWordsJudge.SOUND_ALIKE_LINE))
        assertTrue(user.contains("Choices:\n"))
        assertTrue(user.contains("A. Stops the belt\n"))
        assertTrue(user.contains("B. Removes every block\n"))
        assertTrue(user.contains("C. Adds a blue block to a box\n"))
        assertTrue(user.contains("D. Paints a red block\n"))
        assertTrue(user.contains("Learner's own-words answer: $q28Heard"))
        assertEquals(5, q28Heard.split(" | ").size)
    }

    // Synthetic q1 regression: invented question and recognition alternatives.
    private val q1Question = "Synthetic question 3\nWhich fictional system sorts shapes?\nA. Hand basket\nB. SSS (Synthetic Shape Sorter)\nC. Empty shelf\nD. Paint brush"
    private val q1Correct = "B. SSS (Synthetic Shape Sorter)"
    private val q1Heard = "sss | ess ess ess | SS S | S SS | es es es"

    @Test
    fun `j judge prompt carries the acronym rule (synthetic acronym)`() {
        val msgs = OwnWordsJudge.requestJson("m", q1Question, q1Correct, q1Heard).getJSONArray("messages")
        val sys = msgs.getJSONObject(0).getString("content")
        val user = msgs.getJSONObject(1).getString("content")
        assertTrue(sys.contains(OwnWordsJudge.ACRONYM_LINE))
        assertTrue(sys.contains("letters said one by one"))
        assertTrue(sys.contains("an acronym and its expansion name the same thing"))
        for (s in OwnWordsJudge.Strictness.values()) assertTrue(system(s).contains(OwnWordsJudge.ACRONYM_LINE))
        assertTrue(user.contains("B. SSS (Synthetic Shape Sorter)\n"))
        assertTrue(user.contains("Learner's own-words answer: $q1Heard"))
        assertTrue(OwnWordsJudge.parse(rated("good", "SSS is option B")) is OwnWordsJudge.Verdict.Graded)
    }

    // Synthetic q2 regression: invented question and recognition alternatives.
    private val q2Question = "Synthetic question 4\nWhich activity uses a small toy?\nA. Building a bridge\nB. Launching a ship\nC. Sorting blue blocks in a toy box\nD. Painting a room"
    private val q2Correct = "C. Sorting blue blocks in a toy box"
    private val q2Heard = "sorting blew blocks in a toy box | sorting blue blocks in a toy box | sort blue blocks in the toy box | sorting blue blocks in a toy box | sorting blue blocks in toy box"

    @Test
    fun `k sound-alike guard keeps Q2 and blocks wrong or unrelated answers`() {
        val msgs = OwnWordsJudge.requestJson("m", q2Question, q2Correct, q2Heard).getJSONArray("messages")
        val sys = msgs.getJSONObject(0).getString("content")
        val user = msgs.getJSONObject(1).getString("content")
        assertTrue(sys.contains(OwnWordsJudge.SOUND_ALIKE_GUARD))
        assertTrue(sys.contains("only the words that sound like"))
        assertTrue(sys.contains("name a different choice"))
        assertTrue(sys.contains("not because it is the only choice that fits"))
        assertTrue(sys.indexOf(OwnWordsJudge.SOUND_ALIKE_LINE) < sys.indexOf(OwnWordsJudge.SOUND_ALIKE_GUARD))
        for (s in OwnWordsJudge.Strictness.values()) assertTrue(system(s).contains(OwnWordsJudge.SOUND_ALIKE_GUARD))
        assertTrue(user.contains("C. Sorting blue blocks in a toy box\n"))
        assertTrue(user.contains("Learner's own-words answer: $q2Heard"))
        assertEquals(5, q2Heard.split(" | ").size)
    }
}
