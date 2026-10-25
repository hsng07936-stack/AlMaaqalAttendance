package iq.edu.almaaqal.attendance

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * صفحات تُعرض فوق التطبيق كله (محادثة، إشعارات، تبليغ جديد) من أي مكان دون ربطها بالتبويبات.
 * تُغلق بزر الرجوع. يُفرَّغ المكدس عند الدخول والخروج (App.kt).
 */
object Overlay {
    val stack = mutableStateListOf<@Composable (close: () -> Unit) -> Unit>()
    fun push(page: @Composable (close: () -> Unit) -> Unit) { stack.add(page) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun clear() = stack.clear()
}

/** عدّادات غير المقروء للجرس وتبويب الرسائل، تتجدد كل 30 ثانية ما دام المستخدم مسجّلًا (Inbox). */
object Inbox {
    var notifications by mutableIntStateOf(0)
    var threads by mutableIntStateOf(0)

    suspend fun refresh() {
        if (Api.token == null) return
        try {
            val r = Api.getObj("/api/unread", fresh = true)
            notifications = r.optInt("notifications")
            threads = r.optInt("threads")
            ExamSync.flush()
        } catch (_: Exception) {
        }
    }

    fun reset() { notifications = 0; threads = 0 }
}

/** إشعارات الهاتف بلا خدمات Google: يكتب الدخول في تفضيلات «push» ويشغّل [PushService] (Push في push.dart). */
object Push {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("push", Context.MODE_PRIVATE)

    fun start(ctx: Context) {
        val tok = Api.token ?: return
        try {
            val p = sp(ctx)
            if (p.getString("push_token", null) != tok) {
                p.edit().putString("push_token", tok).remove("push_after").apply() // دخول جديد يبدأ من «الآن»
            }
            p.edit().putString("push_base", Api.base).apply()
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                ctx.findActivity()?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 11) }
            }
            PushService.ensureChannels(ctx)
            ContextCompat.startForegroundService(ctx, Intent(ctx, PushService::class.java))
        } catch (_: Exception) {
        }
    }

    fun stop(ctx: Context) {
        try {
            sp(ctx).edit().remove("push_token").remove("push_base").remove("push_after").apply()
            ctx.stopService(Intent(ctx, PushService::class.java))
        } catch (_: Exception) {
        }
    }

    /** قائمة النظام لإعفاء التطبيق من قيود البطارية (بعض الشركات تقتل التطبيقات في الخلفية). */
    fun batterySettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }
}
