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
 private lateinit var gameSpinner:Spinner
 private lateinit var content:LinearLayout
 private lateinit var status:TextView
 private lateinit var start:Button
 private lateinit var stop:Button
 private lateinit var reanalyze:Button
 private lateinit var importKeys:Button
 private val games=listOf("Marvel Strike Force","Saint Seiya Awakening","F1 Clash")
 private val blue=Color.rgb(45,122,255);private val bg=Color.rgb(7,13,23);private val panel=Color.rgb(16,27,44)
 private val muted=Color.rgb(148,163,184);private val green=Color.rgb(92,224,144);private val amber=Color.rgb(255,190,92)
 private val vercel="https://game-ai-coach-indol.vercel.app"
 private val osm="https://osm-ai-coach-pro-cloud-v4.vercel.app/?exportGameCoach=1"

 private val projectionLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->
  if(r.resultCode==Activity.RESULT_OK&&r.data!=null){
   val game=gameSpinner.selectedItem.toString();Store.saveActiveGame(this,game)
   val i=Intent(this,CaptureService::class.java).putExtra("resultCode",r.resultCode).putExtra("data",r.data)
   if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
   refresh();launchSelectedGame(game)
  }else Toast.makeText(this,"Captura não autorizada.",Toast.LENGTH_LONG).show()
 }

 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),2)
  handleImportIntent(intent)
  Store.migrateLegacyIfNeeded(this,Store.loadActiveGame(this))
  buildUi();refresh()
 }

 override fun onNewIntent(intent:Intent){
  super.onNewIntent(intent);setIntent(intent)
  if(handleImportIntent(intent)&&::content.isInitialized){
   refresh();Toast.makeText(this,"APIs do OSM importadas com sucesso.",Toast.LENGTH_LONG).show()
  }
 }

 override fun onResume(){super.onResume();if(::content.isInitialized)refresh()}

 private fun handleImportIntent(i:Intent?):Boolean{
  val data=i?.data?:return false
  if(data.scheme!="gameaicoach"||data.host!="import")return false
  val payload=data.getQueryParameter("data")?:return false
  return try{
   val json=JSONObject(payload)
   val google=json.optString("google").trim()
   val groq=json.optString("groq").trim()
   if(google.isBlank()&&groq.isBlank())false else{Store.saveApiKeys(this,google,groq);true}
  }catch(_:Exception){false}
 }

 private fun buildUi(){
  val scroll=ScrollView(this).apply{setBackgroundColor(bg);isFillViewport=true}
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(22,30,22,50)}
  scroll.addView(root,ViewGroup.LayoutParams(-1,-2))
  root.addView(txt("GAME AI COACH",30f,true))
  root.addView(txt("Seu plano de evolução, atualizado enquanto você joga",14f,false,muted))
  root.addView(space(14))
  gameSpinner=Spinner(this);gameSpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,games)
  gameSpinner.setSelection(games.indexOf(Store.loadActiveGame(this)).coerceAtLeast(0))
  gameSpinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
   override fun onNothingSelected(p:android.widget.AdapterView<*>?){}
   override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,pos:Int,id:Long){Store.saveActiveGame(this@MainActivity,games[pos]);Store.migrateLegacyIfNeeded(this@MainActivity,games[pos]);refresh()}
  }
  root.addView(card(gameSpinner,Color.rgb(20,33,53)))
  status=txt("",13f,true);root.addView(status)

  importKeys=button("⇩ IMPORTAR APIs DO OSM",Color.rgb(101,84,192)).apply{
   setOnClickListener{
    try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(osm)))}
    catch(_:Exception){Toast.makeText(this@MainActivity,"Não consegui abrir o OSM.",Toast.LENGTH_LONG).show()}
   }
  }
  root.addView(importKeys,LinearLayout.LayoutParams(-1,-2).apply{topMargin=8})

  val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  start=button("▶ INICIAR",blue).apply{setOnClickListener{projectionLauncher.launch((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent())}}
  stop=button("■ FINALIZAR",Color.rgb(65,80,104)).apply{setOnClickListener{
   val g=gameSpinner.selectedItem.toString();Store.saveServerAnalysis(this@MainActivity,g,JSONObject().put("pending",true))
   val i=Intent(this@MainActivity,CaptureService::class.java).setAction(CaptureService.ACTION_FINISH)
   if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
   Toast.makeText(this@MainActivity,"Gerando seu plano…",Toast.LENGTH_SHORT).show();poll(g)
  }}
  row.addView(start,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=7});row.addView(stop,LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=7})
  root.addView(row,LinearLayout.LayoutParams(-1,-2).apply{topMargin=8})

  reanalyze=button("↻ REANALISAR ÚLTIMA SESSÃO",Color.rgb(39,154,117)).apply{setOnClickListener{reanalyzeLastSession()}}
  root.addView(reanalyze,LinearLayout.LayoutParams(-1,-2).apply{topMargin=10})
  root.addView(space(14))

  content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(content)
  setContentView(scroll)
 }

 private fun reanalyzeLastSession(){
  val game=gameSpinner.selectedItem.toString()
  Store.migrateLegacyIfNeeded(this,game)
  val lines=Store.loadRawLines(this,game);val local=Store.loadSummary(this,game)
  if(lines.isEmpty()||local==null){
   Toast.makeText(this,"Nenhuma sessão salva foi encontrada nesta instalação. Faça uma coleta; ela ficará preservada nas próximas versões.",Toast.LENGTH_LONG).show()
   return
  }
  if(!Store.hasApiKeys(this)){Toast.makeText(this,"Importe as APIs do OSM primeiro.",Toast.LENGTH_LONG).show();return}
  Store.saveServerAnalysis(this,game,JSONObject().put("pending",true));refresh()
  Toast.makeText(this,"Reanalisando ${lines.size} linhas já salvas…",Toast.LENGTH_SHORT).show()

  val payload=JSONObject().apply{
   put("game",game);put("sessionId","reanalyze-"+UUID.randomUUID().toString());put("screens",local.optInt("frames",0))
   put("lines",JSONArray(lines.take(2500)));put("localSummary",local);Store.loadPreviousSummary(this@MainActivity,game)?.let{put("previousSummary",it)}
   put("keys",Store.apiKeysJson(this@MainActivity))
  }
  val req=Request.Builder().url("$vercel/api/observe").post(payload.toString().toRequestBody("application/json".toMediaType())).build()
  OkHttpClient.Builder().callTimeout(45,TimeUnit.SECONDS).build().newCall(req).enqueue(object:Callback{
   override fun onFailure(c:Call,e:IOException){runOnUiThread{
    Store.saveServerAnalysis(this@MainActivity,game,JSONObject().put("ok",false).put("pending",false).put("error","Não foi possível consultar a IA agora."))
    refresh();Toast.makeText(this@MainActivity,"Falha ao reanalisar.",Toast.LENGTH_LONG).show()
   }}
   override fun onResponse(c:Call,r:Response){
    val body=r.body?.string().orEmpty()
    runOnUiThread{
     if(r.isSuccessful)runCatching{Store.saveServerAnalysis(this@MainActivity,game,JSONObject(body))}
     else Store.saveServerAnalysis(this@MainActivity,game,JSONObject().put("ok",false).put("pending",false).put("error","Servidor respondeu HTTP ${r.code}."))
     refresh()
     Toast.makeText(this@MainActivity,if(r.isSuccessful)"Plano atualizado." else "Reanálise não concluída.",Toast.LENGTH_SHORT).show()
    };r.close()
   }
  })
  poll(game)
 }

 private fun poll(game:String){var tries=0;val h=Handler(Looper.getMainLooper());val r=object:Runnable{override fun run(){tries++;refresh();val s=Store.loadServerAnalysis(this@MainActivity,game);if(s!=null&&!s.optBoolean("pending",false))return;if(tries<35)h.postDelayed(this,1200)}};h.postDelayed(r,900)}

 private fun refresh(){
  val active=Store.isCaptureActive(this);status.text=if(active)"● COACH ATIVO — jogue normalmente" else "● PRONTO"
  status.setTextColor(if(active)green else muted);start.isEnabled=!active;stop.isEnabled=active
  if(::reanalyze.isInitialized)reanalyze.isEnabled=!active
  if(::importKeys.isInitialized)importKeys.text=if(Store.hasApiKeys(this))"✓ APIs DO OSM CONECTADAS" else "⇩ IMPORTAR APIs DO OSM"
  render()
 }

 private fun render(){
  if(!::content.isInitialized)return;content.removeAllViews()
  val game=gameSpinner.selectedItem?.toString()?:Store.loadActiveGame(this)
  Store.migrateLegacyIfNeeded(this,game)
  val local=Store.loadSummary(this,game);val server=Store.loadServerAnalysis(this,game)
  if(!Store.hasApiKeys(this)){hero("CONECTE AS APIs DO OSM","Toque em IMPORTAR APIs DO OSM. O OSM abre, lê as chaves já salvas e volta automaticamente.",amber);diagnostic(local);return}
  if(server?.optBoolean("pending",false)==true){hero("ANALISANDO","Recalculando seu plano com IA…",blue);diagnostic(local);return}
  if(server?.optString("error")?.isNotBlank()==true){hero("SESSÃO SALVA",server.optString("error"),amber);diagnostic(local);return}
  val connected=CoachEngine.connected(server);val headline=CoachEngine.headline(server)
  if(!connected){hero(if(headline.isBlank())"PRONTO PARA ANALISAR" else headline,CoachEngine.summary(server).ifBlank{if(local==null)"Nenhuma sessão salva nesta instalação." else "Use REANALISAR ÚLTIMA SESSÃO."},amber);diagnostic(local);return}
  hero(headline.ifBlank{"Plano atualizado"},CoachEngine.summary(server),green)
  val meta=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  meta.addView(stat("CONFIANÇA","${CoachEngine.confidence(server)}%"),LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=5})
  meta.addView(stat("UNIDADES",CoachEngine.units(server).size.toString()),LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=5;marginEnd=5})
  meta.addView(stat("AÇÕES",CoachEngine.plan(server).size.toString()),LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=5})
  content.addView(meta)
  section("PLANO DE HOJE");CoachEngine.plan(server).forEachIndexed{i,o->planCard(i+1,o.optString("title"),o.optString("action"),o.optString("reason"),o.optString("impact"))}
  val ups=CoachEngine.upgrades(server);if(ups.isNotEmpty()){section("EVOLUIR AGORA");ups.forEach{o->infoCard(o.optString("target"),listOf(o.optString("current"),o.optString("next")).filter{it.isNotBlank()}.joinToString(" → "),o.optString("reason"),blue)}}
  val teams=CoachEngine.teams(server);if(teams.isNotEmpty()){section("TIMES / FORMAÇÕES");teams.forEach{o->val a=o.optJSONArray("units");val names=if(a==null)"" else (0 until a.length()).map{a.optString(it)}.filter{it.isNotBlank()}.joinToString(" • ");infoCard(o.optString("mode"),names,o.optString("why"),Color.rgb(72,173,255))}}
  val avoid=CoachEngine.objects(server,"avoid");if(avoid.isNotEmpty()){section("NÃO GASTE / EVITE");avoid.forEach{o->infoCard(o.optString("title"),"",o.optString("reason"),amber)}}
  val resources=CoachEngine.resources(server);if(resources.isNotEmpty()){section("RECURSOS");resources.forEach{o->infoCard(o.optString("name"),o.optString("value"),o.optString("advice"),Color.rgb(177,136,255))}}
  val changes=CoachEngine.strings(server,"changes");if(changes.isNotEmpty()){section("MUDOU DESDE A ÚLTIMA SESSÃO");listCard(changes)}
  val web=CoachEngine.objects(server,"webFindings");if(web.isNotEmpty()){section("META / EVENTOS / WEB");web.forEach{o->infoCard(o.optString("title"),"",o.optString("finding"),Color.rgb(86,205,194))}}
  diagnostic(local)
 }

 private fun hero(title:String,body:String,color:Int){val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};b.addView(txt(title,20f,true,color));if(body.isNotBlank())b.addView(txt(body,14f,false,Color.rgb(210,219,232)).apply{setPadding(0,9,0,0)});content.addView(card(b,Color.rgb(18,31,50)))}
 private fun planCard(n:Int,title:String,action:String,reason:String,impact:String){val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};b.addView(txt("$n  ${title.ifBlank{"Prioridade"}}",16f,true));if(action.isNotBlank())b.addView(txt(action,15f,true,Color.rgb(153,197,255)).apply{setPadding(0,7,0,0)});if(reason.isNotBlank())b.addView(txt(reason,13.5f,false,Color.rgb(190,201,217)).apply{setPadding(0,6,0,0)});content.addView(card(b,panel))}
 private fun infoCard(title:String,sub:String,body:String,color:Int){val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};if(title.isNotBlank())b.addView(txt(title,15.5f,true,color));if(sub.isNotBlank())b.addView(txt(sub,13.5f,true).apply{setPadding(0,5,0,0)});if(body.isNotBlank())b.addView(txt(body,13.5f,false,Color.rgb(190,201,217)).apply{setPadding(0,6,0,0)});content.addView(card(b,panel))}
 private fun listCard(items:List<String>){val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};items.forEach{b.addView(txt("• $it",13.8f,false).apply{setPadding(0,5,0,5)})};content.addView(card(b,panel))}
 private fun diagnostic(local:JSONObject?){section("DIAGNÓSTICO DE LEITURA");val t=if(local==null)"Nenhuma sessão ainda." else "Cobertura OCR: ${CoachEngine.coverage(local)} • Telas: ${local.optInt("frames",0)} • Linhas: ${local.optInt("uniqueLines",0)}";content.addView(card(txt(t,12.5f,false,muted),Color.rgb(12,20,33)))}
 private fun section(s:String)=content.addView(txt(s,12.5f,true,Color.rgb(123,146,178)).apply{setPadding(2,20,0,8)})
 private fun stat(label:String,value:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(8,13,8,13);background=round(panel,18);addView(txt(value,22f,true));addView(txt(label,10.5f,true,muted))}
 private fun txt(t:String,s:Float,b:Boolean,c:Int=Color.WHITE)=TextView(this).apply{text=t;textSize=s;setTextColor(c);if(b)setTypeface(typeface,Typeface.BOLD)}
 private fun button(t:String,c:Int)=Button(this).apply{text=t;setTextColor(Color.WHITE);textSize=13f;background=round(c,16)}
 private fun card(v:View,c:Int)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(17,15,17,15);background=round(c,21);addView(v,LinearLayout.LayoutParams(-1,-2));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=6;bottomMargin=6}}
 private fun space(h:Int)=Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,h)}
 private fun round(c:Int,r:Int)=GradientDrawable().apply{setColor(c);cornerRadius=r.toFloat()}
 private fun launchSelectedGame(name:String){val pkg=when{name.startsWith("Marvel")->"com.foxnextgames.m3";name.startsWith("Saint")->"com.tencent.tmgp.sskeus";else->"com.hutchgames.formularacing"};packageManager.getLaunchIntentForPackage(pkg)?.let{it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(it)}}
}
