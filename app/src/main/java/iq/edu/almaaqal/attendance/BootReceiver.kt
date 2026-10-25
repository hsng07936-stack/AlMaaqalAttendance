package iq.edu.almaaqal.attendance

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** يعيد تشغيل خدمة الإشعارات بعد إقلاع الهاتف أو تحديث التطبيق إن كان أحد مسجّلًا. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (PushService.signedIn(context)) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, PushService::class.java))
            } catch (_: Exception) {
            }
        }
    }
}
