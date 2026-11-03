package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject

/**
 * Free-speech MCQ answers judged by a local decision model over OpenAI
 * `chat/completions`. POST `{base}/chat/completions` with an OpenAI-compatible
 * JSON body (temperature 0, `response_format` json_object). The model rates the
 * answer again/hard/good/easy (ease 1-4) from a written rubric; see [systemPrompt].
 * bodies and transport failures are [Verdict.Fallback] so the existing grader
 * still runs.
 */
object OwnWordsJudge {
    const val QUESTION_ID = "rating"
    const val REASON_FIELD = "reason"
    const val REASON_MAX = 160
    /** Easy stands only when speech started within window / EASY_START_DIVISOR. */
    const val EASY_START_DIVISOR = 3L

    enum class Rating(val word: String, val ease: Int) {
        AGAIN("again", 1), HARD("hard", 2), GOOD("good", 3), EASY("easy", 4);
        companion object {
            fun of(raw: String?): Rating? {
                val w = raw?.trim()?.lowercase() ?: return null
                values().firstOrNull { it.word == w }?.let { return it }
                // h98: the small judge model sometimes answers off-rubric ("bad", "correct").
                return when (w) {
                    "bad", "wrong", "incorrect", "fail", "poor" -> AGAIN
                    "partial", "partly", "partially correct", "close" -> HARD
                    "correct", "right", "ok", "fine" -> GOOD
                    else -> null
                }
            }
        }
    }

    enum class Strictness(val key: String) {
        LENIENT("lenient"), NORMAL("normal"), STRICT("strict");
        companion object {
            val DEFAULT = NORMAL
            fun of(key: String?): Strictness {
                val k = key?.trim()?.lowercase() ?: return DEFAULT
                return values().firstOrNull { it.key == k } ?: DEFAULT
            }
        }
    }
    const val MAX_TOKENS = 160
    const val CONNECT_MS = 5_000
    const val READ_MS = 15_000

    /** Rubric the model rates against. The n-best sentence is h79's. */
    private const val RUBRIC =
        "You rate a learner's own-words answer against the correct answer. " +
        "Reply with exactly one JSON object and nothing else: " +
        "{\"rating\": \"again|hard|good|easy\", \"reason\": \"<at most 12 words>\"}. " +
        "Rubric: again = wrong, or missing the key idea. " +
        "hard = mostly right but incomplete, vague or partly wrong. " +
        "good = correct meaning. " +
        "easy = correct and complete. " +
        "The reason says in plain words what was right or missing."

    private const val NBEST_LINE =
        "The learner's answer may list several speech-recognition results separated by \" | \". " +
        "These are alternative transcriptions of one spoken answer; judge the most plausible reading."

    fun strictnessLine(s: Strictness): String = when (s) {
        Strictness.LENIENT -> "Strictness: lenient. When an answer is between two ratings, choose the higher one."
        Strictness.NORMAL -> "Strictness: normal. Give the rating the answer earns under the rubric."
        Strictness.STRICT -> "Strictness: strict. When an answer is between two ratings, choose the lower one. " +
            "The key terms of the correct answer must be said; without them the rating is at most hard."
    }

    /** Q21 (count distinct -> B): a short spoken name of the correct choice counts. */
    const val SHORT_FORM_LINE =
        "When choices are listed, an answer that clearly names the correct choice in short or spoken form " +
        "(for example 'count distinct' for COUNT(DISTINCT column)) is correct, unless it better matches a different choice " +
        "(for example 'approx count distinct' means APPROX_COUNT_DISTINCT(column))."

    /** Q28 (misheard read-aloud of option C): sound-alike ASR errors are read as the fitting word. */
    const val SOUND_ALIKE_LINE =
        "The answer comes from speech recognition, so treat sound-alike errors (for example 'as' for 'adds', " +
        "'steam table scheme' for 'table schema') as the word that fits the question and choices; " +
        "if the answer read that way clearly matches the correct choice, rate it as that choice."

    /** Q1 (DBFS heard as "dbfs" / "DB FS"): spelled-out letters are the acronym; acronym = expansion. */
    const val ACRONYM_LINE =
        "Acronyms may come out as letters said one by one, with spaces, dots or any capitals " +
        "(for example 'DB FS', 'd b f s' or 'dbfs' for DBFS); read them as the acronym, " +
        "and an acronym and its expansion name the same thing (DBFS = Databricks File System)."

    /** Q2 guard (scikit-learn heard as "Hitler"/"can't learn", rest = option C's words): fair pass kept, guesses blocked. */
    const val SOUND_ALIKE_GUARD =
        "Reinterpret only the words that sound like words in the question or choices, and judge on the words that " +
        "were heard clearly; if those words name a different choice, or name nothing in the question, rate it again. " +
        "Rate the answer as the correct choice because its words match it, not because it is the only choice that fits."

    /** h99: json_object mode gave `{ }` or `{"again": ...}` on 11/12 real calls; spell out the shape. */
    const val FORMAT_LINE =
        "The rating must be exactly one of: again, hard, good, easy (lowercase). " +
        "Never use other words such as bad, wrong, correct or partial. " +
        "Example: correct answer 'Delta Lake time travel', learner said 'time travel in delta' -> " +
        "{\"rating\": \"good\", \"reason\": \"Names time travel on Delta.\"} " +
        "Example: correct answer 'Job cluster', learner said 'be nice' -> " +
        "{\"rating\": \"again\", \"reason\": \"Unrelated to the question.\"}"

    /** Second-try reminder after an unreadable reply. */
    const val REMINDER =
        "Your previous reply could not be read. Reply with only " +
        "{\"rating\": \"again|hard|good|easy\", \"reason\": \"...\"} using one of those four words."

    fun systemPrompt(s: Strictness): String =
        "$RUBRIC ${strictnessLine(s)} $SHORT_FORM_LINE $SOUND_ALIKE_LINE $SOUND_ALIKE_GUARD $ACRONYM_LINE $NBEST_LINE $FORMAT_LINE"

    /** h99: strict json_schema with a rating enum (oMLX honours it: 12/12 vs 1/12 with json_object). */
    fun responseFormat(): JSONObject {
        val props = JSONObject()
            .put(QUESTION_ID, JSONObject().put("type", "string")
                .put("enum", JSONArray(Rating.values().map { it.word })))
            .put(REASON_FIELD, JSONObject().put("type", "string"))
        val schema = JSONObject().put("type", "object").put("properties", props)
            .put("required", JSONArray(listOf(QUESTION_ID, REASON_FIELD)))
            .put("additionalProperties", false)
        return JSONObject().put("type", "json_schema").put("json_schema",
            JSONObject().put("name", "judge").put("strict", true).put("schema", schema))
    }

    sealed class Verdict {
        data class Graded(
            val ease: Int,
            val rating: Rating,
            val reason: String
        ) : Verdict()
        data object Fallback : Verdict()
    }

    /**
     * Full judge endpoint. The base is the effective base URL (already ending in
     * `/v1`); a missing `/v1` is appended once and a trailing `/chat/completions`
     * is never doubled. Trailing slashes are trimmed.
     */
    fun endpoint(baseUrl: String): String {
        var root = baseUrl.trim().trimEnd('/')
        if (root.isEmpty()) return ""
        root = root.removeSuffix("/chat/completions").trimEnd('/')
        val base = if (root.endsWith("/v1")) root else "$root/v1"
        return "$base/chat/completions"
    }

    /**
     * Decision base URL; blank falls back to the main chat model base (same oMLX
     * host). A trailing `/chat/completions` on the main URL is dropped.
     */
    fun effectiveBase(decisionUrl: String, mainBaseUrl: String): String {
        val own = decisionUrl.trim()
        if (own.isNotEmpty()) return own
        return mainBaseUrl.trim().trimEnd('/').removeSuffix("/chat/completions").trimEnd('/')
    }

    /** Base used for `GET /models` on the same host (e.g. `{base}/v1/models`). */
    fun modelsBase(baseUrl: String): String {
        var root = baseUrl.trim().trimEnd('/')
        if (root.isEmpty()) return ""
        root = root.removeSuffix("/chat/completions").trimEnd('/')
        return if (root.endsWith("/v1")) root else "$root/v1"
    }

    /**
     * OpenAI `chat/completions` request. `temperature` 0, `max_tokens` 160,
     * thinking disabled, JSON-only response.
     */
    fun requestJson(
        model: String,
        question: String,
        correctAnswer: String,
        userAnswer: String,
        strictness: Strictness = Strictness.DEFAULT,
        previousReply: String? = null
    ): JSONObject {
        val user = buildString {
            appendLine("Question: $question")
            appendLine("Correct answer: $correctAnswer")
            val options = QuestionCard.parse(question).options
            if (options.isNotEmpty()) {
                appendLine("Choices:")
                options.forEach { appendLine("${it.letter}. ${it.text}") }
            }
            appendLine("Learner's own-words answer: $userAnswer")
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", systemPrompt(strictness)))
            .put(JSONObject().put("role", "user").put("content", user))
        if (previousReply != null) {
            messages.put(JSONObject().put("role", "assistant").put("content", previousReply))
                .put(JSONObject().put("role", "user").put("content", REMINDER))
        }
        return JSONObject()
            .put("model", model)
            .put("temperature", 0)
            .put("max_tokens", MAX_TOKENS)
            .put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
            .put("response_format", responseFormat())
            .put("messages", messages)
    }

    /** Separator between n-best hypotheses in the judge input. */
    const val NBEST_SEPARATOR = " | "

    /**
     * Judge input: every non-blank final hypothesis (best first, duplicates dropped,
     * case-insensitive) joined with [NBEST_SEPARATOR], when [transcript] is one of
     * them. Otherwise (a letter, a typed or stale answer) just [transcript].
     */
    fun judgeInput(transcript: String, finals: List<String?>): String {
        val t = transcript.trim()
        val alts = finals.mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .distinctBy { it.lowercase() }
        if (alts.none { it.equals(t, ignoreCase = true) }) return t
        return alts.joinToString(NBEST_SEPARATOR)
    }

    fun requestBody(
        model: String,
        question: String,
        correctAnswer: String,
        userAnswer: String,
        strictness: Strictness = Strictness.DEFAULT,
        previousReply: String? = null
    ): String = requestJson(model, question, correctAnswer, userAnswer, strictness, previousReply).toString()

    /**
     * Parse an OpenAI chat completion. Reads `choices[0].message.content`, takes
     * the first `{...}` block (ignoring surrounding text or code fences), and
     * reads `rating` and `reason`. Anything malformed or missing is
     * [Verdict.Fallback].
     */
    fun parse(body: String): Verdict {
        val content = responseContent(body) ?: return Verdict.Fallback
        return parseContent(content)
    }

    private val RATING_KEYS = listOf(QUESTION_ID, "grade", "score", "verdict")
    private val RUBRIC_WORD = Regex("\\b(again|hard|good|easy)\\b", RegexOption.IGNORE_CASE)

    /** h99 tolerant read of the model text: fences stripped, first {...}, case-insensitive
     * rating/grade/score/verdict, a rubric word used as the key, then a rubric word in plain text. */
    fun parseContent(content: String): Verdict {
        val text = content.replace(Regex("```(?:json)?", RegexOption.IGNORE_CASE), "").trim()
        val obj = jsonBlock(text)
        var rating: Rating? = null
        var reason = ""
        if (obj != null) {
            val lower = obj.keys().asSequence().associateBy { it.lowercase().trim() }
            reason = lower[REASON_FIELD]?.let { obj.optString(it, "") }.orEmpty()
            rating = RATING_KEYS.firstNotNullOfOrNull { k -> lower[k]?.let { Rating.of(obj.optString(it, "")) } }
            if (rating == null && RATING_KEYS.none { it in lower }) {
                // Seen live: {"again": "B. ...", "reason": ...}: the rubric word became the key.
                rating = lower.keys.firstNotNullOfOrNull { k -> Rating.values().firstOrNull { it.word == k } }
            }
        } else {
            rating = RUBRIC_WORD.find(text)?.let { Rating.of(it.value) }
        }
        rating ?: return Verdict.Fallback
        reason = reason.trim().replace(Regex("\\s+"), " ").take(REASON_MAX)
        return Verdict.Graded(ease = rating.ease, rating = rating, reason = reason)
    }

    fun easyLimitMs(windowMs: Long): Long = if (windowMs <= 0L) 0L else windowMs / EASY_START_DIVISOR

    /** Easy stands only if speech started within [easyLimitMs]; unknown start or window -> Good.
     * Never upgrades. */
    fun applyTiming(rating: Rating, startedAfterMs: Long?, windowMs: Long): Rating {
        if (rating != Rating.EASY) return rating
        if (startedAfterMs == null || startedAfterMs < 0L || windowMs <= 0L) return Rating.GOOD
        return if (startedAfterMs <= easyLimitMs(windowMs)) Rating.EASY else Rating.GOOD
    }

    fun spokenLine(rating: Rating): Int = when (rating) {
        Rating.AGAIN -> R.string.own_words_incorrect
        Rating.HARD -> R.string.own_words_partly
        Rating.GOOD, Rating.EASY -> R.string.own_words_correct
    }

    fun verdictLog(
        judged: Rating, final: Rating, reason: String, strictness: Strictness,
        startedAfterMs: Long?, windowMs: Long, input: String
    ): String = "own-words judge rating=${final.word} judged=${judged.word} reason=\"$reason\" " +
        "strictness=${strictness.key} startedMs=${startedAfterMs ?: "unknown"} " +
        "easyLimitMs=${easyLimitMs(windowMs)} windowMs=$windowMs input=$input"

    const val RAW_LOG_MAX = 600
    private val SECRET = Regex("(?i)(bearer\\s+|sk-|api[_-]?key[\"=: ]+|token[\"=: ]+)[A-Za-z0-9._-]+")

    /** First [RAW_LOG_MAX] chars of a judge reply for the log, whitespace collapsed, keys redacted. */
    fun rawSnippet(raw: String?): String {
        if (raw == null) return "none"
        return raw.replace(SECRET, "<redacted>").replace(Regex("\\s+"), " ").trim().take(RAW_LOG_MAX)
    }

    data class Attempt(
        val verdict: Verdict, val raw: String?, val reason: String?, val failLine: Int?, val tries: Int
    )

    /** Retry only a bad/empty reply or a network error (timeout, refused, reset); not HTTP errors. */
    private fun retryable(raw: String?, error: Exception?): Boolean =
        if (error != null) error is java.io.IOException && error !is AiHttpException else true

    /** At most two tries. Bad reply -> own_words_unreadable; any exception -> own_words_fallback. */
    fun judgeWithRetry(stopped: () -> Boolean, post: () -> String): Attempt =
        judgeWithReminder(stopped) { _ -> post() }

    /** [post] gets the previous unreadable reply text on the second try (null on the first). */
    fun judgeWithReminder(stopped: () -> Boolean, post: (previousReply: String?) -> String): Attempt {
        var raw: String? = null
        var error: Exception? = null
        var tries = 0
        while (tries < 2) {
            tries++
            val previous = raw?.let { responseContent(it) ?: it }
            raw = null; error = null
            try { raw = post(previous) } catch (e: Exception) { error = e }
            val v = result(raw, error)
            if (v is Verdict.Graded) return Attempt(v, raw, null, null, tries)
            if (tries >= 2 || stopped() || !retryable(raw, error)) break
        }
        val line = if (error != null) R.string.own_words_fallback else R.string.own_words_unreadable
        return Attempt(Verdict.Fallback, raw, fallbackReason(raw, error), line, tries)
    }

    fun result(body: String?, error: Exception?): Verdict {
        if (error != null) return Verdict.Fallback
        if (body.isNullOrBlank()) return Verdict.Fallback
        return parse(body)
    }

    /**
     * Reason a [result] call produced [Verdict.Fallback], or null when it did not.
     * Safe to log: never contains the API key, auth headers, or request bodies.
     */
    fun fallbackReason(body: String?, error: Exception?): String? = when (result(body, error)) {
        is OwnWordsJudge.Verdict.Graded -> null
        OwnWordsJudge.Verdict.Fallback -> when {
            error != null -> reasonForError(error)
            body.isNullOrBlank() -> "no response"
            responseContent(body) == null -> "no content in response"
            else -> "unparseable or missing judge fields"
        }
    }

    private fun reasonForError(e: Exception): String = when (e) {
        is AiHttpException -> "HTTP ${e.code}"
        else -> buildString {
            append(e.javaClass.simpleName)
            e.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
        }
    }

    /** h107b2: fillers and drill commands are not answers, so the judge never sees them. */
    private val NON_ANSWERS = setOf("um", "uh", "hmm", "er", "again", "repeat", "skip", "pass")

    fun isNonAnswer(transcript: String): Boolean {
        val t = transcript.trim().lowercase().replace(Regex("[.!?,]+"), "").replace(Regex("\\s+"), " ")
        if (t.isEmpty() || t in NON_ANSWERS) return true
        return GradeMapper.parse(t) != VoiceAction.None
    }

    private fun words(s: String): List<String> =
        s.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    /** h107b2: true when [reason] repeats 6+ consecutive words of [correctAnswer] that the learner's [input] never said
     * in a row (the judge copied the key instead of judging). Short keys (< 6 words) are never rubber stamps. */
    fun rubberStamp(reason: String, correctAnswer: String, input: String): Boolean {
        val key = words(correctAnswer)
        if (key.size < 6) return false
        val r = " " + words(reason).joinToString(" ") + " "
        val said = " " + words(input).joinToString(" ") + " "
        for (i in 0..key.size - 6) {
            // h107b2: skip a window that starts with a choice-label letter (e.g. "B.") —
            // the learner need not have spoken the label, so it can't be a rubber-stamp run.
            if (key[i].length == 1) continue
            val run = " " + key.subList(i, i + 6).joinToString(" ") + " "
            if (r.contains(run) && !said.contains(run)) return true
        }
        return false
    }

    /** h107b2: a non-Again verdict with a rubber-stamp reason becomes [Verdict.Fallback] (manual rating, no auto-grade). */
    fun guardVerdict(v: Verdict, correctAnswer: String, input: String): Verdict =
        if (v is Verdict.Graded && v.rating != Rating.AGAIN && rubberStamp(v.reason, correctAnswer, input)) Verdict.Fallback else v

    /** Free speech on an MCQ, not a letter and not a drill command.
     * Non-MCQ cards stay on the existing grader. h83: with [letterListen] false
     * spoken letters are not matched, so everything is judged except the
     * letter the learner [tapped]. */
    fun shouldJudge(
        transcript: String,
        letters: Set<String>?,
        letterListen: Boolean = true,
        tapped: String? = null,
    ): Boolean {
        if (transcript.isBlank() || letters.isNullOrEmpty()) return false
        if (isNonAnswer(transcript)) return false
        if (tapped != null && transcript.trim().equals(tapped.trim(), ignoreCase = true)) return false
        if (letterListen && SpokenLetter.normalise(transcript, letters) != null) return false
        return true
    }

    fun correctText(questionHtml: String, correctField: String, backHtml: String): String {
        val plain = BubbleModel.fullQuestion(questionHtml).ifBlank {
            AnkiText.stripHtml(questionHtml)
        }
        val parsed = QuestionCard.parse(plain)
        val source = correctField.ifBlank { AnkiText.stripHtml(backHtml) }
        val letter = QuestionCard.correctLetter(source, parsed.options)
        if (letter != null) {
            val opt = parsed.options.firstOrNull { it.letter == letter }
            val text = opt?.text?.trim().orEmpty()
            return if (text.isEmpty()) letter else "$letter. $text"
        }
        return source.trim()
    }

    /** Model text for the log (falls back to the raw body). */
    fun contentForLog(body: String?): String = rawSnippet(body?.let { responseContent(it) ?: it })

    /** `choices[0].message.content` from an OpenAI-style chat completion. */
    private fun responseContent(body: String): String? {
        val content = try {
            JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
        } catch (_: Exception) {
            null
        }
        return content?.trim()?.ifBlank { null }
    }

    /** First balanced `{...}` block in [content], ignoring surrounding text and
     * code fences. Returns null when there is no complete block. */
    private fun jsonBlock(content: String): JSONObject? {
        val s = content.trim()
        val start = s.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until s.length) {
            val c = s[i]
            if (escaped) { escaped = false; continue }
            when {
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) {
                        return try {
                            JSONObject(s.substring(start, i + 1))
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
            }
        }
        return null
    }
}
