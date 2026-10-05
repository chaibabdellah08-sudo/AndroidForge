package com.chaibcalligraphy.amanatfalichild

import java.net.HttpURLConnection
import java.net.URL

object Api {
    fun post(base:String, path:String, json:String, done:(Boolean,String)->Unit) {
        Thread {
            try {
                val u=URL(base.trimEnd('/')+path)
                val c=u.openConnection() as HttpURLConnection
                c.requestMethod="POST"; c.connectTimeout=15000; c.readTimeout=15000
                c.setRequestProperty("Content-Type","application/json")
                c.doOutput=true
                c.outputStream.use { it.write(json.toByteArray()) }
                val text=(if(c.responseCode in 200..299)c.inputStream else c.errorStream).bufferedReader().readText()
                done(c.responseCode in 200..299,text)
                c.disconnect()
            } catch(e:Exception){ done(false,e.message ?: "network error") }
        }.start()
    }
}