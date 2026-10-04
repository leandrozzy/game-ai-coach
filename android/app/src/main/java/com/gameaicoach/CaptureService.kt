package com.gameaicoach

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CaptureService:Service(){
 companion object{
  const val ACTION_FINISH="com.gameaicoach.FINISH_SESSION"
 }

 private var projection:MediaProjection?=null
 private var display:VirtualDisplay?=null
 private var reader:ImageReader?=null
 private val lines=ConcurrentHashMap.newKeySet<String>()
 private var frames=0
 private var last=0L
 private var finishing=false
 private val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
 private val session=UUID.randomUUID().toString()
 private val started=System.currentTimeMillis()

 override fun onBind(i:Intent?)=null

 override fun onCreate(){
  super.onCreate()
  val ch="coach_capture"
  if(Build.VERSION.SDK_INT>=26){
   getSystemService(NotificationManager::class.java)
    .createNotificationChannel(NotificationChannel(ch,"Coach ativo",NotificationManager.IMPORTANCE_LOW))
  }
  val n=Notification.Builder(this,ch)
   .setContentTitle("Game AI Coach ativo")
   .setContentText("Lendo telas periodicamente. Toque no Coach para finalizar.")
   .setSmallIcon(android.R.drawable.ic_menu_view)
   .setContentIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE))
   .build()
  startForeground(1001,n)
 }

 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{
  if(i?.action==ACTION_FINISH){
   finishSession()
   return START_NOT_STICKY
  }

  val code=i?.getIntExtra("resultCode",Activity.RESULT_CANCELED)?:Activity.RESULT_CANCELED
  @Suppress("DEPRECATION")
  val data=if(Build.VERSION.SDK_INT>=33){
   i?.getParcelableExtra("data",Intent::class.java)
  }else{
   i?.getParcelableExtra("data")
  }

  if(code==Activity.RESULT_OK&&data!=null)startProjection(code,data)
  return START_NOT_STICKY
 }

 private fun startProjection(code:Int,data:Intent){
  val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  projection=mgr.getMediaProjection(code,data)
  projection?.registerCallback(object:MediaProjection.Callback(){
   override fun onStop(){
    if(!finishing)finishSession()
   }
  },Handler(Looper.getMainLooper()))

  val dm=resources.displayMetrics
  val w=dm.widthPixels
  val h=dm.heightPixels
  reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2)
  display=projection?.createVirtualDisplay(
   "CoachCapture",w,h,dm.densityDpi,
   DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
   reader?.surface,null,null
  )

  reader?.setOnImageAvailableListener({r->
   val now=System.currentTimeMillis()
   if(now-last<5000){
    r.acquireLatestImage()?.close()
    return@setOnImageAvailableListener
   }
   last=now

   val image=r.acquireLatestImage()?:return@setOnImageAvailableListener
   try{
    val plane=image.planes[0]
    val buf=plane.buffer
    val ps=plane.pixelStride
    val rs=plane.rowStride
    val pad=rs-ps*w
    val bmp=Bitmap.createBitmap(w+pad/ps,h,Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(buf)
    frames++

    recognizer.process(InputImage.fromBitmap(bmp,0))
     .addOnSuccessListener{t->
      t.textBlocks
       .flatMap{it.lines}
       .map{it.text.trim()}
       .filter{it.length>1}
       .forEach{lines.add(it)}
      bmp.recycle()
     }
     .addOnFailureListener{bmp.recycle()}
   }finally{
    image.close()
   }
  },Handler(Looper.getMainLooper()))
 }

 private fun finishSession(){
  if(finishing)return
  finishing=true

  try{reader?.setOnImageAvailableListener(null,null)}catch(_:Exception){}
  try{reader?.close()}catch(_:Exception){}
  reader=null
  try{display?.release()}catch(_:Exception){}
  display=null

  saveAndSendSession()
 }

 private fun saveAndSendSession(){
  val p=getSharedPreferences("coach",MODE_PRIVATE)
  val base=p.getString("url","")?.trimEnd('/')?:""
  val game=p.getString("game","Jogo")?:"Jogo"

  val current=lines.toList().take(2500)
  val previous=try{
   val arr=JSONArray(p.getString("lastLines","[]")?:"[]")
   buildSet{
    for(i in 0 until arr.length())add(arr.optString(i))
   }
  }catch(_:Exception){
   emptySet<String>()
  }

  val newCount=current.count{it !in previous}
  val finishedAt=System.currentTimeMillis()

  p.edit()
   .putString("lastLines",JSONArray(current).toString())
   .putString("lastGame",game)
   .putInt("lastScreens",frames)
   .putInt("lastUniqueLines",current.size)
   .putInt("lastNewLines",newCount)
   .putLong("lastFinishedAt",finishedAt)
   .putString("lastSendStatus",if(base.isBlank())"local" else "sending")
   .putString("lastHints","Sessão salva no celular. Aguardando análise do servidor.")
   .apply()

  if(base.isBlank()){
   cleanupAndStop()
   return
  }

  val json=JSONObject()
   .put("game",game)
   .put("sessionId",session)
   .put("screens",frames)
   .put("startedAt",started.toString())
   .put("endedAt",finishedAt.toString())
   .put("lines",JSONArray(current))
   .toString()

  val req=Request.Builder()
   .url("$base/api/observe")
   .post(json.toRequestBody("application/json".toMediaType()))
   .build()

  OkHttpClient().newCall(req).enqueue(object:Callback{
   override fun onFailure(c:Call,e:IOException){
    p.edit()
     .putString("lastSendStatus","error")
     .putString("lastHints","Sessão preservada no celular. O Vercel não respondeu.")
     .apply()
    cleanupAndStop()
   }

   override fun onResponse(c:Call,r:Response){
    val body=r.body?.string().orEmpty()
    try{
     if(r.isSuccessful){
      val o=JSONObject(body)
      val hints=o.optJSONArray("hints")
      val text=if(hints!=null){
       buildString{
        for(i in 0 until hints.length()){
         if(i>0)append("\n")
         append("• ").append(hints.optString(i))
        }
       }
      }else "Sessão analisada pelo Coach."
      p.edit()
       .putString("lastSendStatus","ok")
       .putString("lastHints",text)
       .apply()
     }else{
      p.edit()
       .putString("lastSendStatus","error")
       .putString("lastHints","Sessão preservada no celular. Vercel respondeu HTTP ${r.code}.")
       .apply()
     }
    }catch(_:Exception){
     p.edit()
      .putString("lastSendStatus","error")
      .putString("lastHints","Sessão preservada no celular. Resposta do servidor não pôde ser interpretada.")
      .apply()
    }finally{
     r.close()
     cleanupAndStop()
    }
   }
  })
 }

 private fun cleanupAndStop(){
  try{projection?.stop()}catch(_:Exception){}
  projection=null
  stopForeground(STOP_FOREGROUND_REMOVE)
  stopSelf()
 }

 override fun onDestroy(){
  try{reader?.close()}catch(_:Exception){}
  try{display?.release()}catch(_:Exception){}
  try{projection?.stop()}catch(_:Exception){}
  recognizer.close()
  super.onDestroy()
 }
}
