package com.gameaicoach

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Store {
 private const val PREFS="coach"

 private fun key(game:String)=when{
  game.startsWith("Saint")->"ssa"
  game.startsWith("Marvel")->"msf"
  else->"f1"
 }

 private fun sessionFile(context:Context,game:String)=File(context.filesDir,"coach_session_${key(game)}.json")
 private fun checkpointFile(context:Context,game:String)=File(context.filesDir,"coach_checkpoint_${key(game)}.json")
 private fun analysisFile(context:Context,game:String)=File(context.filesDir,"coach_analysis_${key(game)}.json")

 fun saveApiKeys(context:Context,google:String,groq:String){
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
   .putString("api_google",google.trim())
   .putString("api_groq",groq.trim())
   .apply()
 }

 fun hasApiKeys(context:Context):Boolean{
  val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  return !p.getString("api_google","").isNullOrBlank()||!p.getString("api_groq","").isNullOrBlank()
 }

 fun apiKeysJson(context:Context)=JSONObject().apply{
  val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val g=p.getString("api_google","")?:""
  val q=p.getString("api_groq","")?:""
  if(g.isNotBlank())put("google",g)
  if(q.isNotBlank())put("groq",q)
 }

 fun saveCheckpoint(context:Context,game:String,rawLines:List<String>,frames:Int){
  if(rawLines.isEmpty())return
  val lines=rawLines.distinct().take(2500)
  val data=JSONObject()
   .put("game",game)
   .put("frames",frames)
   .put("savedAt",System.currentTimeMillis())
   .put("lines",JSONArray(lines))
  runCatching{checkpointFile(context,game).writeText(data.toString())}
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
   .putString("${key(game)}_checkpoint_lines",JSONArray(lines).toString())
   .putInt("${key(game)}_checkpoint_frames",frames)
   .apply()
 }

 fun saveSession(context:Context,game:String,summary:JSONObject,rawLines:List<String>,frames:Int){
  val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val k=key(game)
  val lines=rawLines.distinct().take(2500)
  val linesJson=JSONArray(lines).toString()
  summary.put("frames",frames)
  summary.put("uniqueLines",lines.size)
  summary.put("savedAt",System.currentTimeMillis())

  val history=loadHistory(context,game)
  history.put(summary)
  while(history.length()>20){
   val n=JSONArray()
   for(i in 1 until history.length())n.put(history.get(i))
   p.edit().putString("${k}_history",n.toString()).apply()
   break
  }

  p.edit()
   .putString("${k}_summary",summary.toString())
   .putString("${k}_lines",linesJson)
   .putString("${k}_history",history.toString())
   .putString("backup_${k}_summary",summary.toString())
   .putString("backup_${k}_lines",linesJson)
   .putString("lastSummary",summary.toString())
   .putString("lastLines",linesJson)
   .putString("lastGame",game)
   .apply()

  val file=JSONObject()
   .put("game",game)
   .put("summary",summary)
   .put("lines",JSONArray(lines))
  runCatching{sessionFile(context,game).writeText(file.toString())}
  runCatching{checkpointFile(context,game).delete()}
 }

 fun recover(context:Context,game:String):Boolean{
  val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val k=key(game)

  val currentLines=p.getString("${k}_lines",null)
  val currentSummary=p.getString("${k}_summary",null)
  if(!currentLines.isNullOrBlank()&&currentLines!="[]"&&!currentSummary.isNullOrBlank())return true

  // 1. Full local file
  runCatching{
   val f=sessionFile(context,game)
   if(f.exists()){
    val j=JSONObject(f.readText())
    val lines=j.optJSONArray("lines")
    val summary=j.optJSONObject("summary")
    if(lines!=null&&lines.length()>0){
     val s=summary?:minimalSummary(lines.length(),j.optInt("frames",0))
     p.edit().putString("${k}_lines",lines.toString()).putString("${k}_summary",s.toString()).apply()
     return true
    }
   }
  }

  // 2. Checkpoint file saved while playing
  runCatching{
   val f=checkpointFile(context,game)
   if(f.exists()){
    val j=JSONObject(f.readText())
    val lines=j.optJSONArray("lines")
    if(lines!=null&&lines.length()>0){
     val s=minimalSummary(lines.length(),j.optInt("frames",0))
     p.edit().putString("${k}_lines",lines.toString()).putString("${k}_summary",s.toString()).apply()
     return true
    }
   }
  }

  // 3. Older SharedPreferences formats
  val legacyLines=listOf(
   p.getString("backup_${k}_lines",null),
   p.getString("lastLines",null),
   p.getString("${k}_checkpoint_lines",null),
   p.getString("${k}_rawLines",null),
   p.getString("rawLines",null)
  ).firstOrNull{!it.isNullOrBlank()&&it!="[]"}

  if(!legacyLines.isNullOrBlank()){
   val count=runCatching{JSONArray(legacyLines).length()}.getOrDefault(0)
   if(count>0){
    val oldSummary=listOf(
     p.getString("backup_${k}_summary",null),
     p.getString("lastSummary",null),
     p.getString("summary",null)
    ).firstOrNull{!it.isNullOrBlank()}
    val summary=oldSummary?.let{runCatching{JSONObject(it)}.getOrNull()}
     ?:minimalSummary(count,p.getInt("${k}_checkpoint_frames",p.getInt("lastScreens",0)))
    p.edit().putString("${k}_lines",legacyLines).putString("${k}_summary",summary.toString()).apply()
    return true
   }
  }
  return false
 }

 private fun minimalSummary(lines:Int,frames:Int):JSONObject{
  val coverage=when{
   lines>=800->71
   lines>=600->57
   lines>=400->50
   lines>=200->43
   else->29
  }
  return JSONObject()
   .put("frames",frames)
   .put("uniqueLines",lines)
   .put("coveragePercent",coverage)
   .put("status","Sessão recuperada")
   .put("savedAt",System.currentTimeMillis())
 }

 fun loadSummary(context:Context,game:String):JSONObject?{
  recover(context,game)
  val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("${key(game)}_summary",null)?:return null
  return runCatching{JSONObject(raw)}.getOrNull()
 }

 fun loadRawLines(context:Context,game:String):List<String>{
  recover(context,game)
  val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("${key(game)}_lines","[]")?:"[]"
  val arr=runCatching{JSONArray(raw)}.getOrDefault(JSONArray())
  return buildList{
   for(i in 0 until arr.length()){
    val s=arr.optString(i).trim()
    if(s.isNotBlank())add(s)
   }
  }
 }

 fun loadPreviousSummary(context:Context,game:String):JSONObject?{
  val h=loadHistory(context,game)
  return if(h.length()>=2)h.optJSONObject(h.length()-2) else null
 }

 fun loadHistory(context:Context,game:String):JSONArray{
  val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("${key(game)}_history","[]")?:"[]"
  return runCatching{JSONArray(raw)}.getOrDefault(JSONArray())
 }

 fun saveServerAnalysis(context:Context,game:String,json:JSONObject){
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
   .putString("${key(game)}_server",json.toString()).apply()
  runCatching{analysisFile(context,game).writeText(json.toString())}
 }

 fun loadServerAnalysis(context:Context,game:String):JSONObject?{
  val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val raw=p.getString("${key(game)}_server",null)
  if(!raw.isNullOrBlank())return runCatching{JSONObject(raw)}.getOrNull()
  return runCatching{
   val f=analysisFile(context,game)
   if(f.exists())JSONObject(f.readText()) else null
  }.getOrNull()
 }

 fun saveActiveGame(context:Context,game:String){
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("activeGame",game).apply()
 }

 fun loadActiveGame(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  .getString("activeGame","Marvel Strike Force")?:"Marvel Strike Force"

 fun setCaptureActive(context:Context,active:Boolean){
  context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean("captureActive",active).apply()
 }

 fun isCaptureActive(context:Context)=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  .getBoolean("captureActive",false)
}
