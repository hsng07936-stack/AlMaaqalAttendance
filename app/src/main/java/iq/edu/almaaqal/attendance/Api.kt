package iq.edu.almaaqal.attendance

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ApiError(message: String, val code: Int? = null) : Exception(message)

/** عميل الخادم (Flask). نسخة واحدة للتطبيق كله، مثل Api.i في نسخة Flutter. */
object Api {
    const val DEFAULT_BASE = "https://att.alwazzanos.com"

    var base: String = DEFAULT_BASE
        private set
    var token: String? = null
    var user: JSONObject? = null

    val role: String get() = user?.optString("role").orEmpty()

    private lateinit var appContext: Context
    private val prefs get() = appContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // القراءات تُحفظ ثماني ثوانٍ، وأي كتابة تمسحها (كما في النسخة الأصلية)
    private val cache = HashMap<String, Pair<Long, Any?>>()
    private const val CACHE_MS = 8_000L

    fun init(ctx: Context) {
        appContext = ctx.applicationContext
        Session.init(appContext)
        val saved = prefs.getString("base", null)
        if (saved != null) {
            // العنوان المحلي القديم فقط يُستبدل؛ ما كتبه المستخدم يبقى
            if (saved.contains("172.17.1.81")) prefs.edit().remove("base").apply() else base = saved
        }
    }

    fun setBase(v: String) {
        base = v.trim().trimEnd('/')
        prefs.edit().putString("base", base).apply()
    }

    fun forgetCache() = cache.clear()

    fun url(path: String) = base + path
    val authHeaders: Map<String, String> get() = token?.let { mapOf("Authorization" to "Bearer $it") } ?: emptyMap()

    // ---------------------------------------------------------------- الطلبات

    private suspend fun send(method: String, path: String, body: JSONObject?): Any? = withContext(Dispatchers.IO) {
        try {
            val c = URL(base + path).openConnection() as HttpURLConnection
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Accept", "application/json")
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val status = c.responseCode
            val stream = if (status >= 400) c.errorStream else c.inputStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            c.disconnect()
            val parsed: Any? = parse(text)
            if (status >= 400) {
                val m = (parsed as? JSONObject)?.optString("error")?.takeIf { it.isNotEmpty() }
                throw ApiError(arabic(m) ?: "خطأ $status", status)
            }
            parsed
        } catch (e: ApiError) {
            throw e
        } catch (e: Exception) {
            throw ApiError("تعذر الاتصال بالسيرفر. تأكد من العنوان والشبكة.")
        }
    }

    private fun parse(text: String): Any? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return try {
            when (t[0]) {
                '{' -> JSONObject(t)
                '[' -> JSONArray(t)
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun get(path: String, fresh: Boolean = false): Any? {
        val hit = cache[path]
        if (!fresh && hit != null && System.currentTimeMillis() - hit.first < CACHE_MS) return hit.second
        val data = send("GET", path, null)
        cache[path] = System.currentTimeMillis() to data
        return data
    }

    suspend fun getObj(path: String, fresh: Boolean = false): JSONObject = (get(path, fresh) as? JSONObject) ?: JSONObject()
    suspend fun getList(path: String, fresh: Boolean = false): JSONArray = (get(path, fresh) as? JSONArray) ?: JSONArray()

    private suspend fun write(method: String, path: String, b: JSONObject?): Any? {
        cache.clear()
        return send(method, path, b ?: JSONObject())
    }

    suspend fun post(path: String, b: JSONObject? = null) = write("POST", path, b)
    suspend fun put(path: String, b: JSONObject) = write("PUT", path, b)
    suspend fun patch(path: String, b: JSONObject) = write("PATCH", path, b)
    suspend fun delete(path: String, b: JSONObject? = null) = write("DELETE", path, b)

    /** رفع ملف multipart. يُستعمل في ملفات المواد والوثائق (المرحلة التالية). */
    suspend fun upload(path: String, filename: String, bytes: ByteArray, fields: Map<String, String> = emptyMap()): Any? =
        withContext(Dispatchers.IO) {
            cache.clear()
            try {
                val boundary = "----att" + System.currentTimeMillis()
                val c = URL(base + path).openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 15_000
                c.readTimeout = 60_000
                c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
                val out = ByteArrayOutputStream()
                fun w(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
                for ((k, v) in fields) {
                    w("--$boundary\r\nContent-Disposition: form-data; name=\"$k\"\r\n\r\n$v\r\n")
                }
                w("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\n")
                w("Content-Type: application/octet-stream\r\n\r\n")
                out.write(bytes)
                w("\r\n--$boundary--\r\n")
                c.outputStream.use { it.write(out.toByteArray()) }
                val status = c.responseCode
                val text = (if (status >= 400) c.errorStream else c.inputStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
                c.disconnect()
                val parsed = parse(text)
                if (status >= 400) {
                    val m = (parsed as? JSONObject)?.optString("error")?.takeIf { it.isNotEmpty() }
                    throw ApiError(arabic(m) ?: "خطأ $status", status)
                }
                parsed
            } catch (e: ApiError) {
                throw e
            } catch (e: Exception) {
                throw ApiError("تعذر الاتصال بالسيرفر. تأكد من العنوان والشبكة.")
            }
        }

    /** جلب ملف (صورة مستمسك مثلًا) بالدخول الحالي. */
    suspend fun getBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        try {
            val c = URL(base + path).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            token?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            val status = c.responseCode
            if (status >= 400) { c.disconnect(); throw ApiError("خطأ $status", status) }
            val b = c.inputStream.use { it.readBytes() }
            c.disconnect()
            b
        } catch (e: ApiError) {
            throw e
        } catch (e: Exception) {
            throw ApiError("تعذّر الاتصال بالسيرفر. تأكد من العنوان والشبكة.")
        }
    }

    // ---------------------------------------------------------------- الدخول والحساب

    private fun adopt(r: Any?) {
        val o = r as? JSONObject ?: throw ApiError("رد غير متوقع من السيرفر")
        token = o.getString("token")
        user = o.getJSONObject("user")
        Session.saveSession(token!!, user!!)
    }

    suspend fun login(username: String, password: String) {
        adopt(post("/api/login", JSONObject().put("username", username).put("password", password)))
    }

    /** تسجيل ذاتي. يرجع true إذا كان الطلب بانتظار موافقة المدير (تدريسي). */
    suspend fun register(body: JSONObject): Boolean {
        val r = post("/api/register", body)
        if (r is JSONObject && r.optString("token").isNotEmpty()) {
            adopt(r)
            return false
        }
        return true
    }

    suspend fun enableBiometric(label: String): String =
        (post("/api/me/biometric", JSONObject().put("label", label)) as JSONObject).getString("device_token")

    suspend fun disableBiometric(deviceToken: String?) {
        delete("/api/me/biometric", JSONObject().apply { if (deviceToken != null) put("device_token", deviceToken) })
    }

    suspend fun loginDevice(deviceToken: String) {
        adopt(post("/api/login/device", JSONObject().put("device_token", deviceToken)))
    }

    suspend fun cardLookup(uid: String): JSONObject = post("/api/card/lookup", JSONObject().put("card_uid", uid)) as JSONObject

    suspend fun cardLogin(uid: String) {
        adopt(post("/api/card/login", JSONObject().put("card_uid", uid)))
    }

    suspend fun linkMyCard(uid: String) {
        post("/api/me/card", JSONObject().put("card_uid", uid))
        user = (user ?: JSONObject()).put("card_uid", uid)
        Session.saveSession(token!!, user!!)
    }

    suspend fun changePassword(old: String, next: String) {
        post("/api/me/password", JSONObject().put("old", old).put("new", next))
        user = (user ?: JSONObject()).put("must_change", 0)
        Session.saveSession(token!!, user!!)
    }

    /** يفتح الجلسة المحفوظة. true إذا بقيت صالحة. */
    suspend fun resume(): Boolean {
        val s = Session.restore() ?: return false
        token = s.first
        user = s.second
        return try {
            user = getObj("/api/me")
            true
        } catch (e: ApiError) {
            if (e.code == 401) {
                token = null
                user = null
                Push.stop(appContext)
                Session.clearSession() // انتهت أو أُلغيت من السيرفر
                // دخول تلقائي بالبيانات المحفوظة (إن اختار المستخدم «تذكرني»)
                val saved = Session.savedLogin()
                if (saved != null) {
                    try {
                        login(saved.first, saved.second)
                        true
                    } catch (_: Exception) {
                        false
                    }
                } else false
            } else true // خطأ سيرفر أو لا اتصال: نبقى داخل التطبيق
        }
    }

    suspend fun logout() {
        Push.stop(appContext)
        Inbox.reset()
        try {
            post("/api/logout")
        } catch (_: Exception) {
        }
        token = null
        user = null
        cache.clear()
        Session.clearSession()
    }

    private fun arabic(m: String?): String? {
        if (m == null) return null
        val map = mapOf(
            "bad credentials" to "اسم المستخدم أو كلمة المرور غير صحيحة",
            "unauthorized" to "انتهت الجلسة، سجّل الدخول من جديد",
            "forbidden" to "ليس لديك صلاحية لهذا الإجراء",
            "unknown card" to "البطاقة غير مسجلة لأي طالب",
            "student not enrolled in this course" to "الطالب غير مسجل في هذه المادة",
            "student not enrolled" to "الطالب غير مسجل في هذه المادة",
            "lecture ended" to "المحاضرة منتهية",
            "note (reason) required for manual change" to "اكتب سبب التعديل اليدوي",
            "username or card already used" to "اسم المستخدم أو رقم البطاقة مستخدم مسبقًا",
            "card already used" to "رقم البطاقة مستخدم مسبقًا",
            "password too short" to "كلمة المرور قصيرة (6 أحرف على الأقل)",
            "exists" to "موجود مسبقًا",
            "details do not match the student roster" to "البيانات لا تطابق كشف الطلاب. تأكد من الرقم المعرفي ومن كتابة اسمك الثلاثي كما في الكشف",
            "account already activated" to "هذا الحساب مفعّل مسبقًا. ادخل بكلمة مرورك، أو اطلب من المدير إعادة تعيينها",
            "unknown device" to "انتهت صلاحية الدخول بالبصمة. ادخل بكلمة المرور ثم فعّلها من جديد",
            "account not activated" to "حسابك لم يُفعَّل بعد",
            "card already linked" to "بطاقتك مربوطة بحسابك مسبقًا",
            "card already linked to another account" to "هذه البطاقة مربوطة بحساب آخر",
            "reference number and name, or a card, are required" to "اكتب الرقم المعرفي والاسم، أو قرّب بطاقتك",
            "username already used" to "اسم المستخدم مستخدم. اختر اسمًا آخر",
            "too many attempts, try again in a few minutes" to "محاولات كثيرة. حاول بعد دقائق",
        )
        return map[m] ?: m
    }
}
