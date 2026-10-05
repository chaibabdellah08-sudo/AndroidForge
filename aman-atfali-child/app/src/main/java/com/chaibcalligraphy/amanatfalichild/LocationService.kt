package com.chaibcalligraphy.amanatfalichild

import android.app.*
import android.content.*
import android.location.*
import android.os.*
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.util.UUID

class LocationService : Service(), LocationListener {
    private lateinit var lm: LocationManager
    private lateinit var server:String
    private lateinit var code:String
    private lateinit var deviceId:String
    private var lastLocation:Location?=null

    override fun onStartCommand(i:Intent?, flags:Int, startId:Int):Int {
        server=i?.getStringExtra("SERVER").orEmpty()
        code=i?.getStringExtra("CODE").orEmpty()
        deviceId=getSharedPreferences("aft",0).getString("device_id",null) ?: UUID.randomUUID().toString().also {
            getSharedPreferences("aft",0).edit().putString("device_id",it).apply()
        }
        createChannel()
        startForeground(1001, NotificationCompat.Builder(this,"aft_location")
            .setContentTitle("أمان أطفالي").setContentText("مشاركة الموقع مفعلة").setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true).build())
        lm=getSystemService(LocationManager::class.java)
        try {
            if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=0) return START_NOT_STICKY
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,15000L,10f,this)
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,30000L,25f,this)
        } catch(_:Exception){}
        return START_STICKY
    }

    override fun onLocationChanged(l:Location) {
        lastLocation=l
        val battery=(getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)).coerceIn(0,100)
        val body=JSONObject().apply {
            put("code",code); put("device_id",deviceId); put("latitude",l.latitude); put("longitude",l.longitude)
            put("accuracy",l.accuracy.toDouble()); put("battery",battery)
        }.toString()
        Api.post(server,"/wp-json/aft/v1/child-location",body){_,_->}
    }
    override fun onBind(i:Intent?)=null
    override fun onProviderEnabled(p:String){}
    override fun onProviderDisabled(p:String){}
    override fun onStatusChanged(p:String,s:Int,e:Bundle?){}
    private fun createChannel(){ if(Build.VERSION.SDK_INT>=26){getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("aft_location","مشاركة الموقع",NotificationManager.IMPORTANCE_LOW))}}
    override fun onDestroy(){try{lm.removeUpdates(this)}catch(_:Exception){};super.onDestroy()}
}