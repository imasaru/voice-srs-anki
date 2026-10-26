package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject

/** Chat-completion JSON body. Stop sequences live here so tests can see them. */
object AiRequest {
    fun completionBody(
        model: String,
        messages: JSONArray,
        maxTokens: Int,
        stream: Boolean,
        tools: JSONArray? = null
    ): JSONObject {
        val stop = JSONArray()
        for (token in SelfTalk.STOP) stop.put(token)
        val body = JSONObject()
            .put("model", model)
            .put("temperature", AiTimeouts.TEMPERATURE)
            .put("max_tokens", maxTokens)
            .put("stream", stream)
            .put("messages", messages)
            .put("stop", stop)
        if (tools != null && tools.length() > 0) body.put("tools", tools)
        if (stream) body.put("stream_options", JSONObject().put("include_usage", true))
        return body
    }

    /** TypeSafe System One / oMLX `POST /v1/systemone` body. */
    fun systemOneBody(model: String, state: Any, questions: JSONObject): JSONObject {
        val body = JSONObject()
            .put("state", state)
            .put("questions", questions)
        if (model.isNotBlank()) body.put("model", model)
        return body
    }
}
