package com.gameaicoach

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Store {
 private const val PREFS="coach"
 fun saveApiKeys(context:Context,google:String,groq:String){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("api_google",google.trim()).putString("api_groq",groq.trim()).apply()}
 fun hasApiKeys(context:Context):Boolean{val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);return !p.getString("api_google","").isNullOrBlank()||!p.getString("api_groq","").isNullOrBlank()}
 fun apiKeysJson(context:Context)=JSONObject().apply{val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val g=p.getString("api_google","")?:"";val q=p.getString("api_groq","")?:"";if(g.isNotBlank())put("google",g);if(q.isNotBlank())put("groq",q)}
 fun saveSession(context:Context,game:String,summary:JSONObject,rawLines:List<String>,frames:Int){val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);val key=gameKey(game);summary.put("frames",frames).put("savedAt",System.currentTimeMillis());val h=loadHistory(context,game);h.put(summary);p.edit().putString("${key}_summary",summary.toString()).putString("${key}_history",h.toString()).putString("${key}_lines",JSONArray(rawLines.take(2500)).toString()).apply()}
 fun loadSummary(context:Context,game:String):JSONObject?{val r=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("${gameKey(game)}_summary",null)?:return null;return runCatching{JSONObject(r)}.getOrNull()}
 fun loadRawLines(context:Context,game:String):List<String>{val r=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("${gameKey(game)}_lines","[]")?:"[]";val a=runCatching{JSONArray(r)}.getOrDefault(JSONArray());return buildList{for(i in 0 until a.length()){val s=a.optString(i).trim();if(s.isNotBlank())add(s)}}}
 fun loadPreviousSummary(context:Context,game:String):JSONObject?{val h=loadHistory(context,game);return if(h.length()>=2)h.optJSONObject(h.length()-2) else null}
 fun loadHistory(context:Context,game:String):JSONArray{val r=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("${gameKey(game)}_history","[]")?:"[]";return runCatching{JSONArray(r)}.getOrDefault(JSONArray())}
 fun saveServerAnalysis(context:Context,game:String,json:JSONObject){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("${gameKey(game)}_server",json.toString()).apply()}
 fun loadServerAnalysis(context:Context,game:String):JSONObject?{val r=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("${gameKey(game)}_server",null)?:return null;return runCatching{JSONObject(r)}.getOrNull()}
 fun saveActiveGame(context:Context,game:String){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("activeGame",game).apply()}
 fun loadActiveGame(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("activeGame","Marvel Strike Force")?:"Marvel Strike Force"
 fun setCaptureActive(context:Context,active:Boolean){context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("captureActive",active).apply()}
 fun isCaptureActive(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean("captureActive",false)
 private fun gameKey(game:String)=when{game.startsWith("Saint")->"ssa";game.startsWith("Marvel")->"msf";else->"f1"}
}
