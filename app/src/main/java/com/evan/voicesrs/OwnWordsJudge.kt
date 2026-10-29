package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject

/**
 * Free-speech MCQ answers judged by a local decision model over OpenAI
 * `chat/completions`. POST `{base}/chat/completions` with an OpenAI-compatible
 * JSON body (temperature 0, `response_format` json_object). A `correct` of true
 * at or above [THRESHOLD] is Good (ease 3); otherwise Again (ease 1). Malformed
 * bodies and transport failures are [Verdict.Fallback] so the existing grader
 * still runs.
 */
object OwnWordsJudge {
    const val THRESHOLD = 0.70
    const val QUESTION_ID = "correct"
    const val MAX_TOKENS = 64
    const val CONNECT_MS = 5_000
    const val READ_MS = 15_000

    /** The model must reply with exactly this shape; the user answer means the
     * same as the correct answer. */
    private const val SYSTEM_PROMPT =
        "You judge whether a learner's own-words answer means the same as the " +
        "correct answer. Reply with exactly one JSON object and nothing else: " +
        "{\"correct\": <boolean>, \"confidence\": <number 0..1>}. " +
        "Set correct=true only when the meaning matches; otherwise correct=false. " +
        "The learner's answer may list several speech-recognition results separated by \" | \". " +
        "These are alternative transcriptions of one spoken answer; judge the most plausible reading."

    sealed class Verdict {
        data class Graded(
            val ease: Int,
            val confidence: Double,
            val correct: Boolean
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
     * OpenAI `chat/completions` request. `temperature` 0, `max_tokens` 64,
     * thinking disabled, JSON-only response.
     */
    fun requestJson(
        model: String,
        question: String,
        correctAnswer: String,
        userAnswer: String
    ): JSONObject {
        val user = buildString {
            appendLine("Question: $question")
            appendLine("Correct answer: $correctAnswer")
            appendLine("Learner's own-words answer: $userAnswer")
        }
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", user))
        return JSONObject()
            .put("model", model)
            .put("temperature", 0)
            .put("max_tokens", MAX_TOKENS)
            .put("chat_template_kwargs", JSONObject().put("enable_thinking", false))
            .put("response_format", JSONObject().put("type", "json_object"))
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
        userAnswer: String
    ): String = requestJson(model, question, correctAnswer, userAnswer).toString()

    /**
     * Parse an OpenAI chat completion. Reads `choices[0].message.content`, takes
     * the first `{...}` block (ignoring surrounding text or code fences), and
     * grades on `correct` and `confidence`. Anything malformed or missing is
     * [Verdict.Fallback].
     */
    fun parse(body: String): Verdict {
        val content = responseContent(body) ?: return Verdict.Fallback
        val obj = jsonBlock(content) ?: return Verdict.Fallback
        if (!obj.has(QUESTION_ID)) return Verdict.Fallback
        val confidence = obj.optDouble("confidence", Double.NaN)
        if (confidence.isNaN() || confidence.isInfinite()) return Verdict.Fallback
        val correct = obj.optBoolean(QUESTION_ID, false)
        return if (correct && confidence >= THRESHOLD) {
            Verdict.Graded(ease = 3, confidence = confidence, correct = true)
        } else {
            Verdict.Graded(ease = 1, confidence = confidence, correct = false)
        }
    }

    fun grade(confidence: Double): Verdict {
        if (confidence.isNaN() || confidence.isInfinite()) return Verdict.Fallback
        val correct = confidence >= THRESHOLD
        return Verdict.Graded(
            ease = if (correct) 3 else 1,
            confidence = confidence,
            correct = correct
        )
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
