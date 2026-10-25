package iq.edu.almaaqal.attendance

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * الدخول ببصمة الهاتف أو الوجه (biometric.dart).
 * بعد دخول عادي يطلب الهاتف من السيرفر «رمز جهاز» ويحفظه مشفّرًا. لاحقًا يُفرج عن الرمز
 * بعد فحص البصمة، ويبدّله السيرفر بجلسة عادية. السيرفر يحفظ بصمة الرمز (hash) فقط،
 * ويلغيه عند تغيير كلمة المرور أو إيقاف الحساب.
 */
object Bio {
    private const val STRONG = BiometricManager.Authenticators.BIOMETRIC_STRONG

    /** الهاتف يدعم البصمة/الوجه ولديه واحدة مسجّلة على الأقل. */
    fun available(ctx: Context): Boolean = try {
        BiometricManager.from(ctx).canAuthenticate(STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    } catch (_: Exception) {
        false
    }

    /** الدخول بالبصمة مفعّل على هذا الهاتف. */
    fun enabled(): Boolean = Session.deviceToken != null

    private suspend fun prompt(activity: FragmentActivity, reason: String): Boolean =
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val bp = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(false) // إلغاء أو قفل أو لا مستشعر
                }
            })
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle("الحضور الجامعي")
                .setSubtitle(reason)
                .setNegativeButtonText("إلغاء")
                .setAllowedAuthenticators(STRONG)
                .build()
            bp.authenticate(info)
            cont.invokeOnCancellation { try { bp.cancelAuthentication() } catch (_: Exception) {} }
        }

    /** يفعّل الميزة للمستخدم الحالي. يرجع true إذا فُعّلت. */
    suspend fun enable(activity: FragmentActivity): Boolean {
        if (!available(activity)) return false
        if (!prompt(activity, "تأكيد تفعيل الدخول بالبصمة أو الوجه")) return false
        Session.deviceToken = Api.enableBiometric("android phone")
        return true
    }

    suspend fun disable() {
        val tok = Session.deviceToken
        try {
            Api.disableBiometric(tok)
        } catch (_: Exception) {
        } // بلا اتصال أو خرج بالفعل: ننساه على الهاتف على أي حال
        Session.deviceToken = null
    }

    /** يطلب البصمة ثم يسجّل الدخول. true إذا دخل. */
    suspend fun unlock(activity: FragmentActivity): Boolean {
        val tok = Session.deviceToken ?: return false
        if (!prompt(activity, "افتح التطبيق بالبصمة أو الوجه")) return false
        try {
            Api.loginDevice(tok)
            return true
        } catch (e: ApiError) {
            if (e.code == 401) Session.deviceToken = null // أُلغي: تغيّرت كلمة المرور أو أُوقف الحساب
            throw e
        }
    }
}
