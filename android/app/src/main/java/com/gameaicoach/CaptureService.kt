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
 private val started=System.currentTimeMillis()

 override fun onBind(i:Intent?)=null

 override fun onCreate(){
  super.onCreate()
  Store.setCaptureActive(this,true)
  val ch="coach_capture"
  if(Build.VERSION.SDK_INT>=26){
   getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(ch,"Coach ativo",NotificationManager.IMPORTANCE_LOW))
  }
  val n=Notification.Builder(this,ch)
   .setContentTitle("Game AI Coach ativo")
   .setContentText("Observando as telas enquanto você joga.")
   .setSmallIcon(android.R.drawable.ic_menu_view)
   .setContentIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE))
   .build()
  startForeground(1001,n)
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
  projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){if(!finishing)finishSession()}},Handler(Looper.getMainLooper()))
  val dm=resources.displayMetrics
  val w=dm.widthPixels
  val h=dm.heightPixels
  reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2)
  display=projection?.createVirtualDisplay("CoachCapture",w,h,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,null)
  reader?.setOnImageAvailableListener({r->
   val now=System.currentTimeMillis()
   if(now-last<1500 || processing.get()){
    r.acquireLatestImage()?.close();return@setOnImageAvailableListener
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
    processing.set(true)
    recognizer.process(InputImage.fromBitmap(bmp,0))
     .addOnSuccessListener{t->
      val ls=t.textBlocks.flatMap{it.lines}.map{it.text.trim()}.filter{it.length>1}
      if(ls.isNotEmpty())framesText.add(ls)
      bmp.recycle();processing.set(false)
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
  reader=null
  try{display?.release()}catch(_:Exception){}
  display=null
  Handler(Looper.getMainLooper()).postDelayed({saveAndSendSession()},900)
 }

 private fun saveAndSendSession(){
  val game=Store.loadActiveGame(this)
  val previousSummary=Store.loadSummary(this,game)
  val summary=GameParser.parse(game,framesText.toList())
  val lines=framesText.flatten().distinct()
  Store.saveSession(this,game,summary,lines,frames)

  val payload=JSONObject().apply{
   put("game",game);put("sessionId",session);put("screens",frames)
   put("startedAt",started.toString());put("endedAt",System.currentTimeMillis().toString())
   put("lines",JSONArray(lines.take(2500)));put("localSummary",summary)
   if(previousSummary!=null)put("previousSummary",previousSummary)
  }
  val req=Request.Builder().url("$VERCEL_BASE/api/observe")
   .post(payload.toString().toRequestBody("application/json".toMediaType())).build()
  OkHttpClient.Builder().callTimeout(35,java.util.concurrent.TimeUnit.SECONDS).build().newCall(req).enqueue(object:Callback{
   override fun onFailure(c:Call,e:IOException){
    Store.saveServerAnalysis(this@CaptureService,game,JSONObject().put("ok",false).put("pending",false).put("error","Não foi possível consultar a IA agora. A sessão ficou salva no celular."))
    cleanupAndStop()
   }
   override fun onResponse(c:Call,r:Response){
    val body=r.body?.string().orEmpty()
    if(r.isSuccessful)runCatching{Store.saveServerAnalysis(this@CaptureService,game,JSONObject(body))}
    else Store.saveServerAnalysis(this@CaptureService,game,JSONObject().put("ok",false).put("error","Servidor respondeu HTTP ${r.code}."))
    r.close();cleanupAndStop()
   }
  })
 }

 private fun cleanupAndStop(){
  Store.setCaptureActive(this,false)
  try{projection?.stop()}catch(_:Exception){}
  projection=null
  stopForeground(STOP_FOREGROUND_REMOVE)
  stopSelf()
 }

 override fun onDestroy(){
  Store.setCaptureActive(this,false)
  try{reader?.close()}catch(_:Exception){}
  try{display?.release()}catch(_:Exception){}
  try{projection?.stop()}catch(_:Exception){}
  recognizer.close();super.onDestroy()
 }
}
