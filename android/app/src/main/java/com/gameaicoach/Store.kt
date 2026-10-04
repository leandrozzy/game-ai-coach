package com.gameaicoach

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Store {
    private const val PREFS = "coach"

    fun saveSession(context: Context, game: String, summary: JSONObject, rawLines: List<String>, frames: Int) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = gameKey(game)
        val previous = p.getString("${key}_summary", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val prevEntities = previous?.optJSONArray("entities")?.let { arr ->
            buildSet { for (i in 0 until arr.length()) add(arr.optString(i)) }
        } ?: emptySet()
        val entities = summary.optJSONArray("entities") ?: JSONArray()
        var newEntities = 0
        for (i in 0 until entities.length()) if (!prevEntities.contains(entities.optString(i))) newEntities++
        summary.put("newEntities", newEntities)
        summary.put("frames", frames)
        summary.put("savedAt", System.currentTimeMillis())
        val history = loadHistory(context, game)
        history.put(summary)
        while (history.length() > 20) {
            val compact = JSONArray()
            for (i in 1 until history.length()) compact.put(history.get(i))
            p.edit().putString("${key}_history", compact.toString()).apply()
            return saveSessionAfterTrim(context, game, summary, rawLines, frames, compact)
        }
        p.edit()
            .putString("${key}_summary", summary.toString())
            .putString("${key}_history", history.toString())
            .putString("${key}_lines", JSONArray(rawLines.take(2500)).toString())
            .putString("lastGame", game)
            .apply()
    }

    private fun saveSessionAfterTrim(context: Context, game: String, summary: JSONObject, rawLines: List<String>, frames: Int, history: JSONArray) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = gameKey(game)
        p.edit()
            .putString("${key}_summary", summary.toString())
            .putString("${key}_history", history.toString())
            .putString("${key}_lines", JSONArray(rawLines.take(2500)).toString())
            .putString("lastGame", game)
            .apply()
    }

    fun loadSummary(context: Context, game: String): JSONObject? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("${gameKey(game)}_summary", null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun loadHistory(context: Context, game: String): JSONArray {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("${gameKey(game)}_history", "[]") ?: "[]"
        return runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
    }

    fun saveServerAnalysis(context: Context, game: String, json: JSONObject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("${gameKey(game)}_server", json.toString())
            .apply()
    }

    fun loadServerAnalysis(context: Context, game: String): JSONObject? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("${gameKey(game)}_server", null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun saveActiveGame(context: Context, game: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("activeGame", game).apply()
    }

    fun loadActiveGame(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString("activeGame", "Marvel Strike Force") ?: "Marvel Strike Force"

    fun setCaptureActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("captureActive", active).apply()
    }

    fun isCaptureActive(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("captureActive", false)

    private fun gameKey(game: String): String = when {
        game.startsWith("Saint") -> "ssa"
        game.startsWith("Marvel") -> "msf"
        else -> "f1"
    }
}
