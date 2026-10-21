package com.evan.voicesrs

import org.json.JSONObject

/** OpenAI-compatible `GET /models` body: `data[].id`. */
object ModelList {
    fun parse(body: String): List<String> {
        val obj = try {
            JSONObject(body)
        } catch (_: Exception) {
            return emptyList()
        }
        val data = obj.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        for (i in 0 until data.length()) {
            val id = data.optJSONObject(i)?.optString("id", "")?.trim().orEmpty()
            if (id.isEmpty() || !seen.add(id)) continue
            out += id
        }
        return out
    }
}
