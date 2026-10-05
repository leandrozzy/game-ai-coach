package com.gameaicoach

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
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

 private fun localSessionFile(context:Context,game:String)=File(context.filesDir,"coach_session_${key(game)}.json")
 private fun localCheckpointFile(context:Context,game:String)=File(context.filesDir,"coach_checkpoint_${key(game)}.json")
 private fun analysisFile(context:Context,game:String)=File(context.filesDir,"coach_analysis_${key(game)}.json")
 private fun publicName(game:String)="game-ai-coach-${key(game)}-session.json"

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
  val g=p.getString("api_google","")?:""; val q=p.getString("api_groq","")?:""
  if(g.isNotBlank())put("google",g)
  if(q.isNotBlank())put("groq",q)
 }

 fun beginSession(context:Context,game:String){
  val seed=JSONObject()
   .put("game",game)
   .put("frames",0)
   .put("savedAt",System.currentTimeMillis())
   .put("lines",JSONArray())
   .put("inProgress",true)
  writeAtomic(localCheckpointFile(context,game),seed.toString())
  writePublic(context,game,seed.toString())
 }

 fun saveCheckpoint(context:Context,game:String,rawLines:List<String>,frames:Int){
  val lines=rawLines.distinct().take(2500)
  val data=JSONObject()
   .put("game",game)
   .put("frames",frames)
   .put("savedAt",System.currentTimeMillis())
   .put("lines",JSONArray(lines))
   .put("inProgress",true)
  val text=data.toString()
  writeAtomic(localCheckpointFile(context,game),text)
  writePublic(context,game,text)
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

  val data=JSONObject()
   .put("game",game)
   .put("summary",summary)
   .put("frames",frames)
   .put("savedAt",System.currentTimeMillis())
   .put("lines",JSONArray(lines))
   .put("inProgress",false)

  val text=data.toString()
  writeAtomic(localSessionFile(context,game),text)
  writeAtomic(localCheckpointFile(context,game),text)
  writePublic(context,game,text)
 }

 fun recover(context:Context,game:String):Boolean{
  val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
  val k=key(game)

  val currentLines=p.getString("${k}_lines",null)
  val currentSummary=p.getString("${k}_summary",null)
  if(!currentLines.isNullOrBlank()&&currentLines!="[]"&&!currentSummary.isNullOrBlank())return true

  val candidates=mutableListOf<String>()

  runCatching{
   val f=localSessionFile(context,game)
   if(f.exists())candidates.add(f.readText())
  }
  runCatching{
   val f=localCheckpointFile(context,game)
   if(f.exists())candidates.add(f.readText())
  }
  readPublic(context,game)?.let{candidates.add(it)}

  for(raw in candidates){
   val ok=runCatching{
    val j=JSONObject(raw)
    val lines=j.optJSONArray("lines")?:JSONArray()
    if(lines.length()==0)return@runCatching false
    val summary=j.optJSONObject("summary")?:minimalSummary(lines.length(),j.optInt("frames",0))
    p.edit().putString("${k}_lines",lines.toString()).putString("${k}_summary",summary.toString()).apply()
    true
   }.getOrDefault(false)
   if(ok)return true
  }

  val legacy=listOf(
   p.getString("backup_${k}_lines",null),
   p.getString("lastLines",null),
   p.getString("${k}_checkpoint_lines",null)
  ).firstOrNull{!it.isNullOrBlank()&&it!="[]"}

  if(!legacy.isNullOrBlank()){
   val count=runCatching{JSONArray(legacy).length()}.getOrDefault(0)
   if(count>0){
    val oldSummary=listOf(p.getString("backup_${k}_summary",null),p.getString("lastSummary",null))
     .firstOrNull{!it.isNullOrBlank()}
    val summary=oldSummary?.let{runCatching{JSONObject(it)}.getOrNull()}
     ?:minimalSummary(count,p.getInt("${k}_checkpoint_frames",0))
    p.edit().putString("${k}_lines",legacy).putString("${k}_summary",summary.toString()).apply()
    return true
   }
  }
  return false
 }

 private fun minimalSummary(lines:Int,frames:Int)=JSONObject()
  .put("frames",frames)
  .put("uniqueLines",lines)
  .put("coveragePercent",when{
   lines>=800->71
   lines>=600->57
   lines>=400->50
   lines>=200->43
   else->29
  })
  .put("status","Sessão recuperada")
  .put("savedAt",System.currentTimeMillis())

 private fun writeAtomic(file:File,text:String){
  runCatching{
   val tmp=File(file.parentFile,file.name+".tmp")
   tmp.writeText(text)
   if(file.exists())file.delete()
   tmp.renameTo(file)
  }
 }

 private fun writePublic(context:Context,game:String,text:String){
  if(Build.VERSION.SDK_INT<29)return
  runCatching{
   val resolver=context.contentResolver
   val collection=MediaStore.Downloads.EXTERNAL_CONTENT_URI
   val name=publicName(game)
   val projection=arrayOf(MediaStore.Downloads._ID)
   val selection="${MediaStore.Downloads.DISPLAY_NAME}=?"
   val args=arrayOf(name)
   var uri:Uri?=null
   resolver.query(collection,projection,selection,args,null)?.use{c->
    if(c.moveToFirst()){
     val id=c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
     uri=Uri.withAppendedPath(collection,id.toString())
    }
   }
   if(uri==null){
    val values=ContentValues().apply{
     put(MediaStore.Downloads.DISPLAY_NAME,name)
     put(MediaStore.Downloads.MIME_TYPE,"application/json")
     put(MediaStore.Downloads.RELATIVE_PATH,"Download/Game-AI-Coach")
     put(MediaStore.Downloads.IS_PENDING,1)
    }
    uri=resolver.insert(collection,values)
   }
   uri?.let{u->
    resolver.openOutputStream(u,"wt")?.use{it.write(text.toByteArray())}
    val done=ContentValues().apply{put(MediaStore.Downloads.IS_PENDING,0)}
    resolver.update(u,done,null,null)
   }
  }
 }

 private fun readPublic(context:Context,game:String):String?{
  if(Build.VERSION.SDK_INT<29)return null
  return runCatching{
   val resolver=context.contentResolver
   val collection=MediaStore.Downloads.EXTERNAL_CONTENT_URI
   val name=publicName(game)
   val projection=arrayOf(MediaStore.Downloads._ID)
   val selection="${MediaStore.Downloads.DISPLAY_NAME}=?"
   val args=arrayOf(name)
   var result:String?=null
   resolver.query(collection,projection,selection,args,"${MediaStore.Downloads.DATE_MODIFIED} DESC")?.use{c->
    if(c.moveToFirst()){
     val id=c.getLong(c.getColumnIndexOrThrow(MediaStore.Downloads._ID))
     val uri=Uri.withAppendedPath(collection,id.toString())
     result=resolver.openInputStream(uri)?.bufferedReader()?.use{it.readText()}
    }
   }
   result
  }.getOrNull()
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
  writeAtomic(analysisFile(context,game),json.toString())
 }

 fun loadServerAnalysis(context:Context,game:String):JSONObject?{
  val raw=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString("${key(game)}_server",null)
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
