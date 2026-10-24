package com.evan.voicesrs

import org.json.JSONObject

/**
 * Free-speech MCQ answers judged by a local H2O / System One decision model.
 * POST `{base}/v1/systemone` with OpenAI-compatible JSON. Confidence at or
 * above [THRESHOLD] is Good (ease 3); below is Again (ease 1). Malformed
 * bodies and transport failures are [Verdict.Fallback] so the existing
 * grader still runs.
 */
object OwnWordsJudge {
    const val PATH = "/v1/systemone"
    const val THRESHOLD = 0.70
    const val QUESTION_ID = "correct"
    const val CONNECT_MS = 5_000
    const val READ_MS = 15_000

    sealed class Verdict {
        data class Graded(
            val ease: Int,
            val confidence: Double,
            val correct: Boolean
        ) : Verdict()
        data object Fallback : Verdict()
    }

    fun endpoint(baseUrl: String): String {
        val root = baseUrl.trim().trimEnd('/')
        if (root.isEmpty()) return ""
        if (root.endsWith("/v1/systemone") || root.endsWith("/systemone")) return root
        if (root.endsWith("/v1")) return "$root/systemone"
        return "$root$PATH"
    }

    /** Base used for `GET /models` on the same host. */
    fun modelsBase(baseUrl: String): String {
        val root = baseUrl.trim().trimEnd('/')
        if (root.isEmpty()) return ""
        if (root.endsWith("/v1/systemone")) return root.removeSuffix("/systemone")
        if (root.endsWith("/systemone")) {
            val parent = root.removeSuffix("/systemone").trimEnd('/')
            return if (parent.endsWith("/v1")) parent else "$parent/v1"
        }
        if (root.endsWith("/v1")) return root
        return "$root/v1"
    }

    fun requestJson(
        model: String,
        question: String,
        correctAnswer: String,
        userAnswer: String
    ): JSONObject {
        val state = JSONObject()
            .put("question", question)
            .put("correct_answer", correctAnswer)
            .put("user_answer", userAnswer)
        val questions = JSONObject().put(
            QUESTION_ID,
            JSONObject()
                .put("type", "noul")
                .put("instructions", "Is the user's answer correct for this question?")
        )
        return AiRequest.systemOneBody(model, state, questions)
    }

    fun requestBody(
        model: String,
        question: String,
        correctAnswer: String,
        userAnswer: String
    ): String = requestJson(model, question, correctAnswer, userAnswer).toString()

    fun parse(body: String): Verdict {
        val obj = try {
            JSONObject(body)
        } catch (_: Exception) {
            return Verdict.Fallback
        }
        val confidence = readConfidence(obj) ?: return Verdict.Fallback
        return grade(confidence)
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
     * Free speech on an MCQ, not a letter and not a drill command.
     * Non-MCQ cards stay on the existing grader.
     */
    fun shouldJudge(transcript: String, letters: Set<String>?): Boolean {
        if (transcript.isBlank() || letters.isNullOrEmpty()) return false
        if (SpokenLetter.normalise(transcript, letters) != null) return false
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

    private fun readConfidence(obj: JSONObject): Double? {
        obj.optJSONObject("answers")?.let { answers ->
            namedConfidence(answers, QUESTION_ID)?.let { return it }
            namedConfidence(answers, "is_correct")?.let { return it }
            val keys = answers.keys()
            while (keys.hasNext()) {
                val node = answers.optJSONObject(keys.next() as String) ?: continue
                nodeConfidence(node)?.let { return it }
            }
        }
        nodeConfidence(obj)?.let { return it }
        val content = obj.optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            .orEmpty()
            .trim()
        if (content.startsWith("{")) {
            val inner = try {
                JSONObject(content)
            } catch (_: Exception) {
                null
            }
            if (inner != null) return readConfidence(inner)
        }
        return null
    }

    private fun namedConfidence(answers: JSONObject, id: String): Double? {
        if (!answers.has(id)) return null
        return nodeConfidence(answers.optJSONObject(id) ?: return null)
    }

    private fun nodeConfidence(node: JSONObject): Double? {
        if (node.has("noul") && !node.isNull("noul")) {
            val v = node.optDouble("noul", Double.NaN)
            if (!v.isNaN()) return v
        }
        if (node.has("confidence") && !node.isNull("confidence")) {
            val v = node.optDouble("confidence", Double.NaN)
            if (!v.isNaN()) return v
        }
        if (node.has("score") && !node.isNull("score")) {
            val v = node.optDouble("score", Double.NaN)
            if (!v.isNaN()) return v
        }
        return null
    }
}
