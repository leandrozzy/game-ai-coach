package com.gameaicoach

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object Store {
 private const val PREFS="coach"

 fun saveApiKeys(context:Context,google:String,groq:String){
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
   .putString("api_google",google.trim()).putString("api_groq",groq.trim()).apply()
 }

 fun hasApiKeys(context:Context):Boolean{
  val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  return !p.getString("api_google","").isNullOrBlank()||!p.getString("api_groq","").isNullOrBlank()
 }

 fun apiKeysJson(context:Context)=JSONObject().apply{
  val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val g=p.getString("api_google","")?:"";val q=p.getString("api_groq","")?:""
  if(g.isNotBlank())put("google",g);if(q.isNotBlank())put("groq",q)
 }

 fun saveSession(context:Context,game:String,summary:JSONObject,rawLines:List<String>,frames:Int){
  val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val key=gameKey(game)
  summary.put("frames",frames).put("savedAt",System.currentTimeMillis())
  val h=loadHistory(context,game);h.put(summary)
  val linesJson=JSONArray(rawLines.take(2500)).toString()
  p.edit()
   .putString("${key}_summary",summary.toString())
   .putString("${key}_history",h.toString())
   .putString("${key}_lines",linesJson)
   .putString("backup_${key}_summary",summary.toString())
   .putString("backup_${key}_lines",linesJson)
   .putString("lastSummary",summary.toString())
   .putString("lastLines",linesJson)
   .putString("lastGame",game)
   .apply()
 }

 fun migrateLegacyIfNeeded(context:Context,game:String):Boolean{
  val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val key=gameKey(game)
  if(!p.getString("${key}_lines",null).isNullOrBlank() && !p.getString("${key}_summary",null).isNullOrBlank()) return true

  val linesRaw=listOf(
   p.getString("backup_${key}_lines",null),
   p.getString("lastLines",null),
   p.getString("${key}_rawLines",null),
   p.getString("rawLines",null)
  ).firstOrNull{!it.isNullOrBlank() && it!="[]"}

  if(linesRaw.isNullOrBlank()) return false
  val lineCount=runCatching{JSONArray(linesRaw).length()}.getOrDefault(0)
  if(lineCount==0) return false

  var summaryRaw=listOf(
   p.getString("backup_${key}_summary",null),
   p.getString("lastSummary",null),
   p.getString("summary",null)
  ).firstOrNull{!it.isNullOrBlank()}

  if(summaryRaw.isNullOrBlank()){
   val screens=p.getInt("lastScreens",0)
   val unique=p.getInt("lastUniqueLines",lineCount)
   val coverage=when{
    unique>=700->71
    unique>=400->57
    unique>=200->43
    else->29
   }
   summaryRaw=JSONObject()
    .put("frames",screens)
    .put("uniqueLines",unique)
    .put("coveragePercent",coverage)
    .put("savedAt",p.getLong("lastFinishedAt",System.currentTimeMillis()))
    .toString()
  }

  p.edit()
   .putString("${key}_lines",linesRaw)
   .putString("${key}_summary",summaryRaw)
   .putString("backup_${key}_lines",linesRaw)
   .putString("backup_${key}_summary",summaryRaw)
   .apply()
  return true
 }

 fun loadSummary(context:Context,game:String):JSONObject?{
  migrateLegacyIfNeeded(context,game)
  val r=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("${gameKey(game)}_summary",null)?:return null
  return runCatching{JSONObject(r)}.getOrNull()
 }

 fun loadRawLines(context:Context,game:String):List<String>{
  migrateLegacyIfNeeded(context,game)
  val r=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("${gameKey(game)}_lines","[]")?:"[]"
  val a=runCatching{JSONArray(r)}.getOrDefault(JSONArray())
  return buildList{for(i in 0 until a.length()){val s=a.optString(i).trim();if(s.isNotBlank())add(s)}}
 }

 fun loadPreviousSummary(context:Context,game:String):JSONObject?{
  val h=loadHistory(context,game)
  return if(h.length()>=2)h.optJSONObject(h.length()-2) else null
 }

 fun loadHistory(context:Context,game:String):JSONArray{
  val r=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("${gameKey(game)}_history","[]")?:"[]"
  return runCatching{JSONArray(r)}.getOrDefault(JSONArray())
 }

 fun saveServerAnalysis(context:Context,game:String,json:JSONObject){
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("${gameKey(game)}_server",json.toString()).apply()
 }

 fun loadServerAnalysis(context:Context,game:String):JSONObject?{
  val r=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("${gameKey(game)}_server",null)?:return null
  return runCatching{JSONObject(r)}.getOrNull()
 }

 fun saveActiveGame(context:Context,game:String){
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("activeGame",game).apply()
 }

 fun loadActiveGame(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  .getString("activeGame","Marvel Strike Force")?:"Marvel Strike Force"

 fun setCaptureActive(context:Context,active:Boolean){
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("captureActive",active).apply()
 }

 fun isCaptureActive(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean("captureActive",false)

 private fun gameKey(game:String)=when{
  game.startsWith("Saint")->"ssa"
  game.startsWith("Marvel")->"msf"
  else->"f1"
 }
}
