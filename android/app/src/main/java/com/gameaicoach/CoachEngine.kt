package com.gameaicoach

import org.json.JSONArray
import org.json.JSONObject

object CoachEngine {
    fun buildToday(summary: JSONObject?, server: JSONObject?): List<String> {
        val items = mutableListOf<String>()
        val recs = summary?.optJSONArray("recommendations")
        if (recs != null) for (i in 0 until minOf(recs.length(), 5)) items.add(recs.optString(i))
        val web = server?.optJSONArray("webIntel")
        if (web != null && web.length() > 0) {
            val first = web.optJSONObject(0)
            first?.optString("title")?.takeIf { it.isNotBlank() }?.let { items.add("Web: $it") }
        }
        if (items.isEmpty()) items.add("Inicie o Coach e jogue normalmente. As recomendações ficam melhores a cada sessão observada.")
        return items.take(6)
    }

    fun coverageText(summary: JSONObject?): String {
        if (summary == null) return "0%"
        return "${summary.optInt("coveragePercent",0)}%"
    }

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
