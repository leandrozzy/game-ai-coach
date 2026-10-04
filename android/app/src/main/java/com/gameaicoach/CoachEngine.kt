package com.gameaicoach

import org.json.JSONObject

object CoachEngine {
    fun buildToday(summary: JSONObject?, server: JSONObject?): List<String> {
        val out = mutableListOf<String>()
        val ai = server?.optJSONObject("ai")
        val actions = ai?.optJSONArray("actions")
        if (actions != null) for (i in 0 until minOf(actions.length(), 6)) {
            val s = actions.optString(i).trim()
            if (s.isNotBlank()) out.add(s)
        }
        if (out.isEmpty()) {
            val recs = summary?.optJSONArray("recommendations")
            if (recs != null) for (i in 0 until minOf(recs.length(), 5)) out.add(recs.optString(i))
        }
        if (out.isEmpty()) out.add("Inicie o Coach e jogue normalmente. Após finalizar, a IA cruza sua conta com informações atuais e cria prioridades.")
        return out.take(6)
    }

    fun aiSummary(server: JSONObject?): String = server?.optJSONObject("ai")?.optString("accountSummary")?.trim().orEmpty()
    fun aiConfidence(server: JSONObject?): Int = server?.optJSONObject("ai")?.optInt("confidence",0) ?: 0
    fun aiProvider(server: JSONObject?): String = server?.optJSONObject("ai")?.optString("provider")?.trim().orEmpty()

    fun aiList(server: JSONObject?, key: String, max: Int = 6): List<String> {
        val arr = server?.optJSONObject("ai")?.optJSONArray(key) ?: return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until minOf(arr.length(), max)) {
            val s = arr.optString(i).trim()
            if (s.isNotBlank()) out.add(s)
        }
        return out
    }

    fun coverageText(summary: JSONObject?): String = if (summary == null) "0%" else "${summary.optInt("coveragePercent",0)}%"
    fun entityCount(summary: JSONObject?): Int = summary?.optJSONArray("entities")?.length() ?: 0
    fun newEntityCount(summary: JSONObject?): Int = summary?.optInt("newEntities",0) ?: 0

    fun coverageDetails(summary: JSONObject?): List<Pair<String,Boolean>> {
        val obj = summary?.optJSONObject("coverage") ?: return emptyList()
        val out = mutableListOf<Pair<String,Boolean>>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out.add(k to obj.optBoolean(k,false))
        }
        return out
    }
}
