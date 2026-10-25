package iq.edu.almaaqal.attendance

import android.app.Notification
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * خدمة خفيفة تبقى في المقدمة وتسأل السيرفر كل 30 ثانية عن إشعارات جديدة وتعرض كل واحد كإشعار على الهاتف
 * (بلا خدمات Google). عنوان السيرفر ورمز الدخول يكتبهما [Push] في ملف التفضيلات «push»،
 * وتتوقف الخدمة وحدها حين يُحذفان (تسجيل الخروج).
 */
class PushService : Service() {
    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannels(this)
        val ongoing = NotificationCompat.Builder(this, CH_BG)
            .setSmallIcon(R.drawable.ic_stat)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentTitle("الحضور الجامعي")
            .setContentText("يستقبل الإشعارات")
            .setOngoing(true)
            .setContentIntent(openApp(this))
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, ongoing, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, ongoing)
        if (!running) {
            running = true
            worker = Thread {
                while (running) {
                    try { if (!poll()) break } catch (_: Exception) {}
                    try { Thread.sleep(30_000) } catch (_: InterruptedException) { break }
                }
                running = false
                stopSelf()
            }.also { it.isDaemon = true; it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        worker?.interrupt()
        super.onDestroy()
    }

    /** يرجع false إذا لم يعد هناك دخول (فتتوقف الخدمة). */
    private fun poll(): Boolean {
        val prefs = getSharedPreferences("push", Context.MODE_PRIVATE)
        val base = prefs.getString("push_base", null)
        val token = prefs.getString("push_token", null)
        if (base.isNullOrEmpty() || token.isNullOrEmpty()) return false
        val after = prefs.getString("push_after", null)?.toLongOrNull() ?: -1L
        val conn = URL("$base/api/notifications/new?after=$after").openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Authorization", "Bearer $token")
        try {
            if (conn.responseCode == 401) return false
            if (conn.responseCode != 200) return true
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val maxId = json.optLong("max_id", after)
            if (after >= 0) { // أول مرة بعد دخول جديد: لا نعرض سيلًا من الإشعارات القديمة
                val items = json.optJSONArray("items")
                if (items != null) for (i in 0 until items.length()) {
                    val o = items.getJSONObject(i)
                    show(o.getLong("id").toInt(), o.optString("title"), o.optString("body"))
                }
            }
            prefs.edit().putString("push_after", maxId.toString()).apply()
        } finally {
            conn.disconnect()
        }
        return true
    }

    private fun show(id: Int, title: String, body: String) {
        val n = NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_stat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openApp(this))
            .build()
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1000 + id, n)
    }

    companion object {
        const val CH_BG = "bg"
        const val CH_ALERT = "alerts"

        fun ensureChannels(c: Context) {
            if (Build.VERSION.SDK_INT < 26) return // القنوات من أندرويد 8، وما قبله لا يحتاجها
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CH_BG, "عمل التطبيق في الخلفية", NotificationManager.IMPORTANCE_MIN))
            nm.createNotificationChannel(NotificationChannel(CH_ALERT, "إشعارات الحضور", NotificationManager.IMPORTANCE_HIGH))
        }

        fun openApp(c: Context): PendingIntent {
            val i = c.packageManager.getLaunchIntentForPackage(c.packageName)
            return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun signedIn(c: Context): Boolean =
            !c.getSharedPreferences("push", Context.MODE_PRIVATE).getString("push_token", null).isNullOrEmpty()
    }
}
