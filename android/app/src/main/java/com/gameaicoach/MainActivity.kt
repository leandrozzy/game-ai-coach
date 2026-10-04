package com.gameaicoach

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity:AppCompatActivity(){
    private lateinit var gameSpinner: Spinner
    private lateinit var content: LinearLayout
    private lateinit var statusChip: TextView
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private val games=listOf("Marvel Strike Force","Saint Seiya Awakening","F1 Clash")

    private val projectionLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){r->
        if(r.resultCode==Activity.RESULT_OK&&r.data!=null){
            val game=gameSpinner.selectedItem.toString()
            Store.saveActiveGame(this,game)
            val i=Intent(this,CaptureService::class.java).putExtra("resultCode",r.resultCode).putExtra("data",r.data)
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
            refresh()
            launchSelectedGame(game)
        }else Toast.makeText(this,"Captura não autorizada.",Toast.LENGTH_LONG).show()
    }

    override fun onCreate(b:Bundle?){
        super.onCreate(b)
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),2)
        }
        buildUi(); refresh()
    }

    override fun onResume(){super.onResume();if(::content.isInitialized)refresh()}

    private fun buildUi(){
        val scroll=ScrollView(this).apply{setBackgroundColor(Color.rgb(8,14,24));isFillViewport=true}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,34,24,34)}
        scroll.addView(root,ViewGroup.LayoutParams(-1,-2))

        root.addView(text("GAME AI COACH",30f,true))
        root.addView(text("Central Coach • leitura automática + IA",14f,false,Color.rgb(150,165,185)))
        root.addView(space(12))

        gameSpinner=Spinner(this)
        gameSpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,games)
        gameSpinner.setSelection(games.indexOf(Store.loadActiveGame(this)).coerceAtLeast(0))
        gameSpinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?){ }
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){Store.saveActiveGame(this@MainActivity,games[position]);refreshContent()}
        }
        root.addView(card(gameSpinner,Color.rgb(20,31,48)))

        statusChip=text("",14f,true); root.addView(statusChip)

        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        startButton=button("▶ INICIAR COACH",Color.rgb(28,117,255)).apply{setOnClickListener{
            val m=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(m.createScreenCaptureIntent())
        }}
        stopButton=button("■ FINALIZAR",Color.rgb(60,72,92)).apply{setOnClickListener{
            val game=gameSpinner.selectedItem.toString()
            Store.saveServerAnalysis(this@MainActivity,game,org.json.JSONObject().put("pending",true))
            val i=Intent(this@MainActivity,CaptureService::class.java).setAction(CaptureService.ACTION_FINISH)
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
            Toast.makeText(this@MainActivity,"Analisando sessão e consultando IA…",Toast.LENGTH_SHORT).show()
            pollAnalysis(game)
        }}
        actions.addView(startButton,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=8})
        actions.addView(stopButton,LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=8})
        root.addView(actions); root.addView(space(16))

        content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}; root.addView(content)
        setContentView(scroll)
    }

    private fun pollAnalysis(game:String){
        var tries=0
        val h=Handler(Looper.getMainLooper())
        val task=object:Runnable{
            override fun run(){
                tries++; refresh()
                val s=Store.loadServerAnalysis(this@MainActivity,game)
                if(s!=null && !s.optBoolean("pending",false)){
                    Toast.makeText(this@MainActivity,"Análise concluída.",Toast.LENGTH_SHORT).show(); return
                }
                if(tries<20) h.postDelayed(this,1200)
                else Toast.makeText(this@MainActivity,"A sessão ficou salva. A análise pode aparecer ao reabrir o Coach.",Toast.LENGTH_LONG).show()
            }
        }
        h.postDelayed(task,1000)
    }

    private fun refresh(){
        val active=Store.isCaptureActive(this)
        statusChip.text=if(active)"● COACH ATIVO — jogue normalmente" else "● COACH PARADO"
        statusChip.setTextColor(if(active)Color.rgb(91,224,139) else Color.rgb(165,176,194))
        startButton.isEnabled=!active; stopButton.isEnabled=active; refreshContent()
    }

    private fun refreshContent(){
        if(!::content.isInitialized)return
        content.removeAllViews()
        val game=gameSpinner.selectedItem?.toString()?:Store.loadActiveGame(this)
        val summary=Store.loadSummary(this,game)
        val server=Store.loadServerAnalysis(this,game)

        val statRow=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        statRow.addView(statCard("COBERTURA",CoachEngine.coverageText(summary)),LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=6})
        statRow.addView(statCard("ITENS",CoachEngine.entityCount(summary).toString()),LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=6;marginEnd=6})
        statRow.addView(statCard("NOVOS",CoachEngine.newEntityCount(summary).toString()),LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=6})
        content.addView(statRow)

        val pending=server?.optBoolean("pending",false)==true
        if(pending){
            content.addView(sectionTitle("COACH IA"))
            val p=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
            p.addView(text("Analisando sua sessão…",17f,true,Color.rgb(145,190,255)))
            p.addView(text("O app está cruzando OCR, sessão anterior e informações atuais para gerar prioridades específicas.",14f,false,Color.rgb(185,195,210)))
            content.addView(card(p,Color.rgb(16,26,42)))
        }

        val aiSummary=CoachEngine.aiSummary(server)
        if(aiSummary.isNotBlank()){
            content.addView(sectionTitle("COACH IA — SUA CONTA"))
            val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
            box.addView(text(aiSummary,16f,true))
            val provider=CoachEngine.aiProvider(server)
            val confidence=CoachEngine.aiConfidence(server)
            if(provider.isNotBlank()) box.addView(text("Confiança: $confidence% • $provider",12.5f,false,Color.rgb(135,157,186)).apply{setPadding(0,8,0,0)})
            content.addView(card(box,Color.rgb(17,31,50)))
        }

        content.addView(sectionTitle("O QUE FAZER AGORA"))
        val todayBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        CoachEngine.buildToday(summary,server).forEachIndexed{i,item->todayBox.addView(text("${i+1}. $item",15f,false,Color.WHITE).apply{setPadding(0,7,0,7)})}
        content.addView(card(todayBox,Color.rgb(16,26,42)))

        addAiSection("NÃO GASTE / EVITE",CoachEngine.aiList(server,"avoid"),Color.rgb(255,185,100))
        addAiSection("TIMES / FORMAÇÕES",CoachEngine.aiList(server,"teams"),Color.rgb(130,205,255))
        addAiSection("MUDOU DESDE A ÚLTIMA SESSÃO",CoachEngine.aiList(server,"changes"),Color.rgb(105,225,155))
        addAiSection("O QUE AINDA FALTA SABER",CoachEngine.aiList(server,"gaps"),Color.rgb(200,180,255))

        content.addView(sectionTitle("MAPEAMENTO DA CONTA"))
        val coverageBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val details=CoachEngine.coverageDetails(summary)
        if(details.isEmpty())coverageBox.addView(text("Ainda sem sessão. Inicie o Coach e use o jogo normalmente.",14f,false,Color.rgb(180,190,205)))
        details.forEach{(name,ok)->coverageBox.addView(text("${if(ok) "✓" else "○"} $name",14f,false,if(ok)Color.rgb(91,224,139) else Color.rgb(145,158,178)).apply{setPadding(0,5,0,5)})}
        content.addView(card(coverageBox,Color.rgb(16,26,42)))

        content.addView(sectionTitle("ÚLTIMA SESSÃO"))
        val sessionBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        if(summary==null)sessionBox.addView(text("Nenhuma sessão finalizada ainda.",14f,false,Color.rgb(180,190,205)))
        else{
            sessionBox.addView(text(summary.optString("status","Sessão registrada"),17f,true))
            sessionBox.addView(text("Telas lidas: ${summary.optInt("frames",0)}",14f,false,Color.rgb(180,190,205)))
            sessionBox.addView(text("Linhas únicas: ${summary.optInt("uniqueLines",0)}",14f,false,Color.rgb(180,190,205)))
        }
        content.addView(card(sessionBox,Color.rgb(16,26,42)))

        addAiSection("INTELIGÊNCIA WEB",CoachEngine.aiList(server,"webFindings",5),Color.rgb(120,175,255))

        content.addView(space(16))
        content.addView(text("Use normalmente: inicie o Coach, jogue e finalize. Não precisa abrir todos os personagens todos os dias. A IA usa o que apareceu, compara com o histórico e deixa explícito quando faltam dados.",12.5f,false,Color.rgb(130,144,164)))
    }

    private fun addAiSection(title:String,items:List<String>,accent:Int){
        if(items.isEmpty())return
        content.addView(sectionTitle(title))
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        items.forEach{box.addView(text("• $it",14.5f,false,Color.WHITE).apply{setPadding(0,6,0,6)})}
        content.addView(card(box,Color.rgb(16,26,42)).apply{background=rounded(Color.rgb(16,26,42),22,accent)})
    }

    private fun launchSelectedGame(name:String){
        val pm=packageManager
        val packageName=when{
            name.startsWith("Marvel")->"com.foxnextgames.m3"
            name.startsWith("Saint")->"com.tencent.tmgp.sskeus"
            else->"com.hutchgames.formularacing"
        }
        pm.getLaunchIntentForPackage(packageName)?.let{it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(it);return}
        val keys=when{name.startsWith("Saint")->listOf("saint seiya","awakening");name.startsWith("Marvel")->listOf("marvel strike force","strike force");else->listOf("f1 clash")}
        val app=pm.getInstalledApplications(PackageManager.GET_META_DATA).firstOrNull{a->val label=pm.getApplicationLabel(a).toString().lowercase();keys.any{label.contains(it)}}
        if(app!=null){pm.getLaunchIntentForPackage(app.packageName)?.let{it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(it);return}}
        Toast.makeText(this,"$name não foi encontrado instalado neste aparelho. O Coach continua ativo.",Toast.LENGTH_LONG).show()
    }

    private fun sectionTitle(t:String)=text(t,13f,true,Color.rgb(125,147,177)).apply{setPadding(2,20,0,8)}
    private fun text(t:String,size:Float,bold:Boolean,color:Int=Color.WHITE)=TextView(this).apply{text=t;textSize=size;setTextColor(color);if(bold)setTypeface(typeface,Typeface.BOLD);setLineSpacing(0f,1.08f)}
    private fun space(h:Int)=Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,h)}
    private fun button(t:String,color:Int)=Button(this).apply{text=t;setTextColor(Color.WHITE);textSize=13f;background=rounded(color,16);setPadding(8,5,8,5)}
    private fun card(child:View,color:Int)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,16,18,16);background=rounded(color,22);addView(child,LinearLayout.LayoutParams(-1,-2));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=7;bottomMargin=7}}
    private fun statCard(label:String,value:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(8,14,8,14);background=rounded(Color.rgb(16,26,42),20);addView(text(value,23f,true));addView(text(label,11f,true,Color.rgb(125,147,177)))}
    private fun rounded(color:Int,r:Int,stroke:Int?=null)=GradientDrawable().apply{setColor(color);cornerRadius=r.toFloat();if(stroke!=null)setStroke(2,stroke)}
}
