package com.chaibcalligraphy.amanatfalichild

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.graphics.Bitmap
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import org.json.JSONObject
import java.util.UUID

class MainActivity:ComponentActivity(){
    private lateinit var server:EditText; private lateinit var name:EditText; private lateinit var qr:ImageView
    private lateinit var code:TextView; private lateinit var status:TextView; private lateinit var start:Button
    private var childCode=""; private var base=""
    override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main)
        server=findViewById(R.id.server);name=findViewById(R.id.childName);qr=findViewById(R.id.qr);code=findViewById(R.id.code);status=findViewById(R.id.status);start=findViewById(R.id.start)
        val p=getSharedPreferences("aft",0); base=p.getString("server",server.text.toString())?:server.text.toString()
        server.setText(base); childCode=p.getString("code","")?:""; if(childCode.isNotBlank()){showCode(childCode);start.isEnabled=true}
        findViewById<Button>(R.id.create).setOnClickListener{createCode()}
        start.setOnClickListener{requestAndStart()}
    }
    private fun createCode(){
        base=server.text.toString().trim().trimEnd('/'); if(base.isBlank()){status.text="أدخل رابط الموقع";return}
        val c=(100000..999999).random().toString(); childCode=c
        val device=UUID.randomUUID().toString()
        getSharedPreferences("aft",0).edit().putString("server",base).putString("code",c).putString("device_id",device).apply()
        Api.post(base,"/wp-json/aft/v1/child-link",JSONObject().apply{put("code",c);put("device_id",device);put("name",name.text.toString().ifBlank{"طفل"})}.toString()){ok,body->
            runOnUiThread{if(ok){showCode(c);status.text="رمز الربط جاهز — اعرض QR لولي الأمر";start.isEnabled=true}else status.text="تعذر تسجيل جهاز الطفل: $body"}}
    }
    private fun showCode(c:String){code.text="رمز الربط: $c";try{val m=MultiFormatWriter().encode("AFT|$c",BarcodeFormat.QR_CODE,700,700);qr.setImageBitmap(toBitmap(m))}catch(_:Exception){}}
    private fun toBitmap(m:BitMatrix):Bitmap{val w=m.width;val h=m.height;val b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);for(x in 0 until w)for(y in 0 until h)b.setPixel(x,y,if(m[x,y])0xFF111827.toInt() else 0xFFFFFFFF.toInt());return b}
    private fun requestAndStart(){
        val perms=mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
        if(Build.VERSION.SDK_INT>=33)perms.add(Manifest.permission.POST_NOTIFICATIONS)
        ActivityCompat.requestPermissions(this,perms.toTypedArray(),42)
        Handler(Looper.getMainLooper()).postDelayed({
            if(ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
               ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED){
                ContextCompat.startForegroundService(this,Intent(this,LocationService::class.java).putExtra("SERVER",base).putExtra("CODE",childCode))
                status.text="🟢 مشاركة الموقع مفعلة"
            } else status.text="يجب السماح بالموقع"
        },700)
    }
}