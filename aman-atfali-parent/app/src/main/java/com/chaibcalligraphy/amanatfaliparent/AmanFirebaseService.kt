package com.chaibcalligraphy.amanatfaliparent
import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
class AmanFirebaseService:FirebaseMessagingService(){
 companion object{const val CHANNEL_ID="aman_atfali_alerts"}
 override fun onNewToken(token:String){super.onNewToken(token);val p=getSharedPreferences("aft_parent",MODE_PRIVATE);val server=p.getString("server",null);val parent=p.getString("token",null);if(server.isNullOrBlank()||parent.isNullOrBlank())return;val device=p.getString("device_id",null)?:java.util.UUID.randomUUID().toString().also{p.edit().putString("device_id",it).apply()};Thread{try{val body=okhttp3.FormBody.Builder().add("fcm_token",token).add("device_id",device).build();val req=okhttp3.Request.Builder().url(server.trimEnd('/')+"/wp-json/aft/v1/parent-device").addHeader("X-AFT-PARENT-TOKEN",parent).post(body).build();okhttp3.OkHttpClient().newCall(req).execute().close()}catch(_:Exception){}}.start()}
 override fun onMessageReceived(m:RemoteMessage){super.onMessageReceived(m);showAlert(m.notification?.title?:m.data["title"]?:"أمان أطفالي",m.notification?.body?:m.data["body"]?:"وصل تنبيه جديد")}
 private fun showAlert(t:String,b:String){createChannel();val i=Intent(this,MainActivity::class.java).apply{flags=Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP};val p=PendingIntent.getActivity(this,1001,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);val n=NotificationCompat.Builder(this,CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle(t).setContentText(b).setStyle(NotificationCompat.BigTextStyle().bigText(b)).setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).setContentIntent(p).build();if(Build.VERSION.SDK_INT<33||androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)NotificationManagerCompat.from(this).notify((System.currentTimeMillis()%Int.MAX_VALUE).toInt(),n)}
 private fun createChannel(){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){val m=getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;if(m.getNotificationChannel(CHANNEL_ID)==null)m.createNotificationChannel(NotificationChannel(CHANNEL_ID,"تنبيهات أمان أطفالي",NotificationManager.IMPORTANCE_HIGH))}}
}