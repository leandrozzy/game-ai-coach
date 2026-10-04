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
import org.json.JSONObject

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
        buildUi()
        refresh()
    }

    override fun onResume(){super.onResume();if(::content.isInitialized)refresh()}

    private fun buildUi(){
        val scroll=ScrollView(this).apply{setBackgroundColor(Color.rgb(8,14,24));isFillViewport=true}
        val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,34,24,34)}
        scroll.addView(root,ViewGroup.LayoutParams(-1,-2))

        root.addView(text("GAME AI COACH",30f,true))
        root.addView(text("Central Coach • leitura automática sem vídeo",14f,false,Color.rgb(150,165,185)))

        root.addView(space(12))
        gameSpinner=Spinner(this)
        gameSpinner.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,games)
        gameSpinner.setSelection(games.indexOf(Store.loadActiveGame(this)).coerceAtLeast(0))
        gameSpinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?){ }
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){Store.saveActiveGame(this@MainActivity,games[position]);refreshContent()}
        }
        root.addView(card(gameSpinner,Color.rgb(20,31,48)))

        statusChip=text("",14f,true)
        root.addView(statusChip)

        val actions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        startButton=button("▶ INICIAR COACH",Color.rgb(28,117,255)).apply{setOnClickListener{
            val m=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(m.createScreenCaptureIntent())
        }}
        stopButton=button("■ FINALIZAR",Color.rgb(60,72,92)).apply{setOnClickListener{
            val i=Intent(this@MainActivity,CaptureService::class.java).setAction(CaptureService.ACTION_FINISH)
            if(Build.VERSION.SDK_INT>=26)startForegroundService(i) else startService(i)
            Toast.makeText(this@MainActivity,"Finalizando e salvando sessão…",Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({refresh()},2200)
        }}
        actions.addView(startButton,LinearLayout.LayoutParams(0,-2,1f).apply{marginEnd=8})
        actions.addView(stopButton,LinearLayout.LayoutParams(0,-2,1f).apply{marginStart=8})
        root.addView(actions)
        root.addView(space(16))

        content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        root.addView(content)
        setContentView(scroll)
    }

    private fun refresh(){
        val active=Store.isCaptureActive(this)
        statusChip.text=if(active)"● COACH ATIVO — jogue normalmente" else "● COACH PARADO"
        statusChip.setTextColor(if(active)Color.rgb(91,224,139) else Color.rgb(165,176,194))
        startButton.isEnabled=!active
        stopButton.isEnabled=active
        refreshContent()
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

        content.addView(sectionTitle("O QUE FAZER AGORA"))
        val today=CoachEngine.buildToday(summary,server)
        val todayBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        today.forEachIndexed{i,item->todayBox.addView(text("${i+1}. $item",15f,false,Color.WHITE).apply{setPadding(0,7,0,7)})}
        content.addView(card(todayBox,Color.rgb(16,26,42)))

        content.addView(sectionTitle("MAPEAMENTO DA CONTA"))
        val coverageBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val details=CoachEngine.coverageDetails(summary)
        if(details.isEmpty())coverageBox.addView(text("Ainda sem sessão. Inicie o Coach e use o jogo normalmente.",14f,false,Color.rgb(180,190,205)))
        details.forEach{(name,ok)->coverageBox.addView(text("${if(ok) "✓" else "○"} $name",14f,false,if(ok)Color.rgb(91,224,139) else Color.rgb(145,158,178)).apply{setPadding(0,5,0,5)})}
        content.addView(card(coverageBox,Color.rgb(16,26,42)))

        content.addView(sectionTitle("ÚLTIMA SESSÃO"))
        val sessionBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        if(summary==null){
            sessionBox.addView(text("Nenhuma sessão finalizada ainda.",14f,false,Color.rgb(180,190,205)))
        }else{
            sessionBox.addView(text(summary.optString("status","Sessão registrada"),17f,true))
            sessionBox.addView(text("Telas lidas: ${summary.optInt("frames",0)}",14f,false,Color.rgb(180,190,205)))
            sessionBox.addView(text("Linhas únicas: ${summary.optInt("uniqueLines",0)}",14f,false,Color.rgb(180,190,205)))
            val entities=summary.optJSONArray("entities")
            if(entities!=null&&entities.length()>0){
                sessionBox.addView(text("Itens/nomes reconhecidos:",14f,true))
                val max=minOf(entities.length(),12)
                for(i in 0 until max)sessionBox.addView(text("• ${entities.optString(i)}",13f,false,Color.rgb(205,214,226)))
            }
        }
        content.addView(card(sessionBox,Color.rgb(16,26,42)))

        content.addView(sectionTitle("INTELIGÊNCIA WEB"))
        val webBox=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        val web=server?.optJSONArray("webIntel")
        if(web==null||web.length()==0){
            webBox.addView(text("A próxima sessão consulta automaticamente fontes públicas para dicas, eventos e novidades. Nenhuma chave é necessária.",14f,false,Color.rgb(180,190,205)))
        }else{
            for(i in 0 until minOf(web.length(),5)){
                val o=web.optJSONObject(i)?:continue
                webBox.addView(text("• ${o.optString("title")}",14f,true))
                val snippet=o.optString("snippet")
                if(snippet.isNotBlank())webBox.addView(text(snippet.take(220),13f,false,Color.rgb(170,182,199)))
            }
        }
        content.addView(card(webBox,Color.rgb(16,26,42)))

        content.addView(space(16))
        content.addView(text("Como usar: ative o Coach e jogue normalmente. Não precisa parar em cada tela; apenas evite passar instantaneamente por telas importantes. O leitor tenta capturar aproximadamente a cada 1,5 s e ignora quadros enquanto o OCR anterior ainda está processando.",12.5f,false,Color.rgb(130,144,164)))
    }

    private fun launchSelectedGame(name:String){
        val pm=packageManager
        val candidates=pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val keys=when{name.startsWith("Saint")->listOf("saint seiya","awakening");name.startsWith("Marvel")->listOf("marvel strike force","strike force");else->listOf("f1 clash","f1")}
        val app=candidates.firstOrNull{a->val label=pm.getApplicationLabel(a).toString().lowercase();keys.any{label.contains(it)}}
        if(app!=null){pm.getLaunchIntentForPackage(app.packageName)?.let{startActivity(it);return}}
        Toast.makeText(this,"Não encontrei o jogo automaticamente. Abra-o normalmente; o Coach continua ativo.",Toast.LENGTH_LONG).show()
    }

    private fun sectionTitle(t:String)=text(t,13f,true,Color.rgb(125,147,177)).apply{setPadding(2,20,0,8)}
    private fun text(t:String,size:Float,bold:Boolean,color:Int=Color.WHITE)=TextView(this).apply{text=t;textSize=size;setTextColor(color);if(bold)setTypeface(typeface,Typeface.BOLD);setLineSpacing(0f,1.08f)}
    private fun space(h:Int)=Space(this).apply{layoutParams=LinearLayout.LayoutParams(1,h)}
    private fun button(t:String,color:Int)=Button(this).apply{text=t;setTextColor(Color.WHITE);textSize=13f;background=rounded(color,16);setPadding(8,5,8,5)}
    private fun card(child:View,color:Int)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(18,16,18,16);background=rounded(color,22);addView(child,LinearLayout.LayoutParams(-1,-2));layoutParams=LinearLayout.LayoutParams(-1,-2).apply{topMargin=7;bottomMargin=7}}
    private fun statCard(label:String,value:String)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(8,14,8,14);background=rounded(Color.rgb(16,26,42),20);addView(text(value,23f,true));addView(text(label,11f,true,Color.rgb(125,147,177)))}
    private fun rounded(color:Int,r:Int)=GradientDrawable().apply{setColor(color);cornerRadius=r.toFloat()}
}
