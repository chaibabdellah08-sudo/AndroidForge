package com.chaibcalligraphy.amanatfaliparent

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.activity.ComponentActivity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Timer
import java.util.TimerTask

class MapActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var info: TextView
    private var server = ""
    private var token = ""
    private var timer: Timer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        web = findViewById(R.id.mapWeb)
        info = findViewById(R.id.mapInfo)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()

        server = intent.getStringExtra("SERVER").orEmpty()
        token = intent.getStringExtra("TOKEN").orEmpty()
        if (server.isBlank() || token.isBlank()) {
            info.text = "بيانات الاتصال غير مكتملة"
            return
        }

        refresh()
        timer = Timer()
        timer?.schedule(object : TimerTask() {
            override fun run() {
                runOnUiThread { refresh() }
            }
        }, 30_000L, 30_000L)
    }

    private fun refresh() {
        Api.get(server, "/wp-json/aft/v1/parent-location", token) { ok, body ->
            runOnUiThread {
                if (!ok) {
                    info.text = "تعذر جلب الموقع: $body"
                    return@runOnUiThread
                }

                try {
                    val location = JSONObject(body)
                    if (!location.optBoolean("has_location", false)) {
                        info.text = "لم يصل موقع الطفل بعد"
                        return@runOnUiThread
                    }

                    val lat = location.getDouble("latitude")
                    val lng = location.getDouble("longitude")
                    val accuracy = location.optDouble("accuracy", 0.0)
                    val battery = location.optInt("battery", -1)
                    val updated = location.optString("updated_at", "—")

                    Api.get(server, "/wp-json/aft/v1/parent-route?limit=300", token) { _, routeBody ->
                        val points = try {
                            JSONObject(routeBody).optJSONArray("points") ?: JSONArray()
                        } catch (_: Exception) {
                            JSONArray()
                        }

                        Api.get(server, "/wp-json/aft/v1/parent-zone", token) { _, zoneBody ->
                            val zone = try {
                                JSONObject(zoneBody).optJSONObject("zone")
                            } catch (_: Exception) {
                                null
                            }

                            Api.get(server, "/wp-json/aft/v1/parent-alerts", token) { _, alertBody ->
                                runOnUiThread {
                                    var alertText = ""
                                    try {
                                        val alerts = JSONObject(alertBody).optJSONArray("alerts") ?: JSONArray()
                                        if (alerts.length() > 0) {
                                            alertText = " • ⚠ " +
                                                alerts.getJSONObject(0).optString("message", "تنبيه جديد")
                                        }
                                    } catch (_: Exception) {
                                    }

                                    info.text = "آخر تحديث: $updated • البطارية: " +
                                        if (battery >= 0) "$battery%" else "—" + alertText

                                    web.loadDataWithBaseURL(
                                        "https://www.chaibcalligraphy.com/",
                                        buildMapHtml(lat, lng, accuracy, points, zone),
                                        "text/html",
                                        "UTF-8",
                                        null
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    info.text = "بيانات الموقع غير صالحة"
                }
            }
        }
    }

    private fun buildMapHtml(
        lat: Double,
        lng: Double,
        accuracy: Double,
        points: JSONArray,
        zone: JSONObject?
    ): String {
        val pointsJson = points.toString()
        val zoneJson = zone?.toString() ?: "null"
        val safeAccuracy = accuracy.coerceAtLeast(5.0)

        return """
            <!doctype html>
            <html lang="ar" dir="rtl">
            <head>
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
              <style>
                html,body,#m{height:100%;margin:0}
                .badge{position:absolute;z-index:9999;top:12px;right:12px;background:#fff;
                padding:10px 14px;border-radius:14px;box-shadow:0 3px 15px #0002;
                font-family:sans-serif}
              </style>
            </head>
            <body>
              <div id="m"></div>
              <div class="badge">أمان أطفالي</div>
              <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
              <script>
                const lat = $lat;
                const lng = $lng;
                const accuracy = $safeAccuracy;
                const points = $pointsJson;
                const zone = $zoneJson;

                const map = L.map('m').setView([lat, lng], 16);
                L.tileLayer(
                  'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
                  {maxZoom:19, attribution:'© OpenStreetMap'}
                ).addTo(map);

                L.marker([lat,lng]).addTo(map)
                  .bindPopup('آخر موقع للطفل').openPopup();

                L.circle([lat,lng], {
                  radius: accuracy, color:'#2563eb', fillOpacity:.12
                }).addTo(map);

                if (Array.isArray(points) && points.length > 1) {
                  const line = points
                    .map(p => [Number(p.latitude), Number(p.longitude)])
                    .filter(p => Number.isFinite(p[0]) && Number.isFinite(p[1]));
                  if (line.length > 1) {
                    L.polyline(line, {weight:5, opacity:.85}).addTo(map);
                  }
                }

                if (zone && Number.isFinite(Number(zone.latitude)) &&
                    Number.isFinite(Number(zone.longitude))) {
                  L.circle(
                    [Number(zone.latitude), Number(zone.longitude)],
                    {
                      radius: Number(zone.radius) || 200,
                      color:'#16a34a',
                      fillOpacity:.08,
                      weight:3
                    }
                  ).addTo(map).bindPopup(
                    'المنطقة الآمنة: ' + (zone.name || 'المنزل')
                  );
                }
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    override fun onDestroy() {
        timer?.cancel()
        timer = null
        super.onDestroy()
    }
}
