package com.evan.voicesrs

import org.json.JSONArray
import org.json.JSONObject

/** Chat-completion JSON body. Stop sequences live here so tests can see them. */
object AiRequest {
    fun completionBody(
        model: String,
        messages: JSONArray,
        maxTokens: Int,
        stream: Boolean
    ): JSONObject {
        val stop = JSONArray()
        for (token in SelfTalk.STOP) stop.put(token)
        return JSONObject()
            .put("model", model)
            .put("temperature", AiTimeouts.TEMPERATURE)
            .put("max_tokens", maxTokens)
            .put("stream", stream)
            .put("messages", messages)
            .put("stop", stop)
    }
}
