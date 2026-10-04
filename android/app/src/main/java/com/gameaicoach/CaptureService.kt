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
import android.util.DisplayMetrics
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class CaptureService:Service(){
 private var projection:MediaProjection?=null;private var display:VirtualDisplay?=null;private var reader:ImageReader?=null
 private val lines=ConcurrentHashMap.newKeySet<String>();private var frames=0;private var last=0L;private val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);private val session=UUID.randomUUID().toString();private val started=System.currentTimeMillis()
 override fun onBind(i:Intent?)=null
 override fun onCreate(){super.onCreate(); val ch="coach_capture";if(Build.VERSION.SDK_INT>=26){getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(ch,"Coach ativo",NotificationManager.IMPORTANCE_LOW))}; val n=Notification.Builder(this,ch).setContentTitle("Game AI Coach ativo").setContentText("Lendo telas periodicamente. Toque no Coach para finalizar.").setSmallIcon(android.R.drawable.ic_menu_view).setContentIntent(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)).build();startForeground(1001,n)}
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{ val code=i?.getIntExtra("resultCode",Activity.RESULT_CANCELED)?:Activity.RESULT_CANCELED; @Suppress("DEPRECATION") val data=if(Build.VERSION.SDK_INT>=33)i?.getParcelableExtra("data",Intent::class.java) else i?.getParcelableExtra("data"); if(code==Activity.RESULT_OK&&data!=null)startProjection(code,data);return START_NOT_STICKY }
 private fun startProjection(code:Int,data:Intent){val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager;projection=mgr.getMediaProjection(code,data); projection?.registerCallback(object:MediaProjection.Callback(){ override fun onStop(){ stopSelf() } }, Handler(Looper.getMainLooper())); val dm=resources.displayMetrics;val w=dm.widthPixels;val h=dm.heightPixels;reader=ImageReader.newInstance(w,h,PixelFormat.RGBA_8888,2);display=projection?.createVirtualDisplay("CoachCapture",w,h,dm.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,null);reader?.setOnImageAvailableListener({r-> val now=System.currentTimeMillis(); if(now-last<5000){r.acquireLatestImage()?.close();return@setOnImageAvailableListener};last=now; val image=r.acquireLatestImage()?:return@setOnImageAvailableListener; try{val plane=image.planes[0];val buf=plane.buffer;val ps=plane.pixelStride;val rs=plane.rowStride;val pad=rs-ps*w;val bmp=Bitmap.createBitmap(w+pad/ps,h,Bitmap.Config.ARGB_8888);bmp.copyPixelsFromBuffer(buf);frames++;recognizer.process(InputImage.fromBitmap(bmp,0)).addOnSuccessListener{t->t.textBlocks.flatMap{it.lines}.map{it.text.trim()}.filter{it.length>1}.forEach{lines.add(it)};bmp.recycle()}.addOnFailureListener{bmp.recycle()}}finally{image.close()}},Handler(Looper.getMainLooper()))}
 override fun onDestroy(){sendSession(); reader?.close();display?.release();projection?.stop();recognizer.close();super.onDestroy()}
 private fun sendSession(){val p=getSharedPreferences("coach",MODE_PRIVATE);val base=p.getString("url","")?.trimEnd('/')?:"";if(base.isBlank())return;val game=p.getString("game","Jogo")?:"Jogo";val json=JSONObject().put("game",game).put("sessionId",session).put("screens",frames).put("startedAt",started.toString()).put("endedAt",System.currentTimeMillis().toString()).put("lines",JSONArray(lines.toList().take(2500))).toString();val req=Request.Builder().url("$base/api/observe").post(json.toRequestBody("application/json".toMediaType())).build();OkHttpClient().newCall(req).enqueue(object:Callback{override fun onFailure(c:Call,e:java.io.IOException){};override fun onResponse(c:Call,r:Response){r.close()}})}
}
