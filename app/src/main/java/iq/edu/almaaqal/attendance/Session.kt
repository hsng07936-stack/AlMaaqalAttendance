package iq.edu.almaaqal.attendance

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/**
 * يُبقي المستخدم مسجلًا بين مرات التشغيل، ويتذكر اسم المستخدم وكلمة المرور إن اختار ذلك.
 * كل شيء في التخزين المشفّر للهاتف (Keystore). أي مشكلة في التخزين تُبتلع،
 * فلا تمنع التطبيق من الفتح (كما في نسخة Flutter).
 */
object Session {
    private const val T = "session_token"
    private const val U = "session_user"
    private const val N = "saved_username"
    private const val P = "saved_password"
    private const val DEVICE = "bio_device_token"

    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        sp = try {
            val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            EncryptedSharedPreferences.create(
                ctx, "secure_session", key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun read(k: String): String? = try { sp?.getString(k, null) } catch (_: Exception) { null }
    private fun write(k: String, v: String) { try { sp?.edit()?.putString(k, v)?.apply() } catch (_: Exception) {} }
    private fun delete(k: String) { try { sp?.edit()?.remove(k)?.apply() } catch (_: Exception) {} }

    fun saveSession(token: String, user: JSONObject) {
        write(T, token)
        write(U, user.toString())
    }

    fun restore(): Pair<String, JSONObject>? {
        val t = read(T) ?: return null
        val u = read(U) ?: return null
        return try { t to JSONObject(u) } catch (_: Exception) { null }
    }

    /** تسجيل الخروج: ننسى الجلسة ونبقي اسم المستخدم وكلمة المرور المتذكَّرين. */
    fun clearSession() {
        delete(T)
        delete(U)
    }

    fun saveLogin(username: String, password: String, remember: Boolean) {
        if (remember) {
            write(N, username)
            write(P, password)
        } else forgetLogin()
    }

    fun savedLogin(): Pair<String, String>? {
        val n = read(N) ?: return null
        val p = read(P) ?: return null
        return n to p
    }

    fun forgetLogin() {
        delete(N)
        delete(P)
    }

    var bioAsked: Boolean
        get() = read("bio_asked") == "1"
        set(v) = write("bio_asked", if (v) "1" else "0")

    // رمز الجهاز للدخول بالبصمة (المرحلة التالية)
    var deviceToken: String?
        get() = read(DEVICE)
        set(v) { if (v == null) delete(DEVICE) else write(DEVICE, v) }
}
