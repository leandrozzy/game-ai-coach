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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class CaptureService:Service(){
 companion object{
  const val ACTION_FINISH="com.gameaicoach.FINISH_SESSION"
  const val VERCEL_BASE="https://game-ai-coach-indol.vercel.app"
 }
 private var projection:MediaProjection?=null
 private var display:VirtualDisplay?=null
 private var reader:ImageReader?=null
 private val framesText=CopyOnWriteArrayList<List<String>>()
 private val processing=AtomicBoolean(false)
 private var frames=0
 private var last=0L
 private var finishing=false
 private val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
 private val session=UUID.randomUUID().toString()

 override fun onBind(i:Intent?)=null

 override fun onCreate(){
  super.onCreate()
  Store.setCaptureActive(this,true)
  val ch="coach_capture"
  if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java)
   .createNotificationChannel(NotificationChannel(ch,"Coach ativo",NotificationManager.IMPORTANCE_LOW))
  startForeground(1001,Notification.Builder(this,ch)
   .setContentTitle("Game AI Coach ativo")
   .setContentText("Lendo e salvando sua sessão automaticamente.")
   .setSmallIcon(android.R.drawable.ic_menu_view).build())
 }

 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{
  if(i?.action==ACTION_FINISH){finishSession();return START_NOT_STICKY}
  val code=i?.getIntExtra("resultCode",Activity.RESULT_CANCELED)?:Activity.RESULT_CANCELED
  @Suppress("DEPRECATION")
  val data=if(Build.VERSION.SDK_INT>=33)i?.getParcelableExtra("data",Intent::class.java) else i?.getParcelableExtra("data")
  if(code==Activity.RESULT_OK&&data!=null)startProjection(code,data)
  return START_NOT_STICKY
 }

 private fun startProjection(code:Int,data:Intent){
  val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  projection=mgr.getMediaProjection(code,data)
  val dm=resources.displayMetrics
  val w=dm.widthPixels
  val h=dm.heightPixels
  reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2)
  display=projection?.createVirtualDisplay("CoachCapture",w,h,dm.densityDpi,
   DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,null)

  reader?.setOnImageAvailableListener({r->
   val now=System.currentTimeMillis()
   if(now-last<1200||processing.get()){r.acquireLatestImage()?.close();return@setOnImageAvailableListener}
   last=now
   val image=r.acquireLatestImage()?:return@setOnImageAvailableListener
   try{
    val p=image.planes[0]
    val ps=p.pixelStride
    val rs=p.rowStride
    val pad=rs-ps*w
    val bmp=Bitmap.createBitmap(w+pad/ps,h,Bitmap.Config.ARGB_8888)
    bmp.copyPixelsFromBuffer(p.buffer)
    frames++
    processing.set(true)
    recognizer.process(InputImage.fromBitmap(bmp,0))
     .addOnSuccessListener{t->
      val ls=t.textBlocks.flatMap{it.lines}.map{it.text.trim()}.filter{it.length>1}
      if(ls.isNotEmpty()){
       framesText.add(ls)
       if(frames%8==0){
        val game=Store.loadActiveGame(this)
        Store.saveCheckpoint(this,game,framesText.flatten(),frames)
       }
      }
      bmp.recycle()
      processing.set(false)
     }
     .addOnFailureListener{bmp.recycle();processing.set(false)}
   }finally{image.close()}
  },Handler(Looper.getMainLooper()))
 }

 private fun finishSession(){
  if(finishing)return
  finishing=true
  try{reader?.setOnImageAvailableListener(null,null)}catch(_:Exception){}
  try{reader?.close()}catch(_:Exception){}
  try{display?.release()}catch(_:Exception){}
  Handler(Looper.getMainLooper()).postDelayed({saveAndSendSession()},900)
 }

 private fun saveAndSendSession(){
  val game=Store.loadActiveGame(this)
  val previous=Store.loadSummary(this,game)
  val snap=framesText.toList()
  val lines=snap.flatten().distinct()

  if(lines.isEmpty()){
   Store.saveServerAnalysis(this,game,JSONObject()
    .put("ok",false).put("error","Nenhum texto foi capturado nesta sessão."))
   cleanup()
   return
  }

  val summary=GameParser.parse(game,snap)
  Store.saveSession(this,game,summary,lines,frames)

  val payload=JSONObject().apply{
   put("game",game)
   put("sessionId",session)
   put("screens",frames)
   put("lines",JSONArray(lines.take(2500)))
   put("localSummary",summary)
   if(previous!=null)put("previousSummary",previous)
   put("keys",Store.apiKeysJson(this@CaptureService))
  }

  Store.saveServerAnalysis(this,game,JSONObject().put("pending",true))
  val req=Request.Builder().url("$VERCEL_BASE/api/observe")
   .post(payload.toString().toRequestBody("application/json".toMediaType())).build()

  OkHttpClient.Builder().callTimeout(45,TimeUnit.SECONDS).build().newCall(req).enqueue(object:Callback{
   override fun onFailure(c:Call,e:IOException){
    Store.saveServerAnalysis(this@CaptureService,game,JSONObject()
     .put("ok",false).put("pending",false)
     .put("error","Sessão salva. A IA não respondeu agora; use REANALISAR ÚLTIMA SESSÃO."))
    cleanup()
   }
   override fun onResponse(c:Call,r:Response){
    val body=r.body?.string().orEmpty()
    if(r.isSuccessful){
     runCatching{Store.saveServerAnalysis(this@CaptureService,game,JSONObject(body))}
      .onFailure{Store.saveServerAnalysis(this@CaptureService,game,JSONObject()
       .put("ok",false).put("pending",false).put("error","Sessão salva; resposta da IA inválida."))}
    }else{
     Store.saveServerAnalysis(this@CaptureService,game,JSONObject()
      .put("ok",false).put("pending",false)
      .put("error","Sessão salva; servidor respondeu HTTP ${r.code}."))
    }
    r.close()
    cleanup()
   }
  })
 }

 private fun cleanup(){
  Store.setCaptureActive(this,false)
  try{projection?.stop()}catch(_:Exception){}
  stopForeground(STOP_FOREGROUND_REMOVE)
  stopSelf()
 }

 override fun onDestroy(){
  val game=Store.loadActiveGame(this)
  val lines=framesText.flatten()
  if(lines.isNotEmpty())Store.saveCheckpoint(this,game,lines,frames)
  Store.setCaptureActive(this,false)
  recognizer.close()
  super.onDestroy()
 }
}
