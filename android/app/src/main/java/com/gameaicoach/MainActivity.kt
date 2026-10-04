package com.gameaicoach

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity:AppCompatActivity(){
 private lateinit var status:TextView
 private lateinit var summary:TextView
 private lateinit var game:Spinner
 private lateinit var url:EditText
 private val projectionLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->
  if(r.resultCode==Activity.RESULT_OK&&r.data!=null){
   getSharedPreferences("coach",MODE_PRIVATE).edit()
    .putString("game",game.selectedItem.toString())
    .putString("url",url.text.toString().trimEnd('/'))
    .apply()
   val i=Intent(this,CaptureService::class.java)
    .putExtra("resultCode",r.resultCode)
    .putExtra("data",r.data)
   if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
   status.text="🟢 Coach ativo — abra o jogo e jogue normalmente."
   launchSelectedGame(game.selectedItem.toString())
  }else status.text="Captura não autorizada."
 }

 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
   requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),2)
  }

  val prefs=getSharedPreferences("coach",MODE_PRIVATE)
  val root=LinearLayout(this).apply{
   orientation=LinearLayout.VERTICAL
   setPadding(28,48,28,28)
   setBackgroundColor(Color.rgb(7,17,29))
   gravity=Gravity.TOP
  }
  fun tv(t:String,s:Float=16f)=TextView(this).apply{
   text=t
   textSize=s
   setTextColor(Color.WHITE)
   setPadding(0,8,0,8)
  }

  root.addView(tv("GAME AI COACH",28f))
  root.addView(tv("Sem vídeo. O Coach lê a tela enquanto você joga.",15f))

  game=Spinner(this)
  val games=listOf("Saint Seiya Awakening","Marvel Strike Force","F1 Clash")
  game.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,games)
  root.addView(game,LinearLayout.LayoutParams(-1,-2))

  root.addView(tv("URL do seu Vercel",14f))
  url=EditText(this).apply{
   setText(prefs.getString("url",""))
   hint="https://seu-projeto.vercel.app"
   setTextColor(Color.WHITE)
   setHintTextColor(Color.GRAY)
  }
  root.addView(url,LinearLayout.LayoutParams(-1,-2))

  val start=Button(this).apply{
   text="INICIAR COACH"
   setOnClickListener{
    if(url.text.isBlank()){
     Toast.makeText(this@MainActivity,"Informe a URL do Vercel uma vez.",Toast.LENGTH_LONG).show()
     return@setOnClickListener
    }
    val m=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    projectionLauncher.launch(m.createScreenCaptureIntent())
   }
  }
  root.addView(start,LinearLayout.LayoutParams(-1,-2))

  val stop=Button(this).apply{
   text="FINALIZAR SESSÃO"
   setOnClickListener{
    status.text="🟡 Finalizando sessão e enviando resumo..."
    val i=Intent(this@MainActivity,CaptureService::class.java).setAction(CaptureService.ACTION_FINISH)
    if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
    waitForSessionResult()
   }
  }
  root.addView(stop,LinearLayout.LayoutParams(-1,-2))

  status=tv("⚪ Coach parado",15f)
  root.addView(status)

  root.addView(tv("ÚLTIMA SESSÃO",18f))
  summary=tv("Nenhuma sessão finalizada ainda.",14f)
  root.addView(summary)

  root.addView(tv("Na primeira vez, o Android pedirá autorização para compartilhar a tela. O app não controla o jogo e não executa cliques.",13f))

  setContentView(ScrollView(this).apply{addView(root,ViewGroup.LayoutParams(-1,-1))})
  refreshSummary()
 }

 override fun onResume(){
  super.onResume()
  if(::summary.isInitialized) refreshSummary()
 }

 private fun waitForSessionResult(){
  val before=getSharedPreferences("coach",MODE_PRIVATE).getLong("lastFinishedAt",0L)
  var tries=0
  val h=Handler(Looper.getMainLooper())
  val task=object:Runnable{
   override fun run(){
    tries++
    val now=getSharedPreferences("coach",MODE_PRIVATE).getLong("lastFinishedAt",0L)
    refreshSummary()
    if(now>before){
     status.text="✅ Sessão finalizada e salva."
     return
    }
    if(tries<12)h.postDelayed(this,1000) else status.text="🟡 Sessão encerrada. Resumo local será atualizado quando o processamento terminar."
   }
  }
  h.postDelayed(task,700)
 }

 private fun refreshSummary(){
  val p=getSharedPreferences("coach",MODE_PRIVATE)
  val whenDone=p.getLong("lastFinishedAt",0L)
  if(whenDone==0L){
   summary.text="Nenhuma sessão finalizada ainda."
   return
  }
  val gameName=p.getString("lastGame","Jogo")?:"Jogo"
  val screens=p.getInt("lastScreens",0)
  val unique=p.getInt("lastUniqueLines",0)
  val newLines=p.getInt("lastNewLines",0)
  val sendStatus=p.getString("lastSendStatus","local")?:"local"
  val hints=p.getString("lastHints","")?:""
  val sync=when(sendStatus){
   "ok"->"✅ Vercel recebeu"
   "sending"->"🟡 Enviando ao Vercel"
   "error"->"⚠️ Salvo no celular; envio falhou"
   else->"💾 Salvo no celular"
  }
  summary.text=buildString{
   append("$gameName\n")
   append("Telas analisadas: $screens\n")
   append("Linhas únicas lidas: $unique\n")
   append("Novas desde a sessão anterior: $newLines\n")
   append(sync)
   if(hints.isNotBlank())append("\n\n$hints")
  }
 }

 private fun launchSelectedGame(name:String){
  val pm=packageManager
  val candidates=pm.getInstalledApplications(PackageManager.GET_META_DATA)
  val key=when{
   name.startsWith("Saint")->"saint seiya"
   name.startsWith("Marvel")->"marvel"
   else->"f1 clash"
  }
  val app=candidates.firstOrNull{pm.getApplicationLabel(it).toString().lowercase().contains(key)}
  if(app!=null){
   pm.getLaunchIntentForPackage(app.packageName)?.let{
    startActivity(it)
    return
   }
  }
  Toast.makeText(this,"Não encontrei o jogo automaticamente. Abra-o normalmente; o Coach continua ativo.",Toast.LENGTH_LONG).show()
 }
}
