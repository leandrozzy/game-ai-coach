package com.gameaicoach

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
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
 private val games=listOf("Marvel Strike Force","Saint Seiya Awakening","F1 Clash")
 private val blue=Color.rgb(45,122,255);private val bg=Color.rgb(7,13,23);private val panel=Color.rgb(16,27,44)
 private val muted=Color.rgb(148,163,184);private val green=Color.rgb(92,224,144);private val amber=Color.rgb(255,190,92)
 private val vercel="https://game-ai-coach-indol.vercel.app"

 private val projectionLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->
  if(r.resultCode==Activity.RESULT_OK&&r.data!=null){
   val game=gameSpinner.selectedItem.toString();Store.saveActiveGame(this,game)
   val i=Intent(this,CaptureService::class.java).putExtra("resultCode",r.resultCode).putExtra("data",r.data)
   if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
   refresh();launchSelectedGame(game)
  }else Toast.makeText(this,"Captura não autorizada.",Toast.LENGTH_LONG).show()
 }

 override fun onCreate(b:Bundle?){super.onCreate(b)
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),2)
  buildUi();refresh()
 }
 override fun onResume(){super.onResume();if(::content.isInitialized)refresh()}

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
   override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,pos:Int,id:Long){Store.saveActiveGame(this@MainActivity,games[pos]);refresh()}
  }
  root.addView(card(gameSpinner,Color.rgb(20,33,53)))
  status=txt("",13f,true);root.addView(status)
  val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  start=button("▶ INICIAR",blue).apply{setOnClickListener{projectionLauncher.launch((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent())}}
  stop=button("■ FINALIZAR",Color.rgb(65,80,104)).apply{setOnClickListener{
   val g=gameSpinner.selectedItem.toString();Store.saveServerAnalysis(this@MainActivity,g,JSONObject().put("pending",true))
   val i=Intent(this@MainActivity,CaptureService::class.java).setAction(CaptureService.ACTION_FINISH)
   if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
   Toast.makeText(this@MainActivity,"Gerando seu plano…",Toast.LENGTH_SHORT).show();poll(g)
  }}
  row.addView(start,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=7});row.addView(stop,LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=7})
  root.addView(row)

  reanalyze=button("↻ REANALISAR ÚLTIMA SESSÃO",Color.rgb(39,154,117)).apply{
   setOnClickListener{reanalyzeLastSession()}
  }
  root.addView(reanalyze,LinearLayout.LayoutParams(-1,-2).apply{topMargin=10})
  root.addView(space(14))

  content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};root.addView(content)
  setContentView(scroll)
 }

 private fun reanalyzeLastSession(){
  val game=gameSpinner.selectedItem.toString()
  val lines=Store.loadRawLines(this,game)
  val local=Store.loadSummary(this,game)
  if(lines.isEmpty()||local==null){
   Toast.makeText(this,"Ainda não existe uma sessão salva para este jogo.",Toast.LENGTH_LONG).show();return
  }
  Store.saveServerAnalysis(this,game,JSONObject().put("pending",true))
  refresh()
  Toast.makeText(this,"Reanalisando a sessão já salva — não precisa abrir o jogo.",Toast.LENGTH_SHORT).show()

  val payload=JSONObject().apply{
   put("game",game)
   put("sessionId","reanalyze-"+UUID.randomUUID().toString())
   put("screens",local.optInt("frames",0))
   put("lines",JSONArray(lines.take(2500)))
   put("localSummary",local)
   Store.loadPreviousSummary(this@MainActivity,game)?.let{put("previousSummary",it)}
  }

  val req=Request.Builder().url("$vercel/api/observe")
   .post(payload.toString().toRequestBody("application/json".toMediaType())).build()

  OkHttpClient.Builder().callTimeout(45,TimeUnit.SECONDS).build().newCall(req).enqueue(object:Callback{
   override fun onFailure(c:Call,e:IOException){
    runOnUiThread{
     Store.saveServerAnalysis(this@MainActivity,game,JSONObject().put("ok",false).put("pending",false).put("error","Não foi possível consultar a IA agora. A sessão continua salva."))
     refresh();Toast.makeText(this@MainActivity,"Falha ao reanalisar. A sessão foi preservada.",Toast.LENGTH_LONG).show()
    }
   }
   override fun onResponse(c:Call,r:Response){
    val body=r.body?.string().orEmpty()
    runOnUiThread{
     if(r.isSuccessful){
      runCatching{Store.saveServerAnalysis(this@MainActivity,game,JSONObject(body))}
       .onFailure{Store.saveServerAnalysis(this@MainActivity,game,JSONObject().put("ok",false).put("pending",false).put("error","Resposta da IA inválida."))}
     }else Store.saveServerAnalysis(this@MainActivity,game,JSONObject().put("ok",false).put("pending",false).put("error","Servidor respondeu HTTP ${r.code}."))
     refresh()
     Toast.makeText(this@MainActivity,if(r.isSuccessful)"Plano reanalisado." else "Reanálise não concluída.",Toast.LENGTH_SHORT).show()
    }
    r.close()
   }
  })
  poll(game)
 }

 private fun poll(game:String){
  var tries=0;val h=Handler(Looper.getMainLooper())
  val r=object:Runnable{override fun run(){tries++;refresh();val s=Store.loadServerAnalysis(this@MainActivity,game)
   if(s!=null&&!s.optBoolean("pending",false))return
   if(tries<35)h.postDelayed(this,1200)
  }};h.postDelayed(r,900)
 }

 private fun refresh(){
  val active=Store.isCaptureActive(this);status.text=if(active)"● COACH ATIVO — jogue normalmente" else "● PRONTO"
  status.setTextColor(if(active)green else muted);start.isEnabled=!active;stop.isEnabled=active
  if(::reanalyze.isInitialized){
   val game=gameSpinner.selectedItem?.toString()?:Store.loadActiveGame(this)
   reanalyze.isEnabled=!active&&Store.loadRawLines(this,game).isNotEmpty()
  }
  render()
 }

 private fun render(){
  if(!::content.isInitialized)return;content.removeAllViews()
  val game=gameSpinner.selectedItem?.toString()?:Store.loadActiveGame(this)
  val local=Store.loadSummary(this,game);val server=Store.loadServerAnalysis(this,game)
  if(server?.optBoolean("pending",false)==true){hero("ANALISANDO","Usando a sessão salva para recalcular seu plano…",blue);diagnostic(local);return}
  if(server?.optString("error")?.isNotBlank()==true){hero("SESSÃO SALVA",server.optString("error"),amber);diagnostic(local);return}

  val connected=CoachEngine.connected(server)
  val headline=CoachEngine.headline(server)
  if(!connected){
   hero(if(headline.isBlank())"COACH AINDA SEM IA" else headline,CoachEngine.summary(server).ifBlank{"Faça uma sessão para gerar seu primeiro plano."},amber)
   if(local!=null) smallCard("VOCÊ NÃO PRECISA JOGAR DE NOVO","Use REANALISAR ÚLTIMA SESSÃO depois que o backend do OSM estiver pronto.")
   diagnostic(local);return
  }

  hero(headline.ifBlank{"Plano atualizado"},CoachEngine.summary(server),green)
  val meta=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
  meta.addView(stat("CONFIANÇA","${CoachEngine.confidence(server)}%"),LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=5})
  meta.addView(stat("UNIDADES",CoachEngine.units(server).size.toString()),LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=5;marginEnd=5})
  meta.addView(stat("AÇÕES",CoachEngine.plan(server).size.toString()),LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=5})
  content.addView(meta)

  section("PLANO DE HOJE")
  val plans=CoachEngine.plan(server)
  if(plans.isEmpty())empty("A IA ainda não encontrou base suficiente para uma ação segura.")
  plans.forEachIndexed{i,o-> planCard(i+1,o.optString("title"),o.optString("action"),o.optString("reason"),o.optString("impact"))}

  val ups=CoachEngine.upgrades(server)
  if(ups.isNotEmpty()){section("EVOLUIR AGORA");ups.forEach{o->
   val cur=o.optString("current");val nxt=o.optString("next");val path=listOf(cur,nxt).filter{it.isNotBlank()}.joinToString("  →  ")
   infoCard(o.optString("target"),path,o.optString("reason"),blue)
  }}

  val teams=CoachEngine.teams(server)
  if(teams.isNotEmpty()){section("TIMES / FORMAÇÕES");teams.forEach{o->
   val units=o.optJSONArray("units");val names=if(units==null)"" else (0 until units.length()).map{units.optString(it)}.filter{it.isNotBlank()}.joinToString(" • ")
   infoCard(o.optString("mode")+(o.optString("name").takeIf{it.isNotBlank()}?.let{" — $it"}?:""),names,o.optString("why"),Color.rgb(72,173,255))
  }}

  val avoid=CoachEngine.objects(server,"avoid")
  if(avoid.isNotEmpty()){section("NÃO GASTE / EVITE");avoid.forEach{o->infoCard(o.optString("title"),"",o.optString("reason"),amber)}}

  val resources=CoachEngine.resources(server)
  if(resources.isNotEmpty()){section("RECURSOS");resources.forEach{o->infoCard(o.optString("name"),o.optString("value"),o.optString("advice"),Color.rgb(177,136,255))}}

  val units=CoachEngine.units(server)
  if(units.isNotEmpty()){section(unitLabel(game));val wrap=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
   units.take(12).forEach{o->
    val attrs=listOf(
     o.optString("power").takeIf{it.isNotBlank()}?.let{"Poder $it"},
     o.optString("level").takeIf{it.isNotBlank()}?.let{"Nv $it"},
     o.optString("gear").takeIf{it.isNotBlank()}?.let{"Gear $it"},
     o.optString("iso").takeIf{it.isNotBlank()}?.let{"ISO $it"},
     o.optString("stars").takeIf{it.isNotBlank()}
    ).filterNotNull().joinToString(" • ")
    wrap.addView(txt("• ${o.optString("name")}"+if(attrs.isNotBlank())"  —  $attrs" else "",14f,false,Color.WHITE).apply{setPadding(0,5,0,5)})
   };content.addView(card(wrap,panel))
  }

  val changes=CoachEngine.strings(server,"changes")
  if(changes.isNotEmpty()){section("MUDOU DESDE A ÚLTIMA SESSÃO");listCard(changes,green)}
  val web=CoachEngine.objects(server,"webFindings")
  if(web.isNotEmpty()){section("META / EVENTOS / WEB");web.forEach{o->infoCard(o.optString("title"),"",o.optString("finding"),Color.rgb(86,205,194))}}
  val gaps=CoachEngine.strings(server,"gaps")
  if(gaps.isNotEmpty()){section("FALTA PARA DECIDIR COM SEGURANÇA");listCard(gaps,Color.rgb(202,178,255))}
  diagnostic(local)
 }

 private fun unitLabel(g:String)=when{g.startsWith("Marvel")->"PERSONAGENS RECONHECIDOS";g.startsWith("Saint")->"CAVALEIROS RECONHECIDOS";else->"PILOTOS / COMPONENTES RECONHECIDOS"}

 private fun hero(title:String,body:String,color:Int){
  val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  b.addView(txt(title,20f,true,color));if(body.isNotBlank())b.addView(txt(body,14f,false,Color.rgb(210,219,232)).apply{setPadding(0,9,0,0)})
  content.addView(card(b,Color.rgb(18,31,50)))
 }
 private fun planCard(n:Int,title:String,action:String,reason:String,impact:String){
  val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  b.addView(txt("$n  ${title.ifBlank{"Prioridade"}}",16f,true,Color.WHITE))
  if(action.isNotBlank())b.addView(txt(action,15f,true,Color.rgb(153,197,255)).apply{setPadding(0,7,0,0)})
  if(reason.isNotBlank())b.addView(txt(reason,13.5f,false,Color.rgb(190,201,217)).apply{setPadding(0,6,0,0)})
  if(impact.isNotBlank())b.addView(txt("Impacto: $impact",12.5f,true,green).apply{setPadding(0,7,0,0)})
  content.addView(card(b,panel))
 }
 private fun infoCard(title:String,sub:String,body:String,color:Int){
  val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  if(title.isNotBlank())b.addView(txt(title,15.5f,true,color));if(sub.isNotBlank())b.addView(txt(sub,13.5f,true,Color.WHITE).apply{setPadding(0,5,0,0)})
  if(body.isNotBlank())b.addView(txt(body,13.5f,false,Color.rgb(190,201,217)).apply{setPadding(0,6,0,0)})
  content.addView(card(b,panel))
 }
 private fun listCard(items:List<String>,color:Int){val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};items.forEach{b.addView(txt("• $it",13.8f,false,Color.WHITE).apply{setPadding(0,5,0,5)})};content.addView(card(b,panel))}
 private fun smallCard(title:String,body:String){infoCard(title,"",body,amber)}
 private fun diagnostic(local:JSONObject?){
  section("DIAGNÓSTICO DE LEITURA")
  val t=if(local==null)"Nenhuma sessão ainda." else "Cobertura OCR: ${CoachEngine.coverage(local)} • Telas: ${local.optInt("frames",0)} • Linhas: ${local.optInt("uniqueLines",0)}"
  val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};b.addView(txt(t,12.5f,false,muted));content.addView(card(b,Color.rgb(12,20,33)))
 }
 private fun section(s:String)=content.addView(txt(s,12.5f,true,Color.rgb(123,146,178)).apply{setPadding(2,20,0,8)})
 private fun empty(s:String)=content.addView(card(txt(s,13.5f,false,muted),panel))
 private fun stat(label:String,value:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(8,13,8,13);background=round(panel,18);addView(txt(value,22f,true));addView(txt(label,10.5f,true,muted))}
 private fun txt(t:String,s:Float,b:Boolean,c:Int=Color.WHITE)=TextView(this).apply{text=t;textSize=s;setTextColor(c);if(b)setTypeface(typeface,Typeface.BOLD);setLineSpacing(0f,1.08f)}
 private fun button(t:String,c:Int)=Button(this).apply{text=t;setTextColor(Color.WHITE);textSize=13f;background=round(c,16)}
 private fun card(v:View,c:Int)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(17,15,17,15);background=round(c,21);addView(v,LinearLayout.LayoutParams(-1,-2));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=6;bottomMargin=6}}
 private fun space(h:Int)=Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,h)}
 private fun round(c:Int,r:Int)=GradientDrawable().apply{setColor(c);cornerRadius=r.toFloat()}

 private fun launchSelectedGame(name:String){
  val pkg=when{ name.startsWith("Marvel")->"com.foxnextgames.m3";name.startsWith("Saint")->"com.tencent.tmgp.sskeus";else->"com.hutchgames.formularacing"}
  packageManager.getLaunchIntentForPackage(pkg)?.let{it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(it);return}
  Toast.makeText(this,"Jogo não encontrado neste aparelho. O Coach continua ativo.",Toast.LENGTH_LONG).show()
 }
}
