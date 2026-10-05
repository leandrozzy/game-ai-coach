package com.gameaicoach

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

class MainActivity:AppCompatActivity(){
 private lateinit var spinner:Spinner
 private lateinit var body:LinearLayout
 private lateinit var status:TextView
 private lateinit var start:Button
 private lateinit var stop:Button
 private lateinit var reanalyze:Button
 private lateinit var importKeys:Button
 private val games=listOf("Marvel Strike Force","Saint Seiya Awakening","F1 Clash")
 private val bg=Color.rgb(7,13,23)
 private val panel=Color.rgb(16,27,44)
 private val blue=Color.rgb(45,122,255)
 private val green=Color.rgb(92,224,144)
 private val amber=Color.rgb(255,190,92)
 private val muted=Color.rgb(148,163,184)
 private val vercel="https://game-ai-coach-indol.vercel.app"
 private val osm="https://osm-ai-coach-pro-cloud-v4.vercel.app/?exportGameCoach=1"

 private val captureLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->
  if(r.resultCode==Activity.RESULT_OK&&r.data!=null){
   val game=spinner.selectedItem.toString()
   Store.saveActiveGame(this,game)
   Store.beginSession(this,game)
   val i=Intent(this,CaptureService::class.java)
    .putExtra("resultCode",r.resultCode)
    .putExtra("data",r.data)
   if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
   refresh()
   launchGame(game)
  }else{
   Toast.makeText(this,"Captura não autorizada.",Toast.LENGTH_LONG).show()
  }
 }

 override fun onCreate(savedInstanceState:Bundle?){
  super.onCreate(savedInstanceState)
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),2)
  }
  handleImport(intent)
  buildUi()
  Store.recover(this,Store.loadActiveGame(this))
  refresh()
 }

 override fun onNewIntent(intent:Intent){
  super.onNewIntent(intent)
  setIntent(intent)
  if(handleImport(intent)){
   Toast.makeText(this,"APIs do OSM conectadas.",Toast.LENGTH_SHORT).show()
   refresh()
  }
 }

 override fun onResume(){
  super.onResume()
  if(::spinner.isInitialized){
   Store.recover(this,spinner.selectedItem?.toString()?:Store.loadActiveGame(this))
   refresh()
  }
 }

 private fun handleImport(i:Intent?):Boolean{
  val d=i?.data?:return false
  if(d.scheme!="gameaicoach"||d.host!="import")return false
  return try{
   val j=JSONObject(d.getQueryParameter("data")?:"")
   val g=j.optString("google").trim()
   val q=j.optString("groq").trim()
   if(g.isBlank()&&q.isBlank())false else{
    Store.saveApiKeys(this,g,q)
    true
   }
  }catch(_:Exception){false}
 }

 private fun buildUi(){
  val scroll=ScrollView(this).apply{setBackgroundColor(bg)}
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(22,28,22,48)
  }
  scroll.addView(root)

  root.addView(text("GAME AI COACH",30f,true))
  root.addView(text("v2.3.0 • sessão persistente",12f,true,green))
  root.addView(text("Seu plano de evolução, atualizado enquanto você joga",14f,false,muted))
  root.addView(space(14))

  spinner=Spinner(this)
  spinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,games)
  spinner.setSelection(games.indexOf(Store.loadActiveGame(this)).coerceAtLeast(0))
  spinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
   override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){
    Store.saveActiveGame(this@MainActivity,games[position])
    Store.recover(this@MainActivity,games[position])
    refresh()
   }
  }
  root.addView(card(spinner,Color.rgb(20,33,53)))

  status=text("",13f,true)
  root.addView(status)

  importKeys=button("⇩ IMPORTAR APIs DO OSM",Color.rgb(101,84,192))
  importKeys.setOnClickListener{
   startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(osm)))
  }
  root.addView(importKeys,LinearLayout.LayoutParams(-1,-2).apply{topMargin=8})

  val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  start=button("▶ INICIAR",blue)
  start.setOnClickListener{
   val game=spinner.selectedItem.toString()
   Store.beginSession(this,game)
   val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
   captureLauncher.launch(mgr.createScreenCaptureIntent())
  }
  stop=button("■ FINALIZAR",Color.rgb(65,80,104))
  stop.setOnClickListener{
   val i=Intent(this,CaptureService::class.java).setAction(CaptureService.ACTION_FINISH)
   if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
   Toast.makeText(this,"Salvando e analisando…",Toast.LENGTH_SHORT).show()
   Handler(Looper.getMainLooper()).postDelayed({refresh()},1600)
  }
  row.addView(start,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=7})
  row.addView(stop,LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=7})
  root.addView(row,LinearLayout.LayoutParams(-1,-2).apply{topMargin=8})

  reanalyze=button("↻ REANALISAR ÚLTIMA SESSÃO",Color.rgb(39,154,117))
  reanalyze.setOnClickListener{reanalyze()}
  root.addView(reanalyze,LinearLayout.LayoutParams(-1,-2).apply{topMargin=10})

  body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  root.addView(body,LinearLayout.LayoutParams(-1,-2).apply{topMargin=12})
  setContentView(scroll)
 }

 private fun reanalyze(){
  val game=spinner.selectedItem.toString()
  Store.recover(this,game)
  val lines=Store.loadRawLines(this,game)
  if(lines.isEmpty()){
   Toast.makeText(this,"Ainda não há texto salvo. Toque INICIAR e abra algumas telas; cada tela agora é salva imediatamente.",Toast.LENGTH_LONG).show()
   return
  }
  if(!Store.hasApiKeys(this)){
   Toast.makeText(this,"Importe as APIs do OSM primeiro.",Toast.LENGTH_LONG).show()
   return
  }

  val summary=Store.loadSummary(this,game)?:JSONObject()
   .put("frames",0)
   .put("uniqueLines",lines.size)
   .put("coveragePercent",50)

  Store.saveServerAnalysis(this,game,JSONObject().put("pending",true))
  refresh()
  Toast.makeText(this,"Reanalisando ${lines.size} linhas salvas…",Toast.LENGTH_SHORT).show()

  val payload=JSONObject().apply{
   put("game",game)
   put("sessionId","reanalyze-"+UUID.randomUUID())
   put("screens",summary.optInt("frames",0))
   put("lines",JSONArray(lines.take(2500)))
   put("localSummary",summary)
   Store.loadPreviousSummary(this@MainActivity,game)?.let{put("previousSummary",it)}
   put("keys",Store.apiKeysJson(this@MainActivity))
  }

  val req=Request.Builder().url("$vercel/api/observe")
   .post(payload.toString().toRequestBody("application/json".toMediaType())).build()

  OkHttpClient.Builder().callTimeout(45,TimeUnit.SECONDS).build().newCall(req).enqueue(object:Callback{
   override fun onFailure(call:Call,e:IOException){
    runOnUiThread{
     Store.saveServerAnalysis(this@MainActivity,game,JSONObject()
      .put("pending",false).put("error","Sessão continua salva; IA indisponível agora."))
     refresh()
    }
   }
   override fun onResponse(call:Call,response:Response){
    val raw=response.body?.string().orEmpty()
    runOnUiThread{
     if(response.isSuccessful){
      runCatching{Store.saveServerAnalysis(this@MainActivity,game,JSONObject(raw))}
       .onFailure{
        Store.saveServerAnalysis(this@MainActivity,game,JSONObject()
         .put("pending",false).put("error","Resposta da IA inválida."))
       }
     }else{
      Store.saveServerAnalysis(this@MainActivity,game,JSONObject()
       .put("pending",false).put("error","Servidor HTTP ${response.code}."))
     }
     refresh()
    }
    response.close()
   }
  })
 }

 private fun refresh(){
  val active=Store.isCaptureActive(this)
  status.text=if(active)"● COACH ATIVO — salvando cada tela" else "● PRONTO"
  status.setTextColor(if(active)green else muted)
  start.isEnabled=!active
  stop.isEnabled=active
  importKeys.text=if(Store.hasApiKeys(this))"✓ APIs DO OSM CONECTADAS" else "⇩ IMPORTAR APIs DO OSM"
  render()
 }

 private fun render(){
  if(!::body.isInitialized)return
  body.removeAllViews()
  val game=spinner.selectedItem?.toString()?:Store.loadActiveGame(this)
  Store.recover(this,game)
  val lines=Store.loadRawLines(this,game)
  val summary=Store.loadSummary(this,game)
  val server=Store.loadServerAnalysis(this,game)

  if(server?.optBoolean("pending",false)==true){
   hero("ANALISANDO","A sessão já está salva. Gerando prioridades, upgrades e times…",blue)
   diagnostic(lines.size,summary)
   return
  }

  val err=server?.optString("error").orEmpty()
  if(err.isNotBlank()){
   hero("SESSÃO PRESERVADA",err,amber)
   diagnostic(lines.size,summary)
   return
  }

  if(CoachEngine.connected(server)){
   hero(CoachEngine.headline(server).ifBlank{"Plano atualizado"},CoachEngine.summary(server),green)

   section("PLANO DE HOJE")
   val plan=CoachEngine.plan(server)
   if(plan.isEmpty())simpleCard("Sem ações específicas retornadas pela IA.",muted)
   plan.forEachIndexed{i,o->
    val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
    b.addView(text("${i+1}. ${o.optString("title").ifBlank{"Prioridade"}}",16f,true))
    if(o.optString("action").isNotBlank())b.addView(text(o.optString("action"),14f,true,Color.rgb(153,197,255)))
    if(o.optString("reason").isNotBlank())b.addView(text(o.optString("reason"),13f,false,Color.rgb(190,201,217)))
    body.addView(card(b,panel))
   }

   val ups=CoachEngine.upgrades(server)
   if(ups.isNotEmpty()){
    section("EVOLUIR AGORA")
    ups.forEach{o->simpleCard("${o.optString("target")}\n${o.optString("reason")}",blue)}
   }

   val teams=CoachEngine.teams(server)
   if(teams.isNotEmpty()){
    section("TIMES / FORMAÇÕES")
    teams.forEach{o->
     val a=o.optJSONArray("units")
     val names=if(a==null)"" else (0 until a.length()).map{a.optString(it)}.joinToString(" • ")
     simpleCard("${o.optString("mode")}\n$names\n${o.optString("why")}",Color.rgb(72,173,255))
    }
   }

   val avoid=CoachEngine.objects(server,"avoid")
   if(avoid.isNotEmpty()){
    section("NÃO GASTE / EVITE")
    avoid.forEach{o->simpleCard("${o.optString("title")}\n${o.optString("reason")}",amber)}
   }
  }else{
   if(lines.isEmpty()){
    hero("PRONTO PARA COLETAR","Toque INICIAR. A primeira tela reconhecida já será salva no aparelho e em Downloads/Game-AI-Coach.",amber)
   }else{
    hero("SESSÃO SALVA","${lines.size} linhas já estão preservadas. Você pode REANALISAR sem entrar no jogo.",green)
   }
  }

  diagnostic(lines.size,summary)
 }

 private fun diagnostic(count:Int,summary:JSONObject?){
  section("DIAGNÓSTICO DE LEITURA")
  simpleCard(
   if(count==0)"Nenhuma sessão salva."
   else "Sessão persistente • Telas: ${summary?.optInt("frames",0)?:0} • Linhas: $count",
   muted
  )
 }

 private fun hero(title:String,bodyText:String,color:Int){
  val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  b.addView(text(title,20f,true,color))
  b.addView(text(bodyText,14f,false,Color.rgb(210,219,232)))
  body.addView(card(b,Color.rgb(18,31,50)))
 }

 private fun section(s:String){
  body.addView(text(s,12.5f,true,Color.rgb(123,146,178)).apply{setPadding(2,18,0,7)})
 }

 private fun simpleCard(s:String,color:Int){
  body.addView(card(text(s,13.5f,false,color),panel))
 }

 private fun text(t:String,size:Float,bold:Boolean,color:Int=Color.WHITE)=TextView(this).apply{
  text=t
  textSize=size
  setTextColor(color)
  if(bold)setTypeface(typeface,Typeface.BOLD)
  setPadding(0,4,0,4)
 }

 private fun button(label:String,color:Int)=Button(this).apply{
  text=label
  setTextColor(Color.WHITE)
  textSize=13f
  background=round(color,16)
 }

 private fun card(view:View,color:Int)=LinearLayout(this).apply{
  orientation=LinearLayout.VERTICAL
  setPadding(17,15,17,15)
  background=round(color,21)
  addView(view,LinearLayout.LayoutParams(-1,-2))
  layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=6;bottomMargin=6}
 }

 private fun round(color:Int,r:Int)=GradientDrawable().apply{
  setColor(color)
  cornerRadius=r.toFloat()
 }

 private fun space(h:Int)=Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,h)}

 private fun launchGame(name:String){
  val pkg=when{
   name.startsWith("Marvel")->"com.foxnextgames.m3"
   name.startsWith("Saint")->"com.tencent.tmgp.sskeus"
   else->"com.hutchgames.formularacing"
  }
  packageManager.getLaunchIntentForPackage(pkg)?.let{
   it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
   startActivity(it)
  }
 }
}
