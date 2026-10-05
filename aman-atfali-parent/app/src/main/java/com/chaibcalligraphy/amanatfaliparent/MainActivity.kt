package com.chaibcalligraphy.amanatfaliparent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

class MainActivity:ComponentActivity(){
 private lateinit var server:EditText
 private lateinit var result:TextView
 private lateinit var status:TextView
 private lateinit var mapButton:Button

 private val galleryQrLauncher=registerForActivityResult(ActivityResultContracts.GetContent()){uri->
  if(uri==null)return@registerForActivityResult
  status.text="جارٍ قراءة رمز QR من الصورة…"
  try{
   val image=InputImage.fromFilePath(this,uri)
   BarcodeScanning.getClient().process(image)
    .addOnSuccessListener{codes->
      val raw=codes.firstOrNull()?.rawValue
      if(raw.isNullOrBlank()){
       status.text="لم يتم العثور على QR صالح في الصورة."
      }else handleQr(raw)
    }
    .addOnFailureListener{status.text="تعذر قراءة الصورة. اختر صورة QR واضحة."}
  }catch(e:Exception){status.text="تعذر فتح الصورة من المعرض."}
 }

 private var code:String?=null
 private var token:String?=null

 override fun onCreate(b:Bundle?){
  super.onCreate(b)
  setContentView(R.layout.activity_main)
  requestNotificationPermission()

  server=findViewById(R.id.serverUrl)
  result=findViewById(R.id.scanResult)
  status=findViewById(R.id.status)
  mapButton=findViewById(R.id.mapButton)

  val prefs=getSharedPreferences("aft_parent",MODE_PRIVATE)
  server.setText(prefs.getString("server","https://www.chaibcalligraphy.com"))
  token=prefs.getString("token",null)
  code=prefs.getString("code",null)

  if(token!=null){
   registerPushToken()
   result.text="تم ربط الطفل سابقًا"
   status.text="الجلسة محفوظة ويمكن متابعة الموقع."
   mapButton.isEnabled=true
  }

  findViewById<Button>(R.id.scanButton).setOnClickListener{
   if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
    ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.CAMERA),41)
   else startActivityForResult(Intent(this,ScannerActivity::class.java),42)
  }

  findViewById<Button>(R.id.galleryButton).setOnClickListener{
   galleryQrLauncher.launch("image/*")
  }

  mapButton.setOnClickListener{
   val c=code?:return@setOnClickListener
   val t=token?:return@setOnClickListener
   startActivity(Intent(this,MapActivity::class.java).apply{
    putExtra("SERVER",server.text.toString().trimEnd('/'))
    putExtra("CODE",c)
    putExtra("TOKEN",t)
   })
  }
 }

 private fun requestNotificationPermission(){
  if(Build.VERSION.SDK_INT>=33&&ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
   ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.POST_NOTIFICATIONS),72)
 }

 private fun handleQr(raw:String){
  val parsed=Api.parseQr(raw)
  if(parsed==null){
   status.text="رمز QR غير صالح. استخدم رمز أمان أطفالي."
   return
  }
  val base=server.text.toString().trim().trimEnd('/')
  if(!base.startsWith("http")){
   status.text="أدخل رابط WordPress صحيحًا."
   return
  }
  result.text="تمت قراءة QR… جارٍ ربط الطفل"
  status.text="الاتصال بالخادم…"
  Api.parentLink(base,parsed){ok,body->
   runOnUiThread{
    if(!ok){status.text="فشل الربط: $body";return@runOnUiThread}
    try{
     val o=org.json.JSONObject(body)
     val newToken=o.getString("token")
     val child=o.getJSONObject("child")
     code=parsed
     token=newToken
     getSharedPreferences("aft_parent",MODE_PRIVATE).edit()
      .putString("server",base).putString("code",parsed).putString("token",newToken).apply()
     result.text="تم ربط الطفل: "+child.optString("name","الطفل")
     status.text="تم إنشاء جلسة وليّ الأمر بنجاح."
     mapButton.isEnabled=true
     registerPushToken()
    }catch(e:Exception){status.text="استجابة الخادم غير صالحة."}
   }
  }
 }

 private fun registerPushToken(){
  val parent=token?:return
  val base=server.text.toString().trimEnd('/')
  FirebaseMessaging.getInstance().token.addOnSuccessListener{fcm->
   val prefs=getSharedPreferences("aft_parent",MODE_PRIVATE)
   val device=prefs.getString("device_id",null)?:java.util.UUID.randomUUID().toString().also{
    prefs.edit().putString("device_id",it).apply()
   }
   Thread{
    try{
     val body=FormBody.Builder().add("fcm_token",fcm).add("device_id",device).build()
     val req=Request.Builder().url(base+"/wp-json/aft/v1/parent-device")
      .addHeader("X-AFT-PARENT-TOKEN",parent).post(body).build()
     OkHttpClient().newCall(req).execute().close()
    }catch(_:Exception){}
   }.start()
  }
 }

 override fun onActivityResult(r:Int,c:Int,d:Intent?){
  super.onActivityResult(r,c,d)
  if(r==42&&c==RESULT_OK){
   val raw=d?.getStringExtra("QR_CODE")?:return
   handleQr(raw)
  }
 }
}