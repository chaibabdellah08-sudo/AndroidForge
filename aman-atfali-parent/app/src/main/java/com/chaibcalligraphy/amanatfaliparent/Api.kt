package com.chaibcalligraphy.amanatfaliparent
import okhttp3.*
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
object Api {
 private val client=OkHttpClient.Builder().connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).build()
 fun parentLink(base:String,code:String,callback:(Boolean,String)->Unit){execute(Request.Builder().url(base.trimEnd('/')+"/wp-json/aft/v1/parent-link").post(FormBody.Builder().add("code",code).build()).build(),callback)}
 fun get(base:String,path:String,token:String?,callback:(Boolean,String)->Unit){val b=Request.Builder().url(base.trimEnd('/')+path).get();if(!token.isNullOrBlank())b.addHeader("X-AFT-PARENT-TOKEN",token);execute(b.build(),callback)}
 private fun execute(req:Request,callback:(Boolean,String)->Unit){Thread{try{client.newCall(req).execute().use{r->callback(r.isSuccessful,r.body?.string()?:"")}}catch(e:Exception){callback(false,e.message?:"خطأ في الاتصال")}}.start()}
 fun parseQr(value:String):String?{if(!value.startsWith("AFT|"))return null;val p=value.split("|");return if(p.size>=2&&p[1].isNotBlank())p[1]else null}
 fun encode(v:String)=URLEncoder.encode(v,"UTF-8")
}